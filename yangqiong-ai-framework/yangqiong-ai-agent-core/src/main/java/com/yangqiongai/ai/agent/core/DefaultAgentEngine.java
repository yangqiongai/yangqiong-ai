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
package com.yangqiongai.ai.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.core.event.TaskCompletedEvent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.processor.AgentProcessor;
import com.yangqiongai.ai.agent.core.task.AgentTaskRecord;
import com.yangqiongai.ai.agent.core.task.AgentTaskTracker;
import com.yangqiongai.ai.agent.core.task.StepRecordingManager;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.common.util.StringUtils;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.executor.ConfirmPendingRegistry;
import com.yangqiongai.ai.agent.core.executor.ConfirmResumeHandle;
import com.yangqiongai.ai.agent.core.executor.ClarificationPendingRegistry;
import com.yangqiongai.ai.agent.core.executor.ClarificationResumeHandle;
import com.yangqiongai.ai.agent.core.executor.PendingResumeEntry;
import com.yangqiongai.ai.agent.core.executor.ReActAgentExecutor;
import com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer;
import com.yangqiongai.ai.agent.runtime.event.ConfirmResult;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent默认引擎
 * @author yangqiong
 */
@Service
public class DefaultAgentEngine implements AgentEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentEngine.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private AgentManager agentService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final Map<String, AgentProcessor> processorMap = new ConcurrentHashMap<>();

    private final AgentTaskTracker taskTracker;

    private final StepRecordingManager stepRecordingManager;

    private final ClarificationPendingRegistry clarificationPendingRegistry;

    private final ConfirmPendingRegistry confirmPendingRegistry;

    private final ReActAgentExecutor reActAgentExecutor;

    public DefaultAgentEngine(List<AgentProcessor> processors, AgentTaskTracker taskTracker,
                              StepRecordingManager stepRecordingManager,
                              ClarificationPendingRegistry clarificationPendingRegistry,
                              ConfirmPendingRegistry confirmPendingRegistry,
                              ReActAgentExecutor reActAgentExecutor) {
        processors.forEach(h -> processorMap.put(h.getAgentCode(), h));
        this.taskTracker = taskTracker;
        this.stepRecordingManager = stepRecordingManager;
        this.clarificationPendingRegistry = clarificationPendingRegistry;
        this.confirmPendingRegistry = confirmPendingRegistry;
        this.reActAgentExecutor = reActAgentExecutor;
        log.info("注册任务处理器: {} (状态校验通过AgentManager查询数据库)", processorMap.keySet());
    }

    /**
     * 同步运行
     * @param request
     * @return
     */
    @Override
    public AgentResult run(AgentRequest request) {
        syncScopeContext(request);
        log.info("agent run, scopeId={}, sessionId={}, userId={}",
                SessionContext.getScopeId(), request.getSessionId(), request.getUserId());
        AgentProcessor processor = resolveProcessor(request.getAgentCode());
        // 标记执行模式（供 StepRecordingManager 判断来源）
        request.addBody("_internalExecutionMode", "sync");
        AgentContext context = processor.createAgentContext(request);
        long startTime = System.currentTimeMillis();
        try {
            AgentResult result = processor.process(context);
            // 知识命中证据随结果finalPayload透出（供前端展示引用来源）
            attachKnowledgeEvidences(request, result);
            // process 执行期间 createRecorder 才会写入 _recordingTaskId，所以在此处获取
            String recordingTaskId = StepRecordingManager.getRecordingTaskId(request);
            finishRecordingIfNeeded(recordingTaskId, "SUCCEEDED",
                    result != null ? result.getOutputAsText() : "",
                    System.currentTimeMillis() - startTime, result);
            publishTaskCompletedEvent(request, result, true);
            return result;
        } catch (Exception e) {
            String recordingTaskId = StepRecordingManager.getRecordingTaskId(request);
            // 异常无消息时沿因果链取根因，保证失败任务错误信息可追踪
            finishRecordingIfNeeded(recordingTaskId, "FAILED", AgentTaskTracker.resolveErrorMessage(e),
                    System.currentTimeMillis() - startTime, null);
            publishTaskCompletedEvent(request, null, false);
            throw e;
        }
    }

    /**
     * 将请求body中的知识命中证据挂载到结果finalPayload
     * @param request
     * @param result
     */
    private void attachKnowledgeEvidences(AgentRequest request, AgentResult result) {
        if (result == null || request == null) {
            return;
        }
        Object evidences = request.getBody().get(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES);
        if (evidences instanceof List<?> list && !list.isEmpty()) {
            result.setFinalPayload(Map.of(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES, list));
        }
    }

    /**
     * 流式执行，返回结构化事件流
     * @param request
     * @return
     */
    @Override
    public Flux<StreamEvent> stream(AgentRequest request) {
        syncScopeContext(request);
        log.info("agent stream, scopeId={}, sessionId={}, userId={}",
                SessionContext.getScopeId(), request.getSessionId(), request.getUserId());
        AgentProcessor processor = resolveProcessor(request.getAgentCode());
        // 标记执行模式（供 StepRecordingManager 判断来源）
        request.addBody("_internalExecutionMode", "stream");
        AgentContext context = processor.createAgentContext(request);
        long startTime = System.currentTimeMillis();
        // doFinally仅有信号类型拿不到异常，用doOnError先捕获供失败收尾还原原因
        AtomicReference<Throwable> streamError = new AtomicReference<>();
        return processor.stream(context)
                .doOnError(streamError::set)
                .doFinally(signal -> {
                    // stream 执行期间 createRecorder 才会写入 _recordingTaskId，所以在此处获取
                    String recordingTaskId = StepRecordingManager.getRecordingTaskId(request);
                    String status = signal == SignalType.ON_COMPLETE ? "SUCCEEDED" :
                            signal == SignalType.ON_ERROR ? "FAILED" : "CANCELLED";
                    String outputText = signal == SignalType.ON_ERROR && streamError.get() != null
                            ? AgentTaskTracker.resolveErrorMessage(streamError.get()) : "";
                    finishRecordingIfNeeded(recordingTaskId, status, outputText,
                            System.currentTimeMillis() - startTime, null);
                });
    }

    /**
     * 恢复澄清续跑（用户提交澄清答案后调用，返回续跑事件流）
     * <p>
     * 弹出暂停现场登记续跑：同节点登记携带内存句柄直接复用暂停时的执行器、
     * 运行时与上下文引用续跑；跨节点登记（无内存句柄，多节点共享存储部署）凭登记数据重建现场后由引擎从持久检查点续跑，
     * 不存在待恢复登记时返回错误流。
     * </p>
     * @param sessionId
     * @param toolCallId
     * @param answer
     * @return
     */
    @Override
    public Flux<StreamEvent> resumeClarification(String sessionId, String toolCallId, String answer) {
        PendingResumeEntry entry = clarificationPendingRegistry.popEntry(sessionId, toolCallId);
        if (entry == null) {
            return Flux.error(new AiException(AiErrorCode.NOT_FOUND,
                    "无待恢复的澄清请求，可能已恢复或过期"));
        }
        log.info("恢复澄清续跑: sessionId={}, toolCallId={}", sessionId, toolCallId);
        ClarificationResumeHandle handle = entry.getClarificationHandle();
        if (handle != null) {
            // 同节点：复用暂停现场句柄续跑
            // 恢复线程重建scope上下文（复用暂停请求的scope信息），续跑装配与模型调用依赖
            syncScopeContext(handle.getAgentContext().getRequest());
            return handle.getExecutor().streamResumeClarification(handle.getAgentContext(), handle,
                    List.of(new ClarificationAnswer(toolCallId, answer)));
        }
        // 跨节点：凭登记数据重建现场续跑
        return resumeClarificationRebuilt(entry, toolCallId, answer);
    }

    /**
     * 跨节点重建澄清现场续跑
     * @param entry
     * @param toolCallId
     * @param answer
     * @return
     */
    private Flux<StreamEvent> resumeClarificationRebuilt(PendingResumeEntry entry, String toolCallId, String answer) {
        try {
            AgentRequest request = OBJECT_MAPPER.readValue(entry.getRequestData(), AgentRequest.class);
            // 恢复线程重建scope上下文，续跑装配与模型调用依赖
            syncScopeContext(request);
            AgentProcessor processor = resolveProcessor(entry.getAgentCode());
            AgentContext context = processor.createAgentContext(request);
            log.info("跨节点重建澄清现场: sessionId={}, agentCode={}, nodeId={}",
                    entry.getSessionId(), entry.getAgentCode(), entry.getNodeId());
            return reActAgentExecutor.streamResumeClarificationRebuilt(context,
                    List.of(new ClarificationAnswer(toolCallId, answer)));
        } catch (Exception e) {
            log.error("跨节点重建澄清现场失败: sessionId={}", entry.getSessionId(), e);
            return Flux.error(new AiException(AiErrorCode.AGENT_RUNTIME_ERROR,
                    "重建恢复现场失败: " + e.getMessage()));
        }
    }

    /**
     * 恢复引擎确认续跑
     * <p>
     * 同节点登记携带内存句柄直接复用暂停现场续跑；跨节点登记（无内存句柄，多节点共享存储部署）
     * 凭登记数据重建现场后由引擎从持久检查点续跑。
     * </p>
     * @param sessionId
     * @param approved
     * @param operator
     * @param reason
     * @return
     */
    @Override
    public Flux<StreamEvent> resumeConfirm(String sessionId, boolean approved, String operator, String reason) {
        PendingResumeEntry entry = confirmPendingRegistry.popEntry(sessionId);
        if (entry == null) {
            return Flux.error(new AiException(AiErrorCode.NOT_FOUND,
                    "无待恢复的确认请求，可能已处理或过期"));
        }
        log.info("恢复引擎确认续跑: sessionId={}, approved={}, operator={}", sessionId, approved, operator);
        ConfirmResumeHandle handle = entry.getConfirmHandle();
        if (handle != null) {
            // 同节点：复用暂停现场句柄续跑
            // 待确认清单逐个构建确认结果，批准与拒绝按同一清单对齐
            List<ConfirmResult> confirmResults = handle.getPendingToolCalls().stream()
                    .map(call -> approved
                            ? ConfirmResult.approveCall(call.getToolUseId(), call.getToolName())
                            : ConfirmResult.denyCall(call.getToolUseId(), call.getToolName(),
                                    StringUtils.getOrDefault(reason, "人工拒绝")))
                    .toList();
            // 恢复线程重建scope上下文（复用暂停请求的scope信息），续跑装配与模型调用依赖
            syncScopeContext(handle.getAgentContext().getRequest());
            return handle.getExecutor().streamResumeConfirm(handle.getAgentContext(), handle, confirmResults);
        }
        // 跨节点：凭登记数据重建现场续跑
        return resumeConfirmRebuilt(entry, approved, reason);
    }

    /**
     * 跨节点重建确认现场续跑
     * @param entry
     * @param approved
     * @param reason
     * @return
     */
    private Flux<StreamEvent> resumeConfirmRebuilt(PendingResumeEntry entry, boolean approved, String reason) {
        try {
            AgentRequest request = OBJECT_MAPPER.readValue(entry.getRequestData(), AgentRequest.class);
            // 恢复线程重建scope上下文，续跑装配与模型调用依赖
            syncScopeContext(request);
            AgentProcessor processor = resolveProcessor(entry.getAgentCode());
            AgentContext context = processor.createAgentContext(request);
            // 登记的待确认工具清单构建确认结果，批准与拒绝按同一清单对齐
            List<Map<String, Object>> pendingTools = entry.getPendingData() != null
                    ? OBJECT_MAPPER.readValue(entry.getPendingData(),
                            new TypeReference<List<Map<String, Object>>>() { })
                    : List.of();
            List<ConfirmResult> confirmResults = pendingTools.stream()
                    .map(item -> {
                        String toolUseId = item.get("toolUseId") instanceof String s ? s : "";
                        String toolName = item.get("toolName") instanceof String s ? s : "";
                        return approved
                                ? ConfirmResult.approveCall(toolUseId, toolName)
                                : ConfirmResult.denyCall(toolUseId, toolName,
                                        StringUtils.getOrDefault(reason, "人工拒绝"));
                    })
                    .toList();
            log.info("跨节点重建确认现场: sessionId={}, agentCode={}, nodeId={}",
                    entry.getSessionId(), entry.getAgentCode(), entry.getNodeId());
            return reActAgentExecutor.streamResumeConfirmRebuilt(context, confirmResults);
        } catch (Exception e) {
            log.error("跨节点重建确认现场失败: sessionId={}", entry.getSessionId(), e);
            return Flux.error(new AiException(AiErrorCode.AGENT_RUNTIME_ERROR,
                    "重建恢复现场失败: " + e.getMessage()));
        }
    }

    /**
     * 提交异步任务
     * @param request
     * @return
     */
    @Override
    public String submitTask(AgentRequest request) {
        syncScopeContext(request);
        // 捕获主线程的scopeId，供异步线程继承
        String capturedScopeId = resolveCapturedScopeId(request);
        String taskId = StringUtils.generateCompactId();
        return doSubmitTask(taskId, request, capturedScopeId, true);
    }

    /**
     * 执行队列调度器抢占的任务（复用既有执行链路，复用入队时已创建的DB任务记录）
     * @param taskId
     * @param request
     * @return
     */
    @Override
    public String executeClaimed(String taskId, AgentRequest request) {
        String capturedScopeId = request.getScopeId() != null ? request.getScopeId() : SessionContext.getScopeId();
        return doSubmitTask(taskId, request, capturedScopeId, false);
    }

    /**
     * 异步任务提交公共链路
     * @param taskId
     * @param request
     * @param capturedScopeId
     * @param createDbRecord
     * @return
     */
    private String doSubmitTask(String taskId, AgentRequest request, String capturedScopeId, boolean createDbRecord) {
        AgentProcessor processor = resolveProcessor(request.getAgentCode());
        taskTracker.submit(taskId, request, () -> {
            try {
                ScopeContext.setScopeId(capturedScopeId);
                SessionContext.setScopeId(capturedScopeId);
                AgentContext context = processor.createAgentContext(request);
                return processor.process(context);
            } catch (Exception e) {
                log.error("异步任务执行异常: taskId={}, agentCode={}, scopeId={}",
                        taskId, request.getAgentCode(), capturedScopeId, e);
                throw e;
            } finally {
                ScopeContext.clear();
                SessionContext.clear();
            }
        }, createDbRecord);
        log.info("提交异步任务: taskId={}, agentCode={}, scopeId={}, queued={}",
                taskId, request.getAgentCode(), capturedScopeId, !createDbRecord);
        return taskId;
    }

    /**
     * 查询异步任务状态
     * @param taskId
     * @return
     */
    @Override
    public Map<String, Object> queryTask(String taskId) {
        AgentTaskRecord record = taskTracker.query(taskId);
        if (record == null) {
            return Map.of("taskId", taskId, "status", "NOT_FOUND");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", record.getTaskId());
        result.put("status", record.getStatus().name());
        result.put("currentStep", record.getCurrentStep() != null ? record.getCurrentStep() : "");
        result.put("agentCode", record.getAgentCode());
        result.put("agentPath", record.getAgentPath() != null ? record.getAgentPath() : "main");
        result.put("parentTaskId", record.getParentTaskId());
        if (record.getErrorMessage() != null) {
            result.put("errorMessage", record.getErrorMessage());
        }
        if (record.getOutputBlocks() != null) {
            result.put("output", record.getOutputBlocks());
            result.put("outputAsText", ContentBlockConverter.toOutputText(record.getOutputBlocks()));
        }
        if (record.getTokenMetrics() != null) {
            result.put("tokenMetrics", record.getTokenMetrics());
        }
        if (record.getDurationMs() != null) {
            result.put("durationMs", record.getDurationMs());
        }
        return result;
    }

    /**
     * 取消异步任务
     * @param taskId
     * @return
     */
    @Override
    public boolean cancelTask(String taskId) {
        return taskTracker.cancel(taskId, "用户主动取消");
    }

    /**
     * 完成录制并持久化步骤（同步/流式路径）
     * @param recordingTaskId
     * @param status
     * @param outputText
     * @param durationMs
     * @param result
     */
    private void finishRecordingIfNeeded(String recordingTaskId, String status, String outputText, long durationMs, AgentResult result) {
        if (recordingTaskId != null && stepRecordingManager != null) {
            TokenMetrics tokenMetrics = result != null ? result.getTokenMetrics() : null;
            stepRecordingManager.finishRecording(recordingTaskId, status, outputText, durationMs, tokenMetrics);
        }
    }

    /**
     * 发布任务完成事件，供下游模块监听
     * @param request
     * @param result
     * @param success
     */
    private void publishTaskCompletedEvent(AgentRequest request, AgentResult result, boolean success) {
        try {
            String taskId = StepRecordingManager.getRecordingTaskId(request);
            String outputText = result != null ? result.getOutputAsText() : "";
            TaskCompletedEvent event = new TaskCompletedEvent(
                    taskId,
                    request.getUserId(),
                    request.getAgentCode(),
                    request.getInputAsText(),
                    outputText,
                    0,
                    success
            );
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.debug("发布任务完成事件失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 同步作用域上下文到SessionContext，优先使用AgentRequest.scopeId，未设置时沿用ScopeContext
     * @param request
     */
    private void syncScopeContext(AgentRequest request) {
        if (request.getScopeId() != null) {
            SessionContext.setScopeId(request.getScopeId());
            ScopeContext.setScopeId(request.getScopeId());
        } else if (SessionContext.getScopeId() == null) {
            SessionContext.setScopeId(ScopeContext.getScopeId());
        }
    }

    /**
     * 解析需要捕获的scopeId，优先取AgentRequest.scopeId，回退到ScopeContext.getScopeId()
     * @param request
     * @return
     */
    private String resolveCapturedScopeId(AgentRequest request) {
        return request.getScopeId() != null ? request.getScopeId() : ScopeContext.getScopeId();
    }

    private AgentProcessor resolveProcessor(String agentCode) {
        if (agentCode == null){
            agentCode = AgentProcessor.DEFAULT_AGENT;
        }
        Agent agent = loadAgentQuietly(agentCode);
        // 配置优先级：agentConfig.processor显式指定 > agentCode精确匹配 > 默认处理器
        AgentProcessor processor = null;
        String configured = readConfiguredProcessor(agent);
        if (configured != null) {
            processor = processorMap.get(configured);
            if (processor == null) {
                log.warn("agentConfig指定的处理器未注册，忽略配置: agentCode={}, processor={}", agentCode, configured);
            }
        }
        if (processor == null) {
            processor = processorMap.get(agentCode);
        }
        if (processor == null) {
            // 数据库Agent编码与处理器注册编码不一致时回退默认对话处理器
            AgentProcessor fallback = processorMap.get(AgentProcessor.DEFAULT_AGENT);
            if (fallback == null) {
                throw new AiException(AiErrorCode.AGENT_TASK_NOT_FOUND, agentCode);
            }
            log.warn("未找到Agent处理器，回退默认对话: agentCode={}", agentCode);
            processor = fallback;
        }
        // 校验Agent启用状态（复用路由已加载的配置，数据库不可用时降级跳过）
        if (agent != null && agent.getStatus() != null && agent.getStatus() == 0) {
            throw new IllegalStateException("Agent已禁用: " + agentCode);
        }
        return processor;
    }

    /**
     * 加载Agent配置（数据库不可用时降级返回null）
     * @param agentCode
     * @return
     */
    private Agent loadAgentQuietly(String agentCode) {
        try {
            return agentService.getByCode(agentCode);
        } catch (Exception e) {
            log.warn("无法加载Agent配置，降级跳过: agentCode={}", agentCode);
            return null;
        }
    }

    /**
     * 读取agentConfig中的处理器指定（未指定或非法时返回null走默认路由）
     * @param agent
     * @return
     */
    private String readConfiguredProcessor(Agent agent) {
        if (agent == null || agent.getAgentConfig() == null || agent.getAgentConfig().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> config = OBJECT_MAPPER.readValue(agent.getAgentConfig(), new TypeReference<Map<String, Object>>() { });
            Object processor = config == null ? null : config.get("processor");
            return processor instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            log.warn("解析agentConfig.processor失败，走默认路由: agentCode={}", agent.getAgentCode(), e);
            return null;
        }
    }
}
