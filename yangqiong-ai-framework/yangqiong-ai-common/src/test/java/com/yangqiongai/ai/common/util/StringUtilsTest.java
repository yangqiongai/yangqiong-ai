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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字符串工具测试
 * @author yangqiong
 */
@DisplayName("StringUtils 单元测试")
class StringUtilsTest {

    @Nested
    @DisplayName("getOrDefault(String) 测试")
    class GetOrDefaultSingleParamTest {

        @Test
        @DisplayName("null返回空串")
        void shouldReturnEmptyForNull() {
            assertThat(StringUtils.getOrDefault((String) null)).isEmpty();
        }

        @Test
        @DisplayName("非空字符串返回原值")
        void shouldReturnOriginalForNonEmpty() {
            assertThat(StringUtils.getOrDefault("hello")).isEqualTo("hello");
        }

        @Test
        @DisplayName("空字符串保持为空串")
        void shouldKeepEmptyString() {
            assertThat(StringUtils.getOrDefault("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getOrDefault(String, String) 测试")
    class GetOrDefaultTwoParamTest {

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
    @DisplayName("getOrDefault(Object) 测试")
    class GetOrDefaultObjectTest {

        @Test
        @DisplayName("null返回空串")
        void shouldReturnEmptyForNull() {
            assertThat(StringUtils.getOrDefault((Object) null)).isEmpty();
        }

        @Test
        @DisplayName("非null对象返回toString结果")
        void shouldReturnToStringForNonNull() {
            assertThat(StringUtils.getOrDefault(42)).isEqualTo("42");
            assertThat(StringUtils.getOrDefault("hello")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("isEmpty 测试")
    class IsEmptyTest {

        @Test
        @DisplayName("null返回true")
        void shouldReturnTrueForNull() {
            assertThat(StringUtils.isEmpty(null)).isTrue();
        }

        @Test
        @DisplayName("空串返回true")
        void shouldReturnTrueForEmpty() {
            assertThat(StringUtils.isEmpty("")).isTrue();
        }

        @Test
        @DisplayName("非空字符串返回false")
        void shouldReturnFalseForNonEmpty() {
            assertThat(StringUtils.isEmpty("hello")).isFalse();
        }
    }

    @Nested
    @DisplayName("isNotEmpty 测试")
    class IsNotEmptyTest {

        @Test
        @DisplayName("null返回false")
        void shouldReturnFalseForNull() {
            assertThat(StringUtils.isNotEmpty(null)).isFalse();
        }

        @Test
        @DisplayName("空串返回false")
        void shouldReturnFalseForEmpty() {
            assertThat(StringUtils.isNotEmpty("")).isFalse();
        }

        @Test
        @DisplayName("非空字符串返回true")
        void shouldReturnTrueForNonEmpty() {
            assertThat(StringUtils.isNotEmpty("hello")).isTrue();
        }
    }

    @Nested
    @DisplayName("isBlank 测试")
    class IsBlankTest {

        @Test
        @DisplayName("null返回true")
        void shouldReturnTrueForNull() {
            assertThat(StringUtils.isBlank(null)).isTrue();
        }

        @Test
        @DisplayName("空串返回true")
        void shouldReturnTrueForEmpty() {
            assertThat(StringUtils.isBlank("")).isTrue();
        }

        @Test
        @DisplayName("纯空白字符返回true")
        void shouldReturnTrueForWhitespace() {
            assertThat(StringUtils.isBlank("   ")).isTrue();
            assertThat(StringUtils.isBlank("\t\n")).isTrue();
        }

        @Test
        @DisplayName("有内容字符串返回false")
        void shouldReturnFalseForContent() {
            assertThat(StringUtils.isBlank("hello")).isFalse();
            assertThat(StringUtils.isBlank(" hello ")).isFalse();
        }
    }

    @Nested
    @DisplayName("truncate 测试")
    class TruncateTest {

        @Test
        @DisplayName("null返回空串")
        void shouldReturnEmptyForNull() {
            assertThat(StringUtils.truncate(null, 10)).isEmpty();
        }

        @Test
        @DisplayName("短字符串不截断")
        void shouldNotTruncateShortString() {
            assertThat(StringUtils.truncate("hello", 10)).isEqualTo("hello");
        }

        @Test
        @DisplayName("长字符串截断并加省略号")
        void shouldTruncateLongString() {
            assertThat(StringUtils.truncate("hello world", 8)).isEqualTo("hello...");
        }

        @Test
        @DisplayName("maxLength为0返回空串")
        void shouldReturnEmptyForZeroMaxLength() {
            assertThat(StringUtils.truncate("hello", 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("generateCompactId 测试")
    class GenerateCompactIdTest {

        @Test
        @DisplayName("返回32位无横线UUID")
        void shouldReturn32CharId() {
            String id = StringUtils.generateCompactId();
            assertThat(id).hasSize(32);
            assertThat(id).doesNotContain("-");
        }
    }

    @Nested
    @DisplayName("normalize 测试")
    class NormalizeTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(StringUtils.normalize(null)).isNull();
        }

        @Test
        @DisplayName("规范化内部空白")
        void shouldNormalizeInternalWhitespace() {
            assertThat(StringUtils.normalize("hello   world")).isEqualTo("hello world");
        }

        @Test
        @DisplayName("去除首尾空白")
        void shouldTrimWhitespace() {
            assertThat(StringUtils.normalize("  hello  ")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("safeToString 测试")
    class SafeToStringTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(StringUtils.safeToString(null)).isNull();
        }

        @Test
        @DisplayName("非null返回toString")
        void shouldReturnToStringForNonNull() {
            assertThat(StringUtils.safeToString(42)).isEqualTo("42");
        }
    }
}
