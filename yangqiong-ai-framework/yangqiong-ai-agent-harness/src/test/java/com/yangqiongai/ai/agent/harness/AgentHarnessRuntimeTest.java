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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer;
import com.yangqiongai.ai.agent.runtime.event.ConfirmResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentHarnessRuntime恢复链路桥接单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentHarnessRuntimeTest {

    @Mock
    private com.yangqiongai.agent.harness.core.AgentRuntime engineRuntime;

    private AgentHarnessRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new AgentHarnessRuntime(engineRuntime);
    }

    @Test
    @DisplayName("resumeWithClarification桥接应转换应答并透传引擎恢复调用")
    void resumeWithClarification_shouldConvertAnswersAndDelegate() {
        when(engineRuntime.resumeWithClarification(anyList(), any())).thenReturn(Flux.just(
                com.yangqiongai.agent.harness.core.event.AgentEvent.of(
                        com.yangqiongai.agent.harness.core.event.AgentEventType.AGENT_START, "demo")));
        List<AgentEvent> events = runtime.resumeWithClarification(
                List.of(new ClarificationAnswer("tc-1", "按月份统计")), null).collectList().block();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType())
                .isEqualTo(com.yangqiongai.ai.agent.runtime.event.AgentEventType.AGENT_START);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.yangqiongai.agent.harness.core.event.ClarificationAnswer>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(engineRuntime).resumeWithClarification(captor.capture(), any());
        List<com.yangqiongai.agent.harness.core.event.ClarificationAnswer> converted = captor.getValue();
        assertThat(converted).hasSize(1);
        assertThat(converted.get(0).toolCallId()).isEqualTo("tc-1");
        assertThat(converted.get(0).answer()).isEqualTo("按月份统计");
    }

    @Test
    @DisplayName("resumeWithClarification空应答列表桥接为空列表透传")
    void resumeWithClarification_emptyAnswers_shouldDelegateEmptyList() {
        when(engineRuntime.resumeWithClarification(anyList(), any())).thenReturn(Flux.empty());
        runtime.resumeWithClarification(List.of(), null).collectList().block();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.yangqiongai.agent.harness.core.event.ClarificationAnswer>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(engineRuntime).resumeWithClarification(captor.capture(), any());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("resumeWithClarification空入参不产生NPE并透传空列表")
    void resumeWithClarification_nullAnswers_shouldDelegateEmptyList() {
        when(engineRuntime.resumeWithClarification(anyList(), any())).thenReturn(Flux.empty());
        runtime.resumeWithClarification(null, null).collectList().block();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.yangqiongai.agent.harness.core.event.ClarificationAnswer>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(engineRuntime).resumeWithClarification(captor.capture(), any());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("resume桥接应透传工具调用ID给引擎")
    void resume_shouldKeepToolCallIdWhenDelegating() {
        when(engineRuntime.resume(anyList(), any())).thenReturn(Flux.just(
                com.yangqiongai.agent.harness.core.event.AgentEvent.of(
                        com.yangqiongai.agent.harness.core.event.AgentEventType.AGENT_START, "demo")));
        List<AgentEvent> events = runtime.resume(
                List.of(ConfirmResult.approveCall("tc-2", "search")), null).collectList().block();
        assertThat(events).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.yangqiongai.agent.harness.core.event.ConfirmResult>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(engineRuntime).resume(captor.capture(), any());
        List<com.yangqiongai.agent.harness.core.event.ConfirmResult> converted = captor.getValue();
        assertThat(converted).hasSize(1);
        assertThat(converted.get(0).getToolCallId()).isEqualTo("tc-2");
        assertThat(converted.get(0).getToolName()).isEqualTo("search");
        assertThat(converted.get(0).isApproved()).isTrue();
    }
}
