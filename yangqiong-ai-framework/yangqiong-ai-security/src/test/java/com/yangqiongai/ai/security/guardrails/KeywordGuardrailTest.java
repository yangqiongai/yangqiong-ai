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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KeywordGuardrail 单元测试
 *
 * @author yangqiong
 */
@DisplayName("KeywordGuardrail 测试")
class KeywordGuardrailTest {

    private KeywordGuardrail guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new KeywordGuardrail(
                "test-keyword",
                Set.of(HookPoint.INPUT),
                List.of("违禁品", "禁止项", "jailbreak"),
                50,
                "内容包含敏感关键词"
        );
    }

    @Nested
    @DisplayName("接口契约")
    class Contract {

        @Test
        @DisplayName("name应为test-keyword")
        void shouldHaveCorrectName() {
            assertThat(guardrail.name()).isEqualTo("test-keyword");
        }

        @Test
        @DisplayName("hookPoints应为INPUT")
        void shouldHaveInputHookPoint() {
            assertThat(guardrail.hookPoints()).containsExactly(HookPoint.INPUT);
        }

        @Test
        @DisplayName("order应为50")
        void shouldHaveCorrectOrder() {
            assertThat(guardrail.order()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("关键词匹配")
    class KeywordMatching {

        @Test
        @DisplayName("包含关键词应被拦截")
        void shouldBlockKeyword() {
            GuardrailResult result = guardrail.check(HookPoint.INPUT, "这个违禁品怎么样", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).isEqualTo("内容包含敏感关键词");
        }

        @Test
        @DisplayName("英文关键词大小写不敏感")
        void shouldMatchCaseInsensitive() {
            GuardrailResult result = guardrail.check(HookPoint.INPUT, "this is Jailbreak content", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
        }

        @Test
        @DisplayName("不包含关键词应通过")
        void shouldPassNormalInput() {
            GuardrailResult result = guardrail.check(HookPoint.INPUT, "这个产品怎么样", GuardrailContext.empty());
            assertThat(result.isPassed()).isTrue();
        }
    }
}
