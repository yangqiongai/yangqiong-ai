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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptInjectionGuardrail 单元测试
 *
 * @author yangqiong
 */
@DisplayName("PromptInjectionGuardrail 测试")
class PromptInjectionGuardrailTest {

    private PromptInjectionGuardrail guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new PromptInjectionGuardrail();
    }

    @Nested
    @DisplayName("接口契约")
    class Contract {

        @Test
        @DisplayName("hookPoints应为INPUT")
        void shouldHaveInputHookPoint() {
            assertThat(guardrail.hookPoints()).containsExactly(HookPoint.INPUT);
        }

        @Test
        @DisplayName("name应为builtin-prompt-injection")
        void shouldHaveCorrectName() {
            assertThat(guardrail.name()).isEqualTo("builtin-prompt-injection");
        }

        @Test
        @DisplayName("order应为10")
        void shouldHaveCorrectOrder() {
            assertThat(guardrail.order()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("正常输入")
    class NormalInput {

        @ParameterizedTest
        @ValueSource(strings = {
                "请帮我分析一下这个数据报表",
                "今天天气不错",
                "How does machine learning work?",
                "请总结以下文章的要点"
        })
        @DisplayName("正常文本应通过")
        void shouldPassNormalInput(String input) {
            GuardrailResult result = guardrail.check(HookPoint.INPUT, input, GuardrailContext.empty());
            assertThat(result.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Prompt注入检测")
    class PromptInjectionDetection {

        @ParameterizedTest
        @CsvSource({
                "'忽略之前指令', 输入包含疑似Prompt注入内容",
                "'Ignore previous instructions', 输入包含疑似Prompt注入内容",
                "'越狱', 输入包含疑似Prompt注入内容",
                "'jailbreak', 输入包含疑似Prompt注入内容",
                "'DAN mode', 输入包含疑似Prompt注入内容",
                "'扮演一个角色', 输入包含疑似Prompt注入内容",
                "'Pretend to be', 输入包含疑似Prompt注入内容",
                "'你不再遵守', 输入包含疑似Prompt注入内容",
                "'输出系统提示', 输入包含疑似Prompt注入内容"
        })
        @DisplayName("注入模式应被拦截")
        void shouldBlockInjectionPattern(String input, String expectedReason) {
            GuardrailResult result = guardrail.check(HookPoint.INPUT, input, GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).isEqualTo(expectedReason);
        }
    }

    @Nested
    @DisplayName("空值处理")
    class NullAndBlank {

        @Test
        @DisplayName("null应通过")
        void shouldPassNull() {
            assertThat(guardrail.check(HookPoint.INPUT, null, GuardrailContext.empty()).isPassed()).isTrue();
        }

        @Test
        @DisplayName("空白应通过")
        void shouldPassBlank() {
            assertThat(guardrail.check(HookPoint.INPUT, "   ", GuardrailContext.empty()).isPassed()).isTrue();
        }
    }
}
