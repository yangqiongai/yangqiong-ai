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
package com.yangqiongai.ai.agent.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ToolExecutionContext 单元测试")
class ToolExecutionContextTest {

    @Nested
    @DisplayName("setAttribute/getAttribute 测试")
    class AttributeTest {

        @Test
        @DisplayName("设置和获取属性正常工作")
        void shouldSetAndGetAttribute() {
            ToolExecutionContext context = new ToolExecutionContext();
            context.setAttribute("key1", "value1");
            context.setAttribute("key2", 42);

            assertThat(context.<String>getAttribute("key1")).isEqualTo("value1");
            assertThat(context.<Integer>getAttribute("key2")).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("sessionId和agentCode 测试")
    class SessionAndAgentCodeTest {

        @Test
        @DisplayName("sessionId getter/setter正常工作")
        void shouldSetAndGetSessionId() {
            ToolExecutionContext context = new ToolExecutionContext();
            context.setSessionId("session-1");

            assertThat(context.getSessionId()).isEqualTo("session-1");
        }

        @Test
        @DisplayName("agentCode getter/setter正常工作")
        void shouldSetAndGetAgentCode() {
            ToolExecutionContext context = new ToolExecutionContext();
            context.setAgentCode("task-1");

            assertThat(context.getAgentCode()).isEqualTo("task-1");
        }
    }

    @Nested
    @DisplayName("null key处理 测试")
    class NullKeyTest {

        @Test
        @DisplayName("获取不存在的key返回null")
        void shouldReturnNullForNonExistentKey() {
            ToolExecutionContext context = new ToolExecutionContext();

            Object value = context.getAttribute("nonexistent");
            assertThat(value).isNull();
        }
    }
}
