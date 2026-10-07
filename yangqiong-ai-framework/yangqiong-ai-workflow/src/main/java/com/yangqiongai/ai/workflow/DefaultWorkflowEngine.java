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
import com.yangqiongai.ai.approval.ApprovalResolvedEvent;
import com.yangqiongai.ai.approval.ApprovalStatus;
import com.yangqiongai.ai.approval.PendingRequestStore;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.executor.WorkflowAgentExecutor;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.model.WorkflowStreamEvent;
import com.yangqiongai.ai.workflow.event.WorkflowTimeArrivedEvent;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import com.yangqiongai.ai.workflow.repository.WorkflowDefinitionRepository;
import com.yangqiongai.ai.workflow.spi.GovernanceDecision;
import com.yangqiongai.ai.workflow.spi.WorkflowGovernanceGate;
import com.yangqiongai.ai.common.scope.ScopeContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 默认工作流引擎实现
 * @author yangqiong
 */
@Service
public class DefaultWorkflowEngine implements WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkflowEngine.class);

    private final WorkflowAgentExecutor workflowExecutor;
    private final WorkflowStateStore workflowStateStore;
    private final WorkflowDefinitionRepository definitionRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<WorkflowGovernanceGate> governanceGateProvider;
    private final ObjectProvider<PendingRequestStore> pendingRequestStoreProvider;

    private final ExecutorService workflowAsyncExecutor;
    private final Semaphore concurrencyLimiter;

    private final Map<String, CompletableFuture<AgentResult>> runningWorkflowTasks = new ConcurrentHashMap<>();

    public DefaultWorkflowEngine(WorkflowAgentExecutor workflowExecutor,
                                  WorkflowStateStore workflowStateStore,
                                  WorkflowDefinitionRepository definitionRepository,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<WorkflowGovernanceGate> governanceGateProvider,
                                  ObjectProvider<PendingRequestStore> pendingRequestStoreProvider,
                                  @Value("${workflow.engine.max-concurrent:20}") int maxConcurrent,
                                  @Value("${workflow.engine.pool-size:4}") int poolSize,
                                  @Value("${workflow.engine.queue-capacity:100}") int queueCapacity) {
        this.workflowExecutor = workflowExecutor;
        this.workflowStateStore = workflowStateStore;
        this.definitionRepository = definitionRepository;
        this.objectMapper = objectMapper;
        this.governanceGateProvider = governanceGateProvider;
        this.pendingRequestStoreProvider = pendingRequestStoreProvider;
        this.concurrencyLimiter = new Semaphore(maxConcurrent);
        this.workflowAsyncExecutor = new ThreadPoolExecutor(
                poolSize, poolSize * 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> { Thread t = new Thread(r, "workflow-async"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PreDestroy
    public void shutdown() {
        workflowAsyncExecutor.shutdown();
        try {
            if (!workflowAsyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                workflowAsyncExecutor.shutdownNow();
                log.warn("DefaultWorkflowEngine线程池强制关闭");
            }
        } catch (InterruptedException e) {
            workflowAsyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public AgentResult execute(WorkflowDefinition definition, AgentContext context) {
        GovernanceOutcome outcome = applyGovernance(definition, context);
        if (outcome.rejectReason() != null) {
            return AgentResult.failure(outcome.rejectReason());
        }
        definition = outcome.definition();
        try {
            if (!concurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS)) {
                return AgentResult.failure("并发工作流数量超过限制，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.failure("获取并发许可被中断");
        }
        try {
            log.info("开始同步执行工作流: name={}", definition.getName());
            return workflowExecutor.execute(definition, context);
        } finally {
            concurrencyLimiter.release();
        }
    }

    @Override
    public WorkflowExecuteResult executeWithResult(WorkflowDefinition definition, AgentContext context) {
        GovernanceOutcome outcome = applyGovernance(definition, context);
        if (outcome.rejectReason() != null) {
            WorkflowExecuteResult failResult = new WorkflowExecuteResult();
            failResult.setSuccess(false);
            failResult.setErrorMessage(outcome.rejectReason());
            failResult.setDefinitionName(definition.getName());
            return failResult;
        }
        definition = outcome.definition();
        try {
            if (!concurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS)) {
                WorkflowExecuteResult failResult = new WorkflowExecuteResult();
                failResult.setSuccess(false);
                failResult.setErrorMessage("并发工作流数量超过限制，请稍后重试");
                failResult.setDefinitionName(definition.getName());
                return failResult;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            WorkflowExecuteResult failResult = new WorkflowExecuteResult();
            failResult.setSuccess(false);
            failResult.setErrorMessage("获取并发许可被中断");
            failResult.setDefinitionName(definition.getName());
            return failResult;
        }
        try {
            log.info("开始同步执行工作流(结构化结果): name={}", definition.getName());
            return workflowExecutor.executeWithResult(definition, context);
        } finally {
            concurrencyLimiter.release();
        }
    }

    @Override
    public Flux<WorkflowStreamEvent> stream(WorkflowDefinition definition, AgentContext context) {
        GovernanceOutcome outcome = applyGovernance(definition, context);
        if (outcome.rejectReason() != null) {
            return Flux.error(new IllegalStateException(outcome.rejectReason()));
        }
        definition = outcome.definition();
        log.info("开始流式执行工作流: name={}", definition.getName());
        return workflowExecutor.stream(definition, context);
    }

    @Override
    public String submit(WorkflowDefinition definition, AgentContext context) {
        // 同步入口段捕获scopeId写入上下文，供异步线程与审计监听使用
        String scopeId = resolveScopeId(context);
        context.setAttribute("__scopeId", scopeId);
        GovernanceOutcome outcome = applyGovernance(definition, context);
        if (outcome.rejectReason() != null) {
            throw new IllegalStateException(outcome.rejectReason());
        }
        WorkflowDefinition effectiveDefinition = outcome.definition();
        String instanceId = UUID.randomUUID().toString();
        log.info("提交异步工作流任务: name={}, instanceId={}", effectiveDefinition.getName(), instanceId);

        // 序列化definition快照到state，用于恢复
        String definitionSnapshot = serializeDefinition(effectiveDefinition);

        CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                if (!concurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("并发工作流数量超过限制");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("获取并发许可被中断", e);
            }
            try {
                return workflowExecutor.execute(effectiveDefinition, context, instanceId);
            } catch (Exception e) {
                log.error("异步工作流执行异常: instanceId={}, name={}", instanceId, effectiveDefinition.getName(), e);
                throw e;
            } finally {
                concurrencyLimiter.release();
            }
        }, workflowAsyncExecutor);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("异步工作流执行失败: instanceId={}", instanceId, ex);
                WorkflowState state = workflowStateStore.load(instanceId);
                if (state != null) {
                    state.setStatus(ExecutionStatus.FAILED);
                    state.setVariable("errorMessage", ex.getMessage());
                    state.setUpdateTime(System.currentTimeMillis());
                    workflowStateStore.save(state);
                }
            }
            runningWorkflowTasks.remove(instanceId);
        });

        runningWorkflowTasks.put(instanceId, future);
        return instanceId;
    }

    @Override
    public WorkflowState queryStatus(String instanceId) {
        return workflowStateStore.load(instanceId);
    }

    @Override
    public AgentResult resume(String instanceId) {
        return resume(instanceId, null, null);
    }

    @Override
    public AgentResult resume(String instanceId, String operator, String reason) {
        log.info("恢复工作流执行: instanceId={}, operator={}", instanceId, operator);

        WorkflowState state = workflowStateStore.load(instanceId);
        if (state == null) {
            return AgentResult.failure("未找到工作流状态: " + instanceId);
        }

        if (state.isRunning()) {
            return AgentResult.failure("工作流正在运行中，无法恢复: " + instanceId);
        }

        if (state.isCompleted()) {
            return AgentResult.failure("工作流已完成，无需恢复: " + instanceId);
        }

        // 从state快照恢复definition
        WorkflowDefinition definition = deserializeDefinition(state.getDefinitionSnapshot());
        if (definition == null) {
            // fallback: 从数据库加载
            definition = loadDefinitionForState(state);
        }
        if (definition == null) {
            return AgentResult.failure("未找到工作流定义，无法恢复: " + instanceId);
        }

        // 构建恢复上下文
        AgentContext context = buildResumeContext(state);

        // 停在时间控制节点的实例手动恢复等效于跳过剩余等待：将时间节点标记完成后从其下游继续
        String pendingRequestId = state.getPendingRequestId();
        if (pendingRequestId != null && pendingRequestId.startsWith("time:")) {
            return workflowExecutor.resumeFromTimeControl(definition, context, state, operator, reason);
        }

        // 记录手动恢复流水并清空暂停元数据
        workflowExecutor.recordManualResume(state, operator, reason);

        List<String> downstreamNodes = findDownstreamNodesFromLastCompleted(state);
        if (downstreamNodes.isEmpty()) {
            log.info("无需恢复的节点，工作流可能已全部完成: instanceId={}", instanceId);
            state.setStatus(ExecutionStatus.COMPLETED);
            state.setUpdateTime(System.currentTimeMillis());
            workflowStateStore.save(state);
            return AgentResult.success("工作流无需恢复");
        }

        return workflowExecutor.executeFromNodes(definition, context, state, downstreamNodes);
    }

    @Override
    public AgentResult executeByName(String definitionName, AgentContext context) {
        WorkflowDefinition definition = definitionRepository.loadByName(definitionName);
        if (definition == null) {
            return AgentResult.failure("未找到工作流定义: " + definitionName);
        }
        return execute(definition, context);
    }

    @Override
    public boolean pause(String instanceId) {
        return pause(instanceId, null, null);
    }

    @Override
    public boolean pause(String instanceId, String operator, String reason) {
        WorkflowState state = workflowStateStore.load(instanceId);
        if (state == null || !state.isRunning()) {
            return false;
        }
        state.setStatus(ExecutionStatus.PAUSED);
        state.setUpdateTime(System.currentTimeMillis());
        workflowStateStore.save(state);
        // 向执行线程发送暂停信号，在下一个节点边界生效（节点边界落定时记录暂停流水）
        workflowExecutor.requestPause(instanceId, operator, reason);
        log.info("工作流已暂停: instanceId={}, operator={}", instanceId, operator);
        return true;
    }

    @Override
    public boolean cancel(String instanceId) {
        WorkflowState state = workflowStateStore.load(instanceId);
        if (state == null) {
            return false;
        }
        if (state.isCompleted() || state.isCancelled()) {
            return false;
        }
        state.setCancelRequested(true);
        state.setStatus(ExecutionStatus.CANCELLED);
        // 取消时清空暂停元数据，避免遗留误导性暂停信息
        state.setPausedReason(null);
        state.setPausedBy(null);
        state.setPausedTime(null);
        state.setUpdateTime(System.currentTimeMillis());
        workflowStateStore.save(state);
        // 向执行线程发送终止信号，在下一个节点边界生效（保留已执行节点结果）
        workflowExecutor.requestCancel(instanceId);
        // 级联关闭挂起的审批待办，避免遗留脏待办
        closePendingRequest(state);
        log.info("工作流已取消: instanceId={}", instanceId);
        return true;
    }

    @Override
    public WorkflowExecuteResult debugNode(WorkflowDefinition definition, String nodeId,
                                           Map<String, Object> mockVariables, AgentContext context) {
        log.info("单节点调试执行: name={}, nodeId={}", definition != null ? definition.getName() : null, nodeId);
        return workflowExecutor.debugNode(definition, nodeId, mockVariables, context);
    }

    /**
     * 关闭实例关联的挂起审批待办（CAS防止并发误关已处理的待办）
     * @param state
     */
    private void closePendingRequest(WorkflowState state) {
        String pendingRequestId = state.getPendingRequestId();
        if (pendingRequestId == null || pendingRequestId.isBlank()) {
            return;
        }
        // 时间控制节点的合成ID不关联审批待办，跳过清理
        if (pendingRequestId.startsWith("time:")) {
            return;
        }
        PendingRequestStore store = pendingRequestStoreProvider.getIfAvailable();
        if (store == null) {
            log.warn("审批存储不可用，跳过挂起待办清理: instanceId={}, requestId={}",
                    state.getInstanceId(), pendingRequestId);
            return;
        }
        try {
            int updated = store.casUpdateStatus(pendingRequestId, ApprovalStatus.PENDING.name(),
                    ApprovalStatus.CANCELLED.name(), "system:cancel", "工作流已取消", null);
            if (updated > 0) {
                log.info("已关闭工作流挂起待办: instanceId={}, requestId={}", state.getInstanceId(), pendingRequestId);
            } else {
                log.info("挂起待办已被处理，跳过关闭: instanceId={}, requestId={}",
                        state.getInstanceId(), pendingRequestId);
            }
        } catch (Exception e) {
            log.error("关闭工作流挂起待办失败: instanceId={}, requestId={}",
                    state.getInstanceId(), pendingRequestId, e);
        }
    }

    // ==================== 审批恢复 ====================

    /**
     * 监听审批完成事件，恢复暂停的工作流
     * @param event
     */
    @EventListener
    public void onApprovalResolved(ApprovalResolvedEvent event) {
        WorkflowState state = workflowStateStore.findByPendingRequestId(event.getRequestId());
        if (state == null || !state.isPaused()) {
            return;
        }

        WorkflowDefinition loadedDefinition = deserializeDefinition(state.getDefinitionSnapshot());
        if (loadedDefinition == null) {
            loadedDefinition = loadDefinitionForState(state);
        }
        if (loadedDefinition == null) {
            log.error("恢复工作流失败，未找到定义: instanceId={}", state.getInstanceId());
            state.setStatus(ExecutionStatus.FAILED);
            state.setVariable("errorMessage", "恢复时未找到工作流定义");
            state.setUpdateTime(System.currentTimeMillis());
            workflowStateStore.save(state);
            return;
        }
        final WorkflowDefinition definition = loadedDefinition;

        AgentContext context = buildResumeContext(state);

        // 异步恢复执行，不阻塞事件发布线程
        CompletableFuture.runAsync(() -> {
            try {
                if (!concurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS)) {
                    log.error("恢复工作流时并发限制: instanceId={}", state.getInstanceId());
                    state.setStatus(ExecutionStatus.FAILED);
                    state.setVariable("errorMessage", "恢复时并发限制");
                    state.setUpdateTime(System.currentTimeMillis());
                    workflowStateStore.save(state);
                    return;
                }
                try {
                    AgentResult result = workflowExecutor.resumeFromApproval(definition, context, state, event);
                    log.info("工作流恢复完成: instanceId={}, success={}", state.getInstanceId(),
                            result != null && result.isSuccess());
                } finally {
                    concurrencyLimiter.release();
                }
            } catch (Exception e) {
                log.error("恢复工作流异常: instanceId={}", state.getInstanceId(), e);
            }
        }, workflowAsyncExecutor);
    }

    /**
     * 监听时间控制节点到达设定时间事件，恢复暂停的工作流
     * @param event
     */
    @EventListener
    public void onTimeArrived(WorkflowTimeArrivedEvent event) {
        WorkflowState state = workflowStateStore.load(event.getInstanceId());
        if (state == null || !state.isPaused()) {
            return;
        }
        // 仅恢复仍停在该时间控制节点的实例（等待期间被恢复/移动过的实例跳过）
        if (!event.getNodeId().equals(state.getPausedNodeId())) {
            log.info("时间到达但实例暂停节点已变更，跳过恢复: instanceId={}, eventNodeId={}, pausedNodeId={}",
                    state.getInstanceId(), event.getNodeId(), state.getPausedNodeId());
            return;
        }
        if (state.isCancelled() || state.isCancelRequested()) {
            log.info("时间到达但工作流已取消，跳过恢复: instanceId={}", state.getInstanceId());
            return;
        }

        WorkflowDefinition loadedDefinition = deserializeDefinition(state.getDefinitionSnapshot());
        if (loadedDefinition == null) {
            loadedDefinition = loadDefinitionForState(state);
        }
        if (loadedDefinition == null) {
            log.error("时间恢复失败，未找到定义: instanceId={}", state.getInstanceId());
            state.setStatus(ExecutionStatus.FAILED);
            state.setVariable("errorMessage", "时间恢复时未找到工作流定义");
            state.setUpdateTime(System.currentTimeMillis());
            workflowStateStore.save(state);
            return;
        }
        final WorkflowDefinition definition = loadedDefinition;

        AgentContext context = buildResumeContext(state);

        // 异步恢复执行，不阻塞定时器线程
        CompletableFuture.runAsync(() -> {
            try {
                if (!concurrencyLimiter.tryAcquire(30, TimeUnit.SECONDS)) {
                    log.error("时间恢复工作流时并发限制: instanceId={}", state.getInstanceId());
                    state.setStatus(ExecutionStatus.FAILED);
                    state.setVariable("errorMessage", "时间恢复时并发限制");
                    state.setUpdateTime(System.currentTimeMillis());
                    workflowStateStore.save(state);
                    return;
                }
                try {
                    AgentResult result = workflowExecutor.resumeFromTimeControl(definition, context, state);
                    log.info("时间控制恢复完成: instanceId={}, success={}", state.getInstanceId(),
                            result != null && result.isSuccess());
                } finally {
                    concurrencyLimiter.release();
                }
            } catch (Exception e) {
                log.error("时间恢复工作流异常: instanceId={}", state.getInstanceId(), e);
            }
        }, workflowAsyncExecutor);
    }

    // ==================== 内部方法 ====================

    /**
     * 执行前治理检查，未注入门禁时直接放行
     * @param definition
     * @param context
     * @return 治理结果，含放行后的实际执行定义（灰度钉版时按版本重载）与拒绝原因
     */
    private GovernanceOutcome applyGovernance(WorkflowDefinition definition, AgentContext context) {
        WorkflowGovernanceGate gate = governanceGateProvider.getIfAvailable();
        if (gate == null) {
            return new GovernanceOutcome(definition, null);
        }
        String scopeId = resolveScopeId(context);
        GovernanceDecision decision = gate.checkExecute(definition, scopeId);
        if (!decision.isAllow()) {
            log.warn("工作流执行被治理门禁拒绝: name={}, scopeId={}, reason={}",
                    definition.getName(), scopeId, decision.getRejectReason());
            return new GovernanceOutcome(definition, decision.getRejectReason());
        }
        if (decision.getPinnedVersion() != null) {
            WorkflowDefinition pinned = definitionRepository
                    .loadByNameAndVersion(definition.getName(), decision.getPinnedVersion());
            if (pinned != null) {
                log.info("灰度钉版执行: name={}, version={}", definition.getName(), decision.getPinnedVersion());
                return new GovernanceOutcome(pinned, null);
            }
            log.warn("钉版定义未找到，按原定义执行: name={}, version={}",
                    definition.getName(), decision.getPinnedVersion());
        }
        return new GovernanceOutcome(definition, null);
    }

    /**
     * 治理结果（放行后的实际执行定义与拒绝原因）
     */
    private record GovernanceOutcome(WorkflowDefinition definition, String rejectReason) {
    }

    /**
     * 解析作用域ID，优先取请求级scopeId，回退线程级ScopeContext
     * @param context
     * @return
     */
    private String resolveScopeId(AgentContext context) {
        if (context != null && context.getRequest() != null && context.getRequest().getScopeId() != null) {
            return context.getRequest().getScopeId();
        }
        return ScopeContext.getScopeId();
    }

    /**
     * 按状态记录的作用域回退加载定义，避免异步恢复线程丢失作用域上下文
     * @param state
     * @return
     */
    private WorkflowDefinition loadDefinitionForState(WorkflowState state) {
        Object scopeId = state.getVariable("__scopeId");
        boolean scoped = scopeId != null;
        if (scoped) {
            ScopeContext.setScopeId(scopeId.toString());
        }
        try {
            return definitionRepository.loadByName(state.getDefinitionName());
        } finally {
            if (scoped) {
                ScopeContext.clear();
            }
        }
    }

    private String serializeDefinition(WorkflowDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (Exception e) {
            log.warn("序列化工作流定义失败: {}", e.getMessage());
            return null;
        }
    }

    private WorkflowDefinition deserializeDefinition(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot, WorkflowDefinition.class);
        } catch (Exception e) {
            log.warn("反序列化工作流定义快照失败: {}", e.getMessage());
            return null;
        }
    }

    private AgentContext buildResumeContext(WorkflowState state) {
        AgentRequest request = new AgentRequest();
        request.setAgentCode("workflow");
        request.setInput(state.getDefinitionName());
        request.setSessionId(state.getInstanceId());
        Object userId = state.getVariable("userId");
        if (userId != null) {
            request.setUserId(userId.toString());
        }
        // 恢复执行时从状态变量还原作用域ID
        Object scopeId = state.getVariable("__scopeId");
        if (scopeId != null) {
            request.setScopeId(scopeId.toString());
        }
        return new AgentContext(request);
    }

    private List<String> findDownstreamNodesFromLastCompleted(WorkflowState state) {
        if (state.getNodeStates() == null || state.getNodeStates().isEmpty()) {
            return List.of();
        }

        List<String> resumeNodes = new ArrayList<>();
        for (Map.Entry<String, NodeExecutionStatus> entry : state.getNodeStates().entrySet()) {
            NodeExecutionStatus nodeStatus = entry.getValue();
            if (nodeStatus.isFailed()) {
                resumeNodes.add(entry.getKey());
            }
        }

        if (resumeNodes.isEmpty()) {
            String lastCompletedNodeId = null;
            long lastEndTime = 0;
            for (Map.Entry<String, NodeExecutionStatus> entry : state.getNodeStates().entrySet()) {
                NodeExecutionStatus nodeStatus = entry.getValue();
                if (nodeStatus.isCompleted() && nodeStatus.getEndTime() != null && nodeStatus.getEndTime() > lastEndTime) {
                    lastEndTime = nodeStatus.getEndTime();
                    lastCompletedNodeId = entry.getKey();
                }
            }

            if (lastCompletedNodeId != null) {
                resumeNodes.add(lastCompletedNodeId);
            }
        }

        return resumeNodes;
    }
}
