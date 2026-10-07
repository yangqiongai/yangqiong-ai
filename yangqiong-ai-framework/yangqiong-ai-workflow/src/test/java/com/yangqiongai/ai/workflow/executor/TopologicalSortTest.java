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

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.workflow.model.EdgeType;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowEdge;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.repository.NoopWorkflowNodeTraceRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.workflow.spi.WorkflowApprovalInterceptor;
import com.yangqiongai.ai.workflow.spi.WorkflowExecutionListener;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 拓扑排序单元测试（Kahn 算法）
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class TopologicalSortTest {

    @Mock
    private AgentEngine agentEngine;

    @Mock
    private WorkflowStateStore stateStore;

    @Mock
    private ConditionEvaluator conditionEvaluator;

    @Mock
    private WorkflowExecutionHistoryRepository executionHistoryService;

    @Mock
    private ObjectProvider<ApprovalGate> approvalGateProvider;

    @Mock
    private ObjectProvider<WorkflowExecutionListener> executionListenerProvider;

    @Mock
    private ObjectProvider<WorkflowApprovalInterceptor> approvalInterceptorProvider;

    @Mock
    private ObjectProvider<com.yangqiongai.ai.workflow.spi.WorkflowNotifySender> notifySenderProvider;

    private WorkflowAgentExecutor workflowAgentExecutor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowStateService stateService = new WorkflowStateService(stateStore, objectMapper);
        TransformNodeExecutor transformExecutor = new TransformNodeExecutor(stateService);
        ScriptHttpNodeExecutor scriptHttpExecutor = new ScriptHttpNodeExecutor(stateService);
        ApprovalNodeHandler approvalHandler = new ApprovalNodeHandler(approvalGateProvider, approvalInterceptorProvider);
        NotifyNodeHandler notifyHandler = new NotifyNodeHandler(notifySenderProvider, stateService, objectMapper);
        workflowAgentExecutor = new WorkflowAgentExecutor(
                agentEngine, conditionEvaluator, executionHistoryService,
                new NoopWorkflowNodeTraceRepository(),
                approvalHandler, notifyHandler,
                org.mockito.Mockito.mock(TimeControlNodeHandler.class),
                org.mockito.Mockito.mock(com.yangqiongai.ai.workflow.store.WorkflowPauseHistoryStore.class),
                new WorkflowGraphSorter(), stateService,
                transformExecutor, scriptHttpExecutor, null, objectMapper,
                executionListenerProvider, approvalInterceptorProvider, 4, 200
        );
    }

    // ==================== 辅助方法 ====================

    private WorkflowNode createNode(String id, String name, NodeType type) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private WorkflowEdge createEdge(String id, String sourceId, String targetId) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setId(id);
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        edge.setType(EdgeType.NORMAL);
        return edge;
    }

    // ==================== 线性 DAG 测试 ====================

    @Nested
    @DisplayName("线性 DAG: START -> A -> B -> END")
    class LinearDagTests {

        @Test
        @DisplayName("线性 DAG 拓扑排序结果正确")
        void shouldSortLinearDag() {
            WorkflowNode start = createNode("start", "开始", NodeType.START);
            WorkflowNode a = createNode("a", "A", NodeType.AGENT);
            WorkflowNode b = createNode("b", "B", NodeType.AGENT);
            WorkflowNode end = createNode("end", "结束", NodeType.END);

            WorkflowEdge e1 = createEdge("e1", "start", "a");
            WorkflowEdge e2 = createEdge("e2", "a", "b");
            WorkflowEdge e3 = createEdge("e3", "b", "end");

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("linear")
                    .nodes(List.of(start, a, b, end))
                    .edges(List.of(e1, e2, e3))
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).hasSize(4);
            assertThat(result.indexOf("start")).isLessThan(result.indexOf("a"));
            assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
            assertThat(result.indexOf("b")).isLessThan(result.indexOf("end"));
        }
    }

    // ==================== 菱形 DAG 测试 ====================

    @Nested
    @DisplayName("菱形 DAG: START -> A -> B, A -> C, B -> END, C -> END")
    class DiamondDagTests {

        @Test
        @DisplayName("菱形 DAG 拓扑排序结果正确")
        void shouldSortDiamondDag() {
            // START -> A -> B -> END
            //             -> C -> END
            WorkflowNode start = createNode("start", "开始", NodeType.START);
            WorkflowNode a = createNode("a", "A", NodeType.AGENT);
            WorkflowNode b = createNode("b", "B", NodeType.AGENT);
            WorkflowNode c = createNode("c", "C", NodeType.AGENT);
            WorkflowNode end = createNode("end", "结束", NodeType.END);

            WorkflowEdge e1 = createEdge("e1", "start", "a");
            WorkflowEdge e2 = createEdge("e2", "a", "b");
            WorkflowEdge e3 = createEdge("e3", "a", "c");
            WorkflowEdge e4 = createEdge("e4", "b", "end");
            WorkflowEdge e5 = createEdge("e5", "c", "end");

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("diamond")
                    .nodes(List.of(start, a, b, c, end))
                    .edges(List.of(e1, e2, e3, e4, e5))
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).hasSize(5);
            // start 必须在 a 之前
            assertThat(result.indexOf("start")).isLessThan(result.indexOf("a"));
            // a 必须在 b 和 c 之前
            assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
            assertThat(result.indexOf("a")).isLessThan(result.indexOf("c"));
            // b 和 c 必须在 end 之前
            assertThat(result.indexOf("b")).isLessThan(result.indexOf("end"));
            assertThat(result.indexOf("c")).isLessThan(result.indexOf("end"));
        }
    }

    // ==================== 环检测测试 ====================

    @Nested
    @DisplayName("环检测")
    class CycleDetectionTests {

        @Test
        @DisplayName("A -> B -> C -> A 环应抛出 IllegalStateException")
        void shouldDetectCycle() {
            WorkflowNode a = createNode("a", "A", NodeType.AGENT);
            WorkflowNode b = createNode("b", "B", NodeType.AGENT);
            WorkflowNode c = createNode("c", "C", NodeType.AGENT);

            WorkflowEdge e1 = createEdge("e1", "a", "b");
            WorkflowEdge e2 = createEdge("e2", "b", "c");
            WorkflowEdge e3 = createEdge("e3", "c", "a"); // 形成环

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("cycle")
                    .nodes(List.of(a, b, c))
                    .edges(List.of(e1, e2, e3))
                    .build();

            assertThatThrownBy(() -> workflowAgentExecutor.topologicalSort(def))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("工作流存在环");
        }

        @Test
        @DisplayName("自环节应抛出 IllegalStateException")
        void shouldDetectSelfLoop() {
            WorkflowNode a = createNode("a", "A", NodeType.AGENT);

            WorkflowEdge e1 = createEdge("e1", "a", "a"); // 自环

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("self-loop")
                    .nodes(List.of(a))
                    .edges(List.of(e1))
                    .build();

            assertThatThrownBy(() -> workflowAgentExecutor.topologicalSort(def))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("工作流存在环");
        }
    }

    // ==================== 单节点测试 ====================

    @Nested
    @DisplayName("单节点 DAG")
    class SingleNodeTests {

        @Test
        @DisplayName("单节点拓扑排序返回该节点")
        void shouldSortSingleNode() {
            WorkflowNode start = createNode("start", "开始", NodeType.START);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("single")
                    .nodes(List.of(start))
                    .edges(Collections.emptyList())
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).containsExactly("start");
        }
    }

    // ==================== 空定义测试 ====================

    @Nested
    @DisplayName("空定义")
    class EmptyDefinitionTests {

        @Test
        @DisplayName("空节点列表返回空排序结果")
        void shouldReturnEmptyForEmptyNodes() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("empty")
                    .nodes(Collections.emptyList())
                    .edges(Collections.emptyList())
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 节点列表返回空排序结果")
        void shouldReturnEmptyForNullNodes() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("null-nodes")
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).isEmpty();
        }
    }

    // ==================== 不连通分量测试 ====================

    @Nested
    @DisplayName("不连通分量")
    class DisconnectedComponentsTests {

        @Test
        @DisplayName("两个不连通分量可以正确排序")
        void shouldSortDisconnectedComponents() {
            // 分量1: A -> B
            // 分量2: C -> D
            WorkflowNode a = createNode("a", "A", NodeType.AGENT);
            WorkflowNode b = createNode("b", "B", NodeType.AGENT);
            WorkflowNode c = createNode("c", "C", NodeType.AGENT);
            WorkflowNode d = createNode("d", "D", NodeType.AGENT);

            WorkflowEdge e1 = createEdge("e1", "a", "b");
            WorkflowEdge e2 = createEdge("e2", "c", "d");

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("disconnected")
                    .nodes(List.of(a, b, c, d))
                    .edges(List.of(e1, e2))
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).hasSize(4);
            // 分量1内部顺序
            assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
            // 分量2内部顺序
            assertThat(result.indexOf("c")).isLessThan(result.indexOf("d"));
        }

        @Test
        @DisplayName("不连通分量加孤立节点可以正确排序")
        void shouldSortWithIsolatedNode() {
            // A -> B, C 为孤立节点
            WorkflowNode a = createNode("a", "A", NodeType.AGENT);
            WorkflowNode b = createNode("b", "B", NodeType.AGENT);
            WorkflowNode c = createNode("c", "C", NodeType.AGENT); // 孤立

            WorkflowEdge e1 = createEdge("e1", "a", "b");

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("with-isolated")
                    .nodes(List.of(a, b, c))
                    .edges(List.of(e1))
                    .build();

            List<String> result = workflowAgentExecutor.topologicalSort(def);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
        }
    }

    // ==================== 边指向不存在节点的鲁棒性测试 ====================

    @Test
    @DisplayName("边指向不存在的节点时被忽略")
    void shouldIgnoreEdgesToNonExistentNodes() {
        WorkflowNode a = createNode("a", "A", NodeType.AGENT);
        WorkflowNode b = createNode("b", "B", NodeType.AGENT);

        // 这条边的 targetId "nonexistent" 不在节点列表中
        WorkflowEdge e1 = createEdge("e1", "a", "nonexistent");
        WorkflowEdge e2 = createEdge("e2", "a", "b");

        WorkflowDefinition def = WorkflowDefinition.builder()
                .name("invalid-edge")
                .nodes(List.of(a, b))
                .edges(List.of(e1, e2))
                .build();

        List<String> result = workflowAgentExecutor.topologicalSort(def);

        assertThat(result).hasSize(2);
        assertThat(result.indexOf("a")).isLessThan(result.indexOf("b"));
    }
}
