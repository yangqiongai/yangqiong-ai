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
package com.yangqiongai.ai.agent.core.stream;

import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentThinkingBlockDeltaEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentToolCallDeltaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * StreamingAccumulator单元测试
 */
class StreamingAccumulatorTest {

    private StreamingAccumulator accumulator;

    @BeforeEach
    void setUp() {
        accumulator = new StreamingAccumulator();
    }

    @Test
    @DisplayName("初始状态：空visible answer、空reasoning、0次tool调用")
    void initialState_emptyValues() {
        assertThat(accumulator.getVisibleAnswer()).isEmpty();
        assertThat(accumulator.getReasoningContent()).isEmpty();
        assertThat(accumulator.getToolCallCount()).isEqualTo(0);
        assertThat(accumulator.getFirstTokenLatencyMs()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("处理TEXT_DELTA事件追加到visible answer")
    void processTextDelta_appendsToVisibleAnswer() {
        AgentTextBlockDeltaEvent event = mockTextBlockDeltaEvent("Hello ");

        accumulator.consume(event);

        assertThat(accumulator.getVisibleAnswer()).isEqualTo("Hello ");
    }

    @Test
    @DisplayName("处理THINKING_DELTA事件追加到reasoning content")
    void processThinkingDelta_appendsToReasoningContent() {
        AgentThinkingBlockDeltaEvent event = mockThinkingBlockDeltaEvent("thinking...");

        accumulator.consume(event);

        assertThat(accumulator.getReasoningContent()).isEqualTo("thinking...");
    }

    @Test
    @DisplayName("处理TOOL_CALL_DELTA事件增加tool调用计数")
    void processToolCallDelta_incrementsToolCallCount() {
        AgentToolCallDeltaEvent event = mockToolCallDeltaEvent();

        accumulator.consume(event);

        assertThat(accumulator.getToolCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("首Token延迟被记录")
    void firstTokenLatency_recorded() {
        AgentTextBlockDeltaEvent event = mockTextBlockDeltaEvent("first");

        accumulator.consume(event);

        assertThat(accumulator.getFirstTokenLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("多个事件正确累积")
    void multipleEvents_accumulatedCorrectly() {
        accumulator.consume(mockTextBlockDeltaEvent("Hello "));
        accumulator.consume(mockTextBlockDeltaEvent("World"));
        accumulator.consume(mockThinkingBlockDeltaEvent("thinking..."));
        accumulator.consume(mockToolCallDeltaEvent());
        accumulator.consume(mockToolCallDeltaEvent());

        assertThat(accumulator.getVisibleAnswer()).isEqualTo("Hello World");
        assertThat(accumulator.getReasoningContent()).isEqualTo("thinking...");
        assertThat(accumulator.getToolCallCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("consume null事件不做任何处理")
    void consume_nullEvent_noEffect() {
        accumulator.consume(null);

        assertThat(accumulator.getVisibleAnswer()).isEmpty();
        assertThat(accumulator.getReasoningContent()).isEmpty();
        assertThat(accumulator.getToolCallCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("TEXT_DELTA中的thinking标签内容被分离到reasoning")
    void textDelta_withThinkingTags_contentSeparated() {
        // 文本 "Hello" 不含thinking标签，直接进入visible
        accumulator.consume(mockTextBlockDeltaEvent("Hello"));

        assertThat(accumulator.getVisibleAnswer()).isEqualTo("Hello");
        assertThat(accumulator.getReasoningContent()).isEmpty();
    }

    @Test
    @DisplayName("Token计数随文本增量递增")
    void tokenCount_incrementsWithTextDelta() {
        accumulator.consume(mockTextBlockDeltaEvent("a"));
        accumulator.consume(mockTextBlockDeltaEvent("b"));

        assertThat(accumulator.getTokenCount()).isEqualTo(2);
    }

    /**
     * 创建模拟的TextBlockDeltaEvent
     */
    private AgentTextBlockDeltaEvent mockTextBlockDeltaEvent(String delta) {
        AgentTextBlockDeltaEvent event = mock(AgentTextBlockDeltaEvent.class);
        when(event.getType()).thenReturn(AgentEventType.TEXT_BLOCK_DELTA);
        when(event.getDelta()).thenReturn(delta);
        return event;
    }

    /**
     * 创建模拟的ThinkingBlockDeltaEvent
     */
    private AgentThinkingBlockDeltaEvent mockThinkingBlockDeltaEvent(String delta) {
        AgentThinkingBlockDeltaEvent event = mock(AgentThinkingBlockDeltaEvent.class);
        when(event.getType()).thenReturn(AgentEventType.THINKING_BLOCK_DELTA);
        when(event.getDelta()).thenReturn(delta);
        return event;
    }

    /**
     * 创建模拟的ToolCallDeltaEvent
     */
    private AgentToolCallDeltaEvent mockToolCallDeltaEvent() {
        AgentToolCallDeltaEvent event = mock(AgentToolCallDeltaEvent.class);
        when(event.getType()).thenReturn(AgentEventType.TOOL_CALL_DELTA);
        return event;
    }
}
