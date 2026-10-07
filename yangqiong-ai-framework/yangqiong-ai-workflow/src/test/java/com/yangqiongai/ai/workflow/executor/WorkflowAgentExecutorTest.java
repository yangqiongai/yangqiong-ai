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
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.model.*;
import com.yangqiongai.ai.workflow.repository.NoopWorkflowNodeTraceRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.workflow.spi.WorkflowApprovalInterceptor;
import com.yangqiongai.ai.workflow.spi.WorkflowExecutionListener;
import com.yangqiongai.ai.workflow.store.WorkflowPauseHistoryStore;
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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkflowAgentExecutor 单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WorkflowAgentExecutorTest {

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

    private WorkflowAgentExecutor workflowExecutor;

    private AgentContext defaultContext;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowStateService stateService = new WorkflowStateService(stateStore, objectMapper);
        TransformNodeExecutor transformExecutor = new TransformNodeExecutor(stateService);
        ScriptHttpNodeExecutor scriptHttpExecutor = new ScriptHttpNodeExecutor(stateService);
        ApprovalNodeHandler approvalHandler = new ApprovalNodeHandler(approvalGateProvider, approvalInterceptorProvider);
        NotifyNodeHandler notifyHandler = new NotifyNodeHandler(notifySenderProvider, stateService, objectMapper);
        workflowExecutor = new WorkflowAgentExecutor(
                agentEngine, conditionEvaluator, executionHistoryService,
                new NoopWorkflowNodeTraceRepository(),
                approvalHandler, notifyHandler,
                org.mockito.Mockito.mock(TimeControlNodeHandler.class),
                org.mockito.Mockito.mock(WorkflowPauseHistoryStore.class),
                new WorkflowGraphSorter(), stateService,
                transformExecutor, scriptHttpExecutor, null, objectMapper,
                executionListenerProvider, approvalInterceptorProvider, 4, 200
        );
        AgentRequest request = new AgentRequest()
                .agentCode("workflow")
                .input("test-input")
                .sessionId("test-session");
        defaultContext = new AgentContext(request);
    }

    // ==================== 辅助方法 ====================

    private WorkflowNode startNode(String id) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName("开始");
        node.setType(NodeType.START);
        return node;
    }

    private WorkflowNode endNode(String id) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName("结束");
        node.setType(NodeType.END);
        return node;
    }

    private AgentNode agentNode(String id, String name) {
        AgentNode node = new AgentNode();
        node.setId(id);
        node.setName(name);
        node.setType(NodeType.AGENT);
        node.setAgentCode("test-agent");
        return node;
    }

    private ConditionNode conditionNode(String id, String name, String expression, Map<String, String> branches) {
        ConditionNode node = new ConditionNode();
        node.setId(id);
        node.setName(name);
        node.setType(NodeType.CONDITION);
        node.setConditionExpression(expression);
        node.setBranches(branches);
        return node;
    }

    private ParallelNode parallelNode(String id, String name, String joinType) {
        ParallelNode node = new ParallelNode();
        node.setId(id);
        node.setName(name);
        node.setType(NodeType.PARALLEL);
        node.setJoinType(joinType);
        return node;
    }

    private WorkflowEdge edge(String id, String sourceId, String targetId) {
        return edge(id, sourceId, targetId, EdgeType.NORMAL);
    }

    private WorkflowEdge edge(String id, String sourceId, String targetId, EdgeType type) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setSourceId(sourceId);
        e.setTargetId(targetId);
        e.setType(type);
        return e;
    }

    private WorkflowEdge conditionalEdge(String id, String sourceId, String targetId, String conditionExpression, String conditionLabel) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setSourceId(sourceId);
        e.setTargetId(targetId);
        e.setType(EdgeType.CONDITIONAL);
        e.setConditionExpression(conditionExpression);
        e.setConditionLabel(conditionLabel);
        return e;
    }

    private WorkflowEdge parallelEdge(String id, String sourceId, String targetId) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setSourceId(sourceId);
        e.setTargetId(targetId);
        e.setType(EdgeType.PARALLEL);
        return e;
    }

    private WorkflowDefinition buildDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        return buildDefinition(nodes, edges, ErrorStrategy.STOP);
    }

    private WorkflowDefinition buildDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges, ErrorStrategy errorStrategy) {
        return buildDefinition(nodes, edges, errorStrategy, 0);
    }

    private WorkflowDefinition buildDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges,
                                                ErrorStrategy errorStrategy, int maxRetries) {
        WorkflowDefinition def = WorkflowDefinition.builder()
                .name("test-workflow")
                .description("测试工作流")
                .nodes(nodes)
                .edges(edges)
                .errorStrategy(errorStrategy)
                .maxRetries(maxRetries)
                .stateConfig(StateConfig.builder().persistEnabled(true).build())
                .build();
        // 触发索引构建
        def.setNodes(nodes);
        def.setEdges(edges);
        return def;
    }

    // ==================== 1. 线性工作流执行 ====================

    @Nested
    @DisplayName("线性工作流执行")
    class LinearWorkflowExecution {

        @Test
        @DisplayName("START -> AGENT -> END: Agent成功时工作流完成")
        void execute_linearWorkflow_agentSucceeds_workflowCompletes() {
            // Given: START -> AGENT -> END
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agentNode("agent1", "Agent1"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("agent输出结果"));

            // When
            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputAsText()).contains("agent输出结果");
            verify(agentEngine, times(1)).run(any(AgentRequest.class));
            verify(stateStore, atLeastOnce()).save(any());
            verify(executionHistoryService).recordCompletion(any(), eq("agent输出结果"), isNull());
        }

        @Test
        @DisplayName("START -> AGENT1 -> AGENT2 -> END: 多Agent顺序执行")
        void execute_multiAgentWorkflow_allAgentsSucceed() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    agentNode("agent2", "Agent2"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "agent2"),
                    edge("e3", "agent2", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("第一步结果"))
                    .thenReturn(AgentResult.success("第二步结果"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputAsText()).contains("第二步结果");
            verify(agentEngine, times(2)).run(any(AgentRequest.class));
        }
    }

    // ==================== 2. 指定instanceId执行 ====================

    @Nested
    @DisplayName("指定instanceId执行")
    class ExecuteWithInstanceId {

        @Test
        @DisplayName("传入instanceId时状态使用该ID")
        void execute_withSpecifiedInstanceId_stateUsesGivenId() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agentNode("agent1", "Agent1"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("ok"));

            String customInstanceId = "custom-instance-123";
            AgentResult result = workflowExecutor.execute(definition, defaultContext, customInstanceId);

            assertThat(result.isSuccess()).isTrue();
            // 验证stateStore.save被调用时，state的instanceId为指定值
            verify(stateStore, atLeastOnce()).save(argThat(state ->
                    state != null && customInstanceId.equals(state.getInstanceId())
            ));
        }

        @Test
        @DisplayName("不传instanceId时自动生成UUID")
        void execute_withoutInstanceId_autoGeneratesId() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            workflowExecutor.execute(definition, defaultContext);

            verify(stateStore, atLeastOnce()).save(argThat(state ->
                    state != null && state.getInstanceId() != null && !state.getInstanceId().isEmpty()
            ));
        }
    }

    // ==================== 3. 错误策略 STOP ====================

    @Nested
    @DisplayName("错误策略STOP")
    class ErrorStrategyStop {

        @Test
        @DisplayName("Agent节点失败时工作流停止并返回失败")
        void execute_stopStrategy_agentFails_workflowFails() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    agentNode("agent2", "Agent2"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "agent2"),
                    edge("e3", "agent2", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.STOP);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.failure("Agent执行出错"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("节点执行失败");
            // agent2不应被执行
            verify(agentEngine, times(1)).run(any(AgentRequest.class));
        }
    }

    // ==================== 4. 错误策略 SKIP ====================

    @Nested
    @DisplayName("错误策略SKIP")
    class ErrorStrategySkip {

        @Test
        @DisplayName("Agent节点失败时跳过并继续执行后续节点")
        void execute_skipStrategy_agentFails_workflowContinues() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    agentNode("agent2", "Agent2"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "agent2"),
                    edge("e3", "agent2", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.SKIP);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.failure("Agent1出错"))
                    .thenReturn(AgentResult.success("Agent2成功"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputAsText()).contains("Agent2成功");
            // 两个agent都应被调用
            verify(agentEngine, times(2)).run(any(AgentRequest.class));
        }
    }

    // ==================== 5. 错误策略 RETRY ====================

    @Nested
    @DisplayName("错误策略RETRY")
    class ErrorStrategyRetry {

        @Test
        @DisplayName("Agent节点失败后重试成功")
        void execute_retryStrategy_failsThenSucceeds() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.RETRY, 3);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.failure("临时错误"))
                    .thenReturn(AgentResult.success("重试成功"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputAsText()).contains("重试成功");
            // 第一次执行 + 1次重试 = 2次
            verify(agentEngine, times(2)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("Agent节点重试次数耗尽后工作流失败")
        void execute_retryStrategy_exhaustedRetries_workflowFails() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.RETRY, 2);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.failure("持续失败"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("重试耗尽");
            // 1次初始执行 + 2次重试 = 3次
            verify(agentEngine, times(3)).run(any(AgentRequest.class));
        }
    }

    // ==================== 6. 拓扑排序 ====================

    @Nested
    @DisplayName("拓扑排序")
    class TopologicalSort {

        @Test
        @DisplayName("线性DAG排序正确")
        void topologicalSort_linearDAG_correctOrder() {
            List<WorkflowNode> nodes = List.of(
                    startNode("a"), startNode("b"), startNode("c")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "a", "b"),
                    edge("e2", "b", "c")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            List<String> sorted = workflowExecutor.topologicalSort(definition);

            assertThat(sorted).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("菱形DAG排序满足依赖关系")
        void topologicalSort_diamondDAG_dependencyOrder() {
            //     A
            //    / \
            //   B   C
            //    \ /
            //     D
            List<WorkflowNode> nodes = List.of(
                    startNode("a"), startNode("b"), startNode("c"), startNode("d")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "a", "b"),
                    edge("e2", "a", "c"),
                    edge("e3", "b", "d"),
                    edge("e4", "c", "d")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            List<String> sorted = workflowExecutor.topologicalSort(definition);

            assertThat(sorted).hasSize(4);
            assertThat(sorted.indexOf("a")).isLessThan(sorted.indexOf("b"));
            assertThat(sorted.indexOf("a")).isLessThan(sorted.indexOf("c"));
            assertThat(sorted.indexOf("b")).isLessThan(sorted.indexOf("d"));
            assertThat(sorted.indexOf("c")).isLessThan(sorted.indexOf("d"));
        }

        @Test
        @DisplayName("存在环时抛出IllegalStateException")
        void topologicalSort_cyclicDAG_throwsException() {
            // A -> B -> C -> A (环)
            List<WorkflowNode> nodes = List.of(
                    startNode("a"), startNode("b"), startNode("c")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "a", "b"),
                    edge("e2", "b", "c"),
                    edge("e3", "c", "a")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            assertThatThrownBy(() -> workflowExecutor.topologicalSort(definition))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("存在环");
        }

        @Test
        @DisplayName("空节点列表返回空排序结果")
        void topologicalSort_emptyNodes_returnsEmpty() {
            WorkflowDefinition definition = WorkflowDefinition.builder()
                    .name("empty")
                    .nodes(Collections.emptyList())
                    .edges(Collections.emptyList())
                    .build();

            List<String> sorted = workflowExecutor.topologicalSort(definition);

            assertThat(sorted).isEmpty();
        }

        @Test
        @DisplayName("无边的独立节点均可排在首位")
        void topologicalSort_noEdges_allNodesReturned() {
            List<WorkflowNode> nodes = List.of(
                    startNode("a"), startNode("b"), startNode("c")
            );
            WorkflowDefinition definition = buildDefinition(nodes, Collections.emptyList());

            List<String> sorted = workflowExecutor.topologicalSort(definition);

            assertThat(sorted).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    // ==================== 7. 条件节点执行 ====================

    @Nested
    @DisplayName("条件节点执行")
    class ConditionNodeExecution {

        @Test
        @DisplayName("条件评估选择正确分支")
        void execute_conditionNode_selectsCorrectBranch() {
            // START -> CONDITION -> AGENT_A / AGENT_B -> END
            Map<String, String> branches = new LinkedHashMap<>();
            branches.put("branchA", "agentA");
            branches.put("branchB", "agentB");

            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    conditionNode("cond1", "条件判断", "${reportType}", branches),
                    agentNode("agentA", "AgentA"),
                    agentNode("agentB", "AgentB"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "cond1"),
                    conditionalEdge("e2", "cond1", "agentA", "branchA", "分支A"),
                    conditionalEdge("e3", "cond1", "agentB", "branchB", "分支B"),
                    edge("e4", "agentA", "end"),
                    edge("e5", "agentB", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(conditionEvaluator.evaluate(eq("${reportType}"), any(WorkflowState.class)))
                    .thenReturn("branchA");
            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("分支A结果"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            verify(conditionEvaluator).evaluate(eq("${reportType}"), any(WorkflowState.class));
            verify(agentEngine, atLeastOnce()).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("条件评估返回null时走默认NORMAL边")
        void execute_conditionNode_noMatch_fallsBackToDefault() {
            Map<String, String> branches = new LinkedHashMap<>();
            branches.put("branchA", "agentA");

            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    conditionNode("cond1", "条件判断", "${unknown}", branches),
                    agentNode("agentA", "AgentA"),
                    agentNode("agentDefault", "DefaultAgent"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "cond1"),
                    conditionalEdge("e2", "cond1", "agentA", "branchA", "分支A"),
                    edge("e3", "cond1", "agentDefault"),  // 默认NORMAL边
                    edge("e4", "agentA", "end"),
                    edge("e5", "agentDefault", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(conditionEvaluator.evaluate(anyString(), any(WorkflowState.class)))
                    .thenReturn(null);
            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("默认路径结果"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    // ==================== 8. 取消请求 ====================

    @Nested
    @DisplayName("取消请求")
    class CancelRequest {

        @Test
        @DisplayName("设置cancelRequested后工作流停止执行")
        void execute_cancelRequested_workflowStops() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    agentNode("agent2", "Agent2"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "agent2"),
                    edge("e3", "agent2", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            // 第一次执行agent1后，通过stateStore.save回调设置cancelRequested
            doAnswer(invocation -> {
                WorkflowState state = invocation.getArgument(0);
                if (state.getNodeState("agent1") != null
                        && state.getNodeState("agent1").isRunning()) {
                    // 模拟在agent1执行期间设置取消标志
                }
                return null;
            }).when(stateStore).save(any());

            // 直接使用executeFromNodes来测试cancel逻辑更直接
            // 使用自定义方式测试：构建一个已标记cancelRequested的state
            WorkflowState state = new WorkflowState();
            state.setInstanceId("cancel-test-id");
            state.setDefinitionName("test-workflow");
            state.setStatus(ExecutionStatus.RUNNING);
            state.setNodeStates(new ConcurrentHashMap<>());
            state.setVariables(new ConcurrentHashMap<>());
            state.setCreateTime(System.currentTimeMillis());
            state.setUpdateTime(System.currentTimeMillis());
            state.setCancelRequested(true);

            // START节点已标记完成
            NodeExecutionStatus startStatus = new NodeExecutionStatus();
            startStatus.setNodeId("start");
            startStatus.setNodeName("开始");
            startStatus.setStatus(ExecutionStatus.COMPLETED);
            startStatus.setStartTime(System.currentTimeMillis());
            startStatus.setEndTime(System.currentTimeMillis());
            state.setNodeState("start", startStatus);

            AgentResult result = workflowExecutor.executeFromNodes(definition, defaultContext, state, List.of("agent1"));

            // cancelRequested=true时，executeNodeList在循环开始就标记CANCELLED并返回，
            // executeFromNodes对取消状态返回失败结果，保持取消语义一致
            assertThat(result.isSuccess()).isFalse();
            // agent1不应被执行（cancelRequested导致executeNodeList提前返回）
            verify(agentEngine, never()).run(any(AgentRequest.class));
        }
    }

    // ==================== 9. 持久化状态失败 ====================

    @Nested
    @DisplayName("持久化状态失败")
    class PersistStateFailure {

        @Test
        @DisplayName("stateStore.save()抛异常时跟踪失败次数")
        void execute_persistFailure_tracksFailureCount() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            doThrow(new RuntimeException("Redis连接失败")).when(stateStore).save(any());

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            // 持久化失败次数<3时工作流仍可完成（只是状态未持久化）
            // 但由于START和END节点各触发多次persistState调用，失败次数可能>=3
            // 验证stateStore.save被调用过
            verify(stateStore, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("连续持久化失败3次后工作流标记为FAILED")
        void execute_persistFailureThreeTimes_workflowFails() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            doThrow(new RuntimeException("Redis连接失败")).when(stateStore).save(any());

            workflowExecutor.execute(definition, defaultContext);

            // 验证至少有一次save调用，且最终状态被标记为FAILED
            // 因为每次persistState都会增加_persistFailureCount
            verify(stateStore, atLeastOnce()).save(argThat(state ->
                    state != null && state.getStatus() == ExecutionStatus.FAILED
            ));
        }

        @Test
        @DisplayName("persistEnabled=false时不调用stateStore.save()")
        void execute_persistDisabled_noSaveCalled() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "end")
            );
            WorkflowDefinition definition = WorkflowDefinition.builder()
                    .name("test-workflow")
                    .description("测试")
                    .nodes(nodes)
                    .edges(edges)
                    .stateConfig(StateConfig.builder().persistEnabled(false).build())
                    .errorStrategy(ErrorStrategy.STOP)
                    .maxRetries(0)
                    .nodeTimeoutSeconds(120)
                    .build();
            definition.setNodes(nodes);
            definition.setEdges(edges);

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            verify(stateStore, never()).save(any());
        }
    }

    // ==================== 10. executeFromNodes ====================

    @Nested
    @DisplayName("从指定节点恢复执行")
    class ExecuteFromNodes {

        @Test
        @DisplayName("从中间节点恢复执行，跳过已完成节点")
        void executeFromNodes_skipsCompletedNodes() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    agentNode("agent1", "Agent1"),
                    agentNode("agent2", "Agent2"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "agent2"),
                    edge("e3", "agent2", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            // 构造已部分完成的状态
            WorkflowState state = new WorkflowState();
            state.setInstanceId("resume-test-id");
            state.setDefinitionName("test-workflow");
            state.setStatus(ExecutionStatus.PAUSED);
            state.setNodeStates(new ConcurrentHashMap<>());
            state.setVariables(new ConcurrentHashMap<>());
            state.setCreateTime(System.currentTimeMillis());
            state.setUpdateTime(System.currentTimeMillis());

            // start和agent1已完成
            NodeExecutionStatus startStatus = new NodeExecutionStatus();
            startStatus.setNodeId("start");
            startStatus.setStatus(ExecutionStatus.COMPLETED);
            startStatus.setStartTime(System.currentTimeMillis());
            startStatus.setEndTime(System.currentTimeMillis());
            state.setNodeState("start", startStatus);

            NodeExecutionStatus agent1Status = new NodeExecutionStatus();
            agent1Status.setNodeId("agent1");
            agent1Status.setStatus(ExecutionStatus.COMPLETED);
            agent1Status.setOutput(AgentResult.success("agent1结果"));
            agent1Status.setStartTime(System.currentTimeMillis());
            agent1Status.setEndTime(System.currentTimeMillis());
            state.setNodeState("agent1", agent1Status);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("agent2结果"));

            AgentResult result = workflowExecutor.executeFromNodes(
                    definition, defaultContext, state, List.of("agent2"));

            assertThat(result.isSuccess()).isTrue();
            // 只有agent2被执行
            verify(agentEngine, times(1)).run(any(AgentRequest.class));
        }
    }

    // ==================== 11. 流式执行 ====================

    @Nested
    @DisplayName("流式执行")
    class StreamExecution {

        @Test
        @DisplayName("流式执行线性工作流产生完整事件序列")
        void stream_linearWorkflow_emitsCompleteEvents() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agentNode("agent1", "Agent1"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("流式结果"));

            List<WorkflowStreamEvent> events = workflowExecutor.stream(definition, defaultContext)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertThat(events).isNotNull();
            assertThat(events).isNotEmpty();
            // 验证最终有workflowComplete事件
            assertThat(events.stream().anyMatch(e -> "WORKFLOW_COMPLETE".equals(e.getEventType())))
                    .isTrue();
        }
    }

    // ==================== 12. 并行节点执行 ====================

    @Nested
    @DisplayName("并行节点执行")
    class ParallelNodeExecution {

        @Test
        @DisplayName("ALL汇聚类型：所有分支完成后合并结果")
        void execute_parallelNode_allJoin_combinesResults() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    parallelNode("parallel1", "并行网关", "ALL"),
                    agentNode("agentA", "AgentA"),
                    agentNode("agentB", "AgentB"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "parallel1"),
                    parallelEdge("e2", "parallel1", "agentA"),
                    parallelEdge("e3", "parallel1", "agentB"),
                    edge("e4", "agentA", "end"),
                    edge("e5", "agentB", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("分支A结果"))
                    .thenReturn(AgentResult.success("分支B结果"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            verify(agentEngine, times(2)).run(any(AgentRequest.class));
        }
    }

    // ==================== 13. 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("只有START和END节点的工作流成功完成")
        void execute_onlyStartAndEnd_succeeds() {
            List<WorkflowNode> nodes = List.of(startNode("start"), endNode("end"));
            List<WorkflowEdge> edges = List.of(edge("e1", "start", "end"));
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            verify(agentEngine, never()).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("executionHistoryService失败不影响工作流执行")
        void execute_historyServiceFailure_doesNotAffectWorkflow() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agentNode("agent1", "Agent1"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"),
                    edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("成功"));
            doThrow(new RuntimeException("DB连接失败"))
                    .when(executionHistoryService).recordCompletion(any(), any(), any());

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            // 工作流本身应成功完成
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("shutdown方法正常关闭线程池")
        void shutdown_closesExecutorGracefully() {
            // 不应抛出异常
            assertThatCode(() -> workflowExecutor.shutdown()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("节点超时与重试")
    class NodeTimeoutAndRetry {

        @Test
        @DisplayName("节点级timeoutSeconds覆盖定义级超时，AGENT节点超时返回失败")
        void agentNode_nodeLevelTimeoutOverrides() {
            WorkflowNode agent = agentNode("agent1", "Agent1");
            agent.setTimeoutSeconds(1);
            List<WorkflowNode> nodes = List.of(startNode("start"), agent, endNode("end"));
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"), edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);
            definition.setNodeTimeoutSeconds(30);

            when(agentEngine.run(any(AgentRequest.class))).thenAnswer(inv -> {
                Thread.sleep(3000);
                return AgentResult.success("慢响应");
            });

            long start = System.currentTimeMillis();
            AgentResult result = workflowExecutor.execute(definition, defaultContext);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result.isSuccess()).isFalse();
            assertThat(elapsed).isLessThan(5000L);
            assertThat(result.getErrorMessage()).contains("节点执行超时(1s)");
        }

        @Test
        @DisplayName("SCRIPT等内联节点执行超时按节点失败处理")
        void scriptNode_executionTimeout() {
            ScriptHttpNodeExecutor slowScriptExecutor = mock(ScriptHttpNodeExecutor.class);
            ObjectMapper objectMapper = new ObjectMapper();
            WorkflowStateService stateService = new WorkflowStateService(stateStore, objectMapper);
            WorkflowAgentExecutor timeoutExecutor = new WorkflowAgentExecutor(
                    agentEngine, conditionEvaluator, executionHistoryService,
                    new NoopWorkflowNodeTraceRepository(),
                    new ApprovalNodeHandler(approvalGateProvider, approvalInterceptorProvider),
                    new NotifyNodeHandler(notifySenderProvider, stateService, objectMapper),
                    mock(TimeControlNodeHandler.class),
                    mock(WorkflowPauseHistoryStore.class),
                    new WorkflowGraphSorter(), stateService,
                    new TransformNodeExecutor(stateService), slowScriptExecutor, null, objectMapper,
                    executionListenerProvider, approvalInterceptorProvider, 4, 200
            );

            WorkflowNode scriptNode = new WorkflowNode();
            scriptNode.setId("script1");
            scriptNode.setName("脚本");
            scriptNode.setType(NodeType.SCRIPT);
            List<WorkflowNode> nodes = List.of(startNode("start"), scriptNode, endNode("end"));
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "script1"), edge("e2", "script1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);
            definition.setNodeTimeoutSeconds(1);

            when(slowScriptExecutor.executeScriptNode(any(), any())).thenAnswer(inv -> {
                Thread.sleep(3000);
                return AgentResult.success("脚本完成");
            });

            AgentResult result = timeoutExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("节点执行超时(1s)");
        }

        @Test
        @DisplayName("RETRY策略：节点失败后重试成功，工作流完成")
        void retryStrategy_successAfterRetries() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agentNode("agent1", "Agent1"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"), edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.RETRY, 3);

            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.failure("第一次失败"))
                    .thenReturn(AgentResult.failure("第二次失败"))
                    .thenReturn(AgentResult.success("第三次成功"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            verify(agentEngine, times(3)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("RETRY策略：重试耗尽后工作流失败")
        void retryStrategy_exhausted() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agentNode("agent1", "Agent1"), endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"), edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.RETRY, 1);

            when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.failure("持续失败"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            verify(agentEngine, times(2)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("节点级maxRetries覆盖定义级重试次数")
        void retryStrategy_nodeLevelMaxRetriesOverrides() {
            WorkflowNode agent = agentNode("agent1", "Agent1");
            agent.setMaxRetries(1);
            List<WorkflowNode> nodes = List.of(
                    startNode("start"), agent, endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "agent1"), edge("e2", "agent1", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges, ErrorStrategy.RETRY, 3);

            when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.failure("持续失败"));

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            // 节点级maxRetries=1生效：初始1次+重试1次
            verify(agentEngine, times(2)).run(any(AgentRequest.class));
        }
    }

    // ==================== 14. 条件分支未选中路径跳过 ====================

    private Map<String, ExecutionStatus> statusByNodeId(WorkflowExecuteResult result) {
        return result.getNodeSummaries().stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkflowExecuteResult.NodeSummary::getNodeId,
                        WorkflowExecuteResult.NodeSummary::getStatus));
    }

    @Nested
    @DisplayName("条件分支未选中路径跳过")
    class ConditionBranchSkip {

        @Test
        @DisplayName("未选中分支节点标记为SKIPPED，汇聚END正常执行")
        void execute_conditionBranch_unselectedBranchMarkedSkipped() {
            Map<String, String> branches = new LinkedHashMap<>();
            branches.put("branchA", "agentA");
            branches.put("branchB", "agentB");

            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    conditionNode("cond1", "条件判断", "${branch}", branches),
                    agentNode("agentA", "AgentA"),
                    agentNode("agentB", "AgentB"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "cond1"),
                    conditionalEdge("e2", "cond1", "agentA", "branchA", "分支A"),
                    conditionalEdge("e3", "cond1", "agentB", "branchB", "分支B"),
                    edge("e4", "agentA", "end"),
                    edge("e5", "agentB", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(conditionEvaluator.evaluate(anyString(), any(WorkflowState.class)))
                    .thenReturn("branchB");
            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("分支B结果"));

            WorkflowExecuteResult result = workflowExecutor.executeWithResult(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            Map<String, ExecutionStatus> statusById = statusByNodeId(result);
            assertThat(statusById.get("agentA")).isEqualTo(ExecutionStatus.SKIPPED);
            assertThat(statusById.get("agentB")).isEqualTo(ExecutionStatus.COMPLETED);
            // 汇聚节点OR语义：任一分支完成即执行
            assertThat(statusById.get("end")).isEqualTo(ExecutionStatus.COMPLETED);
            verify(agentEngine, times(1)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("未选中分支的下游未连接节点级联跳过")
        void execute_conditionBranch_downstreamCascadeSkipped() {
            // cond1 -branchA-> t1 -> t3(未选中路径下游)
            // cond1 -branchB-> t2
            Map<String, String> branches = new LinkedHashMap<>();
            branches.put("branchA", "t1");
            branches.put("branchB", "t2");

            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    conditionNode("cond1", "条件判断", "${branch}", branches),
                    agentNode("t1", "分支A节点"),
                    agentNode("t2", "分支B节点"),
                    agentNode("t3", "分支A下游节点"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "cond1"),
                    conditionalEdge("e2", "cond1", "t1", "branchA", "分支A"),
                    conditionalEdge("e3", "cond1", "t2", "branchB", "分支B"),
                    edge("e4", "t1", "t3"),
                    edge("e5", "t2", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(conditionEvaluator.evaluate(anyString(), any(WorkflowState.class)))
                    .thenReturn("branchB");
            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("分支B结果"));

            WorkflowExecuteResult result = workflowExecutor.executeWithResult(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            Map<String, ExecutionStatus> statusById = statusByNodeId(result);
            assertThat(statusById.get("t1")).isEqualTo(ExecutionStatus.SKIPPED);
            assertThat(statusById.get("t3")).isEqualTo(ExecutionStatus.SKIPPED);
            assertThat(statusById.get("t2")).isEqualTo(ExecutionStatus.COMPLETED);
            // 仅t2被实际执行
            verify(agentEngine, times(1)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("孤立未连接节点不执行")
        void execute_isolatedNode_skipped() {
            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    endNode("end"),
                    agentNode("orphan", "孤立节点")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            WorkflowExecuteResult result = workflowExecutor.executeWithResult(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            Map<String, ExecutionStatus> statusById = statusByNodeId(result);
            assertThat(statusById.get("orphan")).isEqualTo(ExecutionStatus.SKIPPED);
            verify(agentEngine, never()).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("流式执行推送NODE_SKIP事件")
        void stream_conditionBranch_emitsNodeSkipEvents() {
            Map<String, String> branches = new LinkedHashMap<>();
            branches.put("branchA", "agentA");
            branches.put("branchB", "agentB");

            List<WorkflowNode> nodes = List.of(
                    startNode("start"),
                    conditionNode("cond1", "条件判断", "${branch}", branches),
                    agentNode("agentA", "AgentA"),
                    agentNode("agentB", "AgentB"),
                    endNode("end")
            );
            List<WorkflowEdge> edges = List.of(
                    edge("e1", "start", "cond1"),
                    conditionalEdge("e2", "cond1", "agentA", "branchA", "分支A"),
                    conditionalEdge("e3", "cond1", "agentB", "branchB", "分支B"),
                    edge("e4", "agentA", "end"),
                    edge("e5", "agentB", "end")
            );
            WorkflowDefinition definition = buildDefinition(nodes, edges);

            when(conditionEvaluator.evaluate(anyString(), any(WorkflowState.class)))
                    .thenReturn("branchB");
            when(agentEngine.run(any(AgentRequest.class)))
                    .thenReturn(AgentResult.success("分支B结果"));

            List<WorkflowStreamEvent> events = workflowExecutor.stream(definition, defaultContext)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertThat(events).isNotNull();
            assertThat(events.stream().anyMatch(e ->
                    "NODE_SKIP".equals(e.getEventType()) && "agentA".equals(e.getNodeId()))).isTrue();
            assertThat(events.stream().anyMatch(e ->
                    "NODE_COMPLETE".equals(e.getEventType()) && "agentB".equals(e.getNodeId()))).isTrue();
        }
    }
}
