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

@DisplayName("AgentUtils 单元测试")
class AgentUtilsTest {

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
    @DisplayName("isEmpty 测试")
    class IsEmptyTest {

        @Test
        @DisplayName("null返回true")
        void shouldReturnTrueForNull() {
            assertThat(AgentUtils.isEmpty(null)).isTrue();
        }

        @Test
        @DisplayName("空字符串返回true")
        void shouldReturnTrueForEmpty() {
            assertThat(AgentUtils.isEmpty("")).isTrue();
        }

        @Test
        @DisplayName("非空字符串返回false")
        void shouldReturnFalseForNonEmpty() {
            assertThat(AgentUtils.isEmpty("hello")).isFalse();
        }

        @Test
        @DisplayName("空白字符串返回false（isEmpty不trim）")
        void shouldReturnFalseForWhitespace() {
            assertThat(AgentUtils.isEmpty(" ")).isFalse();
        }
    }

    @Nested
    @DisplayName("normalize 测试")
    class NormalizeTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(AgentUtils.normalize(null)).isNull();
        }

        @Test
        @DisplayName("纯空白字符串返回空字符串")
        void shouldReturnEmptyForWhitespaceOnly() {
            assertThat(AgentUtils.normalize("   ")).isEmpty();
        }

        @Test
        @DisplayName("去除首尾空白")
        void shouldTrimLeadingAndTrailingSpaces() {
            assertThat(AgentUtils.normalize("  hello  ")).isEqualTo("hello");
        }

        @Test
        @DisplayName("多个内部空白合并为单个空格")
        void shouldCollapseInternalSpaces() {
            assertThat(AgentUtils.normalize("hello   world  foo")).isEqualTo("hello world foo");
        }

        @Test
        @DisplayName("首尾空白和内部空白同时处理")
        void shouldHandleBothTrimAndCollapse() {
            assertThat(AgentUtils.normalize("  hello   world  ")).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("truncate 测试")
    class TruncateTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(AgentUtils.truncate(null, 10)).isNull();
        }

        @Test
        @DisplayName("短字符串不截断")
        void shouldNotTruncateShortString() {
            assertThat(AgentUtils.truncate("hi", 10)).isEqualTo("hi");
        }

        @Test
        @DisplayName("恰好等于maxLength不截断")
        void shouldNotTruncateExactLength() {
            assertThat(AgentUtils.truncate("hello", 5)).isEqualTo("hello");
        }

        @Test
        @DisplayName("超出maxLength时添加省略号（maxLength>3）")
        void shouldTruncateWithEllipsis() {
            assertThat(AgentUtils.truncate("hello world", 8)).isEqualTo("hello...");
        }

        @Test
        @DisplayName("maxLength<=0返回空字符串")
        void shouldReturnEmptyForNonPositiveMaxLength() {
            assertThat(AgentUtils.truncate("hello", 0)).isEmpty();
            assertThat(AgentUtils.truncate("hello", -1)).isEmpty();
        }

        @Test
        @DisplayName("maxLength<=3时直接截断不加省略号")
        void shouldTruncateWithoutEllipsisWhenSmallMaxLength() {
            assertThat(AgentUtils.truncate("hello", 3)).isEqualTo("hel");
            assertThat(AgentUtils.truncate("hello", 2)).isEqualTo("he");
            assertThat(AgentUtils.truncate("hello", 1)).isEqualTo("h");
        }
    }

    @Nested
    @DisplayName("safeToString 测试")
    class SafeToStringTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(AgentUtils.safeToString(null)).isNull();
        }

        @Test
        @DisplayName("非null返回toString结果")
        void shouldReturnToStringForNonNull() {
            assertThat(AgentUtils.safeToString(42)).isEqualTo("42");
            assertThat(AgentUtils.safeToString("hello")).isEqualTo("hello");
        }
    }
}
