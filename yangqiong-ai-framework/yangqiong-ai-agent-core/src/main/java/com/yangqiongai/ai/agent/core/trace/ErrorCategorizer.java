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
package com.yangqiongai.ai.agent.core.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 错误分类器（八分类 + 异常类名/堆栈判断 + enum+Function模式）
 * <p>
 * 八大分类覆盖主要故障模式：MODEL_ERROR、TOOL_FAILURE、TIMEOUT、RATE_LIMIT、
 * CONTEXT_OVERFLOW、NETWORK、EMPTY_COMPLETION、UNKNOWN。分类后自动触发恢复策略。
 * </p>
 * @author yangqiong
 */
@Slf4j
@Service
public class ErrorCategorizer {

    /**
     * 分类规则：按异常类名关键词匹配
     */
    private static final Map<ErrorCategory, List<String>> CLASS_NAME_RULES = new LinkedHashMap<>();

    /**
     * 分类规则：按消息内容关键词匹配
     */
    private static final Map<ErrorCategory, List<String>> MESSAGE_RULES = new LinkedHashMap<>();

    /**
     * 分类规则：按堆栈追踪关键词匹配
     */
    private static final Map<ErrorCategory, List<String>> STACK_TRACE_RULES = new LinkedHashMap<>();

    /**
     * 恢复策略映射：enum + Function模式
     */
    private static final Map<ErrorCategory, Function<Map<String, Object>, RecoveryAction>> RECOVERY_STRATEGIES = new EnumMap<>(ErrorCategory.class);

    static {
        // 异常类名匹配规则（优先级从高到低）
        CLASS_NAME_RULES.put(ErrorCategory.CONTEXT_OVERFLOW, Arrays.asList(
                "ContextOverflow", "TokenLimit", "MaxToken", "ContextLength", "TooManyTokens"));
        CLASS_NAME_RULES.put(ErrorCategory.RATE_LIMIT, Arrays.asList(
                "RateLimit", "Throttle", "Quota", "TooManyRequests"));
        CLASS_NAME_RULES.put(ErrorCategory.MODEL_ERROR, Arrays.asList(
                "Model", "Llm", "ChatModel", "Completion"));
        CLASS_NAME_RULES.put(ErrorCategory.TOOL_FAILURE, Arrays.asList(
                "Tool", "Function", "Mcp", "Skill", "Binding"));
        CLASS_NAME_RULES.put(ErrorCategory.TIMEOUT, Arrays.asList(
                "Timeout", "TimeoutException"));
        CLASS_NAME_RULES.put(ErrorCategory.NETWORK, Arrays.asList(
                "Socket", "Connect", "Http", "Network", "UnknownHost", "NoRoute", "Connection"));

        // 消息内容匹配规则（EMPTY_COMPLETION 优先匹配，避免被 MODEL_ERROR 的 "completion" 先匹配）
        MESSAGE_RULES.put(ErrorCategory.EMPTY_COMPLETION, Arrays.asList(
                "empty completion", "返回null"));
        MESSAGE_RULES.put(ErrorCategory.RATE_LIMIT, Arrays.asList(
                "rate limit", "too many requests", "quota exceeded", "throttl"));
        MESSAGE_RULES.put(ErrorCategory.CONTEXT_OVERFLOW, Arrays.asList(
                "context length", "max tokens", "token limit", "too many tokens"));
        MESSAGE_RULES.put(ErrorCategory.MODEL_ERROR, Arrays.asList(
                "model", "llm", "completion"));
        MESSAGE_RULES.put(ErrorCategory.TOOL_FAILURE, Arrays.asList(
                "tool", "function", "mcp"));
        MESSAGE_RULES.put(ErrorCategory.TIMEOUT, Arrays.asList(
                "timeout", "timed out"));
        MESSAGE_RULES.put(ErrorCategory.NETWORK, Arrays.asList(
                "connection", "network", "socket"));

        // 堆栈追踪匹配规则
        STACK_TRACE_RULES.put(ErrorCategory.EMPTY_COMPLETION, Arrays.asList(
                "empty completion", "buildfinalmessage"));
        STACK_TRACE_RULES.put(ErrorCategory.MODEL_ERROR, Arrays.asList(
                "model", "chatmodel", "agentscope.model", "dashscope"));
        STACK_TRACE_RULES.put(ErrorCategory.TOOL_FAILURE, Arrays.asList(
                "tool", "function", "mcp", "skill"));
        STACK_TRACE_RULES.put(ErrorCategory.CONTEXT_OVERFLOW, Arrays.asList(
                "context", "token", "overflow", "maxlength"));
        STACK_TRACE_RULES.put(ErrorCategory.RATE_LIMIT, Arrays.asList(
                "ratelimit", "throttle", "quota"));
        STACK_TRACE_RULES.put(ErrorCategory.NETWORK, Arrays.asList(
                "socket", "connect", "http", "network"));

        // 恢复策略映射
        RECOVERY_STRATEGIES.put(ErrorCategory.EMPTY_COMPLETION, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.RETRY)
                .description("模型空响应，直接重试")
                .retryable(true)
                .build());
        RECOVERY_STRATEGIES.put(ErrorCategory.MODEL_ERROR, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.FALLBACK_MODEL)
                .description("切换备用模型重试")
                .retryable(true)
                .build());
        RECOVERY_STRATEGIES.put(ErrorCategory.TOOL_FAILURE, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.RETRY)
                .description("重试工具调用")
                .retryable(true)
                .build());
        RECOVERY_STRATEGIES.put(ErrorCategory.TIMEOUT, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.WAIT_AND_RETRY)
                .description("等待后重试")
                .retryable(true)
                .build());
        RECOVERY_STRATEGIES.put(ErrorCategory.RATE_LIMIT, ctx -> {
            long waitMs = (Long) ctx.getOrDefault("waitMillis", 5000L);
            return RecoveryAction.builder()
                    .strategy(RecoveryStrategy.WAIT_AND_RETRY)
                    .description("限流等待" + waitMs + "ms后重试")
                    .retryable(true)
                    .waitMillis(waitMs)
                    .build();
        });
        RECOVERY_STRATEGIES.put(ErrorCategory.CONTEXT_OVERFLOW, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.REDUCE_CONTEXT)
                .description("缩减上下文后重试")
                .retryable(true)
                .build());
        RECOVERY_STRATEGIES.put(ErrorCategory.NETWORK, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.WAIT_AND_RETRY)
                .description("网络恢复后重试")
                .retryable(true)
                .build());
        RECOVERY_STRATEGIES.put(ErrorCategory.UNKNOWN, ctx -> RecoveryAction.builder()
                .strategy(RecoveryStrategy.NOTIFY_ADMIN)
                .description("未知错误，通知管理员")
                .retryable(false)
                .build());
    }

    /**
     * 分类错误
     * @param error
     * @return
     */
    public ErrorCategory categorize(Throwable error) {
        if (error == null) {
            return ErrorCategory.UNKNOWN;
        }

        // 优先按异常类型判断
        if (error instanceof TimeoutException) {
            return ErrorCategory.TIMEOUT;
        }
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return ErrorCategory.UNKNOWN;
        }

        // 按异常类名判断
        String className = error.getClass().getName();
        ErrorCategory classBased = matchByRules(className, CLASS_NAME_RULES);
        if (classBased != null) {
            return classBased;
        }

        // 按消息内容判断
        String message = error.getMessage();
        if (message != null) {
            ErrorCategory messageBased = matchByRules(message.toLowerCase(), MESSAGE_RULES);
            if (messageBased != null) {
                return messageBased;
            }
        }

        // 递归检查cause
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            return categorize(cause);
        }

        return ErrorCategory.UNKNOWN;
    }

    /**
     * 引擎终态异常类名（引擎侧NonRetryableAgentException，framework无编译依赖，运行期按名匹配）
     */
    private static final String ENGINE_NON_RETRYABLE_EXCEPTION =
            "com.yangqiongai.agent.harness.exception.NonRetryableAgentException";

    /**
     * 判断是否为不可重试的终态模型/引擎错误（计费认证类错误与连续工具失败中止）
     * <p>
     * 引擎侧通过NonRetryableAgentException结构化标记终态错误，framework与引擎
     * 无编译依赖，运行期按类名匹配；消息匹配仅作为兜底（兼容其他模型实现以
     * 普通RuntimeException抛出的401/402/403）
     * </p>
     * @param error
     * @return
     */
    public static boolean isTerminalModelError(Throwable error) {
        if (error != null && ENGINE_NON_RETRYABLE_EXCEPTION.equals(error.getClass().getName())) {
            return true;
        }
        String message = error == null ? null : error.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("模型API返回错误码: 401")
                || message.contains("模型API返回错误码: 402")
                || message.contains("模型API返回错误码: 403")
                || message.contains("连续工具失败超过阈值");
    }

    /**
     * 分类错误并返回恢复动作
     * @param error
     * @return
     */
    public CategorizeResult categorizeWithRecovery(Throwable error) {
        ErrorCategory category = categorize(error);
        RecoveryAction action = determineRecoveryAction(category);
        log.info("错误分类完成: category={}, strategy={}, description={}",
                category, action.getStrategy(), action.getDescription());
        return CategorizeResult.builder()
                .category(category)
                .recoveryAction(action)
                .build();
    }

    /**
     * 确定恢复策略
     * @param category
     * @return
     */
    public RecoveryAction determineRecoveryAction(ErrorCategory category) {
        if (category == null) {
            category = ErrorCategory.UNKNOWN;
        }
        Function<Map<String, Object>, RecoveryAction> strategy = RECOVERY_STRATEGIES.get(category);
        if (strategy != null) {
            return strategy.apply(Collections.emptyMap());
        }
        return RecoveryAction.builder()
                .strategy(RecoveryStrategy.NOTIFY_ADMIN)
                .description("未知分类，通知管理员")
                .retryable(false)
                .build();
    }

    /**
     * 使用堆栈追踪分类错误
     * @param error
     * @return
     */
    public ErrorCategory categorizeWithStackTrace(Throwable error) {
        if (error == null) {
            return ErrorCategory.UNKNOWN;
        }

        // 先用基本分类
        ErrorCategory basic = categorize(error);
        if (basic != ErrorCategory.UNKNOWN) {
            return basic;
        }

        // 通过堆栈追踪进一步判断
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString().toLowerCase();

        return matchByRules(stackTrace, STACK_TRACE_RULES);
    }

    /**
     * 按规则映射匹配分类
     * @param text
     * @param rules
     * @return
     */
    private ErrorCategory matchByRules(String text, Map<ErrorCategory, List<String>> rules) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Map.Entry<ErrorCategory, List<String>> entry : rules.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 错误类别枚举（八分类）
     */
    public enum ErrorCategory {
        MODEL_ERROR,
        TOOL_FAILURE,
        TIMEOUT,
        RATE_LIMIT,
        CONTEXT_OVERFLOW,
        NETWORK,
        EMPTY_COMPLETION,
        UNKNOWN
    }

    /**
     * 恢复策略枚举
     */
    public enum RecoveryStrategy {
        RETRY,
        FALLBACK_MODEL,
        REDUCE_CONTEXT,
        WAIT_AND_RETRY,
        NOTIFY_ADMIN
    }

    /**
     * 恢复动作
     */
    @lombok.Builder
    @lombok.Data
    public static class RecoveryAction {

        /**
         * 恢复策略
         */
        private RecoveryStrategy strategy;

        /**
         * 描述
         */
        private String description;

        /**
         * 是否可重试
         */
        @lombok.Builder.Default
        private boolean retryable = false;

        /**
         * 等待毫秒数
         */
        @lombok.Builder.Default
        private long waitMillis = 0;
    }

    /**
     * 分类结果
     */
    @lombok.Builder
    @lombok.Data
    public static class CategorizeResult {

        /**
         * 错误类别
         */
        private ErrorCategory category;

        /**
         * 恢复动作
         */
        private RecoveryAction recoveryAction;
    }
}
