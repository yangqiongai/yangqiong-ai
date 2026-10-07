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
package com.yangqiongai.ai.memory.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 对话指标监控
 * @author yangqiong
 */
@Component
public class ConversationMetrics {

    private static final String METRIC_SESSION_CREATED = "conversation.session.created";
    private static final String METRIC_SESSION_CLOSED = "conversation.session.closed";
    private static final String METRIC_SUMMARY_TRIGGERED = "conversation.summary.triggered";
    private static final String METRIC_SUMMARY_DURATION = "conversation.summary.duration";
    private static final String METRIC_SUMMARY_FAILED = "conversation.summary.failed";
    private static final String METRIC_TRIM_MESSAGES_KEPT = "conversation.trim.messages.kept";
    private static final String METRIC_TRIM_TOKEN_USAGE = "conversation.trim.token.usage";
    private static final String METRIC_LONG_TERM_MEMORY_RETRIEVE_COUNT = "memory.long-term.retrieve.count";
    private static final String METRIC_LONG_TERM_MEMORY_RECORD_COUNT = "memory.long-term.record.count";
    private static final String METRIC_LONG_TERM_MEMORY_RETRIEVE_DURATION = "memory.long-term.retrieve.duration";
    private static final String METRIC_LONG_TERM_MEMORY_RECORD_DURATION = "memory.long-term.record.duration";

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * 记录会话创建
     */
    public void incrementSessionCreated() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_SESSION_CREATED)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录会话关闭
     */
    public void incrementSessionClosed() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_SESSION_CLOSED)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录摘要触发
     */
    public void incrementSummaryTriggered() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_SUMMARY_TRIGGERED)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录摘要生成耗时
     * @param durationMillis
     */
    public void recordSummaryDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_SUMMARY_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * 记录摘要生成失败
     */
    public void incrementSummaryFailed() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_SUMMARY_FAILED)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录裁剪后保留消息数
     * @param count
     */
    public void recordTrimMessagesKept(int count) {
        if (meterRegistry == null) {
            return;
        }
        DistributionSummary.builder(METRIC_TRIM_MESSAGES_KEPT)
                .register(meterRegistry)
                .record(count);
    }

    /**
     * 记录裁剪Token使用量
     * @param usedTokens
     */
    public void recordTrimTokenUsage(int usedTokens) {
        if (meterRegistry == null) {
            return;
        }
        DistributionSummary.builder(METRIC_TRIM_TOKEN_USAGE)
                .register(meterRegistry)
                .record(usedTokens);
    }

    /**
     * LLM 主动记忆检索计数
     * @param success
     */
    public void incrementLongTermMemoryRetrieve(boolean success) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_LONG_TERM_MEMORY_RETRIEVE_COUNT)
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    /**
     * LLM 主动记忆记录计数
     * @param success
     */
    public void incrementLongTermMemoryRecord(boolean success) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_LONG_TERM_MEMORY_RECORD_COUNT)
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    /**
     * LLM 主动记忆检索耗时
     * @param durationMillis
     */
    public void recordLongTermMemoryRetrieveDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_LONG_TERM_MEMORY_RETRIEVE_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * LLM 主动记忆记录耗时
     * @param durationMillis
     */
    public void recordLongTermMemoryRecordDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_LONG_TERM_MEMORY_RECORD_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }
}
