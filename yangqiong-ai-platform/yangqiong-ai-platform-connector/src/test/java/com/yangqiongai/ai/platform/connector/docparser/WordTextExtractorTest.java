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
package com.yangqiongai.ai.platform.connector.docparser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Word文本提取单元测试")
class WordTextExtractorTest {

    @Test
    @DisplayName("按文档顺序提取段落与表格且表格线性化")
    void shouldExtractParagraphsAndLinearizedTables() throws Exception {
        byte[] docx = createDocx();
        WordTextExtractor extractor = new WordTextExtractor();

        List<DocSegment> segments = extractor.extract(docx);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getType()).isEqualTo("text");
        assertThat(segments.get(0).getText()).isEqualTo("合同标题");
        assertThat(segments.get(1).getType()).isEqualTo("table");
        assertThat(segments.get(1).getText()).isEqualTo("姓名 | 金额\n张三 | 1000");
    }

    @Test
    @DisplayName("非docx字节抛出解析失败")
    void shouldRejectInvalidBytes() {
        WordTextExtractor extractor = new WordTextExtractor();

        assertThatThrownBy(() -> extractor.extract("not-word".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Word解析失败");
    }

    /**
     * 生成包含段落与表格的测试docx
     * @return
     */
    private static byte[] createDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("合同标题");

            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("姓名");
            table.getRow(0).getCell(1).setText("金额");
            table.getRow(1).getCell(0).setText("张三");
            table.getRow(1).getCell(1).setText("1000");

            document.write(bos);
            return bos.toByteArray();
        }
    }
}
