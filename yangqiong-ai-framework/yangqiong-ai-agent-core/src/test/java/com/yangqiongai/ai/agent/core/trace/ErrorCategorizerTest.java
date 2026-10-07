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

import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer.ErrorCategory;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer.RecoveryAction;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer.RecoveryStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ErrorCategorizer单元测试
 */
class ErrorCategorizerTest {

    private ErrorCategorizer categorizer;

    @BeforeEach
    void setUp() {
        categorizer = new ErrorCategorizer();
    }

    @Test
    @DisplayName("TimeoutException分类为TIMEOUT")
    void timeoutException_categorizedAsTimeout() {
        ErrorCategory category = categorizer.categorize(new TimeoutException());

        assertThat(category).isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    @DisplayName("异常类名含Model分类为MODEL_ERROR")
    void modelErrorException_categorizedAsModelError() {
        RuntimeException ex = new RuntimeException("some error");
        ex.setStackTrace(new StackTraceElement[0]);

        // 创建一个类名包含"Model"的异常
        ErrorCategory category = categorizer.categorize(new ModelTestException("model failed"));

        assertThat(category).isEqualTo(ErrorCategory.MODEL_ERROR);
    }

    @Test
    @DisplayName("异常类名含Tool分类为TOOL_FAILURE")
    void toolException_categorizedAsToolFailure() {
        ErrorCategory category = categorizer.categorize(new ToolTestException("tool failed"));

        assertThat(category).isEqualTo(ErrorCategory.TOOL_FAILURE);
    }

    @Test
    @DisplayName("异常类名含RateLimit分类为RATE_LIMIT")
    void rateLimitException_categorizedAsRateLimit() {
        ErrorCategory category = categorizer.categorize(new RateLimitTestException("rate limited"));

        assertThat(category).isEqualTo(ErrorCategory.RATE_LIMIT);
    }

    @Test
    @DisplayName("异常类名含ContextOverflow分类为CONTEXT_OVERFLOW")
    void contextOverflowException_categorizedAsContextOverflow() {
        ErrorCategory category = categorizer.categorize(new ContextOverflowTestException("overflow"));

        assertThat(category).isEqualTo(ErrorCategory.CONTEXT_OVERFLOW);
    }

    @Test
    @DisplayName("异常类名含Socket分类为NETWORK")
    void socketException_categorizedAsNetwork() {
        ErrorCategory category = categorizer.categorize(new SocketTestException("connection refused"));

        assertThat(category).isEqualTo(ErrorCategory.NETWORK);
    }

    @Test
    @DisplayName("消息含rate limit分类为RATE_LIMIT")
    void messageWithRateLimit_categorizedAsRateLimit() {
        ErrorCategory category = categorizer.categorize(new RuntimeException("rate limit exceeded"));

        assertThat(category).isEqualTo(ErrorCategory.RATE_LIMIT);
    }

    @Test
    @DisplayName("消息含timeout分类为TIMEOUT")
    void messageWithTimeout_categorizedAsTimeout() {
        ErrorCategory category = categorizer.categorize(new RuntimeException("request timed out"));

        assertThat(category).isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    @DisplayName("消息含context length分类为CONTEXT_OVERFLOW")
    void messageWithContextLength_categorizedAsContextOverflow() {
        ErrorCategory category = categorizer.categorize(new RuntimeException("context length exceeded"));

        assertThat(category).isEqualTo(ErrorCategory.CONTEXT_OVERFLOW);
    }

    @Test
    @DisplayName("未知异常分类为UNKNOWN")
    void unknownException_categorizedAsUnknown() {
        ErrorCategory category = categorizer.categorize(new RuntimeException("something unexpected"));

        assertThat(category).isEqualTo(ErrorCategory.UNKNOWN);
    }

    @Test
    @DisplayName("null异常分类为UNKNOWN")
    void nullException_categorizedAsUnknown() {
        ErrorCategory category = categorizer.categorize(null);

        assertThat(category).isEqualTo(ErrorCategory.UNKNOWN);
    }

    @Test
    @DisplayName("嵌套cause异常正确分类")
    void nestedCause_categorizedCorrectly() {
        TimeoutException cause = new TimeoutException();
        RuntimeException wrapper = new RuntimeException("wrapper", cause);

        ErrorCategory category = categorizer.categorize(wrapper);

        assertThat(category).isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    @DisplayName("MODEL_ERROR恢复策略为FALLBACK_MODEL")
    void modelErrorRecovery_fallbackModel() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.MODEL_ERROR);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.FALLBACK_MODEL);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("TOOL_FAILURE恢复策略为RETRY")
    void toolFailureRecovery_retry() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.TOOL_FAILURE);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.RETRY);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("TIMEOUT恢复策略为WAIT_AND_RETRY")
    void timeoutRecovery_waitAndRetry() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.TIMEOUT);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.WAIT_AND_RETRY);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("RATE_LIMIT恢复策略为WAIT_AND_RETRY")
    void rateLimitRecovery_waitAndRetry() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.RATE_LIMIT);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.WAIT_AND_RETRY);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("CONTEXT_OVERFLOW恢复策略为REDUCE_CONTEXT")
    void contextOverflowRecovery_reduceContext() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.CONTEXT_OVERFLOW);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.REDUCE_CONTEXT);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("NETWORK恢复策略为WAIT_AND_RETRY")
    void networkRecovery_waitAndRetry() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.NETWORK);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.WAIT_AND_RETRY);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("UNKNOWN恢复策略为NOTIFY_ADMIN")
    void unknownRecovery_notifyAdmin() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.UNKNOWN);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.NOTIFY_ADMIN);
        assertThat(action.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("消息含empty completion分类为EMPTY_COMPLETION")
    void messageWithEmptyCompletion_categorizedAsEmptyCompletion() {
        ErrorCategory category = categorizer.categorize(
                new IllegalStateException("Agent empty completion: 模型返回空响应（无文本无工具调用）"));

        assertThat(category).isEqualTo(ErrorCategory.EMPTY_COMPLETION);
    }

    @Test
    @DisplayName("消息含返回null分类为EMPTY_COMPLETION")
    void messageWithReturnNull_categorizedAsEmptyCompletion() {
        ErrorCategory category = categorizer.categorize(
                new IllegalStateException("Agent同步执行返回null"));

        assertThat(category).isEqualTo(ErrorCategory.EMPTY_COMPLETION);
    }

    @Test
    @DisplayName("EMPTY_COMPLETION恢复策略为RETRY且可重试")
    void emptyCompletionRecovery_retry() {
        RecoveryAction action = categorizer.determineRecoveryAction(ErrorCategory.EMPTY_COMPLETION);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.RETRY);
        assertThat(action.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("null分类恢复策略为NOTIFY_ADMIN")
    void nullCategoryRecovery_notifyAdmin() {
        RecoveryAction action = categorizer.determineRecoveryAction(null);

        assertThat(action.getStrategy()).isEqualTo(RecoveryStrategy.NOTIFY_ADMIN);
        assertThat(action.isRetryable()).isFalse();
    }

    // 测试用异常类，类名包含关键词
    static class ModelTestException extends RuntimeException {
        ModelTestException(String message) { super(message); }
    }

    static class ToolTestException extends RuntimeException {
        ToolTestException(String message) { super(message); }
    }

    static class RateLimitTestException extends RuntimeException {
        RateLimitTestException(String message) { super(message); }
    }

    static class ContextOverflowTestException extends RuntimeException {
        ContextOverflowTestException(String message) { super(message); }
    }

    static class SocketTestException extends RuntimeException {
        SocketTestException(String message) { super(message); }
    }
}
