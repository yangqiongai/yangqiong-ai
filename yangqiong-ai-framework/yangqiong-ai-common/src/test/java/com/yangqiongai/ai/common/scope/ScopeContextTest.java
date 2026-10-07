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
package com.yangqiongai.ai.common.scope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScopeContext 单元测试")
class ScopeContextTest {

    @AfterEach
    void cleanUp() {
        ScopeContext.clear();
    }

    @Nested
    @DisplayName("getScopeId 默认值测试")
    class DefaultValueTest {

        @Test
        @DisplayName("未设置时返回 default 而非 null")
        void shouldReturnDefaultWhenNotSet() {
            assertThat(ScopeContext.getScopeId()).isEqualTo("default");
        }

        @Test
        @DisplayName("未设置时永不返回 null")
        void shouldNeverReturnNull() {
            assertThat(ScopeContext.getScopeId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("setScopeId 设置值测试")
    class SetValueTest {

        @Test
        @DisplayName("设置后返回设置的值")
        void shouldReturnSetValue() {
            ScopeContext.setScopeId("scope-001");

            assertThat(ScopeContext.getScopeId()).isEqualTo("scope-001");
        }

        @Test
        @DisplayName("设置 null 后仍返回 default 而非 null")
        void shouldReturnDefaultWhenSetToNull() {
            ScopeContext.setScopeId(null);

            assertThat(ScopeContext.getScopeId()).isEqualTo("default");
        }

        @Test
        @DisplayName("多次设置以最后一次为准")
        void shouldReturnLatestSetValue() {
            ScopeContext.setScopeId("scope-001");
            ScopeContext.setScopeId("scope-002");

            assertThat(ScopeContext.getScopeId()).isEqualTo("scope-002");
        }
    }

    @Nested
    @DisplayName("clear 清除测试")
    class ClearTest {

        @Test
        @DisplayName("清除后返回 default")
        void shouldReturnDefaultAfterClear() {
            ScopeContext.setScopeId("scope-001");
            ScopeContext.clear();

            assertThat(ScopeContext.getScopeId()).isEqualTo("default");
        }

        @Test
        @DisplayName("未设置时清除不会抛出异常")
        void shouldNotThrowWhenClearWithoutSet() {
            ScopeContext.clear();

            assertThat(ScopeContext.getScopeId()).isEqualTo("default");
        }
    }
}
