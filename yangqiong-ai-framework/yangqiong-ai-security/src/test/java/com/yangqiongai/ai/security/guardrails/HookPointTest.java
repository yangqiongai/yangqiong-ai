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
 * HookPoint 枚举测试
 *
 * @author yangqiong
 */
@DisplayName("HookPoint 测试")
class HookPointTest {

    @Nested
    @DisplayName("枚举值")
    class EnumValues {

        @Test
        @DisplayName("应有6个挂载点")
        void shouldHaveSixHookPoints() {
            assertThat(HookPoint.values()).hasSize(6);
        }

        @Test
        @DisplayName("应包含INPUT")
        void shouldContainInput() {
            assertThat(HookPoint.valueOf("INPUT")).isEqualTo(HookPoint.INPUT);
        }

        @Test
        @DisplayName("应包含SYSTEM_PROMPT")
        void shouldContainSystemPrompt() {
            assertThat(HookPoint.valueOf("SYSTEM_PROMPT")).isEqualTo(HookPoint.SYSTEM_PROMPT);
        }

        @Test
        @DisplayName("应包含THINKING")
        void shouldContainThinking() {
            assertThat(HookPoint.valueOf("THINKING")).isEqualTo(HookPoint.THINKING);
        }

        @Test
        @DisplayName("应包含TOOL_CALL")
        void shouldContainToolCall() {
            assertThat(HookPoint.valueOf("TOOL_CALL")).isEqualTo(HookPoint.TOOL_CALL);
        }

        @Test
        @DisplayName("应包含OUTPUT")
        void shouldContainOutput() {
            assertThat(HookPoint.valueOf("OUTPUT")).isEqualTo(HookPoint.OUTPUT);
        }

        @Test
        @DisplayName("应包含MODERATION")
        void shouldContainModeration() {
            assertThat(HookPoint.valueOf("MODERATION")).isEqualTo(HookPoint.MODERATION);
        }
    }
}
