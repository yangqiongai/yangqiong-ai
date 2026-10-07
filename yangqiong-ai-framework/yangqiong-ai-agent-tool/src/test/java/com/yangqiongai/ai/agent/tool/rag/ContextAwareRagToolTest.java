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
package com.yangqiongai.ai.agent.tool.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("ContextAwareRagTool 单元测试")
class ContextAwareRagToolTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTest {

        @Test
        @DisplayName("null contextProvider抛出IllegalArgumentException")
        void shouldThrowForNullContextProvider() {
            assertThatThrownBy(() -> new ContextAwareRagTool(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contextProvider不允许为空");
        }
    }

    @Nested
    @DisplayName("fetch_rag_context 测试")
    class FetchRagContextTest {

        @Test
        @DisplayName("有效查询返回上下文")
        void shouldReturnContextForValidQuery() {
            RagContextProvider provider = mock(RagContextProvider.class);
            when(provider.execute("测试查询", List.of("kb1"), List.of("doc1"), 5)).thenReturn("上下文结果");

            ContextAwareRagTool tool = new ContextAwareRagTool(provider);
            String result = tool.fetch_rag_context("测试查询", List.of("kb1"), List.of("doc1"));

            assertThat(result).isEqualTo("上下文结果");
        }

        @Test
        @DisplayName("null查询返回空字符串")
        void shouldReturnEmptyForNullQuery() {
            RagContextProvider provider = mock(RagContextProvider.class);
            ContextAwareRagTool tool = new ContextAwareRagTool(provider);

            String result = tool.fetch_rag_context(null, List.of("kb1"), List.of("doc1"));

            assertThat(result).isEmpty();
            verify(provider, never()).execute(anyString(), anyList(), anyList(), anyInt());
        }

        @Test
        @DisplayName("空查询返回空字符串")
        void shouldReturnEmptyForEmptyQuery() {
            RagContextProvider provider = mock(RagContextProvider.class);
            ContextAwareRagTool tool = new ContextAwareRagTool(provider);

            String result = tool.fetch_rag_context("   ", List.of("kb1"), List.of("doc1"));

            assertThat(result).isEmpty();
            verify(provider, never()).execute(anyString(), anyList(), anyList(), anyInt());
        }

        @Test
        @DisplayName("null kbIds/docIds使用空列表")
        void shouldUseEmptyListsForNullKbIdsAndDocIds() {
            RagContextProvider provider = mock(RagContextProvider.class);
            when(provider.execute("查询", List.of(), List.of(), 5)).thenReturn("结果");

            ContextAwareRagTool tool = new ContextAwareRagTool(provider);
            String result = tool.fetch_rag_context("查询", null, null);

            assertThat(result).isEqualTo("结果");
            verify(provider).execute("查询", List.of(), List.of(), 5);
        }
    }
}
