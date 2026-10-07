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
package com.yangqiongai.ai.rag.retriever;

import com.yangqiongai.ai.rag.model.ChunkCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalFormatter 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class RetrievalFormatterTest {

    private RetrievalFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new RetrievalFormatter();
    }

    @Test
    @DisplayName("format - 空列表返回空字符串")
    void format_returnsEmpty_whenEmptyList() {
        assertThat(formatter.format(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("format - null返回空字符串")
    void format_returnsEmpty_whenNull() {
        assertThat(formatter.format(null)).isEmpty();
    }

    @Test
    @DisplayName("format - 使用---分隔符格式化")
    void format_formatsWithSeparator() {
        ChunkCandidate c1 = new ChunkCandidate();
        c1.setText("内容1");
        ChunkCandidate c2 = new ChunkCandidate();
        c2.setText("内容2");

        String result = formatter.format(List.of(c1, c2));

        assertThat(result).isEqualTo("内容1\n---\n内容2");
    }

    @Test
    @DisplayName("format - 单个候选项不含分隔符")
    void format_singleCandidate_noSeparator() {
        ChunkCandidate c1 = new ChunkCandidate();
        c1.setText("内容1");

        String result = formatter.format(List.of(c1));

        assertThat(result).isEqualTo("内容1");
    }

    @Test
    @DisplayName("formatWithCitations - 空列表返回空字符串")
    void formatWithCitations_returnsEmpty_whenEmptyList() {
        assertThat(formatter.formatWithCitations(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("formatWithCitations - 包含docId/sliceId/score")
    void formatWithCitations_includesDocIdSliceIdScore() {
        ChunkCandidate c1 = new ChunkCandidate();
        c1.setText("检索内容");
        c1.setDocId("doc-1");
        c1.setSliceId("slice-1");
        c1.setScore(0.95);

        String result = formatter.formatWithCitations(List.of(c1));

        assertThat(result).contains("文档ID=doc-1");
        assertThat(result).contains("切片ID=slice-1");
        assertThat(result).contains("相似度=0.9500");
        assertThat(result).contains("检索内容");
    }

    @Test
    @DisplayName("formatWithCitations - docId为null时显示未知")
    void formatWithCitations_showsUnknown_whenDocIdNull() {
        ChunkCandidate c1 = new ChunkCandidate();
        c1.setText("内容");
        c1.setDocId(null);
        c1.setScore(0.5);

        String result = formatter.formatWithCitations(List.of(c1));

        assertThat(result).contains("文档ID=未知");
    }

    @Test
    @DisplayName("formatWithCitations - sliceId为null时不包含切片ID")
    void formatWithCitations_omitsSliceId_whenNull() {
        ChunkCandidate c1 = new ChunkCandidate();
        c1.setText("内容");
        c1.setDocId("doc-1");
        c1.setSliceId(null);
        c1.setScore(0.8);

        String result = formatter.formatWithCitations(List.of(c1));

        assertThat(result).doesNotContain("切片ID=");
        assertThat(result).contains("文档ID=doc-1");
    }

    @Test
    @DisplayName("formatWithCitations - 多个候选项使用---分隔")
    void formatWithCitations_multipleCandidates_withSeparator() {
        ChunkCandidate c1 = new ChunkCandidate();
        c1.setText("内容1");
        c1.setDocId("doc-1");
        c1.setScore(0.9);

        ChunkCandidate c2 = new ChunkCandidate();
        c2.setText("内容2");
        c2.setDocId("doc-2");
        c2.setScore(0.8);

        String result = formatter.formatWithCitations(List.of(c1, c2));

        assertThat(result).contains("---");
        assertThat(result).contains("内容1");
        assertThat(result).contains("内容2");
    }
}
