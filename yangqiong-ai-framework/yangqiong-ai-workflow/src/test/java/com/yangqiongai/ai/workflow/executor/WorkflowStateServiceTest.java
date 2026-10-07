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
package com.yangqiongai.ai.workflow.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * 工作流状态管理测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("工作流状态管理测试")
class WorkflowStateServiceTest {

    @Mock
    private WorkflowStateStore stateStore;

    private WorkflowStateService stateService;

    private WorkflowState state;

    @BeforeEach
    void setUp() {
        stateService = new WorkflowStateService(stateStore, new ObjectMapper());
        state = new WorkflowState();
        state.setInstanceId("inst-1");
        state.setVariables(new ConcurrentHashMap<>());
        state.setNodeStates(new ConcurrentHashMap<>());
    }

    @Nested
    @DisplayName("resolveNodeInput - 节点输入解析")
    class ResolveNodeInputTests {

        @Test
        @DisplayName("默认快照排除其他节点的输入变量，防止嵌套膨胀")
        void resolveNodeInput_excludesNodeInputVariables() {
            state.setVariable("input", "上游内容");
            state.setVariable("agent_1.output", "agent输出");
            Map<String, Object> priorInput = new LinkedHashMap<>();
            priorInput.put("input", "旧输入");
            state.setVariable("agent_1.input", priorInput);

            WorkflowNode node = new WorkflowNode();
            node.setId("transform_1");
            node.setType(NodeType.TRANSFORM);

            Map<String, Object> input = stateService.resolveNodeInput(node, state);

            assertThat(input).containsKey("input");
            assertThat(input).containsKey("agent_1.output");
            assertThat(input).doesNotContainKey("agent_1.input");
        }

        @Test
        @DisplayName("默认快照排除内部变量（_前缀）")
        void resolveNodeInput_excludesInternalVariables() {
            state.setVariable("_nodeTimeoutSeconds", 120);
            state.setVariable("input", "内容");

            WorkflowNode node = new WorkflowNode();
            node.setId("transform_1");
            node.setType(NodeType.TRANSFORM);

            Map<String, Object> input = stateService.resolveNodeInput(node, state);

            assertThat(input).containsOnlyKeys("input");
        }

        @Test
        @DisplayName("按inputMappings解析变量引用")
        void resolveNodeInput_withMappings() {
            state.setVariable("agent_1.output", "agent输出");

            WorkflowNode node = new WorkflowNode();
            node.setId("transform_1");
            node.setType(NodeType.TRANSFORM);
            Map<String, String> mappings = new LinkedHashMap<>();
            mappings.put("param", "${agent_1.output}");
            node.setInputMappings(mappings);

            Map<String, Object> input = stateService.resolveNodeInput(node, state);

            assertThat(input).containsEntry("param", "agent输出");
        }
    }

    @Nested
    @DisplayName("resolveNodeOutput - 节点输出解析")
    class ResolveNodeOutputTests {

        @Test
        @DisplayName("普通节点成功时写入output变量")
        void resolveNodeOutput_writesOutputVariable() {
            WorkflowNode node = new WorkflowNode();
            node.setId("agent_1");
            node.setType(NodeType.AGENT);

            stateService.resolveNodeOutput(node, AgentResult.success("agent回复"), state);

            assertThat(state.getVariable("agent_1.output")).isEqualTo("agent回复");
        }

        @Test
        @DisplayName("条件节点不把路由描述文字写入output变量")
        void resolveNodeOutput_conditionSkipsOutputVariable() {
            WorkflowNode node = new WorkflowNode();
            node.setId("condition_1");
            node.setType(NodeType.CONDITION);

            stateService.resolveNodeOutput(node, AgentResult.success("条件分支选择: true -> 数据变换2"), state);

            assertThat(state.getVariable("condition_1.output")).isNull();
        }

        @Test
        @DisplayName("按outputMappings写入自定义变量")
        void resolveNodeOutput_withOutputMappings() {
            WorkflowNode node = new WorkflowNode();
            node.setId("transform_4");
            node.setType(NodeType.TRANSFORM);
            Map<String, String> mappings = new LinkedHashMap<>();
            mappings.put("output", "value");
            node.setOutputMappings(mappings);

            stateService.resolveNodeOutput(node, AgentResult.success("2eeee"), state);

            assertThat(state.getVariable("value")).isEqualTo("2eeee");
        }
    }

    @Nested
    @DisplayName("orderVariablesForDisplay - 变量展示排序")
    class OrderVariablesTests {

        @Test
        @DisplayName("节点变量按节点开始时间排序，独立变量按名称殿后，内部变量排除")
        void orderVariables_byNodeExecutionOrder() {
            state.setVariable("b_var", "独立变量");
            state.setVariable("agent_1.output", "agent输出");
            state.setVariable("agent_1.input", Map.of("input", "agent输入"));
            state.setVariable("start_1.output", "");
            state.setVariable("_internal", "内部");

            NodeExecutionStatus startStatus = new NodeExecutionStatus();
            startStatus.setNodeId("start_1");
            startStatus.setStatus(ExecutionStatus.COMPLETED);
            startStatus.setStartTime(100L);
            state.setNodeState("start_1", startStatus);

            NodeExecutionStatus agentStatus = new NodeExecutionStatus();
            agentStatus.setNodeId("agent_1");
            agentStatus.setStatus(ExecutionStatus.COMPLETED);
            agentStatus.setStartTime(200L);
            state.setNodeState("agent_1", agentStatus);

            Map<String, Object> ordered = stateService.orderVariablesForDisplay(state);

            assertThat(ordered.keySet()).containsExactly(
                    "start_1.output", "agent_1.input", "agent_1.output", "b_var");
        }

        @Test
        @DisplayName("未跟踪执行状态的节点变量按名称殿后")
        void orderVariables_untrackedNodesLast() {
            state.setVariable("orphan.output", "孤儿变量");
            state.setVariable("a", "1");

            NodeExecutionStatus status = new NodeExecutionStatus();
            status.setNodeId("known");
            status.setStatus(ExecutionStatus.COMPLETED);
            status.setStartTime(100L);
            state.setNodeState("known", status);
            state.setVariable("known.output", "已知");

            Map<String, Object> ordered = stateService.orderVariablesForDisplay(state);

            assertThat(ordered.keySet()).containsExactly("known.output", "a", "orphan.output");
        }
    }
}
