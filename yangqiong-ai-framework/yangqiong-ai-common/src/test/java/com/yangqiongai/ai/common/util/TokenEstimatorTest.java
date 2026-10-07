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
package com.yangqiongai.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenEstimator 单元测试")
class TokenEstimatorTest {

    @Nested
    @DisplayName("estimateTokens(String) 单文本估算")
    class EstimateTokensStringTest {

        @Test
        @DisplayName("null输入返回0")
        void shouldReturnZeroForNull() {
            assertThat(TokenEstimator.estimateTokens((String) null)).isEqualTo(0);
        }

        @Test
        @DisplayName("空字符串返回0")
        void shouldReturnZeroForEmpty() {
            assertThat(TokenEstimator.estimateTokens("")).isEqualTo(0);
        }

        @Test
        @DisplayName("纯中文文本估算")
        void shouldEstimateChineseText() {
            String text = "你好世界测试";
            int tokens = TokenEstimator.estimateTokens(text);
            // 6个中文字符 * 1.5 = 9.0, ceil = 9
            assertThat(tokens).isEqualTo((int) Math.ceil(6 * 1.5));
        }

        @Test
        @DisplayName("纯英文文本估算")
        void shouldEstimateEnglishText() {
            String text = "hello world test";
            int tokens = TokenEstimator.estimateTokens(text);
            // 3个英文单词 * 1.3 = 3.9, ceil = 4
            assertThat(tokens).isEqualTo((int) Math.ceil(3 * 1.3));
        }

        @Test
        @DisplayName("中英文混合文本估算")
        void shouldEstimateMixedText() {
            String text = "你好hello世界world";
            int tokens = TokenEstimator.estimateTokens(text);
            // 2个中文字符 * 1.5 + 2个英文单词 * 1.3 = 3.0 + 2.6 = 5.6, ceil = 6
            assertThat(tokens).isPositive();
        }

        @Test
        @DisplayName("估算结果应大于0（非空文本）")
        void shouldReturnPositiveForNonEmptyText() {
            assertThat(TokenEstimator.estimateTokens("a")).isPositive();
            assertThat(TokenEstimator.estimateTokens("你")).isPositive();
        }
    }

    @Nested
    @DisplayName("estimateTokens(List) 批量估算")
    class EstimateTokensListTest {

        @Test
        @DisplayName("null列表返回空列表")
        void shouldReturnEmptyListForNull() {
            assertThat(TokenEstimator.estimateTokens((List<String>) null)).isEmpty();
        }

        @Test
        @DisplayName("空列表返回空列表")
        void shouldReturnEmptyListForEmptyList() {
            assertThat(TokenEstimator.estimateTokens(List.of())).isEmpty();
        }

        @Test
        @DisplayName("批量估算返回与输入等长的结果列表")
        void shouldReturnSameSizeResults() {
            List<String> texts = List.of("你好", "hello", "你好world");
            List<Integer> results = TokenEstimator.estimateTokens(texts);

            assertThat(results).hasSize(3);
            assertThat(results.get(0)).isEqualTo(TokenEstimator.estimateTokens("你好"));
            assertThat(results.get(1)).isEqualTo(TokenEstimator.estimateTokens("hello"));
            assertThat(results.get(2)).isEqualTo(TokenEstimator.estimateTokens("你好world"));
        }

        @Test
        @DisplayName("列表中包含null元素时估算为0")
        void shouldHandleNullElementsInList() {
            List<String> texts = new java.util.ArrayList<>();
            texts.add("hello");
            texts.add(null);
            texts.add("世界");
            List<Integer> results = TokenEstimator.estimateTokens(texts);

            assertThat(results).hasSize(3);
            assertThat(results.get(1)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("isWithinBudget 预算检查")
    class IsWithinBudgetTest {

        @Test
        @DisplayName("在预算内返回true")
        void shouldReturnTrueWhenWithinBudget() {
            assertThat(TokenEstimator.isWithinBudget("hi", 1000)).isTrue();
        }

        @Test
        @DisplayName("超出预算返回false")
        void shouldReturnFalseWhenExceedingBudget() {
            String longText = "这是一个很长的文本用于测试超出预算的情况".repeat(100);
            assertThat(TokenEstimator.isWithinBudget(longText, 5)).isFalse();
        }

        @Test
        @DisplayName("恰好等于预算返回true")
        void shouldReturnTrueWhenExactlyAtBudget() {
            String text = "hello";
            int tokens = TokenEstimator.estimateTokens(text);
            assertThat(TokenEstimator.isWithinBudget(text, tokens)).isTrue();
        }

        @Test
        @DisplayName("null文本在预算内")
        void shouldReturnTrueForNullText() {
            assertThat(TokenEstimator.isWithinBudget(null, 0)).isTrue();
        }
    }
}
