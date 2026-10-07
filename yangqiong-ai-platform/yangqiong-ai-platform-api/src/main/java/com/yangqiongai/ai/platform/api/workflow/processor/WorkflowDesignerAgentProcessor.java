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
package com.yangqiongai.ai.platform.api.workflow.processor;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.processor.AbstractAgentProcessor;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.workflow.config.WorkflowJsonSchema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流程设计助手
 * <p>
 * 根据用户自然语言需求生成或修改工作流定义，通过工作流设计工具完成定义校验，
 * 最终以 JSON 代码块输出完整的工作流定义供前端应用到画布。
 * </p>
 * @author yangqiong
 */
@Component
public class WorkflowDesignerAgentProcessor extends AbstractAgentProcessor {

    private static final String SYSTEM_PROMPT = String.format("""
            你是工作流流程设计助手，负责把用户的自然语言需求转换为合法的工作流定义 JSON。

            工作流定义 JSON 结构（严格遵循）：
            %s

            节点配置（config）属性说明：
            %s

            工作流设计规则：
            1. 流程必须包含 START 节点作为起点、END 节点作为终点，start 节点不能有入边，end 节点不能有出边。
            2. 节点 id 使用有意义的英文标识（如 start、end、agent1、condition1、transform1），保证 nodes 与 edges 中的引用完全一致。
            3. 节点 position 提供 x/y 坐标并适当错开，避免重叠；START 放在最上方，END 放在最下方。
            4. 有数据依赖的节点使用 inputMappings 将上游输出 ${nodeId.output} 或工作流变量映射为节点输入，使用 outputMappings 将节点输出写入有意义的变量名。
            5. AGENT 节点 config 必须配置 agentCode。agentCode 只能使用用户明确指定的智能体编码，用户未指定时必须使用 default，严禁虚构、自创或臆造不存在的 agentCode（如 contractRiskAnalyzer 之类），否则校验工具会拒绝。
            6. NOTIFY 通知节点用于对接已配置的第三方集成渠道发送通知，config 必须配置 channelId（用户明确指定的集成渠道ID，用户未提供时先询问，严禁虚构）和 content（支持 ${var} 变量模板）；需要修改接收人等渠道参数时使用 override（如 {"to": "${email}"}）；同步模式（async 缺省或 false）执行后把发送结果 JSON 写入固定变量 notifyOutput，异步模式（async: true）不产生该变量；通知失败默认不影响主流程（ignoreFailure 默认 true）。
            7. 生成或修改流程后，必须调用 validate_workflow_definition 工具校验，校验失败时按错误修正后重新校验，直到校验通过。
            8. 修改已有流程时，先调用 get_workflow_definition 工具获取当前定义，基于当前定义做增量修改。
            9. 最终回答必须用 ```json 代码块输出完整的工作流定义 JSON，不允许省略任何节点和字段。
            10. 输出 JSON 前，必须先用结构化 Markdown 说明设计内容，禁止把多个要点挤在同一段落，格式要求：
               - 用 ## 小节标题划分内容，按顺序输出：## 设计思路、## 流程步骤、## 分支与变量说明。
               - ## 流程步骤 下用编号列表（1. 2. 3.）逐个描述节点，每项写明：节点名称（类型）→ 作用，有子步骤时用缩进的 - 子列表。
               - ## 分支与变量说明 下用列表说明条件分支的走向和关键变量的来源去向。
               - 关键节点名、变量名用反引号包裹，重要结论用粗体，多个要点必须各占一行。
               - 说明内容要简洁，每个小节不超过 8 行。
            """,
            WorkflowJsonSchema.WORKFLOW_DEFINITION_SCHEMA,
            WorkflowJsonSchema.NODE_CONFIG_DESCRIPTION);

    /**
     * 获取Agent编码
     * @return
     */
    @Override
    public String getAgentCode() {
        return "workflowDesigner";
    }

    /**
     * 获取Agent名称
     * @return
     */
    @Override
    protected String getAgentName() {
        return "流程设计助手";
    }

    /**
     * 获取最大迭代次数（生成、校验、修正循环需要充足迭代）
     * @return
     */
    @Override
    protected int getMaxIterations() {
        return 15;
    }

    /**
     * 解析系统提示词
     * @param request
     * @return
     */
    @Override
    protected String resolveSystemPrompt(AgentRequest request) {
        return SYSTEM_PROMPT;
    }

    /**
     * 构建输入消息列表
     * @param request
     * @return
     */
    @Override
    protected List<AgentMessage> buildInputMessages(AgentRequest request) {
        List<InputBlock> inputBlocks = request == null ? List.of() : request.getInput();
        List<AgentContentBlock> userInput;
        if (inputBlocks == null || inputBlocks.isEmpty()) {
            userInput = List.of(AgentTextBlock.builder().text("").build());
        } else {
            userInput = ContentBlockConverter.fromInputBlocks(inputBlocks);
        }
        List<AgentMessage> inputs = new ArrayList<>();
        AgentMessage userMessage = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(userInput)
                .build();
        inputs.add(userMessage);
        return inputs;
    }
}
