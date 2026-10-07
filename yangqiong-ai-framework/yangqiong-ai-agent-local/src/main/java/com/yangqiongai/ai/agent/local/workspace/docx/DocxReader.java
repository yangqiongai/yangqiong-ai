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
package com.yangqiongai.ai.agent.local.workspace.docx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Word文档读回
 * @author yangqiong
 */
public class DocxReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 读回给模型的段落数上限
     */
    private static final int MAX_PARAGRAPHS = 300;

    /**
     * 单段文本长度上限
     */
    private static final int MAX_TEXT_LENGTH = 2000;

    /**
     * 表格读回数据行上限
     */
    private static final int MAX_TABLE_ROWS = 100;

    private DocxReader() {
    }

    /**
     * 解析docx为结构化JSON文本（段落超300条截断，表格行超100行截断）
     * @param target
     * @return
     * @throws Exception
     */
    public static String readAsJson(Path target) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(readAsMap(target));
    }

    /**
     * 解析docx为结构化Map（按body元素顺序，段落与表格分别编号）
     * @param target
     * @return
     * @throws Exception
     */
    public static Map<String, Object> readAsMap(Path target) throws Exception {
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        List<Map<String, Object>> tables = new ArrayList<>();
        boolean truncated = false;
        try (InputStream in = Files.newInputStream(target); XWPFDocument document = new XWPFDocument(in)) {
            int index = 0;
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    if (paragraphs.size() >= MAX_PARAGRAPHS) {
                        truncated = true;
                        index++;
                        continue;
                    }
                    paragraphs.add(readParagraph(p, index));
                } else if (element instanceof XWPFTable table) {
                    tables.add(readTable(table, index));
                }
                index++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paragraphs", paragraphs);
        result.put("tables", tables);
        result.put("truncated", truncated);
        return result;
    }

    /**
     * 读回单个段落（标题按样式识别，文本超长截断）
     * @param p
     * @param index body内下标
     * @return
     */
    private static Map<String, Object> readParagraph(XWPFParagraph p, int index) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", index);
        String style = p.getStyleID();
        result.put("type", style != null && style.toUpperCase(Locale.ROOT).startsWith("HEADING")
                ? "heading" : "paragraph");
        String text = p.getText();
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        result.put("text", text == null ? "" : text);
        return result;
    }

    /**
     * 读回单个表格（首行为表头，数据行超100行截断）
     * @param table
     * @param index body内下标
     * @return
     */
    private static Map<String, Object> readTable(XWPFTable table, int index) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", index);
        List<String> header = new ArrayList<>();
        if (!table.getRows().isEmpty()) {
            XWPFTableRow headerRow = table.getRow(0);
            for (int c = 0; c < headerRow.getTableCells().size(); c++) {
                header.add(cellText(headerRow.getCell(c)));
            }
        }
        result.put("header", header);
        List<List<String>> rows = new ArrayList<>();
        boolean tableTruncated = false;
        for (int r = 1; r < table.getRows().size(); r++) {
            if (rows.size() >= MAX_TABLE_ROWS) {
                tableTruncated = true;
                break;
            }
            XWPFTableRow row = table.getRow(r);
            List<String> values = new ArrayList<>();
            for (int c = 0; c < row.getTableCells().size(); c++) {
                values.add(cellText(row.getCell(c)));
            }
            rows.add(values);
        }
        result.put("rows", rows);
        result.put("truncated", tableTruncated);
        return result;
    }

    /**
     * 单元格文本（空单元格返回空串）
     * @param cell
     * @return
     */
    private static String cellText(XWPFTableCell cell) {
        if (cell == null) {
            return "";
        }
        String text = cell.getText();
        return text == null ? "" : text;
    }
}
