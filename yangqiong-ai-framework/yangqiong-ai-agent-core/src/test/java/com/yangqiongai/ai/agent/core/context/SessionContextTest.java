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
package com.yangqiongai.ai.agent.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SessionContext单元测试
 */
@DisplayName("SessionContext 作用域上下文测试")
class SessionContextTest {

    @AfterEach
    void cleanUp() {
        SessionContext.clear();
    }

    @Nested
    @DisplayName("scopeId ThreadLocal")
    class ScopeIdTest {

        @Test
        @DisplayName("未设置时getScopeId返回null")
        void shouldReturnNullWhenNotSet() {
            assertThat(SessionContext.getScopeId()).isNull();
        }

        @Test
        @DisplayName("设置后返回设置的值")
        void shouldReturnSetValue() {
            SessionContext.setScopeId("tenant-001");

            assertThat(SessionContext.getScopeId()).isEqualTo("tenant-001");
        }

        @Test
        @DisplayName("多次设置以最后一次为准")
        void shouldReturnLatestSetValue() {
            SessionContext.setScopeId("tenant-001");
            SessionContext.setScopeId("tenant-002");

            assertThat(SessionContext.getScopeId()).isEqualTo("tenant-002");
        }
    }

    @Nested
    @DisplayName("clear 清除")
    class ClearTest {

        @Test
        @DisplayName("clear后scopeId返回null")
        void shouldReturnNullAfterClear() {
            SessionContext.setScopeId("tenant-001");
            SessionContext.setUserId("user-001");
            SessionContext.setSessionId("session-001");

            SessionContext.clear();

            assertThat(SessionContext.getScopeId()).isNull();
            assertThat(SessionContext.getUserId()).isNull();
            assertThat(SessionContext.getSessionId()).isNull();
        }

        @Test
        @DisplayName("未设置时clear不会抛出异常")
        void shouldNotThrowWhenClearWithoutSet() {
            SessionContext.clear();

            assertThat(SessionContext.getScopeId()).isNull();
        }
    }
}
