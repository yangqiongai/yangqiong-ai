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

import com.yangqiongai.ai.agent.core.AgentResultConverter;
import com.yangqiongai.ai.agent.core.bootstrap.ImageInputSupportChecker;
import com.yangqiongai.ai.agent.core.circuit.AgentCircuitBreaker;
import com.yangqiongai.ai.agent.core.circuit.FallbackModelResolver;
import com.yangqiongai.ai.agent.core.circuit.RetryWithBackoff;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.event.ApprovalEventBridge;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.session.ConversationBridge;
import com.yangqiongai.ai.agent.core.executor.ClarificationPendingRegistry;
import com.yangqiongai.ai.agent.core.stream.InterruptControlRegistry;
import com.yangqiongai.ai.agent.core.task.StepRecordingManager;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer;
import com.yangqiongai.ai.agent.core.trace.TraceCollector;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.AgentResultEvent;
import com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ReAct执行器澄清与预算告警SSE映射单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class ReActAgentExecutorSseMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private TraceCollector traceCollector;

    @Mock
    private ConversationBridge conversationBridge;

    @Mock
    private ApprovalEventBridge approvalEventBridge;

    @Mock
    private AgentCircuitBreaker circuitBreaker;

    @Mock
    private RetryWithBackoff retryWithBackoff;

    @Mock
    private FallbackModelResolver fallbackModelResolver;

    @Mock
    private AgentModelFactory agentModelFactory;

    @Mock
    private ErrorCategorizer errorCategorizer;

    @Mock
    private InterruptControlRegistry interruptControlRegistry;

    @Mock
    private StepRecordingManager stepRecordingManager;

    @Mock
    private ImageInputSupportChecker imageInputSupportChecker;

    @Mock
    private ObjectProvider<com.yangqiongai.ai.agent.runtime.budget.UsageListener> usageListenerProvider;

    private ReActAgentExecutor executor;

    @BeforeEach
    void setUp() {
        InMemoryPendingResumeStore pendingResumeStore = new InMemoryPendingResumeStore();
        executor = new ReActAgentExecutor(traceCollector, conversationBridge, approvalEventBridge,
                circuitBreaker, retryWithBackoff, fallbackModelResolver, agentModelFactory,
                errorCategorizer, interruptControlRegistry, new ClarificationPendingRegistry(pendingResumeStore),
                new ConfirmPendingRegistry(pendingResumeStore), stepRecordingManager,
                imageInputSupportChecker, usageListenerProvider);
    }

    /**
     * 构建流式执行上下文，executionAgent为入参运行时
     */
    private AgentContext buildContext(AgentRuntime agent) {
        AgentContext context = new AgentContext(new AgentRequest().sessionId("sse-test"));
        context.setAttribute(AgentContext.CTX_EXECUTION_AGENT, agent);
        context.setAttribute(AgentContext.CTX_INPUTS, List.of(AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text("你好").build()))
                .build()));
        context.setAttribute(AgentContext.CTX_RESULT_CONVERTER,
                (AgentResultConverter) (ctx, answer, usage, msg) -> AgentResult.success("ok"));
        lenient().when(conversationBridge.restoreSession(any())).thenReturn(List.of());
        return context;
    }

    /**
     * 构建带最终消息的事件流，避免空响应触发重试
     */
    private Flux<AgentEvent> eventsWithResult(AgentEvent... prefix) {
        AgentMessage finalMsg = AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentTextBlock.builder().text("最终回答").build()))
                .build();
        return Flux.concat(Flux.just(prefix), Flux.just(new AgentResultEvent(finalMsg)));
    }

    @Test
    @DisplayName("澄清事件映射为CLARIFICATION_REQUIRED且payload含question与toolCallId")
    void streamExecute_clarificationEvent_shouldMapToSse() throws Exception {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                new RequireUserClarificationEvent("请确认查询时间范围", "tc-1")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        StreamEvent clarification = events.stream()
                .filter(e -> e.getKind() == StreamEvent.Kind.CLARIFICATION_REQUIRED)
                .findFirst().orElseThrow();
        JsonNode payload = MAPPER.readTree(clarification.getPayload());
        assertThat(payload.get("question").asText()).isEqualTo("请确认查询时间范围");
        assertThat(payload.get("toolCallId").asText()).isEqualTo("tc-1");
    }

    @Test
    @DisplayName("非类型化澄清事件payload降级为question")
    void streamExecute_plainClarificationEvent_shouldUsePayloadAsQuestion() throws Exception {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                AgentEvent.of(AgentEventType.REQUIRE_USER_CLARIFICATION, "要使用哪个数据源？")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        StreamEvent clarification = events.stream()
                .filter(e -> e.getKind() == StreamEvent.Kind.CLARIFICATION_REQUIRED)
                .findFirst().orElseThrow();
        JsonNode payload = MAPPER.readTree(clarification.getPayload());
        assertThat(payload.get("question").asText()).isEqualTo("要使用哪个数据源？");
    }

    @Test
    @DisplayName("Token预算告警映射为BUDGET_WARNING且budgetType为TOKEN")
    void streamExecute_tokenBudgetWarn_shouldMapWithTokenType() throws Exception {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                AgentEvent.of(AgentEventType.TOKEN_BUDGET_WARN, "Token预算告警: used=900, warnThreshold=800")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        StreamEvent warning = events.stream()
                .filter(e -> e.getKind() == StreamEvent.Kind.BUDGET_WARNING)
                .findFirst().orElseThrow();
        JsonNode payload = MAPPER.readTree(warning.getPayload());
        assertThat(payload.get("budgetType").asText()).isEqualTo("TOKEN");
        assertThat(payload.get("level").asText()).isEqualTo("WARN");
        assertThat(payload.get("message").asText()).contains("Token预算告警");
    }

    @Test
    @DisplayName("Token预算超限映射level为EXCEEDED")
    void streamExecute_tokenBudgetExceeded_shouldMapExceededLevel() throws Exception {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                AgentEvent.of(AgentEventType.TOKEN_BUDGET_EXCEEDED, "Token预算超硬上限: used=2000")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        StreamEvent warning = events.stream()
                .filter(e -> e.getKind() == StreamEvent.Kind.BUDGET_WARNING)
                .findFirst().orElseThrow();
        JsonNode payload = MAPPER.readTree(warning.getPayload());
        assertThat(payload.get("budgetType").asText()).isEqualTo("TOKEN");
        assertThat(payload.get("level").asText()).isEqualTo("EXCEEDED");
    }

    @Test
    @DisplayName("成本预算超限映射budgetType为COST")
    void streamExecute_costBudgetExceeded_shouldMapCostType() throws Exception {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                AgentEvent.of(AgentEventType.COST_BUDGET_EXCEEDED, "成本预算超限中止: used=$5.0")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        StreamEvent warning = events.stream()
                .filter(e -> e.getKind() == StreamEvent.Kind.BUDGET_WARNING)
                .findFirst().orElseThrow();
        JsonNode payload = MAPPER.readTree(warning.getPayload());
        assertThat(payload.get("budgetType").asText()).isEqualTo("COST");
        assertThat(payload.get("level").asText()).isEqualTo("EXCEEDED");
    }

    @Test
    @DisplayName("成本预算告警映射budgetType为COST且level为WARN")
    void streamExecute_costBudgetWarn_shouldMapCostWarn() throws Exception {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                AgentEvent.of(AgentEventType.COST_BUDGET_WARN, "成本预算告警: used=$0.9")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        StreamEvent warning = events.stream()
                .filter(e -> e.getKind() == StreamEvent.Kind.BUDGET_WARNING)
                .findFirst().orElseThrow();
        JsonNode payload = MAPPER.readTree(warning.getPayload());
        assertThat(payload.get("budgetType").asText()).isEqualTo("COST");
        assertThat(payload.get("level").asText()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("预算超限场景run正常完成不因事件转换失败")
    void streamExecute_budgetExceeded_runShouldCompleteNormally() {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                new com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent("部分回答"),
                AgentEvent.of(AgentEventType.TOKEN_BUDGET_EXCEEDED, "Token预算超限中止")));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        // 预算告警之前文本增量已正常推送，说明run未因预算事件中断
        assertThat(events.stream().anyMatch(StreamEvent::isTextDelta)).isTrue();
        assertThat(events.stream().anyMatch(StreamEvent::isBudgetWarning)).isTrue();
    }

    @Test
    @DisplayName("CUSTOM事件默认忽略不出现在SSE流")
    void streamExecute_customEvent_shouldBeIgnored() {
        AgentRuntime agent = runtimeOf(eventsWithResult(
                new com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent("部分回答"),
                AgentEvent.custom("EXCEED_MAX_ITERS", null)));
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        assertThat(events.stream().anyMatch(e -> e.getKind() == StreamEvent.Kind.BUDGET_WARNING)).isFalse();
        assertThat(events.stream().anyMatch(e -> e.getKind() == StreamEvent.Kind.CLARIFICATION_REQUIRED)).isFalse();
        assertThat(events.stream().anyMatch(StreamEvent::isTextDelta)).isTrue();
    }

    @Test
    @DisplayName("流式空响应重试应重新执行Agent而非重放已终结流")
    void streamExecute_emptyResponse_shouldReallyRetryAgent() {
        AtomicInteger invocations = new AtomicInteger(0);
        AgentRuntime agent = new AgentRuntime() {

            @Override
            public reactor.core.publisher.Mono<AgentMessage> call(
                    List<AgentMessage> inputs, AgentRuntimeContext context) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public Flux<AgentEvent> streamEvents(List<AgentMessage> inputs, AgentRuntimeContext context) {
                // 第一次订阅返回空流模拟模型空响应，重试后返回正常回答
                if (invocations.incrementAndGet() == 1) {
                    return Flux.empty();
                }
                return eventsWithResult(new com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent("重试后的回答"));
            }

            @Override
            public String getName() {
                return "stub-runtime";
            }
        };
        List<StreamEvent> events = executor.streamExecute(buildContext(agent)).collectList().block();
        assertThat(events).isNotNull();
        assertThat(invocations.get()).isEqualTo(2);
        assertThat(events.stream().anyMatch(StreamEvent::isTextDelta)).isTrue();
    }

    /**
     * 构建流式事件由入参事件流提供的测试桩运行时
     */
    private AgentRuntime runtimeOf(Flux<AgentEvent> events) {
        return new AgentRuntime() {

            @Override
            public reactor.core.publisher.Mono<AgentMessage> call(
                    List<AgentMessage> inputs, AgentRuntimeContext context) {
                return reactor.core.publisher.Mono.empty();
            }

            @Override
            public Flux<AgentEvent> streamEvents(List<AgentMessage> inputs, AgentRuntimeContext context) {
                return events;
            }

            @Override
            public String getName() {
                return "stub-runtime";
            }
        };
    }
}
