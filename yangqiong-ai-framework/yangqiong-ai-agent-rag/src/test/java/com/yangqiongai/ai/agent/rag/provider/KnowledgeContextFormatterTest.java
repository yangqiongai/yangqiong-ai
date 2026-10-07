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
package com.yangqiongai.ai.agent.rag.provider;

import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识上下文格式化单元测试
 * @author yangqiong
 */
@DisplayName("知识上下文格式化单元测试")
class KnowledgeContextFormatterTest {

    @Test
    @DisplayName("结果为null或空时返回空文本")
    void format_nullOrEmpty_returnsEmpty() {
        assertThat(KnowledgeContextFormatter.format(null)).isEmpty();
        assertThat(KnowledgeContextFormatter.format(List.of())).isEmpty();
    }

    @Test
    @DisplayName("多个片段以knowledge标签包裹并以空行分隔")
    void format_wrapsChunksInKnowledgeTag() {
        RetrievalEvidence first = evidence("第一段内容", "文档一.pdf", "知识库A");
        RetrievalEvidence second = evidence("第二段内容", "文档二.pdf", "知识库A");

        String text = KnowledgeContextFormatter.format(List.of(first, second));

        assertThat(text).startsWith("<knowledge>\n");
        assertThat(text).endsWith("\n</knowledge>");
        assertThat(text).contains("<chunk source=\"文档一.pdf\">\n第一段内容\n</chunk>");
        assertThat(text).contains("<chunk source=\"文档二.pdf\">\n第二段内容\n</chunk>");
        assertThat(text).contains("</chunk>\n\n<chunk");
    }

    @Test
    @DisplayName("全部片段内容为空时返回空文本")
    void format_allBlankContent_returnsEmpty() {
        RetrievalEvidence blank = evidence("   ", "文档.pdf", "知识库A");

        assertThat(KnowledgeContextFormatter.format(List.of(blank))).isEmpty();
    }

    @Test
    @DisplayName("空内容片段被过滤保留有效片段")
    void format_blankContentFiltered() {
        RetrievalEvidence blank = evidence("", "文档一.pdf", "知识库A");
        RetrievalEvidence valid = evidence("有效内容", "文档二.pdf", "知识库A");

        String text = KnowledgeContextFormatter.format(List.of(blank, valid));

        assertThat(text).contains("有效内容");
        assertThat(text).doesNotContain("文档一.pdf");
    }

    @Test
    @DisplayName("文档名优先作为source属性")
    void formatChunk_prefersSourceDocName() {
        String text = KnowledgeContextFormatter.formatChunk(evidence("内容", "文档.pdf", "知识库A"));

        assertThat(text).isEqualTo("<chunk source=\"文档.pdf\">\n内容\n</chunk>");
    }

    @Test
    @DisplayName("文档名空白时回退知识库名称")
    void formatChunk_fallsBackToKbName() {
        String text = KnowledgeContextFormatter.formatChunk(evidence("内容", "  ", "知识库A"));

        assertThat(text).isEqualTo("<chunk source=\"知识库A\">\n内容\n</chunk>");
    }

    @Test
    @DisplayName("来源均缺失时省略source属性")
    void formatChunk_noSourceOmitsAttribute() {
        RetrievalEvidence item = evidence("内容", null, null);

        assertThat(KnowledgeContextFormatter.formatChunk(item)).isEqualTo("<chunk>\n内容\n</chunk>");
    }

    private RetrievalEvidence evidence(String content, String sourceDocName, String kbName) {
        RetrievalEvidence item = new RetrievalEvidence(content, "doc-1", sourceDocName, "slice-1", 0.72, null);
        item.setKbName(kbName);
        return item;
    }
}
