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
import com.yangqiongai.ai.workflow.model.*;
import com.yangqiongai.ai.workflow.repository.NoopWorkflowNodeTraceRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowNodeTraceRepository;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 节点执行轨迹埋点单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WorkflowNodeTraceTest {

    @Mock
    private AgentEngine agentEngine;

    @Mock
    private WorkflowStateStore stateStore;

    @Mock
    private ConditionEvaluator conditionEvaluator;

    @Mock
    private WorkflowExecutionHistoryRepository executionHistoryRepository;

    @Mock
    private ObjectProvider<ApprovalGate> approvalGateProvider;

    @Mock
    private ObjectProvider<WorkflowExecutionListener> executionListenerProvider;

    @Mock
    private ObjectProvider<WorkflowApprovalInterceptor> approvalInterceptorProvider;

    @Mock
    private ObjectProvider<com.yangqiongai.ai.workflow.spi.WorkflowNotifySender> notifySenderProvider;

    @Mock
    private ScriptHttpNodeExecutor scriptHttpExecutor;

    private RecordingTraceRepository traceRepository;

    private WorkflowAgentExecutor workflowExecutor;

    private AgentContext defaultContext;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowStateService stateService = new WorkflowStateService(stateStore, objectMapper);
        TransformNodeExecutor transformExecutor = new TransformNodeExecutor(stateService);
        ApprovalNodeHandler approvalHandler = new ApprovalNodeHandler(approvalGateProvider, approvalInterceptorProvider);
        NotifyNodeHandler notifyHandler = new NotifyNodeHandler(notifySenderProvider, stateService, objectMapper);
        traceRepository = new RecordingTraceRepository();
        workflowExecutor = new WorkflowAgentExecutor(
                agentEngine, conditionEvaluator, executionHistoryRepository,
                traceRepository,
                approvalHandler, notifyHandler,
                org.mockito.Mockito.mock(TimeControlNodeHandler.class),
                org.mockito.Mockito.mock(com.yangqiongai.ai.workflow.store.WorkflowPauseHistoryStore.class),
                new WorkflowGraphSorter(), stateService,
                transformExecutor, scriptHttpExecutor, null, objectMapper,
                executionListenerProvider, approvalInterceptorProvider, 4, 200
        );
        AgentRequest request = new AgentRequest()
                .agentCode("workflow")
                .input("test-input")
                .sessionId("test-session")
                .scopeId("scope-1");
        defaultContext = new AgentContext(request);
    }

    // ==================== 辅助方法 ====================

    /**
     * 覆盖写语义的内存轨迹仓储（模拟数据库同实例同节点仅保留最终一次）
     */
    private static class RecordingTraceRepository implements WorkflowNodeTraceRepository {

        final Map<String, WorkflowNodeTrace> traces = new LinkedHashMap<>();

        int failCountdown = 0;

        @Override
        public void record(WorkflowNodeTrace trace) {
            if (failCountdown > 0) {
                failCountdown--;
                throw new RuntimeException("模拟轨迹写入失败");
            }
            traces.put(trace.getNodeId(), trace);
        }

        @Override
        public List<WorkflowNodeTrace> listByInstance(String instanceId) {
            return traces.values().stream()
                    .filter(t -> Objects.equals(t.getInstanceId(), instanceId))
                    .toList();
        }
    }

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

    private AgentNode agentNode(String id, String name, Map<String, String> inputMappings) {
        AgentNode node = new AgentNode();
        node.setId(id);
        node.setName(name);
        node.setType(NodeType.AGENT);
        node.setAgentCode("test-agent");
        node.setInputMappings(inputMappings);
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

    private LoopNode loopNode(String id, String name, Integer maxIterations, List<WorkflowNode> subNodes) {
        LoopNode node = new LoopNode();
        node.setId(id);
        node.setName(name);
        node.setType(NodeType.LOOP);
        node.setMaxIterations(maxIterations);
        node.setSubNodes(subNodes);
        return node;
    }

    private WorkflowEdge edge(String id, String sourceId, String targetId) {
        WorkflowEdge e = new WorkflowEdge();
        e.setId(id);
        e.setSourceId(sourceId);
        e.setTargetId(targetId);
        e.setType(EdgeType.NORMAL);
        return e;
    }

    private WorkflowDefinition buildDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges,
                                               ErrorStrategy errorStrategy, int maxRetries) {
        WorkflowDefinition def = WorkflowDefinition.builder()
                .name("trace-test-workflow")
                .description("轨迹测试工作流")
                .nodes(nodes)
                .edges(edges)
                .errorStrategy(errorStrategy)
                .maxRetries(maxRetries)
                .stateConfig(StateConfig.builder().persistEnabled(true).build())
                .build();
        def.setNodes(nodes);
        def.setEdges(edges);
        return def;
    }

    private WorkflowState buildState(String instanceId) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(instanceId);
        state.setDefinitionName("trace-test-workflow");
        state.setStatus(ExecutionStatus.RUNNING);
        state.setNodeStates(new ConcurrentHashMap<>());
        state.setVariables(new ConcurrentHashMap<>());
        state.setCreateTime(System.currentTimeMillis());
        return state;
    }

    // ==================== 测试用例 ====================

    @Nested
    @DisplayName("轨迹完整性")
    class TraceCompleteness {

        @Test
        @DisplayName("正常同步执行后每个节点落一条轨迹且字段完整")
        void executeSuccess_recordsCompleteTrace() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowNode agent = agentNode("agent1", "代理节点", Map.of("question", "${prompt}"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agent, endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.STOP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext);

            assertThat(traceRepository.traces).containsKeys("start", "agent1", "end");
            WorkflowNodeTrace agentTrace = traceRepository.traces.get("agent1");
            assertThat(agentTrace.getInstanceId()).isNotBlank();
            assertThat(agentTrace.getNodeId()).isEqualTo("agent1");
            assertThat(agentTrace.getNodeName()).isEqualTo("代理节点");
            assertThat(agentTrace.getNodeType()).isEqualTo("AGENT");
            assertThat(agentTrace.getStatus()).isEqualTo("COMPLETED");
            assertThat(agentTrace.getErrorMessage()).isNull();
            assertThat(agentTrace.getStartTime()).isNotNull();
            assertThat(agentTrace.getEndTime()).isNotNull();
            assertThat(agentTrace.getDurationMs()).isGreaterThanOrEqualTo(0L);
            assertThat(agentTrace.getRetryCount()).isEqualTo(0);
            assertThat(agentTrace.getScopeId()).isEqualTo("scope-1");
        }

        @Test
        @DisplayName("执行顺序按拓扑序号写入且从1开始")
        void executionOrder_followsTopologicalSequence() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.STOP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext);

            assertThat(traceRepository.traces.get("start").getExecutionOrder()).isEqualTo(1);
            assertThat(traceRepository.traces.get("agent1").getExecutionOrder()).isEqualTo(2);
            assertThat(traceRepository.traces.get("end").getExecutionOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("节点失败轨迹状态与错误信息正确")
        void nodeFailure_recordsFailedTrace() {
            when(agentEngine.run(any())).thenReturn(AgentResult.failure("模型调用失败"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.SKIP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext);

            WorkflowNodeTrace agentTrace = traceRepository.traces.get("agent1");
            assertThat(agentTrace.getStatus()).isEqualTo("FAILED");
            assertThat(agentTrace.getErrorMessage()).contains("模型调用失败");
        }

        @Test
        @DisplayName("节点输入按inputMappings解析写入轨迹")
        void nodeInput_resolvedByInputMappings() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowNode agent = agentNode("agent1", "代理节点", Map.of("question", "${prompt}"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agent, endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.STOP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext);

            WorkflowNodeTrace agentTrace = traceRepository.traces.get("agent1");
            assertThat(agentTrace.getInputData()).isNotNull();
            assertThat(agentTrace.getInputData()).containsEntry("question", null);
        }

        @Test
        @DisplayName("异步submit路径同样落轨迹")
        void submitPath_recordsTrace() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.STOP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext, "async-instance-1");

            assertThat(traceRepository.listByInstance("async-instance-1")).hasSize(3);
        }
    }

    @Nested
    @DisplayName("覆盖写语义")
    class OverwriteSemantics {

        @Test
        @DisplayName("重试耗尽后同节点仅保留最终一次轨迹且retryCount正确")
        void retry_overwritesToFinalTrace() {
            when(agentEngine.run(any())).thenReturn(AgentResult.failure("模型调用失败"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.RETRY, 1);

            AgentResult result = workflowExecutor.execute(definition, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(traceRepository.traces).containsKey("agent1");
            assertThat(traceRepository.traces).doesNotContainKeys("end");
            WorkflowNodeTrace agentTrace = traceRepository.traces.get("agent1");
            assertThat(agentTrace.getStatus()).isEqualTo("FAILED");
            assertThat(agentTrace.getRetryCount()).isEqualTo(1);
            assertThat(traceRepository.listByInstance(agentTrace.getInstanceId()))
                    .filteredOn(t -> t.getNodeId().equals("agent1"))
                    .hasSize(1);
        }

        @Test
        @DisplayName("恢复路径已执行过的节点不重复写，重新执行的节点覆盖写")
        void resumePath_noDuplicateForCompletedNodes() {
            WorkflowNode agent = agentNode("agent1", "代理", Map.of());
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agent, endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.STOP, 0);
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));

            WorkflowState state = buildState("resume-instance-1");
            NodeExecutionStatus startStatus = new NodeExecutionStatus();
            startStatus.setNodeId("start");
            startStatus.setStatus(ExecutionStatus.COMPLETED);
            startStatus.setStartTime(System.currentTimeMillis());
            startStatus.setEndTime(System.currentTimeMillis());
            state.setNodeState("start", startStatus);
            NodeExecutionStatus agentStatus = new NodeExecutionStatus();
            agentStatus.setNodeId("agent1");
            agentStatus.setStatus(ExecutionStatus.FAILED);
            agentStatus.setStartTime(System.currentTimeMillis());
            state.setNodeState("agent1", agentStatus);

            workflowExecutor.executeFromNodes(definition, defaultContext, state, List.of("agent1"));

            assertThat(traceRepository.traces).doesNotContainKey("start");
            assertThat(traceRepository.traces.get("agent1").getStatus()).isEqualTo("COMPLETED");
            assertThat(traceRepository.traces).containsKey("end");
        }
    }

    @Nested
    @DisplayName("特殊节点轨迹")
    class SpecialNodeTrace {

        @Test
        @DisplayName("CONDITION节点branch_taken命中值正确")
        void conditionNode_recordsBranchTaken() {
            when(conditionEvaluator.evaluate(anyString(), any())).thenReturn("yes");
            ConditionNode condition = conditionNode("cond1", "条件", "input == 'yes'", Map.of("yes", "end"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), condition, endNode("end")),
                    List.of(edge("e1", "start", "cond1"), edge("e2", "cond1", "end")),
                    ErrorStrategy.STOP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext);

            WorkflowNodeTrace conditionTrace = traceRepository.traces.get("cond1");
            assertThat(conditionTrace.getStatus()).isEqualTo("COMPLETED");
            assertThat(conditionTrace.getBranchTaken()).isEqualTo("yes");
        }

        @Test
        @DisplayName("循环节点iteration_count记录迭代次数")
        void loopNode_recordsIterationCount() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            LoopNode loop = loopNode("loop1", "循环", 2, List.of(agentNode("agent1", "代理", Map.of())));
            // 配置退出条件使其按条件循环模式执行2轮（无退出条件时仅执行一次）
            loop.setExitCondition("${loop1.iteration} >= 2");
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), loop, endNode("end")),
                    List.of(edge("e1", "start", "loop1"), edge("e2", "loop1", "end")),
                    ErrorStrategy.STOP, 0);

            workflowExecutor.executeWithResult(definition, defaultContext);

            WorkflowNodeTrace loopTrace = traceRepository.traces.get("loop1");
            assertThat(loopTrace.getStatus()).isEqualTo("COMPLETED");
            assertThat(loopTrace.getIterationCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("取消实例时循环体节点保留轨迹且循环及时中断")
        void cancelledNode_recordsCancelledTrace() {
            WorkflowState state = buildState("cancel-instance-1");
            LoopNode loop = loopNode("loop1", "循环", 3, List.of(scriptNode()));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), loop, endNode("end")),
                    List.of(edge("e1", "start", "loop1"), edge("e2", "loop1", "end")),
                    ErrorStrategy.STOP, 0);
            // 脚本节点执行中产生取消请求，循环体正常完成后循环在下一边界及时中断
            when(scriptHttpExecutor.executeScriptNode(any(), any())).thenAnswer(inv -> {
                WorkflowState s = inv.getArgument(0);
                s.setCancelRequested(true);
                return AgentResult.success("ok");
            });

            AgentResult result = workflowExecutor.executeFromNodes(definition, defaultContext, state,
                    List.of("loop1"));

            assertThat(result.isSuccess()).isFalse();
            assertThat(traceRepository.traces.get("script1").getStatus()).isEqualTo("COMPLETED");
            assertThat(traceRepository.traces.get("loop1").getStatus()).isEqualTo("FAILED");
        }

        private WorkflowNode scriptNode() {
            WorkflowNode node = new WorkflowNode();
            node.setId("script1");
            node.setName("脚本");
            node.setType(NodeType.SCRIPT);
            return node;
        }
    }

    @Nested
    @DisplayName("容错与兜底")
    class FaultTolerance {

        @Test
        @DisplayName("轨迹写入失败仅告警，不影响工作流主流程")
        void traceWriteFailure_doesNotBreakWorkflow() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            traceRepository.failCountdown = Integer.MAX_VALUE;
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of(edge("e1", "start", "agent1"), edge("e2", "agent1", "end")),
                    ErrorStrategy.STOP, 0);

            var result = workflowExecutor.executeWithResult(definition, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(traceRepository.traces).isEmpty();
        }

        @Test
        @DisplayName("Noop兜底实现不报错且返回空列表")
        void noopRepository_neverThrows() {
            NoopWorkflowNodeTraceRepository noop = new NoopWorkflowNodeTraceRepository();

            assertThatCode(() -> noop.record(null)).doesNotThrowAnyException();
            assertThat(noop.listByInstance("any")).isEmpty();
        }
    }
}
