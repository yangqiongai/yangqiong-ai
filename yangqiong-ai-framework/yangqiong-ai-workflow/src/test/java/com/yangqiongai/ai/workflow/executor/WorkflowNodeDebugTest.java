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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 单节点调试执行单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WorkflowNodeDebugTest {

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
                .input("debug-input")
                .sessionId("debug-session")
                .scopeId("scope-1");
        defaultContext = new AgentContext(request);
    }

    // ==================== 辅助方法 ====================

    /**
     * 记录型轨迹仓储（验证调试路径不落轨迹）
     */
    private static class RecordingTraceRepository implements WorkflowNodeTraceRepository {

        final Map<String, WorkflowNodeTrace> traces = new java.util.LinkedHashMap<>();

        @Override
        public void record(WorkflowNodeTrace trace) {
            traces.put(trace.getNodeId(), trace);
        }

        @Override
        public List<WorkflowNodeTrace> listByInstance(String instanceId) {
            return traces.values().stream()
                    .filter(t -> java.util.Objects.equals(t.getInstanceId(), instanceId))
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

    private WorkflowNode approvalNode(String id, String name) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName(name);
        node.setType(NodeType.APPROVAL);
        return node;
    }

    private WorkflowDefinition buildDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowDefinition def = WorkflowDefinition.builder()
                .name("debug-test-workflow")
                .description("调试测试工作流")
                .nodes(nodes)
                .edges(edges)
                .errorStrategy(ErrorStrategy.STOP)
                .maxRetries(0)
                .stateConfig(StateConfig.builder().persistEnabled(true).build())
                .build();
        def.setNodes(nodes);
        def.setEdges(edges);
        return def;
    }

    private WorkflowExecuteResult.NodeSummary firstSummary(WorkflowExecuteResult result) {
        assertThat(result.getNodeSummaries()).isNotNull().hasSize(1);
        return result.getNodeSummaries().get(0);
    }

    // ==================== 测试用例 ====================

    @Nested
    @DisplayName("基础调试语义")
    class BasicDebugSemantics {

        @Test
        @DisplayName("不存在的nodeId返回明确错误")
        void unknownNodeId_returnsClearError() {
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "not-exist", null, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("不存在节点").contains("not-exist");
            assertThat(result.getNodeSummaries()).isNull();
        }

        @Test
        @DisplayName("调试使用debug前缀临时实例ID且不落轨迹、不写历史")
        void debugExecution_usesTempInstanceAndNoPersistence() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "agent1", null, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getInstanceId()).startsWith("debug-");
            assertThat(traceRepository.traces).isEmpty();
            verifyNoInteractions(executionHistoryRepository);
        }

        @Test
        @DisplayName("START节点可调试且语义正确")
        void startNode_debuggable() {
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "start", Map.of("foo", "bar"), defaultContext);

            WorkflowExecuteResult.NodeSummary summary = firstSummary(result);
            assertThat(result.isSuccess()).isTrue();
            assertThat(summary.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(summary.getInput()).containsEntry("foo", "bar");
        }

        @Test
        @DisplayName("END节点可调试且输出成功")
        void endNode_debuggable() {
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "end", null, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(firstSummary(result).getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Mock变量与输入解析")
    class MockVariables {

        @Test
        @DisplayName("AGENT节点真实调用且Mock变量参与输入解析")
        void agentNode_realCallWithMockVariables() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of("question", "${prompt}")), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "agent1", Map.of("prompt", "你好"), defaultContext);

            WorkflowExecuteResult.NodeSummary summary = firstSummary(result);
            assertThat(result.isSuccess()).isTrue();
            assertThat(summary.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(summary.getInput()).containsEntry("question", "你好");
            assertThat(summary.getOutput()).containsEntry("output", "agent输出");
            verify(agentEngine).run(any());
        }

        @Test
        @DisplayName("Mock变量缺失时降级为空值并返回告警")
        void missingMockVariables_degradeToEmptyWithWarning() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of("question", "${prompt}")), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "agent1", null, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(firstSummary(result).getInput()).containsEntry("question", "");
            assertThat(result.getErrorMessage()).contains("Mock变量缺失").contains("${prompt}");
        }

        @Test
        @DisplayName("调试结果包含过滤内部变量后的状态变量")
        void debugResult_containsBusinessVariables() {
            when(scriptHttpExecutor.executeScriptNode(any(), any())).thenAnswer(inv -> {
                WorkflowState s = inv.getArgument(0);
                s.setVariable("result", "脚本结果");
                s.setVariable("_internal", "内部值");
                return AgentResult.success("脚本输出");
            });
            WorkflowNode script = new WorkflowNode();
            script.setId("script1");
            script.setName("脚本");
            script.setType(NodeType.SCRIPT);
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), script, endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "script1", null, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getVariables()).containsEntry("result", "脚本结果");
            assertThat(result.getVariables()).doesNotContainKey("_internal");
        }
    }

    @Nested
    @DisplayName("特殊节点与失败场景")
    class SpecialNodesAndFailures {

        @Test
        @DisplayName("APPROVAL节点调试不产生待办且输出Mock审批通过")
        void approvalNode_mockWithoutPendingRequest() {
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), approvalNode("approval1", "审批"), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "approval1", null, defaultContext);

            assertThat(result.isSuccess()).isTrue();
            assertThat(firstSummary(result).getOutput()).containsEntry("output", "Mock审批通过");
            assertThat(result.getVariables()).containsEntry("approval1.approvalResult", "APPROVED");
            verifyNoInteractions(approvalGateProvider);
            verifyNoInteractions(executionHistoryRepository);
            assertThat(traceRepository.traces).isEmpty();
        }

        @Test
        @DisplayName("节点执行失败时调试结果状态与错误信息正确")
        void nodeFailure_failedDebugResult() {
            when(agentEngine.run(any())).thenReturn(AgentResult.failure("模型调用失败"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "agent1", null, defaultContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            WorkflowExecuteResult.NodeSummary summary = firstSummary(result);
            assertThat(summary.getStatus()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(summary.getErrorMessage()).contains("模型调用失败");
            assertThat(result.getErrorMessage()).contains("模型调用失败");
        }

        @Test
        @DisplayName("节点执行抛异常时调试结果转换为失败且不向外抛出")
        void nodeException_convertedToFailureResult() {
            when(agentEngine.run(any())).thenThrow(new RuntimeException("引擎异常"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of());

            assertThatCode(() -> {
                WorkflowExecuteResult result = workflowExecutor.debugNode(
                        definition, "agent1", null, defaultContext);
                assertThat(result.isSuccess()).isFalse();
                assertThat(firstSummary(result).getErrorMessage()).contains("节点执行异常").contains("引擎异常");
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("调试结果记录节点耗时")
        void debugResult_recordsDuration() {
            when(agentEngine.run(any())).thenReturn(AgentResult.success("agent输出"));
            WorkflowDefinition definition = buildDefinition(
                    List.of(startNode("start"), agentNode("agent1", "代理", Map.of()), endNode("end")),
                    List.of());

            WorkflowExecuteResult result = workflowExecutor.debugNode(
                    definition, "agent1", null, defaultContext);

            assertThat(firstSummary(result).getDurationMs()).isGreaterThanOrEqualTo(0L);
            assertThat(result.getTotalDurationMs()).isGreaterThanOrEqualTo(0L);
        }
    }
}
