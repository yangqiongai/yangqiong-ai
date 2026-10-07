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
package com.yangqiongai.ai.agent.core.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.AgentResultConverter;
import com.yangqiongai.ai.agent.core.bootstrap.ImageInputSupportChecker;
import com.yangqiongai.ai.agent.core.event.ApprovalEventBridge;
import com.yangqiongai.ai.agent.core.middleware.SdkMiddlewareAdapter;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.core.model.content.OutputBlock;
import com.yangqiongai.ai.agent.core.orchestration.SubagentDeclaration;
import com.yangqiongai.ai.agent.core.circuit.AgentCircuitBreaker;
import com.yangqiongai.ai.agent.core.circuit.FallbackModelResolver;
import com.yangqiongai.ai.agent.core.circuit.RetryWithBackoff;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer;
import com.yangqiongai.ai.agent.runtime.event.ConfirmResult;
import com.yangqiongai.ai.agent.runtime.event.ModelCallEndInfo;
import com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent;
import com.yangqiongai.ai.agent.runtime.event.RequireUserConfirmEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;
import com.yangqiongai.ai.agent.runtime.budget.ModelCallUsage;
import com.yangqiongai.ai.agent.runtime.budget.UsageListener;
import com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl;
import com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.agent.core.session.ConversationBridge;
import com.yangqiongai.ai.agent.core.stream.CancellationToken;
import com.yangqiongai.ai.agent.core.stream.InterruptControlRegistry;
import com.yangqiongai.ai.agent.core.stream.ThinkTagSplitter;
import com.yangqiongai.ai.agent.core.task.AgentTaskTracker;
import com.yangqiongai.ai.agent.core.task.StepRecordingManager;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.common.util.StringUtils;
import com.yangqiongai.ai.agent.core.stream.StreamingAccumulator;
import com.yangqiongai.ai.agent.core.task.TaskStepRecorder;
import com.yangqiongai.ai.agent.core.trace.TraceCollector;
import com.yangqiongai.ai.agent.core.trace.TraceContext;
import com.yangqiongai.ai.agent.core.util.ContentBlockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ReAct模式Agent执行器，支持结构化输出与流式事件推送
 * @author yangqiong
 */
@Service
public class ReActAgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentExecutor.class);

    private static final int DEFAULT_STREAM_TIMEOUT_SECONDS = 180;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TraceCollector traceCollector;
    private final ConversationBridge conversationBridge;
    private final ApprovalEventBridge approvalEventBridge;
    private final AgentCircuitBreaker circuitBreaker;
    private final RetryWithBackoff retryWithBackoff;
    private final FallbackModelResolver fallbackModelResolver;
    private final AgentModelFactory agentModelFactory;
    private final ErrorCategorizer errorCategorizer;
    private final InterruptControlRegistry interruptControlRegistry;
    private final ClarificationPendingRegistry clarificationPendingRegistry;
    private final ConfirmPendingRegistry confirmPendingRegistry;
    private final StepRecordingManager stepRecordingManager;

    /**
     * 图片输入能力校验
     */
    private final ImageInputSupportChecker imageInputSupportChecker;

    /**
     * 用量监听SPI提供者（metering未启用时无bean，getIfAvailable返回null）
     */
    private final ObjectProvider<UsageListener> usageListenerProvider;

    public ReActAgentExecutor(TraceCollector traceCollector, ConversationBridge conversationBridge,
                              ApprovalEventBridge approvalEventBridge,
                              AgentCircuitBreaker circuitBreaker, RetryWithBackoff retryWithBackoff,
                              FallbackModelResolver fallbackModelResolver, AgentModelFactory agentModelFactory,
                              ErrorCategorizer errorCategorizer, InterruptControlRegistry interruptControlRegistry,
                              ClarificationPendingRegistry clarificationPendingRegistry,
                              ConfirmPendingRegistry confirmPendingRegistry,
                              StepRecordingManager stepRecordingManager,
                              ImageInputSupportChecker imageInputSupportChecker,
                              ObjectProvider<UsageListener> usageListenerProvider) {
        this.traceCollector = traceCollector;
        this.conversationBridge = conversationBridge;
        this.approvalEventBridge = approvalEventBridge;
        this.circuitBreaker = circuitBreaker;
        this.retryWithBackoff = retryWithBackoff;
        this.fallbackModelResolver = fallbackModelResolver;
        this.agentModelFactory = agentModelFactory;
        this.errorCategorizer = errorCategorizer;
        this.interruptControlRegistry = interruptControlRegistry;
        this.clarificationPendingRegistry = clarificationPendingRegistry;
        this.confirmPendingRegistry = confirmPendingRegistry;
        this.stepRecordingManager = stepRecordingManager;
        this.imageInputSupportChecker = imageInputSupportChecker;
        this.usageListenerProvider = usageListenerProvider;
    }

    /**
     * 发布调用级用量明细（按模型拆分计价的基础数据）
     * <p>
     * 配对MODEL_CALL_START/END事件构造调用级用量，taskId缺失（非任务路径）时跳过，
     * 监听器异常仅记录不阻断事件流。
     * </p>
     * @param context
     * @param event MODEL_CALL_END事件
     * @param startTimeMs 调用开始时间（epoch毫秒，0表示未知）
     * @param callSeq 调用序号（run内从1递增）
     */
    private void publishModelCallUsage(AgentContext context, AgentEvent event, long startTimeMs, int callSeq) {
        if (usageListenerProvider == null || !(event.getPayload() instanceof ModelCallEndInfo info)) {
            return;
        }
        UsageListener listener = usageListenerProvider.getIfAvailable();
        if (listener == null) {
            return;
        }
        try {
            AgentRequest request = context.getRequest();
            String taskId = null;
            if (request != null && request.getBody() != null) {
                Object value = request.getBody().get(AgentRequest.BodyKeys.TASK_ID);
                taskId = value instanceof String s && !s.isBlank() ? s : null;
            }
            if (taskId == null) {
                return;
            }
            String modelCode = info.getModelName() != null && !info.getModelName().isBlank()
                    ? info.getModelName() : resolveModelKey(context);
            long durationMs = startTimeMs > 0 ? Math.max(0, System.currentTimeMillis() - startTimeMs) : 0;
            listener.onModelCall(new ModelCallUsage(taskId, null, callSeq, modelCode,
                    info.getInputTokens(), info.getOutputTokens(), 0, info.getTotalTokens(),
                    durationMs, startTimeMs, request != null ? request.getScopeId() : null));
        } catch (Exception e) {
            log.warn("调用级用量上报失败: callSeq={}", callSeq, e);
        }
    }

    /**
     * 同步执行Agent，支持结构化输出（三层容错：熔断→重试→Fallback）
     * @param context
     * @return
     */
    public AgentResult execute(AgentContext context) {
        // 图片输入能力校验
        imageInputSupportChecker.check(context);

        // 注册审批事件桥接（同步执行也需要，否则审批通知无法推送）
        String sessionId = resolveSessionId(context);
        Sinks.Many<StreamEvent> approvalSink = Sinks.many().multicast().onBackpressureBuffer();
        if (sessionId != null) {
            approvalEventBridge.registerLlmSink(sessionId, approvalSink);
        }

        try {
            String modelKey = resolveModelKey(context);
            if (!circuitBreaker.allowRequest(modelKey)) {
                log.warn("熔断器开启，尝试Fallback模型: modelKey={}", modelKey);
                AgentResult fallbackResult = tryFallbackModel(context, modelKey);
                if (fallbackResult != null) {
                    return fallbackResult;
                }
                throw new AiException(AiErrorCode.MODEL_CALL_FAILED, "模型调用被熔断且无Fallback: " + modelKey);
            }

            try {
                AgentResult result = retryWithBackoff.executeWithRetry(
                    () -> doExecuteInternal(context),
                    ex -> shouldRetry(ex, context)
                );
                return result;
            } catch (Exception e) {
                // 重试耗尽后尝试Fallback模型
                ErrorCategorizer.CategorizeResult catResult = errorCategorizer.categorizeWithRecovery(e);
                if (catResult.getCategory() == ErrorCategorizer.ErrorCategory.MODEL_ERROR
                        || catResult.getCategory() == ErrorCategorizer.ErrorCategory.EMPTY_COMPLETION) {
                    AgentResult fallbackResult = tryFallbackModel(context, modelKey);
                    if (fallbackResult != null) return fallbackResult;
                }
                throw e;
            }
        } finally {
            if (sessionId != null) {
                approvalEventBridge.removeLlmSink(sessionId);
            }
        }
    }

    /**
     * 内部执行逻辑（不含熔断/重试/Fallback）
     * @param context
     * @return
     */
    private AgentResult doExecuteInternal(AgentContext context) {
        AgentExecution execution = prepareExecution(context);
        List<AgentMessage> currentInputs = requireMsgInputs(context);
        List<AgentMessage> historyMsgs = conversationBridge.restoreSession(context);
        List<AgentMessage> inputs = buildInputsWithHistory(currentInputs, historyMsgs, execution.agent());
        AgentResultConverter resultConverter = requireAttribute(context, AgentContext.CTX_RESULT_CONVERTER, AgentResultConverter.class);
        int timeoutSeconds = resolveTimeoutSeconds(context);

        Duration timeout = Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_STREAM_TIMEOUT_SECONDS);
        AgentMessage finalMessage = execution.agent().call(inputs, buildRuntimeContext(context)).block(timeout);
        if (finalMessage == null) {
            throw new IllegalStateException("Agent empty completion: 模型返回空响应（无文本无工具调用）");
        }

        // 从 finalMessage 提取多模态内容，过滤 ThinkingBlock 并清理 TextBlock 中的 thinking 标签
        List<AgentContentBlock> answerBlocks = processAnswerBlocks(finalMessage);
        // 过滤后无实际内容（模型可能只返回了 ThinkingBlock），视为空响应触发重试
        if (answerBlocks == null || answerBlocks.isEmpty()) {
            throw new IllegalStateException("Agent empty completion: 模型返回空响应（无文本无工具调用）");
        }
        // 持久化时图片块转为markdown文本（base64替换为占位符），避免会话历史携带兆级数据
        conversationBridge.persistSession(context, ContentBlockUtils.sanitizeImagesForPersist(finalMessage));
        // 结构化输出处理
        Class<?> outputType = context.getStructuredOutputType();
        if (outputType != null) {
            String textAnswer = ContentBlockUtils.toText(answerBlocks);
            String structured = convertToStructuredOutput(textAnswer, outputType);
            answerBlocks = List.of(AgentTextBlock.builder().text(structured).build());
        }
        String modelKey = resolveModelKey(context);
        circuitBreaker.recordSuccess(modelKey);
        return resultConverter.converter(context, answerBlocks, finalMessage.getChatUsage(), finalMessage);
    }

    /**
     * 判断异常是否可重试，同时记录熔断器失败
     * @param ex
     * @param context
     * @return
     */
    private boolean shouldRetry(Exception ex, AgentContext context) {
        ErrorCategorizer.CategorizeResult result = errorCategorizer.categorizeWithRecovery(ex);
        String modelKey = resolveModelKey(context);

        // 模型错误、空响应或限流错误记录熔断器失败
        if (result.getCategory() == ErrorCategorizer.ErrorCategory.MODEL_ERROR
            || result.getCategory() == ErrorCategorizer.ErrorCategory.RATE_LIMIT
            || result.getCategory() == ErrorCategorizer.ErrorCategory.EMPTY_COMPLETION) {
            circuitBreaker.recordFailure(modelKey);
        }

        return result.getRecoveryAction().isRetryable();
    }

    /**
     * 尝试使用Fallback模型执行
     * @param context
     * @param failedModelCode
     * @return Fallback执行结果，无可用Fallback时返回null
     */
    private AgentResult tryFallbackModel(AgentContext context, String failedModelCode) {
        String fallbackModelCode = fallbackModelResolver.resolveFallbackModelCode(failedModelCode);
        if (fallbackModelCode == null) {
            log.warn("无可用Fallback模型: failedModelCode={}", failedModelCode);
            return null;
        }
        log.info("尝试Fallback模型: failedModel={}, fallbackModel={}", failedModelCode, fallbackModelCode);

        try {
            // 替换执行计划中的模型
            Object agent = context.getAttribute(AgentContext.CTX_EXECUTION_AGENT);
            if (agent instanceof AgentRuntimeBuilder builder) {
                builder.model(agentModelFactory.getModel(fallbackModelCode, null));
                context.setAttribute(AgentContext.CTX_EXECUTION_AGENT, builder);
                // 使用Fallback模型执行
                AgentResult result = doExecuteInternal(context);
                circuitBreaker.recordSuccess(fallbackModelCode);
                return result;
            }
        } catch (Exception e) {
            log.warn("Fallback模型执行失败: fallbackModelCode={}", fallbackModelCode, e);
            circuitBreaker.recordFailure(fallbackModelCode);
        }
        return null;
    }

    /**
     * 从上下文中解析模型标识
     * @param context
     * @return
     */
    private String resolveModelKey(AgentContext context) {
        if (context.getRequest() != null) {
            String modelCode = context.getRequest().getModelCode();
            if (modelCode != null) {
                return modelCode;
            }
        }
        return "default";
    }

    /**
     * 流式执行Agent，返回结构化事件流
     * @param context
     * @return
     */
    public Flux<StreamEvent> streamExecute(AgentContext context) {
        // 图片输入能力校验
        imageInputSupportChecker.check(context);

        Class<?> outputType = context.getStructuredOutputType();
        if (outputType != null) {
            return streamWithStructuredFallback(context, outputType)
                    .retryWhen(reactor.util.retry.Retry.max(MAX_STREAM_RETRIES)
                            .filter(this::isStreamRetryable)
                            .doBeforeRetry(signal -> log.info("结构化输出空响应重试: attempt={}", signal.totalRetries() + 1)));
        }
        // defer保证每次重试真正重新执行（重建sink与会话恢复），否则重订阅只会重放已终结sink的错误
        return Flux.defer(() -> streamRawEvents(context))
                .retryWhen(reactor.util.retry.Retry.max(MAX_STREAM_RETRIES)
                        .filter(this::isStreamRetryable)
                        .doBeforeRetry(signal -> log.info("流式空响应重试: attempt={}", signal.totalRetries() + 1)));
    }

    /**
     * 流式重试最大次数
     */
    private static final int MAX_STREAM_RETRIES = 3;

    /**
     * 判断流式错误是否可重试
     * @param error
     * @return
     */
    private boolean isStreamRetryable(Throwable error) {
        if (error instanceof AiException) {
            // 业务确定性错误（如安全护栏拦截、参数校验失败）重试不会成功
            return false;
        }
        if (error instanceof IllegalStateException && error.getMessage() != null
                && error.getMessage().contains("empty completion")) {
            log.info("流式空响应，准备重试");
            return true;
        }
        ErrorCategorizer.CategorizeResult result = errorCategorizer.categorizeWithRecovery(error);
        return result.getRecoveryAction().isRetryable();
    }

    /**
     * 带结构化输出的流式执行，回退为同步调用后包装为单事件流
     * @param context
     * @param outputType
     * @return
     */
    private Flux<StreamEvent> streamWithStructuredFallback(AgentContext context, Class<?> outputType) {
        AgentExecution execution = prepareExecution(context);
        List<AgentMessage> currentInputs = requireMsgInputs(context);
        List<AgentMessage> historyMsgs = conversationBridge.restoreSession(context);
        List<AgentMessage> inputs = buildInputsWithHistory(currentInputs, historyMsgs, execution.agent());
        AgentResultConverter resultConverter = requireAttribute(context, AgentContext.CTX_RESULT_CONVERTER, AgentResultConverter.class);
        int timeoutSeconds = resolveTimeoutSeconds(context);

        Duration timeout = Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_STREAM_TIMEOUT_SECONDS);
        return Flux.defer(() -> {
            try {
                AgentMessage finalMessage = execution.agent().call(inputs, buildRuntimeContext(context)).block(timeout);
                if (finalMessage == null) {
                    return Flux.error(new IllegalStateException("结构化输出 empty completion: 模型返回空响应（无文本无工具调用）"));
                }
                // 从 finalMessage 提取多模态内容，过滤 ThinkingBlock 并清理 TextBlock 中的 thinking 标签
                List<AgentContentBlock> rawAnswerBlocks = processAnswerBlocks(finalMessage);
                // 过滤后无实际内容（模型可能只返回了 ThinkingBlock），视为空响应触发重试
                if (rawAnswerBlocks == null || rawAnswerBlocks.isEmpty()) {
                    return Flux.error(new IllegalStateException("结构化输出 empty completion: 模型返回空响应（无文本无工具调用）"));
                }
                // 持久化时图片块转为markdown文本（base64替换为占位符），避免会话历史携带兆级数据
        conversationBridge.persistSession(context, ContentBlockUtils.sanitizeImagesForPersist(finalMessage));
                String textAnswer = ContentBlockUtils.toText(rawAnswerBlocks);
                String structured = convertToStructuredOutput(textAnswer, outputType);
                List<AgentContentBlock> structuredBlocks = List.of(AgentTextBlock.builder().text(structured).build());
                resultConverter.converter(context, structuredBlocks, finalMessage.getChatUsage(), finalMessage);
                return Flux.just(StreamEvent.textDelta(structured));
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }

    /**
     * 恢复澄清续跑执行，事件管道与正常流式一致，仅数据源换为引擎恢复入口
     * <p>
     * 恢复不经过空响应重试包装，避免重复调用resumeWithClarification造成消息重复。
     * </p>
     * @param context 暂停时保存的执行上下文
     * @param resume 暂停现场句柄
     * @param answers 用户澄清应答
     * @return
     */
    public Flux<StreamEvent> streamResumeClarification(AgentContext context, ClarificationResumeHandle resume,
                                                       List<ClarificationAnswer> answers) {
        return streamRawEvents(context, new ResumePlan(resume.getRuntimeContext(),
                rc -> resume.getRuntime().resumeWithClarification(answers, rc)));
    }

    /**
     * 恢复引擎确认续跑（用户批准或拒绝后调用，复用暂停时的运行时与上下文引用透传引擎）
     * @param context 暂停时保存的执行上下文
     * @param resume 暂停现场句柄
     * @param confirmResults 用户确认结果清单
     * @return
     */
    public Flux<StreamEvent> streamResumeConfirm(AgentContext context, ConfirmResumeHandle resume,
                                                 List<ConfirmResult> confirmResults) {
        return streamRawEvents(context, new ResumePlan(resume.getRuntimeContext(),
                rc -> resume.getRuntime().resume(confirmResults, rc)));
    }

    /**
     * 恢复引擎确认续跑（重建式：跨节点恢复凭登记数据重建上下文后，由引擎从持久检查点续跑）
     * @param context 重建的执行上下文
     * @param confirmResults 用户确认结果清单
     * @return
     */
    public Flux<StreamEvent> streamResumeConfirmRebuilt(AgentContext context, List<ConfirmResult> confirmResults) {
        AgentExecution execution = prepareExecution(context);
        return streamRawEvents(context, new ResumePlan(buildRuntimeContext(context),
                rc -> execution.agent().resume(confirmResults, rc)), execution);
    }

    /**
     * 恢复澄清续跑（重建式：跨节点恢复凭登记数据重建上下文后，由引擎从持久检查点续跑）
     * @param context 重建的执行上下文
     * @param answers 用户澄清应答
     * @return
     */
    public Flux<StreamEvent> streamResumeClarificationRebuilt(AgentContext context, List<ClarificationAnswer> answers) {
        AgentExecution execution = prepareExecution(context);
        return streamRawEvents(context, new ResumePlan(buildRuntimeContext(context),
                rc -> execution.agent().resumeWithClarification(answers, rc)), execution);
    }

    /**
     * 原始事件流式执行，推送TEXT_DELTA/THINKING_DELTA/TOOL_CALL_DELTA
     * @param context
     * @return
     */
    private Flux<StreamEvent> streamRawEvents(AgentContext context) {
        return streamRawEvents(context, null);
    }

    private Flux<StreamEvent> streamRawEvents(AgentContext context, ResumePlan plan) {
        AgentExecution execution = prepareExecution(context);
        return streamRawEvents(context, plan, execution);
    }

    /**
     * 原始事件流式执行（预构建执行实例版本，恢复模式跳过重复prepareExecution）
     * @param context
     * @param plan 恢复计划（null为正常执行）
     * @param execution 预构建的执行实例
     * @return
     */
    private Flux<StreamEvent> streamRawEvents(AgentContext context, ResumePlan plan, AgentExecution execution) {
        // 恢复模式续用引擎暂停快照中的对话，不重建输入与会话历史
        List<AgentMessage> inputs = List.of();
        if (plan == null) {
            List<AgentMessage> currentInputs = requireMsgInputs(context);
            List<AgentMessage> historyMsgs = conversationBridge.restoreSession(context);
            inputs = buildInputsWithHistory(currentInputs, historyMsgs, execution.agent());
        }
        AgentResultConverter resultConverter = requireAttribute(context, AgentContext.CTX_RESULT_CONVERTER, AgentResultConverter.class);
        int timeoutSeconds = resolveTimeoutSeconds(context);
        CancellationToken cancellationToken = resolveCancellationToken(context);

        // 创建中断控制器，存入上下文供外部API触发
        // AgentInterruptControl为接口，此处创建简单实现，实际中断由disposable.dispose()完成
        AgentInterruptControl interruptControl = (source, message) -> {
            log.info("收到中断请求: source={}, sessionId={}", source, resolveSessionId(context));
        };
        context.setAttribute(AgentContext.CTX_INTERRUPT_CONTROL, interruptControl);

        Duration timeout = Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_STREAM_TIMEOUT_SECONDS);
        Sinks.Many<StreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        StreamingAccumulator collector = new StreamingAccumulator();

        // 注册当前sink，使审批事件能推送到此SSE流
        String sessionId = resolveSessionId(context);
        if (sessionId != null) {
            approvalEventBridge.registerLlmSink(sessionId, sink);
            // 注册中断控制器到注册表，供外部API通过sessionId触发中断
            interruptControlRegistry.register(sessionId, interruptControl);
        }

        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        AtomicBoolean terminalSignal = new AtomicBoolean(false);
        AtomicInteger prevVisibleLen = new AtomicInteger(0);
        AtomicInteger prevReasoningLen = new AtomicInteger(0);
        // 调用级计量配对状态（单run内）
        AtomicLong modelCallStartMs = new AtomicLong(0);
        AtomicInteger callSeq = new AtomicInteger(0);
        // 引擎层已传播的业务错误（如安全护栏拦截），onComplete时不再按空响应处理
        AtomicReference<Throwable> terminalError = new AtomicReference<>();
        // 引擎以事件形式上报的非终态模型错误（重试耗尽后流完成无内容），空完成时还原真实失败原因
        AtomicReference<Throwable> lastModelError = new AtomicReference<>();
        // 本轮已向用户发出澄清提问或引擎确认（引擎暂停属正常收尾，不按空响应处理）
        AtomicBoolean clarificationEmitted = new AtomicBoolean(false);
        AtomicBoolean confirmEmitted = new AtomicBoolean(false);

        Runnable cleanup = cancellationToken == null ? () -> {} : cancellationToken.onCancel(() -> {
            if (terminalSignal.get()) {
                return;
            }
            // 触发中断并取消订阅
            interruptControl.trigger(AgentInterruptSource.USER, AgentMessage.builder()
                    .name("system")
                    .role(AgentMessageRole.SYSTEM)
                    .content(List.of(AgentTextBlock.builder().text("用户取消").build()))
                    .build());
            Disposable disposable = subscriptionRef.get();
            if (disposable != null) {
                disposable.dispose();
            }
            sink.tryEmitComplete();
        });

        // 恢复模式必须复用暂停时的运行时与上下文引用（快照写入其attributes），否则引擎找不到暂停状态
        AgentRuntimeContext runtimeContext = plan == null ? buildRuntimeContext(context) : plan.runtimeContext();
        Flux<AgentEvent> eventFlux = plan == null
                ? execution.agent().streamEvents(inputs, runtimeContext)
                : plan.eventFlux().apply(runtimeContext);

        Disposable subscription = eventFlux
                .subscribe(
                        event -> {
                            collector.consume(event);
                            AgentEventType type = event.getType();
                            if (type == AgentEventType.TEXT_BLOCK_DELTA) {
                                emitTextDelta(sink, collector, prevVisibleLen);
                            } else if (type == AgentEventType.THINKING_BLOCK_DELTA) {
                                emitThinkingDelta(sink, collector, prevReasoningLen);
                            } else if (type == AgentEventType.TOOL_CALL_DELTA) {
                                sink.tryEmitNext(StreamEvent.toolCallDelta(""));
                            } else if (type == AgentEventType.MODEL_CALL_START) {
                                modelCallStartMs.set(System.currentTimeMillis());
                            } else if (type == AgentEventType.MODEL_CALL_END) {
                                publishModelCallUsage(context, event, modelCallStartMs.getAndSet(0),
                                        callSeq.incrementAndGet());
                            } else if (type == AgentEventType.REQUIRE_USER_CLARIFICATION) {
                                clarificationEmitted.set(true);
                                emitClarificationRequired(sink, event);
                                // 保存暂停现场供外部提交答案后续跑（运行时上下文引用含引擎暂停快照）
                                if (event instanceof RequireUserClarificationEvent clarification && sessionId != null) {
                                    clarificationPendingRegistry.register(new ClarificationResumeHandle(
                                            sessionId, clarification.getToolCallId(), clarification.getQuestion(),
                                            this, execution.agent(), runtimeContext, context));
                                }
                            } else if (type == AgentEventType.REQUIRE_USER_CONFIRM) {
                                confirmEmitted.set(true);
                                emitConfirmRequired(sink, event, sessionId);
                                // 保存暂停现场供外部批准/拒绝后恢复续跑（运行时上下文引用含引擎暂停快照）
                                if (event instanceof RequireUserConfirmEvent confirm && sessionId != null) {
                                    confirmPendingRegistry.register(new ConfirmResumeHandle(
                                            sessionId, confirm.getPendingToolCalls(),
                                            this, execution.agent(), runtimeContext, context));
                                }
                            } else if (type == AgentEventType.TOKEN_BUDGET_WARN
                                    || type == AgentEventType.TOKEN_BUDGET_EXCEEDED
                                    || type == AgentEventType.COST_BUDGET_WARN
                                    || type == AgentEventType.COST_BUDGET_EXCEEDED) {
                                emitBudgetWarning(sink, event);
                            } else if (type == AgentEventType.ERROR) {
                                // 引擎将middleware异常包装为ERROR事件，还原为错误信号避免被空响应重试逻辑吞掉真实原因
                                if (event.getPayload() instanceof AiException aiError) {
                                    terminalError.set(aiError);
                                    sink.tryEmitError(aiError);
                                } else if (event.getPayload() instanceof Throwable engineError
                                        && ErrorCategorizer.isTerminalModelError(engineError)) {
                                    // 计费认证类模型错误与连续工具失败中止均为终态，包装后短路避免空响应重试烧token
                                    AiException terminal = new AiException(AiErrorCode.MODEL_CALL_FAILED,
                                            engineError.getMessage());
                                    terminalError.set(terminal);
                                    sink.tryEmitError(terminal);
                                } else if (event.getPayload() instanceof Throwable engineError) {
                                    // 非终态模型错误由引擎自行重试，此处仅记录最后一条，空完成时还原真实原因
                                    lastModelError.set(engineError);
                                }
                            }
                        },
                        throwable -> {
                            terminalSignal.set(true);
                            sink.tryEmitError(throwable);
                        },
                        () -> {
                            terminalSignal.set(true);
                            AgentMessage finalMsg = collector.getFinalMessage();
                            if (finalMsg != null) {
                                // 引擎无图片增量事件，模型返回的图片仅体现在最终消息，统一转为markdown追加到回答尾部推送
                                String imageMarkdown = ContentBlockUtils.toImageMarkdown(finalMsg.getContent());
                                String answer = StreamingAccumulator.determineStreamAnswer(finalMsg, collector.getVisibleAnswer());
                                // 过滤后无实际内容（模型可能只返回了 ThinkingBlock），视为空响应触发重试
                                if ((answer == null || answer.isBlank()) && imageMarkdown.isEmpty()) {
                                    log.warn("ReActAgent流式执行收到空响应（仅含思考内容）: sessionId={}", StringUtils.getOrDefault(context.getRequest() == null ? null : context.getRequest().getSessionId()));
                                    sink.tryEmitError(new IllegalStateException("Agent empty completion: 模型返回空响应（无文本无工具调用）"));
                                    return;
                                }
                                if (answer == null) {
                                    answer = "";
                                }
                                if (!imageMarkdown.isEmpty()) {
                                    answer = answer + imageMarkdown;
                                    sink.tryEmitNext(StreamEvent.textDelta(imageMarkdown));
                                }
                                // 持久化时图片块转为markdown文本（base64替换为占位符），避免会话历史携带兆级数据
                                conversationBridge.persistSession(context, ContentBlockUtils.sanitizeImagesForPersist(finalMsg));
                                collector.flushFinalAnswer(answer);
                                // 构造多模态输出内容块（过滤ThinkingBlock等内部类型）
                                List<OutputBlock> finalBlocks = collector.getFinalOutputBlocks();
                                try {
                                    TokenMetrics tokenMetrics = TokenMetrics.fromChatUsage(finalMsg.getChatUsage());
                                    AgentResult result = AgentResult.success(finalBlocks)
                                            .tokenMetrics(tokenMetrics);
                                    log.info("ReActAgent流式任务完成: sessionId={}, totalTokens={}",
                                            StringUtils.getOrDefault(context.getRequest() == null ? null : context.getRequest().getSessionId()),
                                            tokenMetrics.getTotalTokens());
                                } catch (Exception e) {
                                    log.warn("流式结果处理异常", e);
                                }
                                sink.tryEmitComplete();
                            } else if (terminalError.get() != null) {
                                // 引擎层已传播业务错误（如安全护栏拦截），不再按空响应处理
                                return;
                            } else if (clarificationEmitted.get() || confirmEmitted.get()) {
                                // 澄清提问或引擎确认后引擎暂停结束本轮流，属正常收尾，等待用户操作通过恢复端点续跑
                                sink.tryEmitComplete();
                            } else if (lastModelError.get() != null) {
                                // 模型调用最终失败（重试耗尽后流完成无内容），还原真实失败原因而非误报空响应
                                // 引擎侧消息已含"模型调用失败:"前缀，用int构造器避免错误码描述重复拼接
                                sink.tryEmitError(new AiException(AiErrorCode.MODEL_CALL_FAILED.getCode(),
                                        AgentTaskTracker.resolveErrorMessage(lastModelError.get())));
                            } else {
                                // 流式路径空响应：推送错误而非静默完成
                                log.warn("ReActAgent流式执行收到空响应: sessionId={}", StringUtils.getOrDefault(context.getRequest() == null ? null : context.getRequest().getSessionId()));
                                sink.tryEmitError(new IllegalStateException("Agent empty completion: 模型返回空响应（无文本无工具调用）"));
                            }
                        }
                );

        subscriptionRef.set(subscription);
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            subscription.dispose();
        }

        return sink.asFlux()
                .timeout(timeout)
                .doFinally(signal -> {
                    if (sessionId != null) {
                        approvalEventBridge.removeLlmSink(sessionId);
                        interruptControlRegistry.unregister(sessionId);
                    }
                    cleanup.run();
                });
    }

    /**
     * 推送可见文本增量
     * @param sink
     * @param collector
     * @param prevLen
     */
    private void emitTextDelta(Sinks.Many<StreamEvent> sink, StreamingAccumulator collector, AtomicInteger prevLen) {
        try {
            String current = collector.getVisibleAnswer();
            int from = prevLen.get();
            if (current.length() > from) {
                String delta = current.substring(from);
                prevLen.set(current.length());
                sink.tryEmitNext(StreamEvent.textDelta(delta));
            }
        } catch (Exception e) {
            log.warn("推送文本增量失败", e);
        }
    }

    /**
     * 推送推理思考增量
     * @param sink
     * @param collector
     * @param prevLen
     */
    private void emitThinkingDelta(Sinks.Many<StreamEvent> sink, StreamingAccumulator collector, AtomicInteger prevLen) {
        try {
            String current = collector.getReasoningContent();
            int from = prevLen.get();
            if (current.length() > from) {
                String delta = current.substring(from);
                prevLen.set(current.length());
                sink.tryEmitNext(StreamEvent.thinkingDelta(delta));
            }
        } catch (Exception e) {
            log.warn("推送推理增量失败", e);
        }
    }

    /**
     * 推送用户澄清请求事件
     * <p>
     * 类型化事件取question/toolCallId，非类型化事件载荷视为question；
     * 序列化异常仅记录不阻断事件流。
     * </p>
     * @param sink
     * @param event
     */
    private void emitClarificationRequired(Sinks.Many<StreamEvent> sink, AgentEvent event) {
        try {
            Map<String, Object> payload = new HashMap<>();
            if (event instanceof RequireUserClarificationEvent clarification) {
                payload.put("question", clarification.getQuestion());
                payload.put("toolCallId", clarification.getToolCallId());
                // 结构化候选选项透传前端渲染为可点击选择按钮
                if (clarification.getOptions() != null) {
                    payload.put("options", clarification.getOptions());
                }
            } else {
                payload.put("question", event.getPayload() == null ? null : event.getPayload().toString());
            }
            sink.tryEmitNext(StreamEvent.clarificationRequired(OBJECT_MAPPER.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("推送澄清请求事件失败", e);
        }
    }

    /**
     * 推送引擎工具确认请求事件
     * <p>
     * 载荷携带requestId（即会话ID）与待确认工具清单（toolUseId/toolName/input），
     * 序列化异常仅记录不阻断事件流。
     * </p>
     * @param sink
     * @param event
     * @param sessionId
     */
    private void emitConfirmRequired(Sinks.Many<StreamEvent> sink, AgentEvent event, String sessionId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("requestId", sessionId);
            payload.put("sessionId", sessionId);
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            if (event instanceof RequireUserConfirmEvent confirm) {
                for (AgentToolUseBlock call : confirm.getPendingToolCalls()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("toolUseId", call.getToolUseId());
                    item.put("toolName", call.getToolName());
                    item.put("input", call.getInput());
                    toolCalls.add(item);
                }
            }
            payload.put("toolCalls", toolCalls);
            sink.tryEmitNext(StreamEvent.confirmRequired(OBJECT_MAPPER.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("推送引擎确认请求事件失败", e);
        }
    }

    /**
     * 推送预算告警事件
     * <p>
     * budgetType取TOKEN/COST，level取WARN/EXCEEDED，message透传引擎告警内容；
     * 序列化异常仅记录不阻断事件流，预算超限场景run不因事件转换失败。
     * </p>
     * @param sink
     * @param event
     */
    private void emitBudgetWarning(Sinks.Many<StreamEvent> sink, AgentEvent event) {
        try {
            String name = event.getType().name();
            Map<String, Object> payload = new HashMap<>();
            payload.put("budgetType", name.startsWith("TOKEN") ? "TOKEN" : "COST");
            payload.put("level", name.endsWith("EXCEEDED") ? "EXCEEDED" : "WARN");
            payload.put("message", event.getPayload() == null ? null : event.getPayload().toString());
            sink.tryEmitNext(StreamEvent.budgetWarning(OBJECT_MAPPER.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("推送预算告警事件失败", e);
        }
    }

    /**
     * 将历史消息拼接到当前输入之前，构建完整的输入列表
     * <p>
     * 历史消息放在当前用户消息之前，确保 LLM 能看到上下文。
     * 然后调用 {@link #normalizeInputsForModel} 进行归一化处理。
     * </p>
     * @param currentInputs 当前请求的输入消息
     * @param historyMsgs 从数据库恢复的历史消息
     * @param agent AgentRuntime实例
     * @return 拼接并归一化后的完整输入列表
     */
    private List<AgentMessage> buildInputsWithHistory(List<AgentMessage> currentInputs, List<AgentMessage> historyMsgs, AgentRuntime agent) {
        if (historyMsgs == null || historyMsgs.isEmpty()) {
            return normalizeInputsForModel(agent, currentInputs);
        }
        List<AgentMessage> combined = new ArrayList<>(historyMsgs.size() + currentInputs.size());
        combined.addAll(historyMsgs);
        combined.addAll(currentInputs);
        log.debug("拼接历史消息到输入: historySize={}, currentSize={}, totalSize={}",
                historyMsgs.size(), currentInputs.size(), combined.size());
        return normalizeInputsForModel(agent, combined);
    }

    /**
     * 归一化输入消息，降级非首位SYSTEM角色消息为USER
     * <p>
     * 框架层AgentRuntime不暴露sysPrompt查询，仅根据消息顺序降级SYSTEM消息：
     * 出现在非首位的SYSTEM消息会被降级为USER，避免覆盖Agent的systemPrompt。
     * </p>
     * @param agent
     * @param inputs
     * @return
     */
    public List<AgentMessage> normalizeInputsForModel(AgentRuntime agent, List<AgentMessage> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        boolean seenNonSystem = false;
        boolean changed = false;
        List<AgentMessage> normalized = new ArrayList<>(inputs.size());
        for (AgentMessage message : inputs) {
            if (message == null) {
                continue;
            }
            boolean isSystem = message.getRole() == AgentMessageRole.SYSTEM;
            boolean shouldDemote = isSystem && seenNonSystem;
            if (shouldDemote) {
                normalized.add(demoteSystemMessage(message));
                changed = true;
                continue;
            }
            normalized.add(message);
            if (!isSystem) {
                seenNonSystem = true;
            }
        }
        if (changed) {
            log.info("归一化输入消息: agent={}, originalCount={}, normalizedCount={}",
                    agent == null ? "" : StringUtils.getOrDefault(agent.getName()),
                    inputs.size(), normalized.size());
        }
        return changed ? List.copyOf(normalized) : inputs;
    }

    private AgentExecution prepareExecution(AgentContext context) {
        Object executionAgent = context.getAttribute(AgentContext.CTX_EXECUTION_AGENT);
        if (executionAgent instanceof HarnessAgentRuntimeBuilder builder) {
            configureTrace(builder, context);
            configureSubagents(builder, context);
            configurePlanMode(builder, context);
            conversationBridge.prepareSession(context, builder);
            AgentRuntime agent = builder.build();
            log.debug("AgentRuntime构建完成: name={}", agent.getName());
            return new AgentExecution(agent);
        }
        if (executionAgent instanceof AgentRuntime agent) {
            return new AgentExecution(agent);
        }
        throw new IllegalArgumentException("缺少有效的Agent执行计划(executionPlan)");
    }

    /**
     * 配置子代理中间件
     * @param builder
     * @param context
     */
    @SuppressWarnings("unchecked")
    private void configureSubagents(HarnessAgentRuntimeBuilder builder, AgentContext context) {
        Object declObj = context.getAttribute(AgentContext.CTX_SUBAGENT_DECLARATIONS);
        if (declObj instanceof List<?> list && !list.isEmpty()) {
            if (list.stream().allMatch(SubagentDeclaration.class::isInstance)) {
                List<SubagentDeclaration> declarations = (List<SubagentDeclaration>) list;
                SdkMiddlewareAdapter.registerSubagentDeclarations(builder, declarations);
                log.info("已注册子代理声明: count={}", declarations.size());
            }
        } else {
            AgentRequest request = context.getRequest();
            boolean sdkSubagentsEnabled = request != null && request.isSdkSubagentsEnabled();
            if (sdkSubagentsEnabled) {
                // 前端显式启用SDK内置子代理功能，保留agent_spawn等工具
                log.info("SDK内置子代理功能已启用（enableSdkSubagents=true）");
            } else {
                // 默认禁用，完全禁用子代理中间件，避免SDK内置的agent_spawn等工具被注册
                builder.subagentsEnabled(false);
                log.debug("SDK内置子代理功能已禁用（默认），无子代理声明");
            }
        }
    }

    /**
     * 配置规划模式中间件
     * @param builder
     * @param context
     */
    private void configurePlanMode(HarnessAgentRuntimeBuilder builder, AgentContext context) {
        Object planModeObj = context.getAttribute(AgentContext.CTX_PLAN_MODE_ENABLED);
        if (planModeObj instanceof Boolean enabled && enabled) {
            SdkMiddlewareAdapter.enablePlanMode(builder);
            log.info("已启用规划模式");
        }
    }

    private void configureTrace(AgentRuntimeBuilder builder, AgentContext context) {
        TraceContext traceCtx = buildTraceContext(context);
        traceCollector.configure(context, traceCtx);
    }

    private TraceContext buildTraceContext(AgentContext context) {
        if (context == null || context.getRequest() == null) {
            return new TraceContext("", "", "", "");
        }
        return new TraceContext(
                context.getTraceId(),
                context.getRunId(),
                StringUtils.getOrDefault(context.getRequest().getAgentCode()),
                StringUtils.getOrDefault(context.getRequest().getSessionId())
        );
    }

    private AgentRuntimeContext buildRuntimeContext(AgentContext context) {
        if (context == null) {
            return AgentRuntimeContext.empty();
        }
        // AgentRuntimeContext.Builder无put方法，使用Map累积属性后通过attributes注入
        Map<String, Object> attributes = new HashMap<>();
        AgentRuntimeContext.Builder builder = AgentRuntimeContext.builder();
        if (context.getRequest() != null) {
            if (context.getRequest().getSessionId() != null) {
                builder.sessionId(context.getRequest().getSessionId());
            }
            if (context.getRequest().getUserId() != null) {
                builder.userId(context.getRequest().getUserId());
            }
            // 统一步骤采集器注入（使用字符串键存储，确保SDK重建RuntimeContext时属性不丢失）
            if (stepRecordingManager != null) {
                TaskStepRecorder recorder = stepRecordingManager.createRecorder(context.getRequest());
                if (recorder != null) {
                    attributes.put(TaskStepRecorder.RUNTIME_CONTEXT_KEY, recorder);
                    // Span冗余字段：供TraceMiddleware导出span时关联任务与Agent
                    attributes.put("taskId", recorder.getTaskId());
                }
            }
            if (context.getRequest().getAgentCode() != null) {
                attributes.put("agentCode", context.getRequest().getAgentCode());
            }
            // Span/引擎桥接冗余字段：供TraceMiddleware导出span与引擎上下文透传隔离域
            if (context.getRequest().getScopeId() != null) {
                attributes.put("scopeId", context.getRequest().getScopeId());
            }
            // 种子上下文消息透传给引擎（轨迹分叉场景，引擎组装初始历史时并入）
            List<Map<String, Object>> seedMessages = context.getRequest().getSeedMessages();
            if (!seedMessages.isEmpty()) {
                attributes.put(AgentRequest.BodyKeys.SEED_MESSAGES, seedMessages);
            }
        }
        // Hook注入模式下传递SkillBox到RuntimeContext（使用字符串键存储）
        Object skillBox = context.getAttribute(AgentContext.CTX_SKILL_BOX);
        if (skillBox != null) {
            attributes.put(AgentContext.CTX_SKILL_BOX, skillBox);
        }
        if (!attributes.isEmpty()) {
            builder.attributes(attributes);
        }
        return builder.build();
    }

    /**
     * 将原始文本转换为结构化输出JSON
     * @param rawAnswer
     * @param outputType
     * @return
     */
    private String convertToStructuredOutput(String rawAnswer, Class<?> outputType) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(rawAnswer, outputType);
            return OBJECT_MAPPER.writeValueAsString(parsed);
        } catch (Exception e) {
            log.warn("结构化输出解析失败，返回原始文本: type={}", outputType.getSimpleName(), e);
            return rawAnswer;
        }
    }

    private List<AgentMessage> requireMsgInputs(AgentContext context) {
        Object value = context == null ? null : context.getAttribute(AgentContext.CTX_INPUTS);
        if (value instanceof List<?> list && list.stream().allMatch(AgentMessage.class::isInstance)) {
            @SuppressWarnings("unchecked")
            List<AgentMessage> inputs = (List<AgentMessage>) list;
            return inputs;
        }
        throw new IllegalArgumentException("缺少有效的Agent输入消息(inputs)");
    }

    private int resolveTimeoutSeconds(AgentContext context) {
        if (context == null || context.getAttributes() == null) {
            return DEFAULT_STREAM_TIMEOUT_SECONDS;
        }
        Object value = context.getAttributes().get("streamTimeoutSeconds");
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return DEFAULT_STREAM_TIMEOUT_SECONDS;
    }

    private CancellationToken resolveCancellationToken(AgentContext context) {
        if (context == null) {
            return null;
        }
        Object value = context.getAttribute(AgentContext.CTX_CANCELLATION_TOKEN);
        return value instanceof CancellationToken token ? token : null;
    }

    private <T> T requireAttribute(AgentContext context, String key, Class<T> type) {
        T value = getAttribute(context, key, type);
        if (value != null) {
            return value;
        }
        throw new IllegalArgumentException("缺少必要的上下文属性: " + key);
    }

    private <T> T getAttribute(AgentContext context, String key, Class<T> type) {
        Map<String, Object> attributes = context == null ? Map.of() : context.getAttributes();
        Object value = attributes.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    private AgentMessage demoteSystemMessage(AgentMessage message) {
        String text = StringUtils.getOrDefault(message == null ? null : message.getTextContent());
        String normalizedText = text.startsWith("上下文备注：\n")
                ? text
                : "上下文备注：\n" + text;
        return AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(normalizedText).build()))
                .build();
    }

    private String stripThinkingTags(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        ThinkTagSplitter.ThinkSplitResult result =  ThinkTagSplitter.split(text);
        return result.visible();
    }

    /**
     * 从 finalMessage 提取多模态内容块，过滤 ThinkingBlock 并清理 TextBlock 中的 thinking 标签
     * @param finalMessage
     * @return
     */
    private List<AgentContentBlock> processAnswerBlocks(AgentMessage finalMessage) {
        if (finalMessage == null || finalMessage.getContent() == null) {
            return List.of();
        }
        return finalMessage.getContent().stream()
                .filter(b -> !(b instanceof AgentThinkingBlock))
                .map(b -> {
                    if (b instanceof AgentTextBlock tb) {
                        String stripped = stripThinkingTags(tb.getText());
                        return AgentTextBlock.builder().text(stripped).build();
                    }
                    return b;
                })
                .collect(Collectors.toList());
    }

    /**
     * 从上下文中提取sessionId
     */
    private String resolveSessionId(AgentContext context) {
        if (context == null || context.getRequest() == null) {
            return null;
        }
        return context.getRequest().getSessionId();
    }

    private record AgentExecution(AgentRuntime agent) {
    }

    /**
     * 恢复续跑计划（澄清与引擎确认恢复共用的暂停现场载体）
     */
    private record ResumePlan(AgentRuntimeContext runtimeContext,
                              Function<AgentRuntimeContext, Flux<AgentEvent>> eventFlux) {
    }
}
