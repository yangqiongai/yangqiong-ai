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
import com.yangqiongai.ai.workflow.model.EdgeType;
import com.yangqiongai.ai.workflow.model.ErrorStrategy;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowEdge;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowPauseHistory;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.workflow.store.WorkflowPauseHistoryStore;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流暂停恢复流水记录测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WorkflowPauseHistoryFlowTest {

    @Mock
    private AgentEngine agentEngine;

    @Mock
    private WorkflowStateStore stateStore;

    @Mock
    private WorkflowExecutionHistoryRepository executionHistoryService;

    @Mock
    private ObjectProvider<com.yangqiongai.ai.workflow.spi.WorkflowExecutionListener> executionListenerProvider;

    @Mock
    private ObjectProvider<com.yangqiongai.ai.workflow.spi.WorkflowApprovalInterceptor> approvalInterceptorProvider;

    @Mock
    private WorkflowPauseHistoryStore pauseHistoryStore;

    private WorkflowAgentExecutor workflowExecutor;

    private AgentContext defaultContext;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkflowStateService stateService = new WorkflowStateService(stateStore, objectMapper);
        workflowExecutor = new WorkflowAgentExecutor(
                agentEngine, mock(ConditionEvaluator.class), executionHistoryService,
                new com.yangqiongai.ai.workflow.repository.NoopWorkflowNodeTraceRepository(),
                new ApprovalNodeHandler(mock(org.springframework.beans.factory.ObjectProvider.class),
                        mock(org.springframework.beans.factory.ObjectProvider.class)),
                new NotifyNodeHandler(mock(org.springframework.beans.factory.ObjectProvider.class),
                        stateService, objectMapper),
                mock(TimeControlNodeHandler.class), pauseHistoryStore,
                new WorkflowGraphSorter(), stateService,
                new TransformNodeExecutor(stateService), new ScriptHttpNodeExecutor(stateService),
                null, objectMapper, executionListenerProvider, approvalInterceptorProvider, 4, 200
        );
        AgentRequest request = new AgentRequest()
                .agentCode("workflow")
                .input("test-input")
                .sessionId("test-session");
        defaultContext = new AgentContext(request);
    }

    /**
     * 构建START -> AGENT -> END线性定义
     * @return
     */
    private WorkflowDefinition buildDefinition() {
        WorkflowNode start = new WorkflowNode();
        start.setId("start");
        start.setName("开始");
        start.setType(NodeType.START);

        com.yangqiongai.ai.workflow.model.AgentNode agent = new com.yangqiongai.ai.workflow.model.AgentNode();
        agent.setId("agent1");
        agent.setName("Agent1");
        agent.setType(NodeType.AGENT);
        agent.setAgentCode("test-agent");

        WorkflowNode end = new WorkflowNode();
        end.setId("end");
        end.setName("结束");
        end.setType(NodeType.END);

        WorkflowEdge e1 = new WorkflowEdge();
        e1.setId("e1");
        e1.setSourceId("start");
        e1.setTargetId("agent1");
        e1.setType(EdgeType.NORMAL);

        WorkflowEdge e2 = new WorkflowEdge();
        e2.setId("e2");
        e2.setSourceId("agent1");
        e2.setTargetId("end");
        e2.setType(EdgeType.NORMAL);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("test-workflow");
        definition.setNodes(List.of(start, agent, end));
        definition.setEdges(List.of(e1, e2));
        definition.setErrorStrategy(ErrorStrategy.STOP);
        return definition;
    }

    // ==================== 暂停流水 ====================

    @Nested
    @DisplayName("暂停流水测试")
    class PauseHistoryTests {

        @Test
        @DisplayName("外部手动暂停在节点边界落定时记录PAUSE流水")
        void manualPauseRecordsPauseHistory() {
            WorkflowDefinition definition = buildDefinition();
            workflowExecutor.requestPause("inst-1", "zhangsan", "暂停核对数据");

            workflowExecutor.execute(definition, defaultContext, "inst-1");

            ArgumentCaptor<WorkflowPauseHistory> captor = ArgumentCaptor.forClass(WorkflowPauseHistory.class);
            verify(pauseHistoryStore, times(1)).record(captor.capture());
            WorkflowPauseHistory history = captor.getValue();
            assertThat(history.getAction()).isEqualTo(WorkflowPauseHistory.ACTION_PAUSE);
            assertThat(history.getInstanceId()).isEqualTo("inst-1");
            assertThat(history.getOperator()).isEqualTo("zhangsan");
            assertThat(history.getReason()).isEqualTo("暂停核对数据");
            assertThat(history.getDefinitionName()).isEqualTo("test-workflow");
        }

        @Test
        @DisplayName("手动暂停未传操作人与原因时使用默认值")
        void manualPauseUsesDefaultMeta() {
            WorkflowDefinition definition = buildDefinition();
            workflowExecutor.requestPause("inst-1", null, null);

            workflowExecutor.execute(definition, defaultContext, "inst-1");

            ArgumentCaptor<WorkflowPauseHistory> captor = ArgumentCaptor.forClass(WorkflowPauseHistory.class);
            verify(pauseHistoryStore, times(1)).record(captor.capture());
            assertThat(captor.getValue().getOperator()).isEqualTo("manual");
            assertThat(captor.getValue().getReason()).isEqualTo("手动暂停");
        }
    }

    // ==================== 恢复流水 ====================

    @Nested
    @DisplayName("恢复流水测试")
    class ResumeHistoryTests {

        @Test
        @DisplayName("手动恢复记录RESUME流水并清空暂停元数据")
        void manualResumeRecordsAndClears() {
            WorkflowState state = new WorkflowState();
            state.setInstanceId("inst-1");
            state.setDefinitionName("test-workflow");
            state.setStatus(ExecutionStatus.PAUSED);
            state.setPausedNodeId("node-2");
            state.setPausedReason("等待审批");
            state.setPausedBy("zhangsan");
            state.setPausedTime(System.currentTimeMillis() - 5000);
            state.setVariable("key1", "value1");

            workflowExecutor.recordManualResume(state, "lisi", "手动恢复");

            ArgumentCaptor<WorkflowPauseHistory> captor = ArgumentCaptor.forClass(WorkflowPauseHistory.class);
            verify(pauseHistoryStore, times(1)).record(captor.capture());
            WorkflowPauseHistory history = captor.getValue();
            assertThat(history.getAction()).isEqualTo(WorkflowPauseHistory.ACTION_RESUME);
            assertThat(history.getInstanceId()).isEqualTo("inst-1");
            assertThat(history.getOperator()).isEqualTo("lisi");
            assertThat(history.getReason()).isEqualTo("手动恢复");
            assertThat(history.getWaitDurationMs()).isGreaterThanOrEqualTo(5000L);
            assertThat(state.getPausedReason()).isNull();
            assertThat(state.getPausedBy()).isNull();
            assertThat(state.getPausedTime()).isNull();
        }

        @Test
        @DisplayName("手动恢复未传操作人与原因时使用默认值")
        void manualResumeUsesDefaultMeta() {
            WorkflowState state = new WorkflowState();
            state.setInstanceId("inst-1");
            state.setStatus(ExecutionStatus.PAUSED);

            workflowExecutor.recordManualResume(state, null, null);

            ArgumentCaptor<WorkflowPauseHistory> captor = ArgumentCaptor.forClass(WorkflowPauseHistory.class);
            verify(pauseHistoryStore, times(1)).record(captor.capture());
            assertThat(captor.getValue().getOperator()).isEqualTo("manual");
            assertThat(captor.getValue().getReason()).isEqualTo("手动恢复");
        }
    }

    // ==================== 时间控制节点恢复 ====================

    /**
     * 构建START -> TIME_CONTROL -> AGENT -> END线性定义
     * @return
     */
    private WorkflowDefinition buildTimeDefinition() {
        WorkflowNode start = new WorkflowNode();
        start.setId("start");
        start.setName("开始");
        start.setType(NodeType.START);

        WorkflowNode timeNode = new WorkflowNode();
        timeNode.setId("time1");
        timeNode.setName("定时等待");
        timeNode.setType(NodeType.TIME_CONTROL);

        com.yangqiongai.ai.workflow.model.AgentNode agent = new com.yangqiongai.ai.workflow.model.AgentNode();
        agent.setId("agent1");
        agent.setName("Agent1");
        agent.setType(NodeType.AGENT);
        agent.setAgentCode("test-agent");

        WorkflowNode end = new WorkflowNode();
        end.setId("end");
        end.setName("结束");
        end.setType(NodeType.END);

        WorkflowEdge e1 = new WorkflowEdge();
        e1.setId("e1");
        e1.setSourceId("start");
        e1.setTargetId("time1");
        e1.setType(EdgeType.NORMAL);

        WorkflowEdge e2 = new WorkflowEdge();
        e2.setId("e2");
        e2.setSourceId("time1");
        e2.setTargetId("agent1");
        e2.setType(EdgeType.NORMAL);

        WorkflowEdge e3 = new WorkflowEdge();
        e3.setId("e3");
        e3.setSourceId("agent1");
        e3.setTargetId("end");
        e3.setType(EdgeType.NORMAL);

        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setName("test-workflow");
        definition.setNodes(List.of(start, timeNode, agent, end));
        definition.setEdges(List.of(e1, e2, e3));
        definition.setErrorStrategy(ErrorStrategy.STOP);
        return definition;
    }

    /**
     * 构建时间节点暂停中的实例状态
     * @return
     */
    private WorkflowState buildTimePausedState() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-time");
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.PAUSED);
        state.setPausedNodeId("time1");
        state.setPausedReason("时间控制等待");
        state.setPausedBy("system");
        state.setPausedTime(System.currentTimeMillis() - 5000);
        state.setPendingRequestId("time:time1");
        NodeExecutionStatus timeStatus = new NodeExecutionStatus();
        timeStatus.setNodeId("time1");
        timeStatus.setStatus(ExecutionStatus.RUNNING);
        timeStatus.setStartTime(System.currentTimeMillis() - 5000);
        state.setNodeState("time1", timeStatus);
        return state;
    }

    @Nested
    @DisplayName("时间控制节点恢复测试")
    class TimeControlResumeTests {

        @Test
        @DisplayName("手动恢复时间节点跳过等待继续执行并记录RESUME流水")
        void manualResumeSkipsWaitAndRecordsHistory() {
            WorkflowDefinition definition = buildTimeDefinition();
            WorkflowState state = buildTimePausedState();
            when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("agent-output"));

            AgentResult result = workflowExecutor.resumeFromTimeControl(definition, defaultContext, state, "lisi", "提前恢复");

            assertThat(result.isSuccess()).isTrue();
            ArgumentCaptor<WorkflowPauseHistory> captor = ArgumentCaptor.forClass(WorkflowPauseHistory.class);
            verify(pauseHistoryStore, times(1)).record(captor.capture());
            WorkflowPauseHistory history = captor.getValue();
            assertThat(history.getAction()).isEqualTo(WorkflowPauseHistory.ACTION_RESUME);
            assertThat(history.getInstanceId()).isEqualTo("inst-time");
            assertThat(history.getOperator()).isEqualTo("lisi");
            assertThat(history.getReason()).isEqualTo("提前恢复");
            assertThat(history.getWaitDurationMs()).isGreaterThanOrEqualTo(5000L);
            assertThat(state.getPausedNodeId()).isNull();
            assertThat(state.getPendingRequestId()).isNull();
            assertThat(state.getNodeState("time1").getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            verify(agentEngine, times(1)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("时间到点恢复使用system操作人与默认原因")
        void autoResumeUsesSystemOperatorAndDefaultReason() {
            WorkflowDefinition definition = buildTimeDefinition();
            WorkflowState state = buildTimePausedState();
            when(agentEngine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("agent-output"));

            AgentResult result = workflowExecutor.resumeFromTimeControl(definition, defaultContext, state);

            assertThat(result.isSuccess()).isTrue();
            ArgumentCaptor<WorkflowPauseHistory> captor = ArgumentCaptor.forClass(WorkflowPauseHistory.class);
            verify(pauseHistoryStore, times(1)).record(captor.capture());
            assertThat(captor.getValue().getOperator()).isEqualTo("system");
            assertThat(captor.getValue().getReason()).isEqualTo("时间控制到达设定时间");
        }

        @Test
        @DisplayName("暂停节点不是时间控制节点时恢复失败且不记流水")
        void resumeFailsWhenNodeNotTimeControl() {
            WorkflowDefinition definition = buildTimeDefinition();
            WorkflowState state = buildTimePausedState();
            state.setPausedNodeId("agent1");

            AgentResult result = workflowExecutor.resumeFromTimeControl(definition, defaultContext, state, "lisi", "恢复");

            assertThat(result.isSuccess()).isFalse();
            verify(pauseHistoryStore, times(0)).record(any());
            verify(agentEngine, times(0)).run(any(AgentRequest.class));
        }

        @Test
        @DisplayName("已取消的实例时间恢复跳过")
        void resumeSkippedWhenCancelled() {
            WorkflowDefinition definition = buildTimeDefinition();
            WorkflowState state = buildTimePausedState();
            state.setCancelRequested(true);

            AgentResult result = workflowExecutor.resumeFromTimeControl(definition, defaultContext, state, "lisi", "恢复");

            assertThat(result.isSuccess()).isFalse();
            verify(pauseHistoryStore, times(0)).record(any());
            verify(agentEngine, times(0)).run(any(AgentRequest.class));
        }
    }
}
