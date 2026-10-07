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
package com.yangqiongai.ai.memory.memory;

import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.metrics.ConversationMetrics;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HistoryTrimmer 单元测试")
class HistoryTrimmerTest {

    private final HistoryTrimmer trimmer = createTrimmer();

    private static HistoryTrimmer createTrimmer() {
        HistoryTrimmer t = new HistoryTrimmer();
        injectField(t, "conversationMetrics", new ConversationMetrics());
        return t;
    }

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private ChatMemoryRecord buildMemory(String role, String content, Integer tokenCount) {
        ChatMemoryRecord memory = new ChatMemoryRecord();
        memory.setMessageRole(role);
        memory.setMessageContent(content);
        memory.setTokenCount(tokenCount);
        return memory;
    }

    private ChatMemoryRecord buildMemory(String role, String content) {
        ChatMemoryRecord memory = new ChatMemoryRecord();
        memory.setMessageRole(role);
        memory.setMessageContent(content);
        // 不设置tokenCount，让resolveTokenCount使用TokenEstimator估算
        return memory;
    }

    @Nested
    @DisplayName("trim 测试")
    class TrimTest {

        @Test
        @DisplayName("空列表：返回空列表")
        void shouldReturnEmptyListForEmptyInput() {
            List<ChatMemoryRecord> result = trimmer.trim(Collections.emptyList(), 1000, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null列表：返回空列表")
        void shouldReturnEmptyListForNullInput() {
            List<ChatMemoryRecord> result = trimmer.trim(null, 1000, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("在预算内：返回所有消息")
        void shouldReturnAllMessagesWhenWithinBudget() {
            List<ChatMemoryRecord> messages = List.of(
                    buildMemory("user", "你好", 10),
                    buildMemory("assistant", "你好！", 10)
            );

            List<ChatMemoryRecord> result = trimmer.trim(messages, 1000, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("超出预算：从最旧消息开始裁剪，保留系统消息")
        void shouldTrimFromOldestAndKeepSystemMessages() {
            List<ChatMemoryRecord> messages = new ArrayList<>();
            messages.add(buildMemory("system", "你是一个助手", 10));
            messages.add(buildMemory("user", "第一条消息", 40));
            messages.add(buildMemory("assistant", "第一条回复", 40));
            messages.add(buildMemory("user", "第二条消息", 40));
            messages.add(buildMemory("assistant", "第二条回复", 40));

            // 预算：系统消息10 + 最多保留2条普通消息(80) = 90
            List<ChatMemoryRecord> result = trimmer.trim(messages, 90, null);

            // 系统消息应保留
            assertThat(result.stream().filter(m -> "system".equals(m.getMessageRole())).count()).isEqualTo(1);
            // 应裁剪掉最旧的消息，保留最新的
            assertThat(result.size()).isLessThan(messages.size());
            // 最新的消息应保留
            assertThat(result.get(result.size() - 1).getMessageContent()).isEqualTo("第二条回复");
        }

        @Test
        @DisplayName("带摘要：摘要按1.2倍系数计算Token")
        void shouldCountSummaryWithFactor() {
            String summary = "这是一个较长的摘要内容用于测试摘要Token系数";
            int summaryTokens = TokenEstimator.estimateTokens(summary);
            int expectedSummaryCost = (int) Math.ceil(summaryTokens * 1.2);

            List<ChatMemoryRecord> messages = new ArrayList<>();
            messages.add(buildMemory("user", "你好", 10));
            messages.add(buildMemory("assistant", "你好！", 10));

            // 预算刚好够摘要+系统消息，但不够普通消息
            int budget = expectedSummaryCost + 5; // 不够普通消息
            List<ChatMemoryRecord> result = trimmer.trim(messages, budget, summary);

            // 应包含摘要系统消息
            assertThat(result.stream()
                    .anyMatch(m -> "system".equals(m.getMessageRole())
                            && m.getMessageContent().contains("Summary from previous conversation")))
                    .isTrue();
        }

        @Test
        @DisplayName("带摘要：摘要消息添加在系统消息之后")
        void shouldAddSummaryAfterSystemMessages() {
            String summary = "这是摘要";
            List<ChatMemoryRecord> messages = List.of(
                    buildMemory("user", "你好", 10),
                    buildMemory("assistant", "你好！", 10)
            );

            List<ChatMemoryRecord> result = trimmer.trim(messages, 1000, summary);

            // 找到摘要消息的位置
            int summaryIndex = -1;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).getMessageContent().contains("Summary from previous conversation")) {
                    summaryIndex = i;
                    break;
                }
            }
            assertThat(summaryIndex).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("trimByTokenBudget 测试")
    class TrimByTokenBudgetTest {

        @Test
        @DisplayName("空列表：返回空列表")
        void shouldReturnEmptyListForEmptyInput() {
            List<ChatMemoryRecord> result = trimmer.trimByTokenBudget(Collections.emptyList(), "hello", 1000);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("在预算内：返回所有消息")
        void shouldReturnAllMessagesWhenWithinBudget() {
            List<ChatMemoryRecord> messages = List.of(
                    buildMemory("user", "你好", 10),
                    buildMemory("assistant", "你好！", 10)
            );

            List<ChatMemoryRecord> result = trimmer.trimByTokenBudget(messages, "hello", 1000);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("超出预算：裁剪最旧消息，保留系统消息")
        void shouldTrimOldestAndKeepSystemMessages() {
            List<ChatMemoryRecord> messages = new ArrayList<>();
            messages.add(buildMemory("system", "系统提示", 10));
            messages.add(buildMemory("user", "第一条", 40));
            messages.add(buildMemory("assistant", "第二条", 40));
            messages.add(buildMemory("user", "第三条", 40));

            // 预算：系统10 + 只能容纳1条普通消息
            List<ChatMemoryRecord> result = trimmer.trimByTokenBudget(messages, "hello", 50);

            // 系统消息保留
            assertThat(result.stream().filter(m -> "system".equals(m.getMessageRole())).count()).isEqualTo(1);
            // 最新消息保留
            assertThat(result.get(result.size() - 1).getMessageContent()).isEqualTo("第三条");
        }

        @Test
        @DisplayName("无摘要：不添加摘要系统消息")
        void shouldNotAddSummaryWhenNull() {
            List<ChatMemoryRecord> messages = List.of(
                    buildMemory("user", "你好", 10),
                    buildMemory("assistant", "你好！", 10)
            );

            List<ChatMemoryRecord> result = trimmer.trimByTokenBudget(messages, "hello", 1000);

            // 不应包含摘要消息
            assertThat(result.stream()
                    .noneMatch(m -> m.getMessageContent() != null
                            && m.getMessageContent().contains("Summary from previous conversation")))
                    .isTrue();
        }
    }
}
