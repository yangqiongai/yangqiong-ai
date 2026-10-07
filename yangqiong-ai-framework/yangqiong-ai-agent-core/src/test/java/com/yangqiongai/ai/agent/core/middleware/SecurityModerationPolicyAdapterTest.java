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
package com.yangqiongai.ai.agent.core.middleware;

import com.yangqiongai.ai.agent.runtime.guardrail.ModerationVerdict;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.security.guardrails.GuardrailResult;
import com.yangqiongai.ai.security.GuardrailsManager;
import com.yangqiongai.ai.security.guardrails.HookPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容审查策略适配器单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityModerationPolicyAdapter 单元测试")
class SecurityModerationPolicyAdapterTest {

    @Mock
    private GuardrailsManager guardrailsManager;

    private SecurityModerationPolicyAdapter adapter;

    /**
     * 构造适配器
     */
    @BeforeEach
    void setUp() {
        adapter = new SecurityModerationPolicyAdapter(guardrailsManager);
    }

    /**
     * 构造指定角色的文本消息
     * @param role
     * @param text
     * @return
     */
    private AgentMessage message(AgentMessageRole role, String text) {
        return AgentMessage.builder()
                .role(role)
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .build();
    }

    @Test
    @DisplayName("审查通过时应返回放行裁决")
    void moderatePasses() {
        when(guardrailsManager.check(eq(HookPoint.MODERATION), eq("正常内容"), any()))
                .thenReturn(GuardrailResult.passed());

        ModerationVerdict verdict = adapter.moderate(message(AgentMessageRole.USER, "正常内容"));

        assertThat(verdict.isPass()).isTrue();
    }

    @Test
    @DisplayName("审查命中阻断规则时应返回中止裁决并携带原因")
    void moderateBlocks() {
        when(guardrailsManager.check(eq(HookPoint.MODERATION), anyString(), any()))
                .thenReturn(GuardrailResult.blocked("命中敏感规则"));

        ModerationVerdict verdict = adapter.moderate(message(AgentMessageRole.ASSISTANT, "敏感内容"));

        assertThat(verdict.isBlock()).isTrue();
        assertThat(verdict.getReason()).isEqualTo("命中敏感规则");
    }

    @Test
    @DisplayName("空消息应直接放行且不触发护栏检查")
    void moderateSkipsNullMessage() {
        ModerationVerdict verdict = adapter.moderate(null);

        assertThat(verdict.isPass()).isTrue();
        verify(guardrailsManager, never()).check(any(), anyString(), any());
    }

    @Test
    @DisplayName("空白文本应直接放行且不触发护栏检查")
    void moderateSkipsBlankText() {
        ModerationVerdict verdict = adapter.moderate(message(AgentMessageRole.USER, "   "));

        assertThat(verdict.isPass()).isTrue();
        verify(guardrailsManager, never()).check(any(), anyString(), any());
    }
}
