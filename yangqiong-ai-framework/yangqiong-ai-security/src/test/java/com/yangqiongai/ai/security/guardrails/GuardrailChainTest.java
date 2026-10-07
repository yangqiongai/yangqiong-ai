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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GuardrailChain 单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuardrailChain 测试")
class GuardrailChainTest {

    private GuardrailChain chain;

    @Mock
    private GuardrailManager guardrailManager;

    @Mock
    private Guardrail guardrail1;

    @Mock
    private Guardrail guardrail2;

    @BeforeEach
    void setUp() {
        chain = new GuardrailChain();
        // 通过反射注入guardrailManager
        try {
            var field = GuardrailChain.class.getDeclaredField("guardrailManager");
            field.setAccessible(true);
            field.set(chain, guardrailManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("INPUT挂载点测试")
    class InputHookPointTests {

        @Test
        @DisplayName("所有护栏通过时应返回passed")
        void shouldReturnPassedWhenAllPass() {
            when(guardrailManager.getEffectiveGuardrails(HookPoint.INPUT))
                    .thenReturn(List.of(guardrail1, guardrail2));
            when(guardrail1.check(any(), anyString(), any())).thenReturn(GuardrailResult.passed());
            when(guardrail2.check(any(), anyString(), any())).thenReturn(GuardrailResult.passed());

            GuardrailResult result = chain.check(HookPoint.INPUT, "正常输入", GuardrailContext.empty());

            assertThat(result.isPassed()).isTrue();
        }

        @Test
        @DisplayName("首个护栏阻断时应短路返回")
        void shouldShortCircuitOnBlock() {
            when(guardrailManager.getEffectiveGuardrails(HookPoint.INPUT))
                    .thenReturn(List.of(guardrail1, guardrail2));
            when(guardrail1.check(any(), anyString(), any()))
                    .thenReturn(GuardrailResult.blocked("注入检测", "g1", HookPoint.INPUT));

            GuardrailResult result = chain.check(HookPoint.INPUT, "恶意输入", GuardrailContext.empty());

            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).isEqualTo("注入检测");
            verify(guardrail2, never()).check(any(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("空护栏列表测试")
    class EmptyGuardrailsTests {

        @Test
        @DisplayName("无护栏时应返回passed")
        void shouldReturnPassedWhenNoGuardrails() {
            when(guardrailManager.getEffectiveGuardrails(HookPoint.OUTPUT))
                    .thenReturn(List.of());

            GuardrailResult result = chain.check(HookPoint.OUTPUT, "任意内容", GuardrailContext.empty());

            assertThat(result.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("MASK动作测试")
    class MaskActionTests {

        @Test
        @DisplayName("MASK动作应返回脱敏结果")
        void shouldReturnMaskedResult() {
            when(guardrailManager.getEffectiveGuardrails(HookPoint.OUTPUT))
                    .thenReturn(List.of(guardrail1));
            when(guardrail1.check(any(), anyString(), any()))
                    .thenReturn(GuardrailResult.masked("138****5678", "pii", HookPoint.OUTPUT));

            GuardrailResult result = chain.check(HookPoint.OUTPUT, "13812345678", GuardrailContext.empty());

            assertThat(result.isPassed()).isTrue();
            assertThat(result.getAction()).isEqualTo(GuardrailAction.MASK);
            assertThat(result.getMaskedContent()).isEqualTo("138****5678");
        }
    }

    @Nested
    @DisplayName("hasGuardrails测试")
    class HasGuardrailsTests {

        @Test
        @DisplayName("有护栏时返回true")
        void shouldReturnTrueWhenGuardrailsExist() {
            when(guardrailManager.getEffectiveGuardrails(HookPoint.INPUT))
                    .thenReturn(List.of(guardrail1));

            assertThat(chain.hasGuardrails(HookPoint.INPUT)).isTrue();
        }

        @Test
        @DisplayName("无护栏时返回false")
        void shouldReturnFalseWhenNoGuardrails() {
            when(guardrailManager.getEffectiveGuardrails(HookPoint.THINKING))
                    .thenReturn(List.of());

            assertThat(chain.hasGuardrails(HookPoint.THINKING)).isFalse();
        }
    }
}
