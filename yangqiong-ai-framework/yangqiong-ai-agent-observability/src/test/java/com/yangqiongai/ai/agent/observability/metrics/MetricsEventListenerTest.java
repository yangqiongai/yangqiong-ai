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
package com.yangqiongai.ai.agent.observability.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.budget.ModelPricing;
import com.yangqiongai.ai.agent.runtime.budget.PricingProvider;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.ModelCallEndInfo;
import com.yangqiongai.ai.agent.runtime.event.ModelCallStartInfo;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

/**
 * 指标采集事件监听器测试
 * @author yangqiong
 */
class MetricsEventListenerTest {

    /**
     * 定价提供方：deepseek模型固定定价
     */
    private static final PricingProvider PRICING = modelCode ->
            "deepseek".equals(modelCode) ? new ModelPricing("deepseek", 0.001, 0.002) : null;

    @Test
    @DisplayName("仅白名单事件被消费")
    void onlyInterestedEventsConsumed() {
        MetricsEventListener listener = new MetricsEventListener(new SimpleMeterRegistry(), null);

        assertThat(listener.isInterestedIn(AgentEventType.AGENT_START)).isTrue();
        assertThat(listener.isInterestedIn(AgentEventType.MODEL_CALL_END)).isTrue();
        assertThat(listener.isInterestedIn(AgentEventType.TEXT_BLOCK_DELTA)).isFalse();
        assertThat(listener.isInterestedIn(AgentEventType.AGENT_RESULT)).isFalse();
    }

    @Test
    @DisplayName("运行开始到结束：计数、耗时与活跃会话归零")
    void runLifecycleRecordsRunMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, "writer"));
        assertThat(registry.get("agent.sessions.active").gauge().value()).isEqualTo(1.0);

        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_END, "writer"));

        assertThat(registry.get("agent.run.total").tag("agent", "writer")
                .tag("result", "completed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.sessions.active").gauge().value()).isZero();
        assertThat(registry.get("agent.run.duration")
                .tag("agent", "writer").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ERROR先于AGENT_END时运行归因为error")
    void errorAttributedToNextRunEnd() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, "writer"));
        listener.onEvent(AgentEvent.of(AgentEventType.ERROR, new RuntimeException("boom")));
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_END, "writer"));

        assertThat(registry.get("agent.run.total").tag("result", "error").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("模型调用配对：计数、延迟与Token用量")
    void modelCallPairingRecordsLatencyAndTokens() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_START,
                new ModelCallStartInfo("writer", "deepseek", 1)));
        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                new ModelCallEndInfo("writer", "deepseek", 1, 100, 50, 150)));

        assertThat(registry.get("agent.model.calls").tag("model", "deepseek").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("agent.model.latency").timer().count()).isEqualTo(1L);
        assertThat(registry.get("agent.tokens").tag("kind", "prompt").summary()
                .totalAmount()).isEqualTo(100.0);
        assertThat(registry.get("agent.tokens").tag("kind", "completion").summary()
                .totalAmount()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("定价命中时按Token换算累计成本")
    void modelCallRecordsCostWhenPricingPresent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, PRICING);

        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                new ModelCallEndInfo("writer", "deepseek", 1, 1000, 500, 1500)));

        assertThat(registry.get("agent.cost.usd").counter().count()).isEqualTo(0.002);
    }

    @Test
    @DisplayName("定价未命中时跳过成本指标")
    void modelCallSkipsCostWhenPricingMissing() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, PRICING);

        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_END,
                new ModelCallEndInfo("writer", "unknown-model", 1, 1000, 500, 1500)));

        assertThat(registry.getMeters().stream()
                .noneMatch(m -> m.getId().getName().equals("agent.cost.usd"))).isTrue();
    }

    @Test
    @DisplayName("工具调用以块列表载荷配对：计数与耗时")
    void toolCallBlockPayloadRecordsMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_START,
                List.of(new AgentToolUseBlock("web_search", "call-1", Map.of()))));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_END,
                List.of(AgentToolResultBlock.of("call-1",
                        List.of(AgentTextBlock.builder().text("ok").build())))));

        assertThat(registry.get("agent.tool.calls").tag("tool", "web_search").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("agent.tool.duration").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("工具调用以消息列表载荷配对：错误结果计入errors")
    void toolCallMessagePayloadRecordsErrors() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_START,
                List.of(new AgentToolUseBlock("shell", "call-2", Map.of()))));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_END,
                List.of(AgentMessage.builder()
                        .name("writer")
                        .role(AgentMessageRole.TOOL)
                        .content(List.of(AgentToolResultBlock.error("call-2", "timeout")))
                        .build())));

        assertThat(registry.get("agent.tool.errors").tag("tool", "shell").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.getMeters().stream()
                .noneMatch(m -> m.getId().getName().equals("agent.tool.calls"))).isTrue();
    }

    @Test
    @DisplayName("审批请求计数并支撑等待耗时统计")
    void approvalRequestRecordsWaitDuration() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, "writer"));
        listener.onEvent(AgentEvent.of(AgentEventType.REQUIRE_USER_CONFIRM, null));
        Thread.sleep(5);
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_END, "writer"));

        assertThat(registry.get("agent.approval.requested").tag("agent", "writer")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("agent.approval.wait.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("agent.approval.wait.duration").timer()
                .totalTime(TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    @DisplayName("INTERRUPTED先于AGENT_END时运行归因为interrupted")
    void interruptionAttributedToNextRunEnd() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, "writer"));
        listener.onEvent(AgentEvent.of(AgentEventType.INTERRUPTED, "用户取消"));
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_END, "writer"));

        assertThat(registry.get("agent.run.total").tag("result", "interrupted")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("空事件与非白名单载荷静默忽略")
    void ignoresInvalidEvents() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsEventListener listener = new MetricsEventListener(registry, null);

        listener.onEvent(null);
        listener.onEvent(AgentEvent.of(AgentEventType.AGENT_START, 12345));
        listener.onEvent(AgentEvent.of(AgentEventType.MODEL_CALL_START, "bad-payload"));
        listener.onEvent(AgentEvent.of(AgentEventType.TOOL_CALL_END, List.of("not-a-block")));

        assertThat(registry.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("agent.run")
                        || m.getId().getName().startsWith("agent.model")
                        || m.getId().getName().startsWith("agent.tool"))
                .toList()).isEmpty();
    }
}
