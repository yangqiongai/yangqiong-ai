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

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Word文本提取
 * <p>
 * 仅支持docx格式，按文档顺序提取段落与表格，表格行线性化为
 * "单元格1 | 单元格2 | ..."文本，保留表格结构语义。
 * </p>
 * @author yangqiong
 */
public class WordTextExtractor {

    /**
     * 表格单元格连接符
     */
    static final String CELL_SEPARATOR = " | ";

    /**
     * 提取Word分段
     * @param content 文件字节
     * @return 段落与表格分段清单
     */
    public List<DocSegment> extract(byte[] content) {
        List<DocSegment> segments = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        segments.add(new DocSegment("text", text.trim(), null));
                    }
                } else if (element instanceof XWPFTable table) {
                    String linearized = linearizeTable(table);
                    if (!linearized.isBlank()) {
                        segments.add(new DocSegment("table", linearized, null));
                    }
                }
            }
            return segments;
        } catch (Exception e) {
            throw new IllegalStateException("Word解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 表格线性化为逐行文本
     * @param table 表格
     * @return 线性化文本
     */
    private String linearizeTable(XWPFTable table) {
        return table.getRows().stream()
                .map(this::linearizeRow)
                .filter(row -> !row.isBlank())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 表格行线性化
     * @param row 表格行
     * @return 单元格拼接文本
     */
    private String linearizeRow(XWPFTableRow row) {
        return row.getTableCells().stream()
                .map(XWPFTableCell::getText)
                .map(cell -> cell == null ? "" : cell.trim())
                .collect(Collectors.joining(CELL_SEPARATOR));
    }
}
