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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PiiGuardrail 单元测试
 *
 * @author yangqiong
 */
@DisplayName("PiiGuardrail 测试")
class PiiGuardrailTest {

    private PiiGuardrail guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new PiiGuardrail();
    }

    @Nested
    @DisplayName("接口契约")
    class Contract {

        @Test
        @DisplayName("hookPoints应为OUTPUT")
        void shouldHaveOutputHookPoint() {
            assertThat(guardrail.hookPoints()).containsExactly(HookPoint.OUTPUT);
        }

        @Test
        @DisplayName("name应为builtin-pii-detection")
        void shouldHaveCorrectName() {
            assertThat(guardrail.name()).isEqualTo("builtin-pii-detection");
        }

        @Test
        @DisplayName("order应为10")
        void shouldHaveCorrectOrder() {
            assertThat(guardrail.order()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("PII泄露检测")
    class PiiDetection {

        @Test
        @DisplayName("包含手机号应被阻断")
        void shouldBlockPhoneNumber() {
            GuardrailResult result = guardrail.check(HookPoint.OUTPUT, "请联系13812345678", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).contains("手机号");
        }

        @Test
        @DisplayName("包含身份证号应被阻断")
        void shouldBlockIdNumber() {
            GuardrailResult result = guardrail.check(HookPoint.OUTPUT, "身份证号110101199001011234", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).contains("身份证号");
        }

        @Test
        @DisplayName("包含邮箱应被阻断")
        void shouldBlockEmail() {
            GuardrailResult result = guardrail.check(HookPoint.OUTPUT, "发送至test@example.com", GuardrailContext.empty());
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).contains("邮箱");
        }
    }

    @Nested
    @DisplayName("正常输出")
    class NormalOutput {

        @Test
        @DisplayName("普通文本应通过")
        void shouldPassNormalOutput() {
            GuardrailResult result = guardrail.check(HookPoint.OUTPUT, "本季度销售额增长了15%", GuardrailContext.empty());
            assertThat(result.isPassed()).isTrue();
        }
    }
}
