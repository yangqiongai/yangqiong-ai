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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.approval.ApprovalStatus;
import com.yangqiongai.ai.approval.PendingRequestStore;
import com.yangqiongai.ai.workflow.executor.WorkflowAgentExecutor;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.repository.WorkflowDefinitionRepository;
import com.yangqiongai.ai.workflow.spi.WorkflowGovernanceGate;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DefaultWorkflowEngine 单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultWorkflowEngineTest {

    @Mock
    private WorkflowAgentExecutor workflowExecutor;

    @Mock
    private WorkflowStateStore workflowStateStore;

    @Mock
    private WorkflowDefinitionRepository definitionDbService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectProvider<WorkflowGovernanceGate> governanceGateProvider;

    @Mock
    private ObjectProvider<PendingRequestStore> pendingRequestStoreProvider;

    @Mock
    private PendingRequestStore pendingRequestStore;

    private DefaultWorkflowEngine engine;

    private WorkflowDefinition testDefinition;
    private AgentContext testContext;

    @BeforeEach
    void setUp() {
        engine = new DefaultWorkflowEngine(
                workflowExecutor, workflowStateStore, definitionDbService,
                objectMapper, governanceGateProvider, pendingRequestStoreProvider, 20, 4, 100
        );
        lenient().when(pendingRequestStoreProvider.getIfAvailable()).thenReturn(pendingRequestStore);

        testDefinition = WorkflowDefinition.builder()
                .name("test-workflow")
                .description("测试工作流")
                .build();

        AgentRequest request = new AgentRequest();
        request.setAgentCode("workflow");
        request.setInput("test-input");
        request.setSessionId("test-session");
        testContext = new AgentContext(request);
    }

    // ==================== execute ====================

    @Test
    @DisplayName("execute: 正常执行工作流，返回成功结果")
    void execute_success() {
        AgentResult successResult = AgentResult.success("执行完成");
        when(workflowExecutor.execute(any(WorkflowDefinition.class), any(AgentContext.class)))
                .thenReturn(successResult);

        AgentResult result = engine.execute(testDefinition, testContext);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputAsText()).isEqualTo("执行完成");
        verify(workflowExecutor).execute(testDefinition, testContext);
    }

    // ==================== queryStatus ====================

    @Test
    @DisplayName("queryStatus: 查询已存在的工作流状态")
    void queryStatus_found() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.RUNNING);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        WorkflowState result = engine.queryStatus("inst-001");

        assertThat(result).isNotNull();
        assertThat(result.getInstanceId()).isEqualTo("inst-001");
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        verify(workflowStateStore).load("inst-001");
    }

    @Test
    @DisplayName("queryStatus: 查询不存在的工作流状态，返回null")
    void queryStatus_notFound() {
        when(workflowStateStore.load("non-existent")).thenReturn(null);

        WorkflowState result = engine.queryStatus("non-existent");

        assertThat(result).isNull();
        verify(workflowStateStore).load("non-existent");
    }

    // ==================== pause ====================

    @Test
    @DisplayName("pause: 暂停运行中的工作流，返回true")
    void pause_running() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.RUNNING);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.pause("inst-001");

        assertThat(result).isTrue();
        assertThat(state.getStatus()).isEqualTo(ExecutionStatus.PAUSED);
        assertThat(state.getUpdateTime()).isNotNull();
        verify(workflowStateStore).save(state);
    }

    @Test
    @DisplayName("pause: 暂停非运行中的工作流，返回false")
    void pause_notRunning() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.COMPLETED);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.pause("inst-001");

        assertThat(result).isFalse();
        verify(workflowStateStore, never()).save(any());
    }

    @Test
    @DisplayName("pause: 暂停不存在的工作流，返回false")
    void pause_notFound() {
        when(workflowStateStore.load("non-existent")).thenReturn(null);

        boolean result = engine.pause("non-existent");

        assertThat(result).isFalse();
        verify(workflowStateStore, never()).save(any());
    }

    // ==================== cancel ====================

    @Test
    @DisplayName("cancel: 取消运行中的工作流，设置cancelRequested=true和状态CANCELLED")
    void cancel_running() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.RUNNING);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isTrue();
        assertThat(state.isCancelRequested()).isTrue();
        assertThat(state.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(state.getUpdateTime()).isNotNull();
        verify(workflowStateStore).save(state);
    }

    @Test
    @DisplayName("cancel: 取消待执行的工作流，返回true")
    void cancel_pending() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.PENDING);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isTrue();
        assertThat(state.isCancelRequested()).isTrue();
        assertThat(state.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        verify(workflowStateStore).save(state);
    }

    @Test
    @DisplayName("cancel: 取消已完成的工作流，返回false")
    void cancel_completed() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.COMPLETED);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isFalse();
        verify(workflowStateStore, never()).save(any());
    }

    @Test
    @DisplayName("cancel: 取消已取消的工作流，返回false")
    void cancel_alreadyCancelled() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.CANCELLED);
        state.setCancelRequested(true);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isFalse();
        verify(workflowStateStore, never()).save(any());
    }

    @Test
    @DisplayName("cancel: 取消不存在的工作流，返回false")
    void cancel_notFound() {
        when(workflowStateStore.load("non-existent")).thenReturn(null);

        boolean result = engine.cancel("non-existent");

        assertThat(result).isFalse();
        verify(workflowStateStore, never()).save(any());
    }

    @Test
    @DisplayName("cancel: 级联关闭实例关联的挂起审批待办")
    void cancel_closesPendingRequest() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.PAUSED);
        state.setPendingRequestId("req-001");

        when(workflowStateStore.load("inst-001")).thenReturn(state);
        when(pendingRequestStore.casUpdateStatus(eq("req-001"), eq(ApprovalStatus.PENDING.name()),
                eq(ApprovalStatus.CANCELLED.name()), anyString(), anyString(), any())).thenReturn(1);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isTrue();
        verify(pendingRequestStore).casUpdateStatus(eq("req-001"), eq(ApprovalStatus.PENDING.name()),
                eq(ApprovalStatus.CANCELLED.name()), eq("system:cancel"), eq("工作流已取消"), isNull());
    }

    @Test
    @DisplayName("cancel: 无挂起待办时不触碰审批存储")
    void cancel_withoutPendingRequest() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.RUNNING);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isTrue();
        verify(pendingRequestStore, never()).casUpdateStatus(anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("cancel: 审批存储不可用时仍正常取消")
    void cancel_pendingStoreUnavailable() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.PAUSED);
        state.setPendingRequestId("req-001");

        when(workflowStateStore.load("inst-001")).thenReturn(state);
        when(pendingRequestStoreProvider.getIfAvailable()).thenReturn(null);

        boolean result = engine.cancel("inst-001");

        assertThat(result).isTrue();
        assertThat(state.isCancelled()).isTrue();
        verify(workflowStateStore).save(state);
    }

    // ==================== resume ====================

    @Test
    @DisplayName("resume: 恢复运行中的工作流，返回失败（已在运行）")
    void resume_running() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.RUNNING);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        AgentResult result = engine.resume("inst-001");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("正在运行中");
    }

    @Test
    @DisplayName("resume: 恢复已完成的工作流，返回失败（已完成）")
    void resume_completed() {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setStatus(ExecutionStatus.COMPLETED);

        when(workflowStateStore.load("inst-001")).thenReturn(state);

        AgentResult result = engine.resume("inst-001");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("已完成");
    }

    @Test
    @DisplayName("resume: 恢复不存在的工作流，返回失败")
    void resume_notFound() {
        when(workflowStateStore.load("non-existent")).thenReturn(null);

        AgentResult result = engine.resume("non-existent");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("未找到工作流状态");
    }

    @Test
    @DisplayName("resume: 恢复已暂停的工作流（无下游节点），标记为完成")
    void resume_paused_noDownstreamNodes() throws Exception {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.PAUSED);
        state.setNodeStates(new HashMap<>());
        // 设置definitionSnapshot，使反序列化能返回definition
        state.setDefinitionSnapshot("{\"name\":\"test-workflow\"}");

        when(workflowStateStore.load("inst-001")).thenReturn(state);
        when(objectMapper.readValue(eq("{\"name\":\"test-workflow\"}"), eq(WorkflowDefinition.class)))
                .thenReturn(testDefinition);

        AgentResult result = engine.resume("inst-001");

        assertThat(result.isSuccess()).isTrue();
        assertThat(state.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        verify(workflowStateStore).save(state);
    }

    @Test
    @DisplayName("resume: 恢复已暂停的工作流（有失败节点），委托executor执行")
    void resume_paused_withFailedNodes() throws Exception {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.PAUSED);

        NodeExecutionStatus failedStatus = new NodeExecutionStatus();
        failedStatus.setNodeId("node-1");
        failedStatus.setStatus(ExecutionStatus.FAILED);
        Map<String, NodeExecutionStatus> nodeStates = new HashMap<>();
        nodeStates.put("node-1", failedStatus);
        state.setNodeStates(nodeStates);

        state.setDefinitionSnapshot("{\"name\":\"test-workflow\"}");

        AgentResult resumeResult = AgentResult.success("恢复执行完成");

        when(workflowStateStore.load("inst-001")).thenReturn(state);
        when(objectMapper.readValue(eq("{\"name\":\"test-workflow\"}"), eq(WorkflowDefinition.class)))
                .thenReturn(testDefinition);
        when(workflowExecutor.executeFromNodes(eq(testDefinition), any(AgentContext.class), eq(state), any()))
                .thenReturn(resumeResult);

        AgentResult result = engine.resume("inst-001");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputAsText()).isEqualTo("恢复执行完成");
        verify(workflowExecutor).executeFromNodes(eq(testDefinition), any(AgentContext.class), eq(state), any());
    }

    @Test
    @DisplayName("resume: 快照为空时从数据库加载定义")
    void resume_fallbackToDb() throws Exception {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.PAUSED);

        NodeExecutionStatus failedStatus = new NodeExecutionStatus();
        failedStatus.setNodeId("node-1");
        failedStatus.setStatus(ExecutionStatus.FAILED);
        Map<String, NodeExecutionStatus> nodeStates = new HashMap<>();
        nodeStates.put("node-1", failedStatus);
        state.setNodeStates(nodeStates);

        // 快照为空
        state.setDefinitionSnapshot(null);

        AgentResult resumeResult = AgentResult.success("恢复执行完成");

        when(workflowStateStore.load("inst-001")).thenReturn(state);
        when(definitionDbService.loadByName("test-workflow")).thenReturn(testDefinition);
        when(workflowExecutor.executeFromNodes(eq(testDefinition), any(AgentContext.class), eq(state), any()))
                .thenReturn(resumeResult);

        AgentResult result = engine.resume("inst-001");

        assertThat(result.isSuccess()).isTrue();
        verify(definitionDbService).loadByName("test-workflow");
    }

    @Test
    @DisplayName("resume: 快照和数据库都找不到定义，返回失败")
    void resume_definitionNotFound() throws Exception {
        WorkflowState state = new WorkflowState();
        state.setInstanceId("inst-001");
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.PAUSED);
        state.setNodeStates(new HashMap<>());
        state.setDefinitionSnapshot(null);

        when(workflowStateStore.load("inst-001")).thenReturn(state);
        when(definitionDbService.loadByName("test-workflow")).thenReturn(null);

        AgentResult result = engine.resume("inst-001");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("未找到工作流定义");
    }

    // ==================== executeByName ====================

    @Test
    @DisplayName("executeByName: 从数据库加载定义并执行")
    void executeByName_found() {
        when(definitionDbService.loadByName("test-workflow")).thenReturn(testDefinition);
        AgentResult successResult = AgentResult.success("执行完成");
        when(workflowExecutor.execute(any(WorkflowDefinition.class), any(AgentContext.class)))
                .thenReturn(successResult);

        AgentResult result = engine.executeByName("test-workflow", testContext);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputAsText()).isEqualTo("执行完成");
        verify(definitionDbService).loadByName("test-workflow");
        verify(workflowExecutor).execute(testDefinition, testContext);
    }

    @Test
    @DisplayName("executeByName: 定义不存在，返回失败")
    void executeByName_notFound() {
        when(definitionDbService.loadByName("non-existent")).thenReturn(null);

        AgentResult result = engine.executeByName("non-existent", testContext);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("未找到工作流定义");
        verify(definitionDbService).loadByName("non-existent");
        verify(workflowExecutor, never()).execute(any(), any());
    }

    // ==================== stream ====================

    @Test
    @DisplayName("stream: 委托给workflowExecutor.stream()")
    void stream_delegates() {
        when(workflowExecutor.stream(any(WorkflowDefinition.class), any(AgentContext.class)))
                .thenReturn(reactor.core.publisher.Flux.empty());

        engine.stream(testDefinition, testContext);

        verify(workflowExecutor).stream(testDefinition, testContext);
    }

    // ==================== submit ====================

    @Test
    @DisplayName("submit: 提交异步任务，返回instanceId")
    void submit_returnsInstanceId() {
        lenient().when(workflowExecutor.execute(any(WorkflowDefinition.class), any(AgentContext.class), anyString()))
                .thenReturn(AgentResult.success("异步执行完成"));

        String instanceId = engine.submit(testDefinition, testContext);

        assertThat(instanceId).isNotBlank();
    }

    // ==================== shutdown ====================

    @Test
    @DisplayName("shutdown: 调用后引擎正常关闭")
    void shutdown_completes() {
        engine.shutdown();
        // 验证不抛异常即可
    }
}
