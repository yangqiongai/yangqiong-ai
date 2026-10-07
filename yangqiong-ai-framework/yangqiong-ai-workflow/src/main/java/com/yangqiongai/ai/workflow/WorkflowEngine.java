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
package com.yangqiongai.ai.workflow;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.model.WorkflowStreamEvent;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 工作流引擎接口
 * @author yangqiong
 */
public interface WorkflowEngine {

    AgentResult execute(WorkflowDefinition definition, AgentContext context);

    /**
     * 执行工作流，返回结构化结果
     */
    WorkflowExecuteResult executeWithResult(WorkflowDefinition definition, AgentContext context);

    Flux<WorkflowStreamEvent> stream(WorkflowDefinition definition, AgentContext context);

    String submit(WorkflowDefinition definition, AgentContext context);

    WorkflowState queryStatus(String instanceId);

    AgentResult resume(String instanceId);

    /**
     * 恢复工作流执行（带操作人与原因）
     * @param instanceId
     * @param operator
     * @param reason
     * @return
     */
    default AgentResult resume(String instanceId, String operator, String reason) {
        return resume(instanceId);
    }

    AgentResult executeByName(String definitionName, AgentContext context);

    /**
     * 暂停运行中的工作流
     */
    boolean pause(String instanceId);

    /**
     * 暂停运行中的工作流（带操作人与原因）
     * @param instanceId
     * @param operator
     * @param reason
     * @return
     */
    default boolean pause(String instanceId, String operator, String reason) {
        return pause(instanceId);
    }

    /**
     * 取消工作流
     */
    boolean cancel(String instanceId);

    /**
     * 单节点调试执行（不产生实例、不落历史）
     * @param definition 工作流定义
     * @param nodeId 调试目标节点ID
     * @param mockVariables Mock变量
     * @param context 代理上下文
     * @return
     */
    WorkflowExecuteResult debugNode(WorkflowDefinition definition, String nodeId,
                                    Map<String, Object> mockVariables, AgentContext context);
}
