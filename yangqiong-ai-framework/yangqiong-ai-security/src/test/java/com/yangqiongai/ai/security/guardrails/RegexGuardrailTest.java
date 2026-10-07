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
package com.yangqiongai.ai.security.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RegexGuardrail 单元测试
 *
 * @author yangqiong
 */
@DisplayName("RegexGuardrail 测试")
class RegexGuardrailTest {

    private RegexGuardrail guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new RegexGuardrail(
                "test-regex",
                Set.of(HookPoint.TOOL_CALL),
                Pattern.compile("(?i)(drop|delete|truncate)\\s+table"),
                30,
                "检测到危险SQL操作"
        );
    }

    @Nested
    @DisplayName("接口契约")
    class Contract {

        @Test
        @DisplayName("name应为test-regex")
        void shouldHaveCorrectName() {
            assertThat(guardrail.name()).isEqualTo("test-regex");
        }

        @Test
        @DisplayName("hookPoints应为TOOL_CALL")
        void shouldHaveToolCallHookPoint() {
            assertThat(guardrail.hookPoints()).containsExactly(HookPoint.TOOL_CALL);
        }

        @Test
        @DisplayName("order应为30")
        void shouldHaveCorrectOrder() {
            assertThat(guardrail.order()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("正则匹配")
    class RegexMatching {

        @Test
        @DisplayName("匹配正则应被拦截")
        void shouldBlockMatchingPattern() {
            GuardrailResult result = guardrail.check(HookPoint.TOOL_CALL, "DROP TABLE users", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).isEqualTo("检测到危险SQL操作");
        }

        @Test
        @DisplayName("不匹配正则应通过")
        void shouldPassNonMatching() {
            GuardrailResult result = guardrail.check(HookPoint.TOOL_CALL, "SELECT * FROM users", GuardrailContext.empty());
            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("大小写不敏感匹配")
        void shouldMatchCaseInsensitive() {
            GuardrailResult result = guardrail.check(HookPoint.TOOL_CALL, "delete table accounts", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
        }
    }
}
