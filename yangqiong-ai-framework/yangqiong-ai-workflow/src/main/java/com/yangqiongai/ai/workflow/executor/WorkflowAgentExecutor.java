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
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.approval.ApprovalResolvedEvent;
import com.yangqiongai.ai.approval.ApprovalStatus;
import com.yangqiongai.ai.workflow.model.AgentNode;
import com.yangqiongai.ai.workflow.model.ConditionNode;
import com.yangqiongai.ai.workflow.model.EdgeType;
import com.yangqiongai.ai.workflow.model.ErrorStrategy;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.LoopNode;
import com.yangqiongai.ai.workflow.model.NodeApprovalConfig;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.ParallelNode;
import com.yangqiongai.ai.workflow.model.SubgraphNode;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowEdge;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowPauseHistory;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.model.WorkflowStreamEvent;
import com.yangqiongai.ai.workflow.model.WorkflowNodeTrace;
import com.yangqiongai.ai.workflow.repository.WorkflowDefinitionRepository;
import com.yangqiongai.ai.workflow.repository.WorkflowExecutionHistoryRepository;
import com.yangqiongai.ai.workflow.store.WorkflowPauseHistoryStore;
import com.yangqiongai.ai.workflow.repository.WorkflowNodeTraceRepository;
import com.yangqiongai.ai.workflow.spi.WorkflowApprovalInterceptor;
import com.yangqiongai.ai.workflow.spi.WorkflowExecutionEvent;
import com.yangqiongai.ai.workflow.spi.WorkflowExecutionListener;
import com.yangqiongai.ai.common.scope.ScopeContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 工作流执行器
 * @author yangqiong
 */
@Service
public class WorkflowAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAgentExecutor.class);

    private static final int DEFAULT_MAX_LOOP_ITERATIONS = 100;

    private final AgentEngine agentEngine;

    private final ConditionEvaluator conditionEvaluator;

    private final WorkflowExecutionHistoryRepository executionHistoryRepository;

    private final WorkflowNodeTraceRepository nodeTraceRepository;

    private final ExecutorService parallelExecutor;

    private final ObjectMapper objectMapper;

    private final ApprovalNodeHandler approvalHandler;

    private final NotifyNodeHandler notifyHandler;

    private final TimeControlNodeHandler timeControlHandler;

    private final WorkflowPauseHistoryStore pauseHistoryStore;

    private final WorkflowGraphSorter graphSorter;

    private final WorkflowStateService stateService;

    private final TransformNodeExecutor transformExecutor;

    private final ScriptHttpNodeExecutor scriptHttpExecutor;

    private final WorkflowDefinitionRepository definitionRepository;

    private final ObjectProvider<WorkflowExecutionListener> executionListenerProvider;

    private final ObjectProvider<WorkflowApprovalInterceptor> approvalInterceptorProvider;

    /**
     * 本JVM正在执行中的实例ID（心跳保活范围）
     */
    private final Set<String> activeInstanceIds = ConcurrentHashMap.newKeySet();

    /**
     * 外部暂停请求的实例ID集合（节点边界消费，兼容Redis状态下存储的跨线程可见性）
     */
    private final Set<String> pauseRequestedIds = ConcurrentHashMap.newKeySet();

    /**
     * 外部暂停请求的操作人信息（instanceId -> [operator, reason]，节点边界消费）
     */
    private final Map<String, String[]> pauseMetaMap = new ConcurrentHashMap<>();

    /**
     * 外部取消请求的实例ID集合（节点边界消费，兼容Redis状态下存储的跨线程可见性）
     */
    private final Set<String> cancelRequestedIds = ConcurrentHashMap.newKeySet();

    /**
     * 请求暂停运行中的工作流（在下一个节点边界生效）
     * @param instanceId
     */
    public void requestPause(String instanceId) {
        requestPause(instanceId, null, null);
    }

    /**
     * 请求暂停运行中的工作流（带操作人与原因，在下一个节点边界生效）
     * @param instanceId
     * @param operator
     * @param reason
     */
    public void requestPause(String instanceId, String operator, String reason) {
        if (instanceId != null) {
            pauseRequestedIds.add(instanceId);
            pauseMetaMap.put(instanceId, new String[]{operator, reason});
        }
    }

    /**
     * 请求终止运行中的工作流（在下一个节点边界生效）
     * @param instanceId
     */
    public void requestCancel(String instanceId) {
        if (instanceId != null) {
            cancelRequestedIds.add(instanceId);
        }
    }

    /**
     * 将外部暂停/终止请求落到执行状态上（节点边界调用），返回是否产生终止请求
     * @param state
     * @return
     */
    private boolean resolveExternalControlFlags(WorkflowState state) {
        String instanceId = state.getInstanceId();
        if (cancelRequestedIds.remove(instanceId)) {
            state.setCancelRequested(true);
            state.setStatus(ExecutionStatus.CANCELLED);
            return true;
        }
        if (pauseRequestedIds.remove(instanceId)) {
            state.setStatus(ExecutionStatus.PAUSED);
            String[] meta = pauseMetaMap.remove(instanceId);
            String operator = meta != null && meta[0] != null && !meta[0].isBlank() ? meta[0] : "manual";
            String reason = meta != null && meta[1] != null && !meta[1].isBlank() ? meta[1] : "手动暂停";
            state.setPausedNodeId(null);
            state.setPausedReason(reason);
            state.setPausedBy(operator);
            state.setPausedTime(System.currentTimeMillis());
            recordPauseHistory(state, WorkflowPauseHistory.ACTION_PAUSE, reason, operator, null, null);
        }
        return false;
    }

    /**
     * 实例节点执行顺序缓存（instanceId -> nodeId -> 拓扑序号，从1开始）
     */
    private final Map<String, Map<String, Integer>> executionOrderCache = new ConcurrentHashMap<>();

    public WorkflowAgentExecutor(AgentEngine agentEngine,
                                  ConditionEvaluator conditionEvaluator,
                                  WorkflowExecutionHistoryRepository executionHistoryRepository,
                                  WorkflowNodeTraceRepository nodeTraceRepository,
                                  ApprovalNodeHandler approvalHandler,
                                  NotifyNodeHandler notifyHandler,
                                  TimeControlNodeHandler timeControlHandler,
                                  WorkflowPauseHistoryStore pauseHistoryStore,
                                  WorkflowGraphSorter graphSorter,
                                  WorkflowStateService stateService,
                                  TransformNodeExecutor transformExecutor,
                                  ScriptHttpNodeExecutor scriptHttpExecutor,
                                  WorkflowDefinitionRepository definitionRepository,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<WorkflowExecutionListener> executionListenerProvider,
                                  ObjectProvider<WorkflowApprovalInterceptor> approvalInterceptorProvider,
                                  @Value("${workflow.executor.pool-size:4}") int poolSize,
                                  @Value("${workflow.executor.queue-capacity:200}") int queueCapacity) {
        this.agentEngine = agentEngine;
        this.conditionEvaluator = conditionEvaluator;
        this.executionHistoryRepository = executionHistoryRepository;
        this.nodeTraceRepository = nodeTraceRepository;
        this.approvalHandler = approvalHandler;
        this.notifyHandler = notifyHandler;
        this.timeControlHandler = timeControlHandler;
        this.pauseHistoryStore = pauseHistoryStore;
        this.graphSorter = graphSorter;
        this.stateService = stateService;
        this.transformExecutor = transformExecutor;
        this.scriptHttpExecutor = scriptHttpExecutor;
        this.definitionRepository = definitionRepository;
        this.objectMapper = objectMapper;
        this.executionListenerProvider = executionListenerProvider;
        this.approvalInterceptorProvider = approvalInterceptorProvider;
        this.parallelExecutor = new ThreadPoolExecutor(
                poolSize, poolSize * 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> { Thread t = new Thread(r, "workflow-parallel"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PreDestroy
    public void shutdown() {
        parallelExecutor.shutdown();
        try {
            if (!parallelExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                parallelExecutor.shutdownNow();
                log.warn("WorkflowAgentExecutor线程池强制关闭");
            }
        } catch (InterruptedException e) {
            parallelExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 定时刷新本机执行中实例的心跳，防止长节点执行期间被失联回收误判
     */
    @Scheduled(fixedDelayString = "${workflow.engine.heartbeat-interval-ms:30000}")
    public void refreshHeartbeats() {
        if (activeInstanceIds.isEmpty()) {
            return;
        }
        for (String instanceId : activeInstanceIds) {
            try {
                stateService.refreshHeartbeat(instanceId);
            } catch (Exception e) {
                log.warn("刷新工作流心跳失败: instanceId={}, cause={}", instanceId, e.getMessage());
            }
        }
    }

    /**
     * 同步执行工作流
     */
    public AgentResult execute(WorkflowDefinition definition, AgentContext context) {
        return execute(definition, context, null);
    }

    /**
     * 同步执行工作流（使用指定的instanceId），返回结构化结果
     */
    public WorkflowExecuteResult executeWithResult(WorkflowDefinition definition, AgentContext context) {
        return executeWithResult(definition, context, null);
    }

    /**
     * 同步执行工作流（使用指定的instanceId）
     */
    public AgentResult execute(WorkflowDefinition definition, AgentContext context, String instanceId) {
        WorkflowExecuteResult result = executeWithResult(definition, context, instanceId);
        // 兼容旧接口：转换为AgentResult
        AgentResult agentResult = AgentResult.success(result.getOutput());
        agentResult.finalPayload(result.getVariables() != null ? result.getVariables() : Map.of());
        agentResult.body(Map.of(
                "instanceId", result.getInstanceId() != null ? result.getInstanceId() : "",
                "variables", result.getVariables() != null ? result.getVariables() : Map.of(),
                "nodeSummaries", result.getNodeSummaries() != null ? result.getNodeSummaries() : List.of(),
                "totalDurationMs", result.getTotalDurationMs() != null ? result.getTotalDurationMs() : 0
        ));
        if (!result.isSuccess()) {
            agentResult = AgentResult.failure(result.getErrorMessage());
            agentResult.body(Map.of(
                    "instanceId", result.getInstanceId() != null ? result.getInstanceId() : "",
                    "variables", result.getVariables() != null ? result.getVariables() : Map.of()
            ));
        }
        return agentResult;
    }

    /**
     * 同步执行工作流（使用指定的instanceId），返回结构化结果
     */
    public WorkflowExecuteResult executeWithResult(WorkflowDefinition definition, AgentContext context, String instanceId) {
        WorkflowState state = stateService.initializeState(definition);
        if (instanceId != null) {
            state.setInstanceId(instanceId);
        }
        activeInstanceIds.add(state.getInstanceId());
        try {
            return doExecuteWithResult(definition, context, state);
        } finally {
            activeInstanceIds.remove(state.getInstanceId());
            clearExecutionOrderCache(state.getInstanceId());
        }
    }

    /**
     * 工作流执行主体（外层方法负责活跃实例注册）
     */
    private WorkflowExecuteResult doExecuteWithResult(WorkflowDefinition definition, AgentContext context, WorkflowState state) {
        // 从上下文注入初始变量
        stateService.injectInitialVariables(context, state);
        // 捕获作用域ID写入状态变量，供审计监听与恢复路径使用
        state.setVariable("__scopeId", resolveScopeId(context));
        state.setDefinitionSnapshot(stateService.serializeDefinitionSnapshot(definition));
        state.setStatus(ExecutionStatus.RUNNING);
        state.setCreateTime(System.currentTimeMillis());
        state.setUpdateTime(System.currentTimeMillis());
        stateService.persistState(definition, state);
        long workflowStartTime = System.currentTimeMillis();
        fireWorkflowEvent(buildWorkflowEvent(WorkflowExecutionEvent.WorkflowExecutionEventType.WORKFLOW_START,
                state, definition, null, null));

        try {
            List<String> executionOrder = topologicalSort(definition);
            executeNodeList(definition, context, state, executionOrder);
            // 审批暂停：不标记完成，返回暂停结果，等待审批完成事件恢复
            if (state.isPaused()) {
                state.setUpdateTime(System.currentTimeMillis());
                stateService.persistState(definition, state);
                try {
                    executionHistoryRepository.recordCompletion(state, null,
                            "工作流暂停等待恢复: " + state.getPendingRequestId());
                } catch (Exception ex) {
                    log.warn("记录执行历史失败: {}", ex.getMessage());
                }
                WorkflowExecuteResult pausedResult = stateService.buildWorkflowExecuteResult(state, definition, workflowStartTime);
                pausedResult.setPaused(true);
                pausedResult.setPendingRequestId(state.getPendingRequestId());
                pausedResult.setSuccess(false);
                pausedResult.setErrorMessage("工作流已暂停，等待恢复: " + state.getPendingRequestId());
                log.info("工作流暂停等待恢复: instanceId={}, requestId={}", state.getInstanceId(), state.getPendingRequestId());
                fireWorkflowEvent(buildWorkflowEvent(WorkflowExecutionEvent.WorkflowExecutionEventType.WORKFLOW_PAUSED,
                        state, definition, System.currentTimeMillis() - workflowStartTime,
                        "工作流暂停等待恢复: " + state.getPendingRequestId()));
                return pausedResult;
            }
            if (!state.isCancelled()) {
                state.setStatus(ExecutionStatus.COMPLETED);
            }
            state.setUpdateTime(System.currentTimeMillis());
            stateService.persistState(definition, state);

            try {
                executionHistoryRepository.recordCompletion(state,
                        state.isCompleted() ? stateService.buildOutput(state, definition) : null,
                        state.isFailed() ? "工作流执行失败" : null);
            } catch (Exception e) {
                log.warn("记录执行历史失败: {}", e.getMessage());
            }

            fireWorkflowEvent(buildWorkflowEvent(state.isFailed()
                            ? WorkflowExecutionEvent.WorkflowExecutionEventType.WORKFLOW_FAILED
                            : WorkflowExecutionEvent.WorkflowExecutionEventType.WORKFLOW_COMPLETE,
                    state, definition, System.currentTimeMillis() - workflowStartTime, null));
            return stateService.buildWorkflowExecuteResult(state, definition, workflowStartTime);
        } catch (Exception e) {
            log.error("工作流执行失败: definition={}", definition.getName(), e);
            state.setStatus(ExecutionStatus.FAILED);
            state.setUpdateTime(System.currentTimeMillis());
            stateService.persistState(definition, state);

            try {
                executionHistoryRepository.recordCompletion(state, null, e.getMessage());
            } catch (Exception ex) {
                log.warn("记录执行历史失败: {}", ex.getMessage());
            }

            WorkflowExecuteResult result = stateService.buildWorkflowExecuteResult(state, definition, workflowStartTime);
            result.setSuccess(false);
            result.setErrorMessage("工作流执行失败: " + e.getMessage());
            fireWorkflowEvent(buildWorkflowEvent(WorkflowExecutionEvent.WorkflowExecutionEventType.WORKFLOW_FAILED,
                    state, definition, System.currentTimeMillis() - workflowStartTime, e.getMessage()));
            return result;
        }
    }

    /**
     * 单节点调试执行（不产生实例、不落历史与轨迹）
     * @param definition 工作流定义
     * @param nodeId 调试目标节点ID
     * @param mockVariables Mock变量
     * @param context 代理上下文
     * @return
     */
    public WorkflowExecuteResult debugNode(WorkflowDefinition definition, String nodeId,
                                           Map<String, Object> mockVariables, AgentContext context) {
        long debugStartTime = System.currentTimeMillis();
        WorkflowNode node = definition != null ? definition.findNode(nodeId) : null;
        if (node == null) {
            WorkflowExecuteResult failResult = new WorkflowExecuteResult();
            failResult.setSuccess(false);
            failResult.setDefinitionName(definition != null ? definition.getName() : null);
            failResult.setErrorMessage("调试失败：工作流定义中不存在节点: " + nodeId);
            return failResult;
        }

        // 调试使用临时内存状态，debug前缀便于与真实实例区分，不注册活跃实例、不持久化
        WorkflowState state = stateService.initializeState(definition);
        state.setInstanceId("debug-" + UUID.randomUUID().toString().substring(0, 8));
        state.setStatus(ExecutionStatus.RUNNING);
        if (mockVariables != null) {
            mockVariables.forEach(state::setVariable);
        }

        // 缺失的${var}引用降级为空串并追加告警，不中断调试
        List<String> warnings = new ArrayList<>();
        if (node.getInputMappings() != null) {
            for (Map.Entry<String, String> entry : node.getInputMappings().entrySet()) {
                String varRef = entry.getValue();
                if (varRef != null && varRef.startsWith("${") && varRef.endsWith("}")
                        && state.getVariable(varRef.substring(2, varRef.length() - 1)) == null) {
                    state.setVariable(varRef.substring(2, varRef.length() - 1), "");
                    warnings.add("Mock变量缺失: " + varRef + "（已降级为空值）");
                }
            }
        }
        Map<String, Object> nodeInput = stateService.resolveNodeInput(injectUpstreamDefaultInput(definition, state, node), state);

        AgentResult result;
        try {
            result = switch (node.getType()) {
                case AGENT -> executeAgentNode(context, state, node);
                case CONDITION -> executeConditionNode(definition, context, state, node);
                case PARALLEL -> executeParallelNode(definition, context, state, node);
                case LOOP -> executeLoopNode(definition, context, state, node);
                case SUBGRAPH -> executeSubgraphNode(context, state, node);
                case TRANSFORM -> executeWithTimeout(state, node, () -> transformExecutor.executeTransformNode(state, node));
                case SCRIPT -> executeWithTimeout(state, node, () -> scriptHttpExecutor.executeScriptNode(state, node));
                case HTTP -> executeWithTimeout(state, node, () -> scriptHttpExecutor.executeHttpNode(state, node));
                case ASSIGN -> executeWithTimeout(state, node, () -> scriptHttpExecutor.executeAssignNode(state, node));
                case APPROVAL -> mockApprovalResult(state, node);
                case NOTIFY -> notifyHandler.executeNotifyNode(context, state, node);
                case TIME_CONTROL -> timeControlHandler.previewTimeControlNode(node);
                case START -> executeStartNode(state, node);
                case END -> AgentResult.success("");
                default -> AgentResult.failure("不支持的节点类型: " + node.getType());
            };
        } catch (Exception e) {
            log.error("节点调试执行异常: nodeId={}", nodeId, e);
            result = AgentResult.failure("节点执行异常: " + e.getMessage());
        }

        // 组装输出映射（与正式执行一致，仅写入内存状态）
        Map<String, Object> nodeOutput = stateService.resolveNodeOutput(node, result, state);

        WorkflowExecuteResult.NodeSummary summary = new WorkflowExecuteResult.NodeSummary();
        summary.setNodeId(node.getId());
        summary.setNodeName(node.getName());
        summary.setNodeType(node.getType() != null ? node.getType().name() : "UNKNOWN");
        summary.setStatus(result.isSuccess() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED);
        summary.setInput(nodeInput);
        summary.setOutput(nodeOutput);
        summary.setDurationMs(System.currentTimeMillis() - debugStartTime);
        summary.setErrorMessage(result.isSuccess() ? null : result.getErrorMessage());

        WorkflowExecuteResult debugResult = new WorkflowExecuteResult();
        debugResult.setInstanceId(state.getInstanceId());
        debugResult.setDefinitionName(definition.getName());
        debugResult.setStatus(result.isSuccess() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED);
        debugResult.setNodeSummaries(List.of(summary));
        debugResult.setTotalDurationMs(System.currentTimeMillis() - debugStartTime);
        debugResult.setVariables(stateService.orderVariablesForDisplay(state));
        if (result.isSuccess()) {
            debugResult.setSuccess(true);
            debugResult.setOutput(ContentBlockConverter.fromOutputText(
                    result.getOutputAsText() != null ? result.getOutputAsText() : ""));
            if (!warnings.isEmpty()) {
                debugResult.setErrorMessage(String.join("; ", warnings));
            }
        } else {
            debugResult.setSuccess(false);
            String message = result.getErrorMessage() != null ? result.getErrorMessage() : "节点调试执行失败";
            if (!warnings.isEmpty()) {
                message = message + "; " + String.join("; ", warnings);
            }
            debugResult.setErrorMessage(message);
        }
        return debugResult;
    }

    /**
     * 无输入映射时自动继承直接前驱节点的输出作为默认输入（写入变量input）
     * 单前驱直取其output；多前驱按边顺序换行拼接；前驱无输出时不注入；START节点与已配置inputMappings的节点跳过
     * @param definition
     * @param state
     * @param node
     * @return
     */
    private WorkflowNode injectUpstreamDefaultInput(WorkflowDefinition definition, WorkflowState state, WorkflowNode node) {
        if (node.getType() == NodeType.START) {
            return node;
        }
        if (node.getInputMappings() != null && !node.getInputMappings().isEmpty()) {
            return node;
        }
        List<WorkflowEdge> incoming = definition.findIncomingEdges(node.getId()).stream()
                .filter(e -> e.getType() == EdgeType.NORMAL)
                .toList();
        StringBuilder merged = new StringBuilder();
        boolean hasValue = false;
        for (WorkflowEdge edge : incoming) {
            Object upstreamOutput = state.getVariable(edge.getSourceId() + ".output");
            if (upstreamOutput == null || String.valueOf(upstreamOutput).isEmpty()) {
                continue;
            }
            if (hasValue) {
                merged.append("\n");
            }
            merged.append(upstreamOutput);
            hasValue = true;
        }
        if (hasValue) {
            state.setVariable("input", merged.toString());
        }
        return node;
    }

    /**
     * 审批节点调试Mock：不创建待办，直接输出审批通过变量
     * @param state
     * @param node
     * @return
     */
    private AgentResult mockApprovalResult(WorkflowState state, WorkflowNode node) {
        state.setVariable(node.getId() + ".approvalResult", "APPROVED");
        state.setVariable(node.getId() + ".approved", true);
        return AgentResult.success("Mock审批通过");
    }

    /**
     * 从指定节点列表恢复执行工作流
     */
    public AgentResult executeFromNodes(WorkflowDefinition definition, AgentContext context,
                                        WorkflowState state, List<String> startNodeIds) {
        activeInstanceIds.add(state.getInstanceId());
        try {
            return doExecuteFromNodes(definition, context, state, startNodeIds);
        } finally {
            activeInstanceIds.remove(state.getInstanceId());
            clearExecutionOrderCache(state.getInstanceId());
        }
    }

    /**
     * 指定节点恢复执行主体（外层方法负责活跃实例注册）
     */
    private AgentResult doExecuteFromNodes(WorkflowDefinition definition, AgentContext context,
                                           WorkflowState state, List<String> startNodeIds) {
        state.setStatus(ExecutionStatus.RUNNING);
        state.setUpdateTime(System.currentTimeMillis());
        stateService.persistState(definition, state);
        long workflowStartTime = System.currentTimeMillis();

        try {
            List<String> executionOrder = topologicalSort(definition);
            List<String> remainingNodes = new ArrayList<>();
            boolean found = startNodeIds.isEmpty();
            for (String nodeId : executionOrder) {
                if (!found) {
                    if (startNodeIds.contains(nodeId)) {
                        found = true;
                    } else {
                        continue;
                    }
                }
                remainingNodes.add(nodeId);
            }
            executeNodeList(definition, context, state, remainingNodes);
            // 审批暂停：保持PAUSED状态，等待审批完成事件再次恢复
            if (state.isPaused()) {
                state.setUpdateTime(System.currentTimeMillis());
                stateService.persistState(definition, state);
                try {
                    executionHistoryRepository.recordCompletion(state, null,
                            "工作流恢复后再次暂停等待恢复: " + state.getPendingRequestId());
                } catch (Exception ex) {
                    log.warn("记录执行历史失败: {}", ex.getMessage());
                }
                return AgentResult.paused(state.getPendingRequestId(), "工作流恢复后再次暂停等待恢复");
            }
            // 取消场景不覆盖状态（取消请求在executeNodeList内已置为CANCELLED）
            if (!state.isCancelled()) {
                state.setStatus(ExecutionStatus.COMPLETED);
            }
            state.setUpdateTime(System.currentTimeMillis());
            stateService.persistState(definition, state);

            try {
                executionHistoryRepository.recordCompletion(state,
                        state.isCompleted() ? stateService.buildOutput(state, definition) : null,
                        state.isCancelled() ? "工作流已取消" : null);
            } catch (Exception ex) {
                log.warn("记录执行历史失败: {}", ex.getMessage());
            }

            // 兼容旧接口：转换为AgentResult（取消/未完成状态返回失败，保持取消语义一致）
            WorkflowExecuteResult wfResult = stateService.buildWorkflowExecuteResult(state, definition, workflowStartTime);
            AgentResult result = wfResult.isSuccess()
                    ? AgentResult.success(wfResult.getOutput())
                    : AgentResult.failure(state.isCancelled() ? "工作流已取消" : "工作流恢复执行未完成");
            result.finalPayload(wfResult.getVariables() != null ? wfResult.getVariables() : Map.of());
            result.body(Map.of(
                    "instanceId", wfResult.getInstanceId() != null ? wfResult.getInstanceId() : "",
                    "variables", wfResult.getVariables() != null ? wfResult.getVariables() : Map.of()
            ));
            return result;
        } catch (Exception e) {
            log.error("工作流恢复执行失败: definition={}", definition.getName(), e);
            state.setStatus(ExecutionStatus.FAILED);
            state.setUpdateTime(System.currentTimeMillis());
            stateService.persistState(definition, state);
            try {
                executionHistoryRepository.recordCompletion(state, null,
                        "工作流恢复执行失败: " + e.getMessage());
            } catch (Exception ex) {
                log.warn("记录执行历史失败: {}", ex.getMessage());
            }
            return AgentResult.failure("工作流恢复执行失败: " + e.getMessage());
        }
    }

    /**
     * 审批完成后恢复工作流执行
     * @param definition
     * @param context
     * @param state
     * @param event
     * @return
     */
    public AgentResult resumeFromApproval(WorkflowDefinition definition, AgentContext context,
                                           WorkflowState state, ApprovalResolvedEvent event) {
        String pausedNodeId = state.getPausedNodeId();
        if (pausedNodeId == null) {
            log.warn("恢复工作流失败，未找到暂停节点: instanceId={}", state.getInstanceId());
            return AgentResult.failure("未找到暂停节点");
        }
        WorkflowNode node = definition.findNode(pausedNodeId);
        if (node == null) {
            log.warn("恢复工作流失败，未找到节点定义: nodeId={}", pausedNodeId);
            return AgentResult.failure("未找到节点定义: " + pausedNodeId);
        }

        // 企业审批拦截：返回true表示企业侧已接管本次恢复（如仍有下一级审批）
        WorkflowApprovalInterceptor interceptor = approvalInterceptorProvider.getIfAvailable();
        if (interceptor != null) {
            try {
                if (interceptor.beforeResume(definition, context, state, node, event)) {
                    log.info("企业审批拦截已接管本次恢复: instanceId={}, nodeId={}", state.getInstanceId(), pausedNodeId);
                    stateService.persistState(definition, state);
                    return AgentResult.paused(state.getPendingRequestId(), "企业审批拦截已接管本次恢复: " + pausedNodeId);
                }
            } catch (Exception e) {
                log.warn("企业审批拦截beforeResume异常，按社区逻辑继续: {}", e.getMessage());
            }
        }

        state.setStatus(ExecutionStatus.RUNNING);
        state.setPausedNodeId(null);
        state.setPendingRequestId(null);
        Long waitDurationMs = clearPauseMetadata(state);
        ApprovalStatus status = event.getStatus();
        recordPauseHistory(state, WorkflowPauseHistory.ACTION_RESUME, resolveApprovalResumeReason(status),
                resolveApprovalOperator(event), null, waitDurationMs);
        state.setUpdateTime(System.currentTimeMillis());
        log.info("恢复工作流审批: instanceId={}, nodeId={}, status={}", state.getInstanceId(), pausedNodeId, status);

        // 审批通过
        if (status == ApprovalStatus.APPROVED) {
            return resumeFromApproved(definition, context, state, node, pausedNodeId, event);
        }

        // 审批超时
        if (status == ApprovalStatus.TIMEOUT) {
            log.warn("审批超时: nodeId={}", pausedNodeId);
            state.setStatus(ExecutionStatus.FAILED);
            state.setVariable("errorMessage", "审批超时: " + pausedNodeId);
            state.setUpdateTime(System.currentTimeMillis());
            stateService.persistState(definition, state);
            try {
                executionHistoryRepository.recordCompletion(state, null, "审批超时: " + pausedNodeId);
            } catch (Exception ex) {
                log.warn("记录执行历史失败: {}", ex.getMessage());
            }
            return AgentResult.failure("审批超时: " + pausedNodeId);
        }

        // 审批拒绝：根据RejectBehavior处理
        return resumeFromRejected(definition, context, state, node, pausedNodeId, event);
    }

    /**
     * 审批通过后恢复执行
     */
    private AgentResult resumeFromApproved(WorkflowDefinition definition, AgentContext context,
                                            WorkflowState state, WorkflowNode node, String pausedNodeId,
                                            ApprovalResolvedEvent event) {
        // APPROVAL节点：标记完成，从下游节点继续
        if (node.getType() == NodeType.APPROVAL) {
            NodeExecutionStatus nodeStatus = state.getNodeState(pausedNodeId);
            if (nodeStatus != null) {
                nodeStatus.setStatus(ExecutionStatus.COMPLETED);
                nodeStatus.setEndTime(System.currentTimeMillis());
                AgentResult result = AgentResult.success("审批通过");
                if (event.getResponsePayload() != null) {
                    result.body(Map.of("responsePayload", event.getResponsePayload()));
                }
                nodeStatus.setOutput(result);
                state.setNodeState(pausedNodeId, nodeStatus);
            }
            stateService.persistState(definition, state);
            recordNodeTrace(definition, state, node, nodeStatus);
            return executeFromNodes(definition, context, state, graphSorter.findDownstreamNodes(definition, pausedNodeId));
        }

        // 前置审批节点：标记审批通过，重新执行节点本身
        state.setVariable("approvalPassed:" + pausedNodeId, true);
        NodeExecutionStatus existing = state.getNodeState(pausedNodeId);
        if (existing != null) {
            existing.setStatus(ExecutionStatus.RUNNING);
            existing.setEndTime(null);
            existing.setErrorMessage(null);
            state.setNodeState(pausedNodeId, existing);
        }
        stateService.persistState(definition, state);
        return executeFromNodes(definition, context, state, List.of(pausedNodeId));
    }

    /**
     * 审批拒绝后按RejectBehavior恢复执行
     */
    private AgentResult resumeFromRejected(WorkflowDefinition definition, AgentContext context,
                                            WorkflowState state, WorkflowNode node, String pausedNodeId,
                                            ApprovalResolvedEvent event) {
        NodeApprovalConfig config = node.getApprovalConfig();
        NodeApprovalConfig.RejectBehavior behavior = config != null && config.getRejectBehavior() != null
                ? config.getRejectBehavior() : NodeApprovalConfig.RejectBehavior.FAIL;

        // SKIP：标记节点跳过，从下游继续
        if (behavior == NodeApprovalConfig.RejectBehavior.SKIP) {
            log.info("审批拒绝，按SKIP策略跳过节点: nodeId={}", pausedNodeId);
            NodeExecutionStatus nodeStatus = state.getNodeState(pausedNodeId);
            if (nodeStatus != null) {
                nodeStatus.setStatus(ExecutionStatus.COMPLETED);
                nodeStatus.setEndTime(System.currentTimeMillis());
                nodeStatus.setOutput(AgentResult.success("节点已跳过: 审批被拒绝"));
                state.setNodeState(pausedNodeId, nodeStatus);
            }
            stateService.persistState(definition, state);
            recordNodeTrace(definition, state, node, nodeStatus);
            return executeFromNodes(definition, context, state, graphSorter.findDownstreamNodes(definition, pausedNodeId));
        }

        // RETRY：重新发起审批，再次暂停
        if (behavior == NodeApprovalConfig.RejectBehavior.RETRY) {
            log.info("审批拒绝，按RETRY策略重新发起: nodeId={}", pausedNodeId);
            String newRequestId = approvalHandler.createNodeApproval(context, state, node, config);
            if (newRequestId == null) {
                state.setStatus(ExecutionStatus.FAILED);
                state.setVariable("errorMessage", "ApprovalGate不可用，无法重试审批");
                stateService.persistState(definition, state);
                try {
                    executionHistoryRepository.recordCompletion(state, null, "ApprovalGate不可用，无法重试审批");
                } catch (Exception ex) {
                    log.warn("记录执行历史失败: {}", ex.getMessage());
                }
                return AgentResult.failure("ApprovalGate不可用，无法重试审批");
            }
            state.setVariable("approvalRequestId:" + pausedNodeId, newRequestId);
            state.setStatus(ExecutionStatus.PAUSED);
            state.setPausedNodeId(pausedNodeId);
            state.setPendingRequestId(newRequestId);
            state.setUpdateTime(System.currentTimeMillis());
            stateService.persistState(definition, state);
            try {
                executionHistoryRepository.recordCompletion(state, null,
                        "审批被拒绝，重新发起审批: " + newRequestId);
            } catch (Exception ex) {
                log.warn("记录执行历史失败: {}", ex.getMessage());
            }
            return AgentResult.paused(newRequestId, "审批被拒绝，重新发起审批: " + pausedNodeId);
        }

        // FAIL：工作流失败
        log.warn("审批拒绝，按FAIL策略终止工作流: nodeId={}", pausedNodeId);
        state.setStatus(ExecutionStatus.FAILED);
        String errorMsg = event.getRejectReason() != null ? event.getRejectReason() : "审批被拒绝";
        state.setVariable("errorMessage", "审批被拒绝: " + errorMsg);
        state.setUpdateTime(System.currentTimeMillis());
        stateService.persistState(definition, state);
        try {
            executionHistoryRepository.recordCompletion(state, null, "审批被拒绝: " + errorMsg);
        } catch (Exception ex) {
            log.warn("记录执行历史失败: {}", ex.getMessage());
        }
        return AgentResult.failure("审批被拒绝: " + errorMsg);
    }

    /**
     * 时间控制节点到达设定时间后恢复工作流执行
     * @param definition
     * @param context
     * @param state
     * @return
     */
    public AgentResult resumeFromTimeControl(WorkflowDefinition definition, AgentContext context,
                                             WorkflowState state) {
        return resumeFromTimeControl(definition, context, state, null, null);
    }

    /**
     * 时间控制节点恢复工作流执行（时间到点或手动提前恢复共用）
     * @param definition
     * @param context
     * @param state
     * @param operator 手动恢复时的操作人，时间到点恢复传null
     * @param reason 手动恢复时的原因，时间到点恢复传null
     * @return
     */
    public AgentResult resumeFromTimeControl(WorkflowDefinition definition, AgentContext context,
                                             WorkflowState state, String operator, String reason) {
        String pausedNodeId = state.getPausedNodeId();
        if (pausedNodeId == null) {
            log.warn("时间恢复失败，未找到暂停节点: instanceId={}", state.getInstanceId());
            return AgentResult.failure("未找到暂停节点");
        }
        WorkflowNode node = definition.findNode(pausedNodeId);
        if (node == null || node.getType() != NodeType.TIME_CONTROL) {
            log.warn("时间恢复失败，暂停节点不是时间控制节点: instanceId={}, nodeId={}",
                    state.getInstanceId(), pausedNodeId);
            return AgentResult.failure("暂停节点不是时间控制节点: " + pausedNodeId);
        }

        // 等待期间被终止的实例不恢复（终止信号在节点边界已生效，状态保持CANCELLED）
        if (state.isCancelled() || state.isCancelRequested()) {
            log.info("时间恢复时工作流已取消，跳过恢复: instanceId={}, nodeId={}",
                    state.getInstanceId(), pausedNodeId);
            return AgentResult.failure("工作流已取消");
        }

        state.setStatus(ExecutionStatus.RUNNING);
        Long waitDurationMs = clearPauseMetadata(state);
        // 先记录恢复流水再清空暂停节点，保留暂停节点上下文便于追溯
        String resumeReason = reason != null && !reason.isBlank() ? reason : "时间控制到达设定时间";
        String resumeOperator = operator != null && !operator.isBlank() ? operator : "system";
        recordPauseHistory(state, WorkflowPauseHistory.ACTION_RESUME, resumeReason, resumeOperator, null, waitDurationMs);
        state.setPausedNodeId(null);
        state.setPendingRequestId(null);
        state.setUpdateTime(System.currentTimeMillis());
        log.info("时间控制恢复工作流: instanceId={}, nodeId={}", state.getInstanceId(), pausedNodeId);

        NodeExecutionStatus nodeStatus = state.getNodeState(pausedNodeId);
        if (nodeStatus != null) {
            nodeStatus.setStatus(ExecutionStatus.COMPLETED);
            nodeStatus.setEndTime(System.currentTimeMillis());
            nodeStatus.setOutput(AgentResult.success("时间控制等待结束，继续执行"));
            state.setNodeState(pausedNodeId, nodeStatus);
        }
        stateService.persistState(definition, state);
        recordNodeTrace(definition, state, node, nodeStatus);
        return executeFromNodes(definition, context, state, graphSorter.findDownstreamNodes(definition, pausedNodeId));
    }

    /**
     * 流式执行工作流
     */
    public Flux<WorkflowStreamEvent> stream(WorkflowDefinition definition, AgentContext context) {
        WorkflowState state = stateService.initializeState(definition);
        return stream(definition, context, state);
    }

    /**
     * 流式执行工作流（使用外部传入的初始状态，支持条件变量注入）
     */
    public Flux<WorkflowStreamEvent> stream(WorkflowDefinition definition, AgentContext context,
                                            WorkflowState initialState) {
        // autoCancel=false：SSE订阅建立前发出的事件（WORKFLOW_STARTED、首节点事件）先缓冲，订阅后重放，避免竞态丢失
        Sinks.Many<WorkflowStreamEvent> sink = Sinks.many().multicast()
                .onBackpressureBuffer(256, false);
        WorkflowState state = initialState;
        state.setStatus(ExecutionStatus.RUNNING);
        state.setDefinitionSnapshot(stateService.serializeDefinitionSnapshot(definition));
        state.setCreateTime(System.currentTimeMillis());
        state.setUpdateTime(System.currentTimeMillis());
        // 流式路径同样注入初始变量与用户输入，保证${input}在流式执行时可解析
        stateService.injectInitialVariables(context, state);
        stateService.persistState(definition, state);

        // 推送起始事件携带实例ID，供宿主前端调用暂停/终止/恢复控制接口
        sink.tryEmitNext(WorkflowStreamEvent.workflowStarted(definition.getName(), state.getInstanceId()));

        CompletableFuture.runAsync(() -> {
            activeInstanceIds.add(state.getInstanceId());
            try {
                List<String> executionOrder = topologicalSort(definition);
                streamNodeList(definition, context, state, executionOrder, sink);
                // 审批暂停等场景保持PAUSED状态，等待审批完成事件恢复，不覆盖为COMPLETED
                if (state.isPaused() || state.isCancelled()) {
                    return;
                }
                state.setStatus(ExecutionStatus.COMPLETED);
                state.setUpdateTime(System.currentTimeMillis());
                stateService.persistState(definition, state);
                // 推送完整执行结果JSON，供前端在流式结束后展示节点轨迹与流程变量
                WorkflowExecuteResult executeResult = stateService.buildWorkflowExecuteResult(
                        state, definition, state.getCreateTime());
                sink.tryEmitNext(WorkflowStreamEvent.workflowComplete(definition.getName(), serializeResult(executeResult)));
            } catch (Exception e) {
                log.error("流式工作流执行失败: definition={}", definition.getName(), e);
                state.setStatus(ExecutionStatus.FAILED);
                state.setUpdateTime(System.currentTimeMillis());
                stateService.persistState(definition, state);
                sink.tryEmitNext(WorkflowStreamEvent.nodeError(null, null, e.getMessage()));
            } finally {
                activeInstanceIds.remove(state.getInstanceId());
                clearExecutionOrderCache(state.getInstanceId());
            }
            sink.tryEmitComplete();
        }, parallelExecutor);

        return sink.asFlux();
    }

    /**
     * 流式执行节点列表，并行节点的事件使用Flux.merge合并推送
     */
    private void streamNodeList(WorkflowDefinition definition, AgentContext context,
                                WorkflowState state, List<String> executionOrder,
                                Sinks.Many<WorkflowStreamEvent> sink) {
        int index = 0;
        while (index < executionOrder.size()) {
            String nodeId = executionOrder.get(index);

            // 节点边界响应外部暂停/终止请求，终止时保留已执行节点结果
            if (resolveExternalControlFlags(state)) {
                stateService.persistState(definition, state);
                sink.tryEmitNext(WorkflowStreamEvent.workflowComplete(definition.getName(),
                        serializeResult(stateService.buildWorkflowExecuteResult(state, definition, state.getCreateTime()))));
                return;
            }
            if (state.isPaused()) {
                stateService.persistState(definition, state);
                sink.tryEmitNext(WorkflowStreamEvent.workflowComplete(definition.getName(),
                        serializeResult(stateService.buildWorkflowExecuteResult(state, definition, state.getCreateTime()))));
                return;
            }

            WorkflowNode node = definition.findNode(nodeId);
            if (node == null) {
                index++;
                continue;
            }

            // 跳过已完成或已跳过的节点（恢复场景/条件分支未选中分支）
            NodeExecutionStatus existingStatus = state.getNodeState(nodeId);
            if (existingStatus != null && (existingStatus.isCompleted() || existingStatus.isSkipped())) {
                if (existingStatus.isSkipped()) {
                    sink.tryEmitNext(WorkflowStreamEvent.nodeSkip(nodeId, node.getName()));
                }
                index++;
                continue;
            }

            // 前驱均未实际执行（条件分支未选中路径的下游、孤立未连接节点）则级联跳过
            if (shouldSkipForPredecessors(definition, state, node)) {
                markNodeSkipped(definition, state, node);
                sink.tryEmitNext(WorkflowStreamEvent.nodeSkip(nodeId, node.getName()));
                index++;
                continue;
            }

            // 并行节点：收集所有并行分支，合并流式事件
            if (node.getType() == NodeType.PARALLEL) {
                List<WorkflowEdge> parallelEdges = definition.findOutgoingEdges(node.getId()).stream()
                        .filter(e -> e.getType() == EdgeType.PARALLEL || e.getType() == EdgeType.NORMAL)
                        .toList();

                List<WorkflowNode> branchNodes = parallelEdges.stream()
                        .map(e -> definition.findNode(e.getTargetId()))
                        .filter(n -> n != null)
                        .toList();

                // 推送并行网关开始事件
                sink.tryEmitNext(WorkflowStreamEvent.nodeStart(nodeId, node.getName()));

                // 并行执行所有分支，每个分支产生独立的流式事件
                List<Flux<WorkflowStreamEvent>> branchFluxes = new ArrayList<>();
                for (WorkflowNode branchNode : branchNodes) {
                    Flux<WorkflowStreamEvent> branchFlux = streamSingleNode(definition, context, state, branchNode);
                    branchFluxes.add(branchFlux);
                }

                // 使用Flux.merge合并并行节点的流式事件
                if (!branchFluxes.isEmpty()) {
                    Flux<WorkflowStreamEvent> mergedFlux = Flux.merge(branchFluxes);
                    mergedFlux.doOnNext(sink::tryEmitNext).blockLast(Duration.ofMinutes(5));
                }

                // 推送并行网关完成事件
                AgentResult parallelResult = AgentResult.success("并行执行完成");
                sink.tryEmitNext(WorkflowStreamEvent.nodeComplete(nodeId, node.getName(), parallelResult.getOutputAsText()));

                // 标记并行网关节点完成，防止主循环因状态缺失反复执行并行分支
                NodeExecutionStatus parallelStatus = new NodeExecutionStatus();
                parallelStatus.setNodeId(node.getId());
                parallelStatus.setNodeName(node.getName());
                parallelStatus.setStatus(ExecutionStatus.COMPLETED);
                parallelStatus.setStartTime(System.currentTimeMillis());
                parallelStatus.setEndTime(System.currentTimeMillis());
                state.setNodeState(node.getId(), parallelStatus);

                // 推进index：先越过并行网关节点自身，再跳过已处理的分支节点
                index++;
                Set<String> processedIds = branchNodes.stream().map(WorkflowNode::getId).collect(Collectors.toSet());
                while (index < executionOrder.size() && processedIds.contains(executionOrder.get(index))) {
                    index++;
                }
                continue;
            }

            // 非并行节点：顺序执行并推送流式事件
            sink.tryEmitNext(WorkflowStreamEvent.nodeStart(nodeId, node.getName()));
            AgentResult nodeResult = executeNode(definition, context, state, node);
            if (nodeResult != null && nodeResult.isPaused()) {
                // 审批暂停：推送暂停事件并结束流式执行，等待审批恢复
                sink.tryEmitNext(WorkflowStreamEvent.nodePaused(nodeId, node.getName(), nodeResult.getPausedRequestId()));
                state.setStatus(ExecutionStatus.PAUSED);
                stateService.persistState(definition, state);
                return;
            }
            if (nodeResult != null && nodeResult.isSuccess()) {
                sink.tryEmitNext(WorkflowStreamEvent.nodeComplete(nodeId, node.getName(), nodeResult.getOutputAsText()));
            } else if (nodeResult != null) {
                sink.tryEmitNext(WorkflowStreamEvent.nodeError(nodeId, node.getName(), nodeResult.getErrorMessage()));
                if (definition.getErrorStrategy() == ErrorStrategy.STOP) {
                    state.setStatus(ExecutionStatus.FAILED);
                    stateService.persistState(definition, state);
                    return;
                }
            }
            index++;
        }
    }

    /**
     * 流式执行单个节点，返回该节点的流式事件Flux
     */
    private Flux<WorkflowStreamEvent> streamSingleNode(WorkflowDefinition definition, AgentContext context,
                                                       WorkflowState state, WorkflowNode node) {
        return Flux.create(emitter -> {
            emitter.next(WorkflowStreamEvent.nodeStart(node.getId(), node.getName()));
            try {
                AgentResult nodeResult = executeNode(definition, context, state, node);
                if (nodeResult != null && nodeResult.isPaused()) {
                    emitter.next(WorkflowStreamEvent.nodePaused(node.getId(), node.getName(), nodeResult.getPausedRequestId()));
                } else if (nodeResult != null && nodeResult.isSuccess()) {
                    emitter.next(WorkflowStreamEvent.nodeComplete(node.getId(), node.getName(), nodeResult.getOutputAsText()));
                } else if (nodeResult != null) {
                    emitter.next(WorkflowStreamEvent.nodeError(node.getId(), node.getName(), nodeResult.getErrorMessage()));
                }
            } catch (Exception e) {
                emitter.next(WorkflowStreamEvent.nodeError(node.getId(), node.getName(), e.getMessage()));
            }
            emitter.complete();
        });
    }

    /**
     * 执行节点列表
     */
    private void executeNodeList(WorkflowDefinition definition, AgentContext context,
                                 WorkflowState state, List<String> executionOrder) {
        Set<String> skipNodeIds = new LinkedHashSet<>();

        for (String nodeId : executionOrder) {

            // 节点边界消费外部暂停/终止请求（兼容Redis状态下存储的跨线程可见性）
            resolveExternalControlFlags(state);

            if (state.isCancelRequested()) {
                state.setStatus(ExecutionStatus.CANCELLED);
                stateService.persistState(definition, state);
                return;
            }

        
            if (state.isPaused()) {
                stateService.persistState(definition, state);
                return;
            }

            // 跳过LOOP节点的循环体子节点（已在executeLoopNode中执行）
            if (skipNodeIds.contains(nodeId)) {
                continue;
            }

            WorkflowNode node = definition.findNode(nodeId);
            if (node == null) {
                continue;
            }
            // 跳过已完成或已跳过的节点（恢复场景/条件分支未选中分支）
            NodeExecutionStatus existingStatus = state.getNodeState(nodeId);
            if (existingStatus != null && (existingStatus.isCompleted() || existingStatus.isSkipped())) {
                continue;
            }

            // 前驱均未实际执行（条件分支未选中路径的下游、孤立未连接节点）则级联跳过
            if (shouldSkipForPredecessors(definition, state, node)) {
                markNodeSkipped(definition, state, node);
                continue;
            }

            // LOOP节点：收集其出边指向的非END节点ID，后续跳过（这些节点已在executeLoopNode中执行）
            if (node.getType() == NodeType.LOOP) {
                List<WorkflowEdge> loopEdges = definition.findOutgoingEdges(node.getId());
                for (WorkflowEdge edge : loopEdges) {
                    WorkflowNode targetNode = definition.findNode(edge.getTargetId());
                    if (targetNode != null && targetNode.getType() != NodeType.END) {
                        skipNodeIds.add(edge.getTargetId());
                    }
                }
            }

            AgentResult result = executeNode(definition, context, state, node);
            // 审批暂停：立即中断执行，等待审批完成事件恢复
            if (result != null && result.isPaused()) {
                return;
            }
            if (result != null && !result.isSuccess()) {
                boolean shouldRetry = handleNodeError(definition, state, nodeId, result.getErrorMessage());
                if (shouldRetry) {
                    // RETRY策略：while循环重试当前节点
                    while (shouldRetry) {
                        // 重置节点状态以便重新执行
                        NodeExecutionStatus nodeStatus = state.getNodeState(nodeId);
                        if (nodeStatus != null) {
                            nodeStatus.setStatus(ExecutionStatus.RUNNING);
                            nodeStatus.setErrorMessage(null);
                        }
                        result = executeNode(definition, context, state, node);
                        if (result == null || result.isSuccess()) {
                            break;
                        }
                        shouldRetry = handleNodeError(definition, state, nodeId, result.getErrorMessage());
                    }
                    // 重试耗尽后降级为STOP
                    if (result != null && !result.isSuccess()) {
                        throw new RuntimeException("节点执行失败(重试耗尽): " + nodeId + ", 原因: " + result.getErrorMessage());
                    }
                } else if (definition.getErrorStrategy() == ErrorStrategy.STOP) {
                    throw new RuntimeException("节点执行失败: " + nodeId + ", 原因: " + result.getErrorMessage());
                }
            }
        }
    }

    /**
     * 执行单个节点，根据类型分派
     */
    private AgentResult executeNode(WorkflowDefinition definition, AgentContext context,
                                    WorkflowState state, WorkflowNode node) {
 
        if (state.isCancelRequested()) {
            NodeExecutionStatus cancelStatus = new NodeExecutionStatus();
            cancelStatus.setNodeId(node.getId());
            cancelStatus.setNodeName(node.getName());
            cancelStatus.setStatus(ExecutionStatus.CANCELLED);
            cancelStatus.setStartTime(System.currentTimeMillis());
            cancelStatus.setEndTime(System.currentTimeMillis());
            cancelStatus.setErrorMessage("工作流已取消");
            state.setNodeState(node.getId(), cancelStatus);
            stateService.persistState(definition, state);
            recordNodeTrace(definition, state, node, cancelStatus);
            throw new RuntimeException("工作流已取消");
        }

        NodeExecutionStatus existingStatus = state.getNodeState(node.getId());
        int preservedRetryCount = 0;
        int preservedIterationCount = 0;
        if (existingStatus != null) {
            preservedRetryCount = existingStatus.getRetryCount() != null ? existingStatus.getRetryCount() : 0;
            preservedIterationCount = existingStatus.getIterationCount();
        }

        // 构建节点输入（根据inputMappings解析）
        Map<String, Object> nodeInput = stateService.resolveNodeInput(injectUpstreamDefaultInput(definition, state, node), state);

        // 记录节点输入变量，供流程变量按节点展示输入
        state.setVariable(node.getId() + ".input", nodeInput);

        NodeExecutionStatus nodeStatus = new NodeExecutionStatus();
        nodeStatus.setNodeId(node.getId());
        nodeStatus.setNodeName(node.getName());
        nodeStatus.setStatus(ExecutionStatus.RUNNING);
        nodeStatus.setStartTime(System.currentTimeMillis());
        nodeStatus.setRetryCount(preservedRetryCount);
        nodeStatus.setIterationCount(preservedIterationCount);
        nodeStatus.setInput(nodeInput);
        state.setNodeState(node.getId(), nodeStatus);
        stateService.persistState(definition, state);
        fireNodeEvent(WorkflowExecutionEvent.WorkflowExecutionEventType.NODE_START,
                state, definition, node, null, null);

        try {
            AgentResult result;
            switch (node.getType()) {
                case AGENT -> result = checkAndExecuteAgent(context, state, node);
                case CONDITION -> result = checkAndExecute(definition, context, state, node, () -> executeConditionNode(definition, context, state, node));
                case PARALLEL -> result = checkAndExecute(definition, context, state, node, () -> executeParallelNode(definition, context, state, node));
                case LOOP -> result = checkAndExecute(definition, context, state, node, () -> executeLoopNode(definition, context, state, node));
                case SUBGRAPH -> result = checkAndExecute(definition, context, state, node, () -> executeSubgraphNode(context, state, node));
                case TRANSFORM -> result = checkAndExecuteWithTimeout(definition, context, state, node, () -> transformExecutor.executeTransformNode(state, node));
                case SCRIPT -> result = checkAndExecuteWithTimeout(definition, context, state, node, () -> scriptHttpExecutor.executeScriptNode(state, node));
                case HTTP -> result = checkAndExecuteWithTimeout(definition, context, state, node, () -> scriptHttpExecutor.executeHttpNode(state, node));
                case ASSIGN -> result = checkAndExecuteWithTimeout(definition, context, state, node, () -> scriptHttpExecutor.executeAssignNode(state, node));
                case APPROVAL -> result = approvalHandler.executeApprovalNode(context, state, node);
                case NOTIFY -> result = checkAndExecuteWithTimeout(definition, context, state, node, () -> notifyHandler.executeNotifyNode(context, state, node));
                case TIME_CONTROL -> result = checkAndExecute(definition, context, state, node, () -> timeControlHandler.executeTimeControlNode(context, state, node));
                case START -> result = checkAndExecute(definition, context, state, node, () -> executeStartNode(state, node));
                case END -> result = checkAndExecute(definition, context, state, node, () -> AgentResult.success(""));
                default -> result = AgentResult.failure("不支持的节点类型: " + node.getType());
            }

            // 审批暂停：不标记完成，保留RUNNING状态，记录pendingRequestId到state
            if (result.isPaused()) {
                nodeStatus.setStatus(ExecutionStatus.RUNNING);
                nodeStatus.setOutput(result);
                state.setNodeState(node.getId(), nodeStatus);
                state.setStatus(ExecutionStatus.PAUSED);
                state.setPausedNodeId(node.getId());
                state.setPendingRequestId(result.getPausedRequestId());
                applyPauseMetadata(context, state, node);
                state.setUpdateTime(System.currentTimeMillis());
                stateService.persistState(definition, state);
                return result;
            }

            nodeStatus.setStatus(result.isSuccess() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED);
            nodeStatus.setOutput(result);
            nodeStatus.setEndTime(System.currentTimeMillis());
            if (!result.isSuccess()) {
                nodeStatus.setErrorMessage(result.getErrorMessage());
            }

            // 构建节点输出（根据outputMappings写入变量）
            Map<String, Object> nodeOutput = stateService.resolveNodeOutput(node, result, state);
            nodeStatus.setOutputData(nodeOutput);

            state.setNodeState(node.getId(), nodeStatus);
            stateService.persistState(definition, state);
            fireNodeEvent(result.isSuccess()
                            ? WorkflowExecutionEvent.WorkflowExecutionEventType.NODE_COMPLETE
                            : WorkflowExecutionEvent.WorkflowExecutionEventType.NODE_FAILED,
                    state, definition, node,
                    nodeStatus.getEndTime() - nodeStatus.getStartTime(),
                    result.getErrorMessage());
            recordNodeTrace(definition, state, node, nodeStatus);
            return result;
        } catch (Exception e) {
            nodeStatus.setStatus(ExecutionStatus.FAILED);
            nodeStatus.setErrorMessage(e.getMessage());
            nodeStatus.setEndTime(System.currentTimeMillis());
            state.setNodeState(node.getId(), nodeStatus);
            stateService.persistState(definition, state);
            fireNodeEvent(WorkflowExecutionEvent.WorkflowExecutionEventType.NODE_FAILED,
                    state, definition, node,
                    nodeStatus.getEndTime() - nodeStatus.getStartTime(), e.getMessage());
            recordNodeTrace(definition, state, node, nodeStatus);
            return AgentResult.failure(e.getMessage());
        }
    }

    /**
     * 填充暂停元数据（原因/操作人/操作时间）
     * @param context
     * @param state
     * @param node
     */
    private void applyPauseMetadata(AgentContext context, WorkflowState state, WorkflowNode node) {
        // 暂停原因：优先用户输入，未输入时按节点类型生成默认描述
        Object userReason = context != null ? context.getAttribute("pauseReason") : null;
        if (userReason != null && !userReason.toString().isBlank()) {
            state.setPausedReason(userReason.toString());
        } else if (node.getType() == NodeType.TIME_CONTROL) {
            state.setPausedReason("时间控制等待");
        } else {
            state.setPausedReason("审批等待");
        }
        // 操作人：优先执行请求的用户ID，兜底system
        String userId = context != null && context.getRequest() != null ? context.getRequest().getUserId() : null;
        state.setPausedBy(userId != null && !userId.isBlank() ? userId : "system");
        state.setPausedTime(System.currentTimeMillis());
        recordPauseHistory(state, WorkflowPauseHistory.ACTION_PAUSE, state.getPausedReason(), state.getPausedBy(),
                state.getPendingRequestId(), null);
    }

    /**
     * 记录手动恢复（清空暂停元数据并写RESUME流水，供引擎手动恢复入口调用）
     * @param state
     * @param operator
     * @param reason
     */
    public void recordManualResume(WorkflowState state, String operator, String reason) {
        Long waitDurationMs = clearPauseMetadata(state);
        recordPauseHistory(state, WorkflowPauseHistory.ACTION_RESUME,
                reason != null && !reason.isBlank() ? reason : "手动恢复",
                operator != null && !operator.isBlank() ? operator : "manual", null, waitDurationMs);
    }

    /**
     * 记录暂停恢复流水（失败不影响主流程）
     * @param state
     * @param action
     * @param reason
     * @param operator
     * @param pendingRequestId
     * @param waitDurationMs
     */
    private void recordPauseHistory(WorkflowState state, String action, String reason, String operator,
                                    String pendingRequestId, Long waitDurationMs) {
        try {
            WorkflowPauseHistory history = new WorkflowPauseHistory();
            history.setInstanceId(state.getInstanceId());
            history.setDefinitionName(state.getDefinitionName());
            Object scopeId = state.getVariable("__scopeId");
            history.setScopeId(scopeId != null ? scopeId.toString() : null);
            history.setPausedNodeId(state.getPausedNodeId());
            history.setAction(action);
            history.setReason(reason);
            history.setOperator(operator);
            history.setOperatorTime(System.currentTimeMillis());
            history.setPendingRequestId(pendingRequestId);
            history.setWaitDurationMs(waitDurationMs);
            pauseHistoryStore.record(history);
        } catch (Exception e) {
            log.warn("记录暂停恢复流水失败: instanceId={}, action={}", state.getInstanceId(), action, e);
        }
    }

    /**
     * 清空当前暂停元数据
     * @param state
     * @return 本次暂停等待时长（毫秒，无暂停时间时返回null）
     */
    private Long clearPauseMetadata(WorkflowState state) {
        state.setPausedReason(null);
        state.setPausedBy(null);
        Long pausedTime = state.getPausedTime();
        state.setPausedTime(null);
        return pausedTime != null ? System.currentTimeMillis() - pausedTime : null;
    }

    /**
     * 解析审批恢复原因
     * @param status
     * @return
     */
    private String resolveApprovalResumeReason(ApprovalStatus status) {
        if (status == ApprovalStatus.APPROVED) {
            return "审批通过恢复";
        }
        if (status == ApprovalStatus.TIMEOUT) {
            return "审批超时恢复";
        }
        return "审批拒绝恢复";
    }

    /**
     * 解析审批操作人（从审批响应载荷提取approvedBy，失败时兜底system）
     * @param event
     * @return
     */
    private String resolveApprovalOperator(ApprovalResolvedEvent event) {
        String payload = event.getResponsePayload();
        if (payload != null && !payload.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
                String approvedBy = node.path("approvedBy").asText(null);
                if (approvedBy != null && !approvedBy.isBlank()) {
                    return approvedBy;
                }
            } catch (Exception ignored) {
                // 非JSON载荷按兜底处理
            }
        }
        return "system";
    }

    /**
     * 通用节点执行模板：先审批检查，通过后执行节点逻辑
     * @param definition
     * @param context
     * @param state
     * @param node
     * @param executor
     * @return
     */
    private AgentResult checkAndExecute(WorkflowDefinition definition, AgentContext context,
                                         WorkflowState state, WorkflowNode node,
                                         java.util.function.Supplier<AgentResult> executor) {
        AgentResult rejectResult = approvalHandler.checkNodeApproval(context, state, node);
        if (rejectResult != null) {
            return rejectResult;
        }
        return executor.get();
    }

    /**
     * 带超时的节点执行模板：先审批检查，通过后限时执行节点逻辑
     * @param definition
     * @param context
     * @param state
     * @param node
     * @param executor
     * @return
     */
    private AgentResult checkAndExecuteWithTimeout(WorkflowDefinition definition, AgentContext context,
                                                   WorkflowState state, WorkflowNode node,
                                                   java.util.function.Supplier<AgentResult> executor) {
        AgentResult rejectResult = approvalHandler.checkNodeApproval(context, state, node);
        if (rejectResult != null) {
            return rejectResult;
        }
        return executeWithTimeout(state, node, executor);
    }

    /**
     * 限时执行节点逻辑，超时按节点失败处理
     * @param state
     * @param node
     * @param executor
     * @return
     */
    private AgentResult executeWithTimeout(WorkflowState state, WorkflowNode node,
                                           java.util.function.Supplier<AgentResult> executor) {
        int timeoutSeconds = resolveNodeTimeout(state, node);
        try {
            CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(executor, parallelExecutor);
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("节点执行超时: nodeId={}, timeout={}s", node.getId(), timeoutSeconds);
            return AgentResult.failure("节点执行超时(" + timeoutSeconds + "s): " + node.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.failure("节点执行被中断: " + node.getId());
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("节点执行异常: nodeId={}", node.getId(), cause);
            return AgentResult.failure("节点执行异常: " + cause.getMessage());
        }
    }

    /**
     * 解析节点生效超时时间，节点级配置优先于定义级
     * @param state
     * @param node
     * @return
     */
    private int resolveNodeTimeout(WorkflowState state, WorkflowNode node) {
        if (node.getTimeoutSeconds() != null && node.getTimeoutSeconds() > 0) {
            return node.getTimeoutSeconds();
        }
        return getNodeTimeout(state);
    }

    /**
     * 解析节点生效最大重试次数，节点级配置优先于定义级
     * @param definition
     * @param node
     * @return
     */
    private int resolveMaxRetries(WorkflowDefinition definition, WorkflowNode node) {
        if (node != null && node.getMaxRetries() != null && node.getMaxRetries() > 0) {
            return node.getMaxRetries();
        }
        return definition.getMaxRetries() > 0 ? definition.getMaxRetries() : 3;
    }

    /**
     * AGENT节点执行模板：先审批检查，通过后执行Agent
     * @param context
     * @param state
     * @param node
     * @return
     */
    private AgentResult checkAndExecuteAgent(AgentContext context, WorkflowState state, WorkflowNode node) {
        AgentResult rejectResult = approvalHandler.checkNodeApproval(context, state, node);
        if (rejectResult != null) {
            return rejectResult;
        }
        return executeAgentNode(context, state, node);
    }

    /**
     * 执行START节点，读取initialPrompt配置注入到工作流变量
     * @param state
     * @param node
     * @return
     */
    private AgentResult executeStartNode(WorkflowState state, WorkflowNode node) {
        String initialPrompt = node.getConfigString("initialPrompt");
        if (initialPrompt != null && !initialPrompt.isBlank()) {
            state.setVariable("prompt", initialPrompt);
            log.info("START节点初始化问题: {}", initialPrompt);
        }
        return AgentResult.success("");
    }

    /**
     * 执行Agent节点，委托AgentEngine（带超时控制）
     */
    private AgentResult executeAgentNode(AgentContext context, WorkflowState state, WorkflowNode node) {
        AgentNode agentNode = stateService.castNode(node, AgentNode.class);
        AgentRequest nodeRequest = buildNodeRequest(context, state, agentNode);

        int timeoutSeconds = resolveNodeTimeout(state, node);
        try {
            CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(
                    () -> agentEngine.run(nodeRequest), parallelExecutor);
            AgentResult result = future.get(timeoutSeconds, TimeUnit.SECONDS);

            if (result.isSuccess() && result.getOutputAsText() != null) {
                state.setVariable(node.getId() + ".output", result.getOutputAsText());
                if (result.getFinalPayload() != null) {
                    result.getFinalPayload().forEach((k, v) -> state.setVariable(node.getId() + "." + k, v));
                }
            }
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Agent节点执行超时: nodeId={}, timeout={}s", node.getId(), timeoutSeconds);
            return AgentResult.failure("节点执行超时(" + timeoutSeconds + "s): " + node.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentResult.failure("节点执行被中断: " + node.getId());
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Agent节点执行异常: nodeId={}", node.getId(), cause);
            return AgentResult.failure("节点执行异常: " + cause.getMessage());
        }
    }

    private int getNodeTimeout(WorkflowState state) {
        Object timeout = state.getVariable("_nodeTimeoutSeconds");
        if (timeout instanceof Number) {
            return ((Number) timeout).intValue();
        }
        return 120;
    }

    /**
     * 执行条件分支节点
     */
    private AgentResult executeConditionNode(WorkflowDefinition definition, AgentContext context,
                                             WorkflowState state, WorkflowNode node) {
        ConditionNode conditionNode = stateService.castNode(node, ConditionNode.class);
        String expression = conditionNode.getConditionExpression();
        Map<String, String> branches = conditionNode.getBranches();

        // 透传上游内容作为本节点输出，供下游节点自动继承到具体内容（路由信息仅写入selectedBranch/targetNodeId）
        if (node.getInputMappings() == null || node.getInputMappings().isEmpty()) {
            Object passthrough = state.getVariable("input");
            if (passthrough != null && !String.valueOf(passthrough).isEmpty()) {
                state.setVariable(node.getId() + ".output", passthrough);
            }
        }

        // 评估条件表达式，确定分支
        String selectedBranch = evaluateCondition(expression, state);
        String targetNodeId = null;
        String branchLabel = selectedBranch;

        if (branches != null && selectedBranch != null) {
            targetNodeId = branches.get(selectedBranch);
        }

        // 如果分支映射中没有匹配，尝试从条件边中查找
        if (targetNodeId == null) {
            String[] edgeResult = findTargetAndLabelFromConditionalEdges(definition, node.getId(), selectedBranch, state);
            if (edgeResult != null) {
                targetNodeId = edgeResult[0];
                branchLabel = edgeResult[1];
            }
        }

        if (targetNodeId != null) {
            state.setVariable(node.getId() + ".selectedBranch", branchLabel);
            state.setVariable(node.getId() + ".targetNodeId", targetNodeId);
            markNonSelectedBranchesSkipped(definition, node.getId(), targetNodeId, state);
            return AgentResult.success("条件分支选择: " + branchLabel + " -> " + resolveNodeName(definition, targetNodeId));
        }

        // 无匹配分支时走默认出边
        List<WorkflowEdge> normalEdges = definition.findOutgoingEdges(node.getId()).stream()
                .filter(e -> e.getType() == EdgeType.NORMAL)
                .toList();
        if (!normalEdges.isEmpty()) {
            String defaultTargetId = normalEdges.get(0).getTargetId();
            state.setVariable(node.getId() + ".selectedBranch", "default");
            state.setVariable(node.getId() + ".targetNodeId", defaultTargetId);
            markNonSelectedBranchesSkipped(definition, node.getId(), defaultTargetId, state);
            return AgentResult.success("条件分支走默认路径: " + resolveNodeName(definition, defaultTargetId));
        }

        return AgentResult.success("条件分支无匹配，跳过");
    }

    /**
     * 解析节点显示名称（未找到时回退节点ID）
     * @param definition
     * @param nodeId
     * @return
     */
    private String resolveNodeName(WorkflowDefinition definition, String nodeId) {
        WorkflowNode target = definition.findNode(nodeId);
        return target != null && target.getName() != null && !target.getName().isBlank()
                ? target.getName() : nodeId;
    }

    /**
     * 将非选中分支的节点标记为跳过
     */
    private void markNonSelectedBranchesSkipped(WorkflowDefinition definition, String conditionNodeId,
                                                  String selectedTargetId, WorkflowState state) {
        List<WorkflowEdge> allEdges = definition.findOutgoingEdges(conditionNodeId);
        for (WorkflowEdge edge : allEdges) {
            if (!edge.getTargetId().equals(selectedTargetId)) {
                WorkflowNode nonSelectedNode = definition.findNode(edge.getTargetId());
                if (nonSelectedNode != null) {
                    // 汇聚节点（还有条件节点以外的入边来源）不立即跳过，由前驱状态级联判断，
                    // 避免误跳过同时承接命中分支的主链终点
                    boolean exclusiveTarget = definition.findIncomingEdges(nonSelectedNode.getId()).stream()
                            .allMatch(inEdge -> inEdge.getSourceId().equals(conditionNodeId));
                    if (!exclusiveTarget) {
                        continue;
                    }
                    NodeExecutionStatus skipStatus = new NodeExecutionStatus();
                    skipStatus.setNodeId(nonSelectedNode.getId());
                    skipStatus.setNodeName(nonSelectedNode.getName());
                    skipStatus.setStatus(ExecutionStatus.SKIPPED);
                    skipStatus.setStartTime(System.currentTimeMillis());
                    skipStatus.setEndTime(System.currentTimeMillis());
                    state.setNodeState(nonSelectedNode.getId(), skipStatus);
                }
            }
        }
    }

    /**
     * 判断节点是否应因前驱被跳过而级联跳过（非START节点且所有前驱均为SKIPPED时跳过，
     * 孤立未连接节点同样跳过；前驱失败按已到达处理，SKIP错误策略下仍继续执行下游）
     * @param definition
     * @param state
     * @param node
     * @return
     */
    static boolean shouldSkipForPredecessors(WorkflowDefinition definition, WorkflowState state, WorkflowNode node) {
        if (node.getType() == NodeType.START) {
            return false;
        }
        List<WorkflowEdge> incomingEdges = definition.findIncomingEdges(node.getId());
        if (incomingEdges.isEmpty()) {
            return true;
        }
        for (WorkflowEdge edge : incomingEdges) {
            NodeExecutionStatus sourceStatus = state.getNodeState(edge.getSourceId());
            if (sourceStatus == null || !sourceStatus.isSkipped()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将节点标记为跳过并持久化状态、记录轨迹
     * @param definition
     * @param state
     * @param node
     */
    private void markNodeSkipped(WorkflowDefinition definition, WorkflowState state, WorkflowNode node) {
        NodeExecutionStatus skipStatus = new NodeExecutionStatus();
        skipStatus.setNodeId(node.getId());
        skipStatus.setNodeName(node.getName());
        skipStatus.setStatus(ExecutionStatus.SKIPPED);
        skipStatus.setStartTime(System.currentTimeMillis());
        skipStatus.setEndTime(System.currentTimeMillis());
        state.setNodeState(node.getId(), skipStatus);
        stateService.persistState(definition, state);
        recordNodeTrace(definition, state, node, skipStatus);
    }

    /**
     * 序列化执行结果为JSON（流式完成事件payload），失败时回退错误信息
     * @param result
     * @return
     */
    private String serializeResult(WorkflowExecuteResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("工作流执行结果序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行并行网关节点
     */
    private AgentResult executeParallelNode(WorkflowDefinition definition, AgentContext context,
                                            WorkflowState state, WorkflowNode node) {
        ParallelNode parallelNode = stateService.castNode(node, ParallelNode.class);
        String joinType = parallelNode.getJoinType();
        if (joinType == null) {
            joinType = "ALL";
        }

        // 查找并行出边对应的目标节点
        List<WorkflowEdge> parallelEdges = definition.findOutgoingEdges(node.getId()).stream()
                .filter(e -> e.getType() == EdgeType.PARALLEL || e.getType() == EdgeType.NORMAL)
                .toList();

        if (parallelEdges.isEmpty()) {
            return AgentResult.success("并行网关无分支");
        }

        List<WorkflowNode> branchNodes = parallelEdges.stream()
                .map(e -> definition.findNode(e.getTargetId()))
                .filter(n -> n != null)
                .toList();

        // 并行执行所有分支
        List<CompletableFuture<AgentResult>> futures = new ArrayList<>();
        for (WorkflowNode branchNode : branchNodes) {
            CompletableFuture<AgentResult> future = CompletableFuture.supplyAsync(
                    () -> executeNode(definition, context, state, branchNode),
                    parallelExecutor
            );
            futures.add(future);
        }

        try {
            AgentResult combinedResult;
            if ("ANY".equalsIgnoreCase(joinType)) {
                CompletableFuture<Object> anyOf = CompletableFuture.anyOf(
                        futures.toArray(new CompletableFuture[0]));
                AgentResult firstResult = (AgentResult) anyOf.join();
                combinedResult = firstResult;
               
                for (CompletableFuture<AgentResult> f : futures) {
                    if (!f.isDone()) {
                        f.cancel(true);
                    }
                }
            } else {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                StringBuilder combinedOutput = new StringBuilder();
                boolean allSuccess = true;
                for (CompletableFuture<AgentResult> f : futures) {
                    try {
                        AgentResult r = f.join();
                        if (!r.isSuccess()) {
                            allSuccess = false;
                        }
                        if (r.getOutputAsText() != null) {
                            if (combinedOutput.length() > 0) {
                                combinedOutput.append("\n");
                            }
                            combinedOutput.append(r.getOutputAsText());
                        }
                    } catch (Exception e) {
                        allSuccess = false;
                        if (combinedOutput.length() > 0) {
                            combinedOutput.append("\n");
                        }
                        combinedOutput.append("分支执行异常: ").append(e.getMessage());
                    }
                }
                combinedResult = allSuccess
                        ? AgentResult.success(combinedOutput.toString())
                        : AgentResult.failure("部分分支执行失败: " + combinedOutput);
            }
            return combinedResult;
        } catch (Exception e) {
            return AgentResult.failure("并行执行异常: " + e.getMessage());
        }
    }

    /**
     * 执行循环节点
     */
    private AgentResult executeLoopNode(WorkflowDefinition definition, AgentContext context,
                                        WorkflowState state, WorkflowNode node) {
        LoopNode loopNode = stateService.castNode(node, LoopNode.class);
        String exitCondition = loopNode.getExitCondition();
        int maxIterations = loopNode.getMaxIterations() != null
                ? loopNode.getMaxIterations() : DEFAULT_MAX_LOOP_ITERATIONS;

        List<WorkflowNode> subNodes = loopNode.getSubNodes();
        if (subNodes == null || subNodes.isEmpty()) {
            // 从出边获取循环体节点
            subNodes = definition.findOutgoingEdges(node.getId()).stream()
                    .map(e -> definition.findNode(e.getTargetId()))
                    .filter(n -> n != null && n.getType() != NodeType.END)
                    .toList();
        }

        NodeExecutionStatus nodeStatus = state.getNodeState(node.getId());
        int iteration = 0;
        StringBuilder loopOutput = new StringBuilder();

        // 判断是否为数组遍历模式
        String iterateOver = loopNode.getIterateOver();
        if (iterateOver != null && !iterateOver.isBlank()) {
            // 数组遍历模式
            Object iterableObj = state.getVariable(iterateOver);
            List<?> items = toIterableList(iterableObj);
            String itemVar = loopNode.getCurrentItemVar();
            String indexVar = loopNode.getCurrentIndexVar();

            for (int i = 0; i < items.size() && i < maxIterations; i++) {
                state.setVariable(itemVar, items.get(i));
                state.setVariable(indexVar, i);

                // 执行循环体
                for (WorkflowNode subNode : subNodes) {
                    AgentResult subResult = executeNode(definition, context, state, subNode);
                    if (subResult != null && !subResult.isSuccess()
                            && definition.getErrorStrategy() == ErrorStrategy.STOP) {
                        return subResult;
                    }
                    if (subResult != null && subResult.getOutputAsText() != null) {
                        if (loopOutput.length() > 0) {
                            loopOutput.append("\n");
                        }
                        loopOutput.append(subResult.getOutputAsText());
                    }
                }

                // 循环体内产生取消请求时及时中断循环，取消语义与节点级取消一致
                if (state.isCancelRequested()) {
                    throw new RuntimeException("工作流已取消");
                }

                iteration++;
                if (nodeStatus != null) {
                    nodeStatus.setIterationCount(iteration);
                }
                state.setVariable(node.getId() + ".iteration", iteration);
                stateService.persistState(definition, state);
            }
        } else {
            // 条件循环模式
            // 未配置退出条件时仅执行一次循环体，避免无上限循环调用（如循环体内含Agent节点时连续调用）
            boolean hasExitCondition = exitCondition != null && !exitCondition.isBlank();
            if (!hasExitCondition) {
                log.warn("循环节点[{}]未配置退出条件，仅执行一次循环体，如需循环请配置退出条件", node.getName());
                maxIterations = 1;
            }
            while (iteration < maxIterations) {
                // 检查退出条件
                if (hasExitCondition && evaluateExitCondition(exitCondition, state)) {
                    break;
                }

                // 执行循环体
                for (WorkflowNode subNode : subNodes) {
                    AgentResult subResult = executeNode(definition, context, state, subNode);
                    if (subResult != null && !subResult.isSuccess()
                            && definition.getErrorStrategy() == ErrorStrategy.STOP) {
                        return subResult;
                    }
                    if (subResult != null && subResult.getOutputAsText() != null) {
                        if (loopOutput.length() > 0) {
                            loopOutput.append("\n");
                        }
                        loopOutput.append(subResult.getOutputAsText());
                    }
                }

                // 循环体内产生取消请求时及时中断循环，取消语义与节点级取消一致
                if (state.isCancelRequested()) {
                    throw new RuntimeException("工作流已取消");
                }

                iteration++;
                if (nodeStatus != null) {
                    nodeStatus.setIterationCount(iteration);
                }
                state.setVariable(node.getId() + ".iteration", iteration);
                stateService.persistState(definition, state);
            }
        }

        return AgentResult.success(loopOutput.toString());
    }

    /**
     * 将对象转为可遍历的List
     * 支持：List、数组、逗号分隔字符串
     */
    @SuppressWarnings("unchecked")
    private List<?> toIterableList(Object obj) {
        if (obj == null) {
            return List.of();
        }
        if (obj instanceof List) {
            return (List<?>) obj;
        }
        if (obj.getClass().isArray()) {
            return java.util.Arrays.asList((Object[]) obj);
        }
        if (obj instanceof String str) {
            if (str.startsWith("[") && str.endsWith("]")) {
                try {
                    return objectMapper.readValue(str, List.class);
                } catch (Exception e) {
                    // JSON解析失败，按逗号分隔
                }
            }
            return List.of(str.split(","));
        }
        return List.of(obj);
    }

    /**
     * 执行子图节点
     */
    private AgentResult executeSubgraphNode(AgentContext context, WorkflowState state, WorkflowNode node) {
        SubgraphNode subgraphNode = stateService.castNode(node, SubgraphNode.class);
        String workflowRef = subgraphNode.getWorkflowRef();
        if (workflowRef == null || workflowRef.isEmpty()) {
            return AgentResult.failure("子图节点缺少workflowRef配置");
        }

        // 优先从状态中获取预置的子工作流定义，未预置时通过定义仓库按名称加载
        Object subDefObj = state.getVariable("__subgraph_definitions." + workflowRef);
        WorkflowDefinition subDefinition;
        if (subDefObj instanceof WorkflowDefinition definition) {
            subDefinition = definition;
        } else if (definitionRepository != null) {
            subDefinition = definitionRepository.loadByName(workflowRef);
            if (subDefinition == null) {
                return AgentResult.failure("未找到子工作流定义: " + workflowRef);
            }
        } else {
            return AgentResult.failure("未找到子工作流定义: " + workflowRef);
        }

        // 递归执行子工作流
        WorkflowAgentExecutor self = this;
        AgentResult subResult = self.execute(subDefinition, context);

        // 将子工作流输出写入当前工作流变量
        if (subResult.isSuccess() && subResult.getOutputAsText() != null) {
            state.setVariable(node.getId() + ".output", subResult.getOutputAsText());
        }
        return subResult;
    }

    /**
     * DAG拓扑排序（委托给WorkflowGraphSorter）
     * @param definition
     * @return
     */
    public List<String> topologicalSort(WorkflowDefinition definition) {
        return graphSorter.topologicalSort(definition);
    }

    /**
     * 评估条件表达式（委托给ConditionEvaluator）
     */
    private String evaluateCondition(String expression, WorkflowState state) {
        return conditionEvaluator.evaluate(expression, state);
    }

    /**
     * 评估退出条件（委托给ConditionEvaluator）
     */
    private boolean evaluateExitCondition(String exitCondition, WorkflowState state) {
        return conditionEvaluator.evaluateExitCondition(exitCondition, state);
    }

    /**
     * 从条件边中查找目标节点和匹配标签
     * 1. 先尝试通过标签匹配selectedBranch
     * 2. 标签未匹配时，独立评估每条边的条件表达式，选择第一个为真的分支
     * @return [targetNodeId, label]，未匹配返回null
     */
    private String[] findTargetAndLabelFromConditionalEdges(WorkflowDefinition definition, String sourceNodeId,
                                                             String selectedBranch, WorkflowState state) {
        List<WorkflowEdge> conditionalEdges = definition.findOutgoingEdges(sourceNodeId).stream()
                .filter(WorkflowEdge::isConditional)
                .toList();

        // 第一轮：通过标签匹配selectedBranch
        if (selectedBranch != null) {
            for (WorkflowEdge edge : conditionalEdges) {
                if (edge.getConditionLabel() != null && edge.getConditionLabel().equals(selectedBranch)) {
                    return new String[]{edge.getTargetId(), edge.getConditionLabel()};
                }
            }
        }

        // 第二轮：独立评估每条边的条件表达式，选择第一个条件为真的分支
        for (WorkflowEdge edge : conditionalEdges) {
            String condition = edge.getConditionExpression();
            if (condition != null) {
                String result = evaluateCondition(condition, state);
                if (result != null && !"false".equals(result)) {
                    String label = edge.getConditionLabel() != null ? edge.getConditionLabel() : condition;
                    return new String[]{edge.getTargetId(), label};
                }
            }
        }
        return null;
    }

    /**
     * 处理节点执行错误
     * @return true表示需要重试当前节点，false表示不需要重试
     */
    private boolean handleNodeError(WorkflowDefinition definition, WorkflowState state,
                                 String nodeId, String errorMessage) {
        ErrorStrategy strategy = definition.getErrorStrategy();
        if (strategy == null) {
            strategy = ErrorStrategy.STOP;
        }
        log.warn("节点执行失败: nodeId={}, error={}, strategy={}", nodeId, errorMessage, strategy);
        switch (strategy) {
            case SKIP -> {
                log.info("跳过失败节点: nodeId={}", nodeId);
                return false;
            }
            case RETRY -> {
                int maxRetries = resolveMaxRetries(definition, definition.findNode(nodeId));
                NodeExecutionStatus nodeStatus = state.getNodeState(nodeId);
                int retryCount = (nodeStatus != null && nodeStatus.getRetryCount() != null)
                        ? nodeStatus.getRetryCount() : 0;
                if (retryCount < maxRetries) {
                    int currentRetry = retryCount + 1;
                    if (nodeStatus != null) {
                        nodeStatus.setRetryCount(currentRetry);
                    }
                    log.info("重试失败节点: nodeId={}, retryCount={}/{}", nodeId, currentRetry, maxRetries);
                    long backoffMs = calculateBackoff(currentRetry);
                    log.info("节点重试退避等待: nodeId={}, retry={}, backoffMs={}", nodeId, currentRetry, backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    return true;
                } else {
                    log.warn("重试次数已达上限, 降级为STOP策略: nodeId={}, maxRetries={}", nodeId, maxRetries);
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * 计算重试退避间隔（指数退避 + 抖动）
     */
    private long calculateBackoff(int retryCount) {
        long base = 1000L; // 1秒
        double multiplier = 2.0;
        long maxBackoff = 30000L; // 30秒
        long interval = (long) (base * Math.pow(multiplier, retryCount));
        interval = Math.min(interval, maxBackoff);
        // 添加抖动 +/-50%
        long jitter = (long) (interval * 0.5 * (Math.random() * 2 - 1));
        return Math.max(100, interval + jitter);
    }

    /**
     * 构建节点执行请求
     */
    private AgentRequest buildNodeRequest(AgentContext parentContext, WorkflowState state, AgentNode agentNode) {
        AgentRequest parentRequest = parentContext.getRequest();
        AgentRequest nodeRequest = new AgentRequest();
        nodeRequest.setSessionId(parentRequest.getSessionId());
        nodeRequest.setUserId(parentRequest.getUserId());
        if (parentRequest.getBody() != null) {
            nodeRequest.setBody(new HashMap<>(parentRequest.getBody()));
        }
        // 节点配置的agentCode（workflow JSON契约键）优先，未配置则继承父请求
        String agentCode = agentNode.getAgentCode();
        nodeRequest.setAgentCode(agentCode != null ? agentCode : parentRequest.getAgentCode());
        // 节点配置的sysPrompt优先（支持${var}模板替换），未配置则依次回退：工作流变量prompt → 上游默认输入input → 父请求input
        String sysPrompt = agentNode.getSysPrompt();
        if (sysPrompt != null && !sysPrompt.isBlank()) {
            nodeRequest.setInput(stateService.resolveTemplateString(sysPrompt, state));
        } else if (state.getVariables() != null && state.getVariable("prompt") instanceof String promptVar) {
            nodeRequest.setInput(promptVar);
        } else if (state.getVariables() != null && state.getVariable("input") instanceof String upstreamInput) {
            nodeRequest.setInput(upstreamInput);
        } else {
            nodeRequest.setInput(parentRequest.getInput());
        }
        // 注入工作流变量到body，供Agent执行时访问
        if (state.getVariables() != null) {
            nodeRequest.addBody(WorkflowBodyKeys.WORKFLOW_VARIABLES, new HashMap<>(state.getVariables()));
        }
        return nodeRequest;
    }

    /**
     * 解析作用域ID，优先取上下文属性（异步提交段捕获），回退请求级scopeId，再回退线程级ScopeContext
     * @param context
     * @return
     */
    private String resolveScopeId(AgentContext context) {
        Object attr = context != null ? context.getAttribute("__scopeId") : null;
        if (attr != null) {
            return attr.toString();
        }
        if (context != null && context.getRequest() != null && context.getRequest().getScopeId() != null) {
            return context.getRequest().getScopeId();
        }
        return ScopeContext.getScopeId();
    }

    /**
     * 构建工作流级审计事件
     * @param eventType
     * @param state
     * @param definition
     * @param durationMs
     * @param errorMessage
     * @return
     */
    private WorkflowExecutionEvent buildWorkflowEvent(WorkflowExecutionEvent.WorkflowExecutionEventType eventType,
                                                      WorkflowState state, WorkflowDefinition definition,
                                                      Long durationMs, String errorMessage) {
        Object scopeId = state.getVariable("__scopeId");
        return WorkflowExecutionEvent.builder()
                .eventType(eventType)
                .instanceId(state.getInstanceId())
                .definitionName(definition.getName())
                .definitionVersion(definition.getVersion())
                .scopeId(scopeId != null ? scopeId.toString() : null)
                .status(state.getStatus())
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 构建节点级审计事件
     * @param eventType
     * @param state
     * @param definition
     * @param node
     * @param durationMs
     * @param errorMessage
     * @return
     */
    private WorkflowExecutionEvent buildNodeEvent(WorkflowExecutionEvent.WorkflowExecutionEventType eventType,
                                                  WorkflowState state, WorkflowDefinition definition,
                                                  WorkflowNode node, Long durationMs, String errorMessage) {
        Object scopeId = state.getVariable("__scopeId");
        return WorkflowExecutionEvent.builder()
                .eventType(eventType)
                .instanceId(state.getInstanceId())
                .definitionName(definition.getName())
                .definitionVersion(definition.getVersion())
                .scopeId(scopeId != null ? scopeId.toString() : null)
                .status(state.getStatus())
                .nodeId(node.getId())
                .nodeName(node.getName())
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 发布工作流级审计事件，监听异常不影响执行主流程
     * @param event
     */
    private void fireWorkflowEvent(WorkflowExecutionEvent event) {
        WorkflowExecutionListener listener = executionListenerProvider.getIfAvailable();
        if (listener == null) {
            return;
        }
        try {
            switch (event.getEventType()) {
                case WORKFLOW_START -> listener.onWorkflowStart(event);
                case WORKFLOW_COMPLETE -> listener.onWorkflowComplete(event);
                case WORKFLOW_FAILED -> listener.onWorkflowFailed(event);
                case WORKFLOW_PAUSED -> listener.onWorkflowPaused(event);
                default -> { }
            }
        } catch (Exception e) {
            log.warn("工作流审计监听异常: type={}, instanceId={}, error={}",
                    event.getEventType(), event.getInstanceId(), e.getMessage());
        }
    }

    /**
     * 发布节点级审计事件，监听异常不影响执行主流程
     * @param eventType
     * @param state
     * @param definition
     * @param node
     * @param durationMs
     * @param errorMessage
     */
    private void fireNodeEvent(WorkflowExecutionEvent.WorkflowExecutionEventType eventType,
                               WorkflowState state, WorkflowDefinition definition,
                               WorkflowNode node, Long durationMs, String errorMessage) {
        WorkflowExecutionListener listener = executionListenerProvider.getIfAvailable();
        if (listener == null) {
            return;
        }
        WorkflowExecutionEvent event = buildNodeEvent(eventType, state, definition, node, durationMs, errorMessage);
        try {
            switch (eventType) {
                case NODE_START -> listener.onNodeStart(event);
                case NODE_COMPLETE -> listener.onNodeComplete(event);
                case NODE_FAILED -> listener.onNodeFailed(event);
                default -> { }
            }
        } catch (Exception e) {
            log.warn("节点审计监听异常: type={}, nodeId={}, error={}", eventType, node.getId(), e.getMessage());
        }
    }

    /**
     * 记录节点执行轨迹（同实例同节点覆盖写），写入失败仅告警不影响主流程
     * @param definition
     * @param state
     * @param node
     * @param nodeStatus
     */
    private void recordNodeTrace(WorkflowDefinition definition, WorkflowState state,
                                 WorkflowNode node, NodeExecutionStatus nodeStatus) {
        try {
            WorkflowNodeTrace trace = new WorkflowNodeTrace();
            trace.setInstanceId(state.getInstanceId());
            trace.setNodeId(node.getId());
            trace.setNodeName(node.getName());
            trace.setNodeType(node.getType() != null ? node.getType().name() : null);
            trace.setExecutionOrder(resolveExecutionOrder(state, definition, node.getId()));
            trace.setStatus(nodeStatus.getStatus() != null ? nodeStatus.getStatus().name() : null);
            trace.setInputData(nodeStatus.getInput());
            trace.setOutputData(nodeStatus.getOutputData());
            trace.setErrorMessage(nodeStatus.getErrorMessage());
            trace.setRetryCount(nodeStatus.getRetryCount() != null ? nodeStatus.getRetryCount() : 0);
            trace.setIterationCount(nodeStatus.getIterationCount());
            if (node.getType() == NodeType.CONDITION) {
                // 提取条件分支命中值
                Object branch = state.getVariable(node.getId() + ".selectedBranch");
                trace.setBranchTaken(branch != null ? branch.toString() : null);
            }
            trace.setStartTime(nodeStatus.getStartTime());
            trace.setEndTime(nodeStatus.getEndTime());
            trace.setDurationMs(nodeStatus.getDuration());
            Object scopeId = state.getVariable("__scopeId");
            trace.setScopeId(scopeId != null ? scopeId.toString() : null);
            nodeTraceRepository.record(trace);
        } catch (Exception e) {
            log.warn("记录节点轨迹失败: instanceId={}, nodeId={}, cause={}",
                    state.getInstanceId(), node.getId(), e.getMessage());
        }
    }

    /**
     * 解析节点执行顺序（拓扑序号，从1开始），结果按实例缓存
     * @param state
     * @param definition
     * @param nodeId
     * @return
     */
    private int resolveExecutionOrder(WorkflowState state, WorkflowDefinition definition, String nodeId) {
        try {
            Map<String, Integer> orderIndex = executionOrderCache.computeIfAbsent(state.getInstanceId(), k -> {
                Map<String, Integer> map = new HashMap<>();
                List<String> order = topologicalSort(definition);
                for (int i = 0; i < order.size(); i++) {
                    map.put(order.get(i), i + 1);
                }
                return map;
            });
            return orderIndex.getOrDefault(nodeId, 0);
        } catch (Exception e) {
            log.warn("解析节点执行顺序失败: nodeId={}, cause={}", nodeId, e.getMessage());
            return 0;
        }
    }

    /**
     * 清理实例的节点执行顺序缓存
     * @param instanceId
     */
    private void clearExecutionOrderCache(String instanceId) {
        executionOrderCache.remove(instanceId);
    }

}

