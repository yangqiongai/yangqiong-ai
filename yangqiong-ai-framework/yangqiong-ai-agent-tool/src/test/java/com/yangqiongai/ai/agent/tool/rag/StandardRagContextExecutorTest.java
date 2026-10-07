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

import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.RagRetrieveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("StandardRagContextExecutor 单元测试")
class StandardRagContextExecutorTest {

    @Nested
    @DisplayName("execute 测试")
    class ExecuteTest {

        @Test
        @DisplayName("null查询返回空字符串")
        void shouldReturnEmptyForNullQuery() {
            RagRetrieveService ragService = mock(RagRetrieveService.class);
            StandardRagContextExecutor executor = new StandardRagContextExecutor(ragService);

            String result = executor.execute(null, List.of("kb1"), List.of("doc1"), 5);

            assertThat(result).isEmpty();
            verifyNoInteractions(ragService);
        }

        @Test
        @DisplayName("空白查询返回空字符串")
        void shouldReturnEmptyForBlankQuery() {
            RagRetrieveService ragService = mock(RagRetrieveService.class);
            StandardRagContextExecutor executor = new StandardRagContextExecutor(ragService);

            String result = executor.execute("   ", List.of("kb1"), List.of("doc1"), 5);

            assertThat(result).isEmpty();
            verifyNoInteractions(ragService);
        }

        @Test
        @DisplayName("正确格式化证据")
        void shouldFormatEvidenceCorrectly() {
            RagRetrieveService ragService = mock(RagRetrieveService.class);
            RetrievalEvidence evidence = new RetrievalEvidence("这是内容", "doc1", null, "slice1", 0.95, null);
            when(ragService.retrieve("查询", List.of("kb1"), List.of("doc1"), 5))
                    .thenReturn(List.of(evidence));

            StandardRagContextExecutor executor = new StandardRagContextExecutor(ragService);
            String result = executor.execute("查询", List.of("kb1"), List.of("doc1"), 5);

            assertThat(result).contains("doc1").contains("slice1").contains("0.9500").contains("这是内容");
        }
    }

    @Nested
    @DisplayName("retrieve 测试")
    class RetrieveTest {

        @Test
        @DisplayName("委托给ragRetrieveService")
        void shouldDelegateToRagRetrieveService() {
            RagRetrieveService ragService = mock(RagRetrieveService.class);
            RetrievalEvidence evidence = new RetrievalEvidence("结果", null, null, null, 0.0, null);
            when(ragService.retrieve("查询", List.of("kb1"), List.of(), 5))
                    .thenReturn(List.of(evidence));

            StandardRagContextExecutor executor = new StandardRagContextExecutor(ragService);
            List<RetrievalEvidence> result = executor.retrieve("查询", List.of("kb1"), List.of(), 5);

            assertThat(result).hasSize(1);
            verify(ragService).retrieve("查询", List.of("kb1"), List.of(), 5);
        }

        @Test
        @DisplayName("异常时返回空列表")
        void shouldReturnEmptyListOnException() {
            RagRetrieveService ragService = mock(RagRetrieveService.class);
            when(ragService.retrieve("查询", List.of("kb1"), List.of(), 5))
                    .thenThrow(new RuntimeException("检索异常"));

            StandardRagContextExecutor executor = new StandardRagContextExecutor(ragService);
            List<RetrievalEvidence> result = executor.retrieve("查询", List.of("kb1"), List.of(), 5);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null ragRetrieveService返回空列表")
        void shouldReturnEmptyListForNullRagRetrieveService() {
            StandardRagContextExecutor executor = new StandardRagContextExecutor(null);
            List<RetrievalEvidence> result = executor.retrieve("查询", List.of("kb1"), List.of(), 5);

            assertThat(result).isEmpty();
        }
    }
}
