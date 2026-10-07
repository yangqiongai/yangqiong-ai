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
package com.yangqiongai.ai.common.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StreamEvent 单元测试")
class StreamEventTest {

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

        @Test
        @DisplayName("textDelta创建TEXT_DELTA类型事件")
        void shouldCreateTextDeltaEvent() {
            StreamEvent event = StreamEvent.textDelta("Hello");

            assertThat(event.getKind()).isEqualTo(StreamEvent.Kind.TEXT_DELTA);
            assertThat(event.getPayload()).isEqualTo("Hello");
        }

        @Test
        @DisplayName("thinkingDelta创建THINKING_DELTA类型事件")
        void shouldCreateThinkingDeltaEvent() {
            StreamEvent event = StreamEvent.thinkingDelta("思考中...");

            assertThat(event.getKind()).isEqualTo(StreamEvent.Kind.THINKING_DELTA);
            assertThat(event.getPayload()).isEqualTo("思考中...");
        }

        @Test
        @DisplayName("toolCallDelta创建TOOL_CALL_DELTA类型事件")
        void shouldCreateToolCallDeltaEvent() {
            StreamEvent event = StreamEvent.toolCallDelta("{\"name\":\"search\"}");

            assertThat(event.getKind()).isEqualTo(StreamEvent.Kind.TOOL_CALL_DELTA);
            assertThat(event.getPayload()).isEqualTo("{\"name\":\"search\"}");
        }
    }

    @Nested
    @DisplayName("类型判断方法测试")
    class TypeCheckTest {

        @Test
        @DisplayName("isTextDelta对TEXT_DELTA返回true")
        void isTextDeltaShouldReturnTrueForTextDelta() {
            StreamEvent event = StreamEvent.textDelta("text");

            assertThat(event.isTextDelta()).isTrue();
            assertThat(event.isThinkingDelta()).isFalse();
            assertThat(event.isToolCallDelta()).isFalse();
        }

        @Test
        @DisplayName("isThinkingDelta对THINKING_DELTA返回true")
        void isThinkingDeltaShouldReturnTrueForThinkingDelta() {
            StreamEvent event = StreamEvent.thinkingDelta("thinking");

            assertThat(event.isTextDelta()).isFalse();
            assertThat(event.isThinkingDelta()).isTrue();
            assertThat(event.isToolCallDelta()).isFalse();
        }

        @Test
        @DisplayName("isToolCallDelta对TOOL_CALL_DELTA返回true")
        void isToolCallDeltaShouldReturnTrueForToolCallDelta() {
            StreamEvent event = StreamEvent.toolCallDelta("tool");

            assertThat(event.isTextDelta()).isFalse();
            assertThat(event.isThinkingDelta()).isFalse();
            assertThat(event.isToolCallDelta()).isTrue();
        }
    }

    @Nested
    @DisplayName("payload保持测试")
    class PayloadTest {

        @Test
        @DisplayName("payload正确保持传入的值")
        void shouldPreservePayload() {
            String payload = "这是一段测试内容with mixed content 123!@#";
            StreamEvent event = StreamEvent.textDelta(payload);

            assertThat(event.getPayload()).isEqualTo(payload);
        }

        @Test
        @DisplayName("payload可以为null")
        void shouldAllowNullPayload() {
            StreamEvent event = StreamEvent.textDelta(null);

            assertThat(event.getPayload()).isNull();
        }

        @Test
        @DisplayName("payload可以为空字符串")
        void shouldAllowEmptyPayload() {
            StreamEvent event = StreamEvent.thinkingDelta("");

            assertThat(event.getPayload()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Kind枚举测试")
    class KindEnumTest {

        @Test
        @DisplayName("Kind枚举应恰好有7个值")
        void shouldHaveExactlySixValues() {
            assertThat(StreamEvent.Kind.values()).hasSize(7);
            assertThat(StreamEvent.Kind.values()).containsExactlyInAnyOrder(
                    StreamEvent.Kind.TEXT_DELTA,
                    StreamEvent.Kind.THINKING_DELTA,
                    StreamEvent.Kind.TOOL_CALL_DELTA,
                    StreamEvent.Kind.APPROVAL_REQUIRED,
                    StreamEvent.Kind.CLARIFICATION_REQUIRED,
                    StreamEvent.Kind.CONFIRM_REQUIRED,
                    StreamEvent.Kind.BUDGET_WARNING
            );
        }
    }

    @Nested
    @DisplayName("澄清与预算告警事件测试")
    class ClarificationAndBudgetTest {

        @Test
        @DisplayName("clarificationRequired创建CLARIFICATION_REQUIRED类型事件")
        void clarificationRequiredShouldCreateEvent() {
            String payload = "{\"question\":\"请确认查询范围\",\"toolCallId\":\"tc-1\"}";
            StreamEvent event = StreamEvent.clarificationRequired(payload);

            assertThat(event.getKind()).isEqualTo(StreamEvent.Kind.CLARIFICATION_REQUIRED);
            assertThat(event.getPayload()).isEqualTo(payload);
            assertThat(event.isClarificationRequired()).isTrue();
            assertThat(event.isTextDelta()).isFalse();
            assertThat(event.isBudgetWarning()).isFalse();
        }

        @Test
        @DisplayName("budgetWarning创建BUDGET_WARNING类型事件")
        void budgetWarningShouldCreateEvent() {
            String payload = "{\"budgetType\":\"TOKEN\",\"level\":\"EXCEEDED\",\"message\":\"Token预算超限中止\"}";
            StreamEvent event = StreamEvent.budgetWarning(payload);

            assertThat(event.getKind()).isEqualTo(StreamEvent.Kind.BUDGET_WARNING);
            assertThat(event.getPayload()).isEqualTo(payload);
            assertThat(event.isBudgetWarning()).isTrue();
            assertThat(event.isThinkingDelta()).isFalse();
            assertThat(event.isClarificationRequired()).isFalse();
        }

        @Test
        @DisplayName("澄清与预算告警payload可以为null")
        void clarificationAndBudgetShouldAllowNullPayload() {
            assertThat(StreamEvent.clarificationRequired(null).getPayload()).isNull();
            assertThat(StreamEvent.budgetWarning(null).getPayload()).isNull();
        }
    }
}
