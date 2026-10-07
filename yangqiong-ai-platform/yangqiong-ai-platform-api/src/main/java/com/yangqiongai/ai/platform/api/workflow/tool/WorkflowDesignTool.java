/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.platform.api.workflow.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.data.workflow.service.WorkflowDefinitionService;
import com.yangqiongai.ai.workflow.executor.WorkflowAgentExecutor;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.spi.NotifyChannelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流设计工具
 * <p>
 * 供"流程设计助手"Agent 调用，负责工作流定义 JSON 的校验与查询，
 * 生成或修改流程时确保定义合法且可复用现有流程。
 * </p>
 * @author yangqiong
 */
@Component
public class WorkflowDesignTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDesignTool.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private WorkflowAgentExecutor workflowExecutor;

    @Autowired
    private WorkflowDefinitionService definitionDbService;

    @Autowired
    @Lazy
    private AgentManager agentManager;

    /**
     * 渠道校验（企业版装配，社区版缺失时跳过渠道真实性校验）
     */
    @Autowired
    private ObjectProvider<NotifyChannelValidator> notifyChannelValidatorProvider;

    /**
     * 校验工作流定义JSON
     * <p>
     * 生成流程后必须调用本工具校验，校验通过后按传入的 definitionJson 原样输出给用户。
     * </p>
     * @param definitionJson
     * @return
     */
    @AgentTool("校验工作流定义JSON是否符合规范。生成或修改流程后必须调用本工具校验，校验失败时按返回的错误修正后重新校验，通过后把传入的definitionJson原样输出。")
    public String validate_workflow_definition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return "校验失败：definitionJson 不能为空";
        }
        try {
            WorkflowDefinition definition = objectMapper.readValue(definitionJson, WorkflowDefinition.class);
            workflowExecutor.topologicalSort(definition);
            if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
                return "校验失败：nodes 不能为空，至少包含一个节点";
            }
            // 校验AGENT节点引用的agentCode必须真实存在，禁止虚构不存在的智能体编码
            // 校验NOTIFY节点引用的渠道必须真实存在（企业版装配渠道校验时）
            List<WorkflowNode> nodes = definition.getNodes();
            NotifyChannelValidator notifyValidator = notifyChannelValidatorProvider.getIfAvailable();
            for (WorkflowNode node : nodes) {
                if (node.getType() == NodeType.AGENT) {
                    String agentCode = node.getConfigString("agentCode");
                    if (agentCode == null || agentCode.isBlank()) {
                        return "校验失败：AGENT节点[" + node.getName() + "]未配置agentCode，请配置为真实存在的智能体编码（未指定时使用default）";
                    }
                    try {
                        Agent agent = agentManager.getByCode(agentCode);
                        if (agent == null) {
                            return "校验失败：AGENT节点[" + node.getName() + "]配置的agentCode=" + agentCode + " 不存在，禁止虚构智能体编码，请改为真实存在的编码（如default）或用户明确指定的编码";
                        }
                    } catch (Exception e) {
                        // 数据库不可用时降级跳过agentCode校验，避免阻塞流程生成
                        log.warn("校验agentCode时数据库不可用，降级跳过: agentCode={}", agentCode, e);
                    }
                } else if (node.getType() == NodeType.NOTIFY) {
                    String channelId = node.getConfigString("channelId");
                    if (channelId == null || channelId.isBlank()) {
                        return "校验失败：NOTIFY节点[" + node.getName() + "]未配置channelId，请使用集成渠道管理中已配置的渠道ID";
                    }
                    if (notifyValidator != null) {
                        String channelError = notifyValidator.validate(channelId);
                        if (channelError != null) {
                            return "校验失败：NOTIFY节点[" + node.getName() + "]的channelId=" + channelId + " 校验不通过：" + channelError;
                        }
                    }
                } else if (node.getType() == NodeType.TIME_CONTROL) {
                    if (node.getTimeControlConfig() == null) {
                        return "校验失败：TIME_CONTROL节点[" + node.getName() + "]未配置timeControlConfig，请配置时间模式（DELAY/COUNTDOWN/SPECIFIC/PERIODIC）及对应参数";
                    }
                }
            }
            return "校验通过，该工作流定义合法可直接使用，定义JSON如下，请原样输出给用户：" + definitionJson;
        } catch (Exception e) {
            log.debug("工作流定义校验失败: {}", e.getMessage());
            return "校验失败：" + e.getMessage() + "，请修正 definitionJson 后重新调用本工具校验";
        }
    }

    /**
     * 查询现有工作流定义JSON
     * @param name
     * @return
     */
    @AgentTool("按名称查询现有工作流定义JSON，用于修改已有流程。工作流不存在时返回空结果。")
    public String get_workflow_definition(String name) {
        if (name == null || name.isBlank()) {
            return "查询失败：工作流名称不能为空";
        }
        try {
            WorkflowDefinition definition = definitionDbService.loadByName(name);
            if (definition == null) {
                return "工作流 '" + name + "' 不存在，可生成一个全新的工作流定义";
            }
            return objectMapper.writeValueAsString(definition);
        } catch (Exception e) {
            log.warn("查询工作流定义失败: name={}", name, e);
            return "查询失败：" + e.getMessage();
        }
    }
}
