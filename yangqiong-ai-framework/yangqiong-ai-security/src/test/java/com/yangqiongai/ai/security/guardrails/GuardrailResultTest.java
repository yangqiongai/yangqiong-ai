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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GuardrailResult 单元测试
 *
 * @author yangqiong
 */
@DisplayName("GuardrailResult 测试")
class GuardrailResultTest {

    @Nested
    @DisplayName("passed() 工厂方法")
    class PassedFactoryMethod {

        @Test
        @DisplayName("应返回通过的结果")
        void shouldReturnPassedResult() {
            GuardrailResult result = GuardrailResult.passed();

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getReason()).isNull();
            assertThat(result.getAction()).isEqualTo(GuardrailAction.ALLOW);
            assertThat(result.getGuardrailName()).isNull();
            assertThat(result.getHookPoint()).isNull();
            assertThat(result.getMaskedContent()).isNull();
        }
    }

    @Nested
    @DisplayName("blocked() 工厂方法")
    class BlockedFactoryMethod {

        @Test
        @DisplayName("应返回阻断的结果")
        void shouldReturnBlockedResult() {
            GuardrailResult result = GuardrailResult.blocked("注入检测");

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).isEqualTo("注入检测");
            assertThat(result.getAction()).isEqualTo(GuardrailAction.BLOCK);
        }

        @Test
        @DisplayName("带名称和挂载点的阻断结果")
        void shouldReturnBlockedResultWithDetails() {
            GuardrailResult result = GuardrailResult.blocked("拦截", "test-guard", HookPoint.INPUT);

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).isEqualTo("拦截");
            assertThat(result.getGuardrailName()).isEqualTo("test-guard");
            assertThat(result.getHookPoint()).isEqualTo(HookPoint.INPUT);
            assertThat(result.getAction()).isEqualTo(GuardrailAction.BLOCK);
        }
    }

    @Nested
    @DisplayName("masked() 工厂方法")
    class MaskedFactoryMethod {

        @Test
        @DisplayName("应返回脱敏结果")
        void shouldReturnMaskedResult() {
            GuardrailResult result = GuardrailResult.masked("138****5678", "pii", HookPoint.OUTPUT);

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getAction()).isEqualTo(GuardrailAction.MASK);
            assertThat(result.getMaskedContent()).isEqualTo("138****5678");
            assertThat(result.getGuardrailName()).isEqualTo("pii");
        }
    }
}
