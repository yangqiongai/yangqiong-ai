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

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Word文档增量编辑
 * <p>
 * 打开已有docx按operations顺序在内存中执行，任一失败整体失败不落盘（原文件保留）。
 * </p>
 * @author yangqiong
 */
public class DocxEditor {

    private static final Logger log = LoggerFactory.getLogger(DocxEditor.class);

    private DocxEditor() {
    }

    /**
     * 编辑结果
     */
    public static class EditResult {

        /**
         * 成功执行的操作数
         */
        private final int applied;

        /**
         * 各操作执行摘要
         */
        private final String detail;

        EditResult(int applied, String detail) {
            this.applied = applied;
            this.detail = detail;
        }

        public int getApplied() {
            return applied;
        }

        public String getDetail() {
            return detail;
        }
    }

    /**
     * 对已有docx执行编辑操作并落盘
     * @param dsl
     * @param target
     * @return
     * @throws Exception
     */
    public static EditResult apply(DocxEditDsl dsl, Path target) throws Exception {
        try (InputStream in = Files.newInputStream(target); XWPFDocument document = new XWPFDocument(in)) {
            List<String> details = new ArrayList<>();
            List<DocxEditDsl.EditOperation> operations = dsl.getOperations();
            for (int i = 0; i < operations.size(); i++) {
                DocxEditDsl.EditOperation op = operations.get(i);
                try {
                    details.add((i + 1) + ") " + execute(document, op));
                } catch (IllegalArgumentException e) {
                    // 整体失败不落盘，原文件保留
                    throw new IllegalArgumentException("第" + (i + 1) + "条操作失败：" + e.getMessage());
                }
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                document.write(out);
            }
            log.info("Word已编辑: {}（{}条操作）", target, operations.size());
            return new EditResult(operations.size(), String.join("；", details));
        }
    }

    /**
     * 执行单条操作，返回结果摘要
     * @param document
     * @param op
     * @return
     */
    private static String execute(XWPFDocument document, DocxEditDsl.EditOperation op) {
        return switch (op.getOp()) {
            case "replaceText" -> doReplaceText(document, op);
            case "insertPara" -> doInsertPara(document, op);
            case "addTable" -> doAddTable(document, op);
            default -> throw new IllegalArgumentException("非法操作类型：" + op.getOp());
        };
    }

    /**
     * 全文替换文本（遍历所有段落与表格所有单元格段落）
     * @param document
     * @param op
     * @return
     */
    private static String doReplaceText(XWPFDocument document, DocxEditDsl.EditOperation op) {
        if (op.getFrom() == null || op.getFrom().isEmpty()) {
            throw new IllegalArgumentException("from不能为空");
        }
        String to = op.getTo() != null ? op.getTo() : "";
        int count = 0;
        for (XWPFParagraph p : document.getParagraphs()) {
            count += replaceInParagraph(p, op.getFrom(), to);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        count += replaceInParagraph(p, op.getFrom(), to);
                    }
                }
            }
        }
        return "replaceText '" + op.getFrom() + "'→'" + to + "'（" + count + "处）";
    }

    /**
     * 段落内替换文本（run内优先，跨run时整体重建）
     * @param p
     * @param from
     * @param to
     * @return 替换发生次数
     */
    private static int replaceInParagraph(XWPFParagraph p, String from, String to) {
        int count = 0;
        boolean replaced = false;
        for (XWPFRun run : p.getRuns()) {
            String text = run.getText(0);
            if (text != null && text.contains(from)) {
                count += countOccurrences(text, from);
                run.setText(text.replace(from, to), 0);
                replaced = true;
            }
        }
        if (!replaced) {
            String full = p.getText();
            if (full != null && full.contains(from)) {
                // 文本被拆分在多个run中，整体替换后重建
                count += countOccurrences(full, from);
                String merged = full.replace(from, to);
                removeRuns(p);
                XWPFRun run = p.createRun();
                run.setText(merged);
            }
        }
        return count;
    }

    /**
     * 移除段落的全部run
     * @param p
     */
    private static void removeRuns(XWPFParagraph p) {
        for (int i = p.getRuns().size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }
    }

    /**
     * 统计子串出现次数
     * @param text
     * @param sub
     * @return
     */
    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(sub, index)) >= 0) {
            count++;
            index += sub.length();
        }
        return count;
    }

    /**
     * 插入段落（after为空或未命中追加文末；命中第一处后插入，heading应用内置样式）
     * @param document
     * @param op
     * @return
     */
    private static String doInsertPara(XWPFDocument document, DocxEditDsl.EditOperation op) {
        String type = op.getType() != null && !op.getType().isBlank() ? op.getType() : "paragraph";
        XWPFParagraph newPara;
        String position;
        if (op.getAfter() == null || op.getAfter().isBlank()) {
            newPara = document.createParagraph();
            position = "文末追加";
        } else {
            XWPFParagraph anchor = null;
            for (XWPFParagraph p : document.getParagraphs()) {
                String text = p.getText();
                if (text != null && text.contains(op.getAfter())) {
                    anchor = p;
                    break;
                }
            }
            if (anchor == null) {
                newPara = document.createParagraph();
                position = "after未命中，文末追加";
            } else {
                newPara = insertAfter(document, anchor);
                position = "插入到'" + op.getAfter() + "'命中段之后";
            }
        }
        XWPFRun run = newPara.createRun();
        run.setText(op.getText());
        if ("heading".equals(type)) {
            newPara.setStyle("Heading" + op.getLevel());
        }
        return "insertPara " + type + " '" + op.getText() + "'（" + position + "）";
    }

    /**
     * 在锚点段落后插入新段落（XmlCursor定位下一兄弟元素）
     * @param document
     * @param anchor
     * @return
     */
    private static XWPFParagraph insertAfter(XWPFDocument document, XWPFParagraph anchor) {
        try (XmlCursor cursor = anchor.getCTP().newCursor()) {
            // 移到下一兄弟元素，新段落插在其前即锚点之后；无兄弟时追加文末
            if (cursor.toNextSibling()) {
                XWPFParagraph newPara = document.insertNewParagraph(cursor);
                if (newPara != null) {
                    return newPara;
                }
            }
        }
        return document.createParagraph();
    }

    /**
     * 文末追加表格（首行表头加粗）
     * @param document
     * @param op
     * @return
     */
    private static String doAddTable(XWPFDocument document, DocxEditDsl.EditOperation op) {
        List<String> header = op.getHeader();
        List<List<String>> rows = op.getRows() != null ? op.getRows() : List.of();
        int cols = header.size();
        for (List<String> row : rows) {
            if (row != null) {
                cols = Math.max(cols, row.size());
            }
        }
        XWPFTable table = document.createTable(rows.size() + 1, cols);
        for (int c = 0; c < cols; c++) {
            setCellText(table.getRow(0).getCell(c), c < header.size() ? header.get(c) : "", true);
        }
        for (int r = 0; r < rows.size(); r++) {
            List<String> values = rows.get(r);
            if (values == null) {
                continue;
            }
            for (int c = 0; c < values.size() && c < cols; c++) {
                setCellText(table.getRow(r + 1).getCell(c), values.get(c), false);
            }
        }
        return "addTable（" + (rows.size() + 1) + "行" + cols + "列）";
    }

    /**
     * 写入单元格文本
     * @param cell
     * @param text
     * @param bold
     */
    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
    }
}
