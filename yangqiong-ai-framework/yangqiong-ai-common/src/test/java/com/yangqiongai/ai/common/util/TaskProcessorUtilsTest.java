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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskProcessorUtils 单元测试")
class TaskProcessorUtilsTest {

    @Nested
    @DisplayName("getOrDefault 测试")
    class GetOrDefaultTest {

        @Test
        @DisplayName("null返回默认值")
        void shouldReturnDefaultForNull() {
            assertThat(StringUtils.getOrDefault(null, "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("空字符串返回默认值")
        void shouldReturnDefaultForEmpty() {
            assertThat(StringUtils.getOrDefault("", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("非空字符串返回原值")
        void shouldReturnOriginalForNonEmpty() {
            assertThat(StringUtils.getOrDefault("hello", "default")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("isBlank 测试")
    class IsBlankTest {

        @Test
        @DisplayName("null返回true")
        void shouldReturnTrueForNull() {
            assertThat(TaskProcessorUtils.isBlank(null)).isTrue();
        }

        @Test
        @DisplayName("空字符串返回true")
        void shouldReturnTrueForEmpty() {
            assertThat(TaskProcessorUtils.isBlank("")).isTrue();
        }

        @Test
        @DisplayName("纯空白字符串返回true")
        void shouldReturnTrueForWhitespace() {
            assertThat(TaskProcessorUtils.isBlank("   ")).isTrue();
            assertThat(TaskProcessorUtils.isBlank("\t\n")).isTrue();
        }

        @Test
        @DisplayName("非空白字符串返回false")
        void shouldReturnFalseForNonBlank() {
            assertThat(TaskProcessorUtils.isBlank("hello")).isFalse();
            assertThat(TaskProcessorUtils.isBlank(" hello ")).isFalse();
        }
    }

    @Nested
    @DisplayName("clampRagContext 测试")
    class ClampRagContextTest {

        @Test
        @DisplayName("null返回空字符串")
        void shouldReturnEmptyForNull() {
            assertThat(TaskProcessorUtils.clampRagContext(null, 100)).isEmpty();
        }

        @Test
        @DisplayName("短文本不截断")
        void shouldNotClampShortText() {
            assertThat(TaskProcessorUtils.clampRagContext("hello", 100)).isEqualTo("hello");
        }

        @Test
        @DisplayName("恰好等于limit不截断")
        void shouldNotClampExactLength() {
            String text = "a".repeat(100);
            assertThat(TaskProcessorUtils.clampRagContext(text, 100)).isEqualTo(text);
        }

        @Test
        @DisplayName("超出limit时截断")
        void shouldClampOverLimitText() {
            String text = "a".repeat(200);
            assertThat(TaskProcessorUtils.clampRagContext(text, 100)).hasSize(100);
            assertThat(TaskProcessorUtils.clampRagContext(text, 100)).isEqualTo("a".repeat(100));
        }

        @Test
        @DisplayName("maxChars<=0时默认为16000")
        void shouldDefaultTo16000WhenMaxCharsNonPositive() {
            String shortText = "hello";
            assertThat(TaskProcessorUtils.clampRagContext(shortText, 0)).isEqualTo("hello");
            assertThat(TaskProcessorUtils.clampRagContext(shortText, -1)).isEqualTo("hello");
        }

        @Test
        @DisplayName("maxChars<=0且文本超过16000时截断到16000")
        void shouldClampTo16000WhenMaxCharsNonPositive() {
            String longText = "a".repeat(20000);
            assertThat(TaskProcessorUtils.clampRagContext(longText, 0)).hasSize(16000);
            assertThat(TaskProcessorUtils.clampRagContext(longText, -1)).hasSize(16000);
        }
    }

    @Nested
    @DisplayName("resolveListMetadata 测试")
    class ResolveListMetadataTest {

        @Test
        @DisplayName("null body返回空列表")
        void shouldReturnEmptyListForNullBody() {
            assertThat(TaskProcessorUtils.resolveListMetadata(null, "key")).isEmpty();
        }

        @Test
        @DisplayName("缺少key返回空列表")
        void shouldReturnEmptyListForMissingKey() {
            Map<String, Object> body = Map.of("other", "value");
            assertThat(TaskProcessorUtils.resolveListMetadata(body, "missing")).isEmpty();
        }

        @Test
        @DisplayName("正确的列表值返回该列表")
        void shouldReturnListForCorrectKey() {
            List<String> expected = List.of("a", "b", "c");
            Map<String, Object> body = Map.of("items", expected);
            List<String> result = TaskProcessorUtils.resolveListMetadata(body, "items");
            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("值类型不是List时返回空列表")
        void shouldReturnEmptyListForWrongType() {
            Map<String, Object> body = Map.of("items", "not a list");
            assertThat(TaskProcessorUtils.resolveListMetadata(body, "items")).isEmpty();
        }
    }

    @Nested
    @DisplayName("resolveMetadataString 测试")
    class ResolveMetadataStringTest {

        @Test
        @DisplayName("null body返回空字符串")
        void shouldReturnEmptyForNullBody() {
            assertThat(TaskProcessorUtils.resolveMetadataString(null, "key")).isEmpty();
        }

        @Test
        @DisplayName("缺少key返回空字符串")
        void shouldReturnEmptyForMissingKey() {
            Map<String, Object> body = Map.of("other", "value");
            assertThat(TaskProcessorUtils.resolveMetadataString(body, "missing")).isEmpty();
        }

        @Test
        @DisplayName("存在的值返回其toString")
        void shouldReturnToStringForPresentValue() {
            Map<String, Object> body = Map.of("name", "testValue");
            assertThat(TaskProcessorUtils.resolveMetadataString(body, "name")).isEqualTo("testValue");
        }

        @Test
        @DisplayName("值为数字时返回数字字符串")
        void shouldReturnNumberToString() {
            Map<String, Object> body = Map.of("count", 42);
            assertThat(TaskProcessorUtils.resolveMetadataString(body, "count")).isEqualTo("42");
        }
    }
}
