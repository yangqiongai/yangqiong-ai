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
package com.yangqiongai.ai.agent.local.workspace.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel工作簿增量编辑
 * <p>
 * 打开已有xlsx按operations顺序执行，任一失败整体失败不落盘（原文件保留）。
 * </p>
 * @author yangqiong
 */
public class ExcelEditor {

    private static final Logger log = LoggerFactory.getLogger(ExcelEditor.class);

    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelEditor() {
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
     * 对已有xlsx执行编辑操作并落盘
     * @param dsl
     * @param target
     * @param limits
     * @return
     * @throws Exception
     */
    public static EditResult apply(ExcelEditDsl dsl, Path target, ExcelLimits limits) throws Exception {
        try (InputStream in = Files.newInputStream(target); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            ExcelGenerator.StyleFactory styles = new ExcelGenerator.StyleFactory(wb);
            List<String> details = new ArrayList<>();
            List<ExcelEditDsl.EditOperation> operations = dsl.getOperations();
            for (int i = 0; i < operations.size(); i++) {
                ExcelEditDsl.EditOperation op = operations.get(i);
                try {
                    details.add((i + 1) + ") " + execute(wb, op, styles));
                } catch (IllegalArgumentException e) {
                    // 整体失败不落盘，原文件保留
                    throw new IllegalArgumentException("第" + (i + 1) + "条操作失败：" + e.getMessage());
                }
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                wb.write(out);
            }
            log.info("Excel已编辑: {}（{}条操作）", target, operations.size());
            return new EditResult(operations.size(), String.join("；", details));
        }
    }

    /**
     * 执行单条操作，返回结果摘要
     * @param wb
     * @param op
     * @param styles
     * @return
     */
    private static String execute(Workbook wb, ExcelEditDsl.EditOperation op, ExcelGenerator.StyleFactory styles) {
        return switch (op.getOp()) {
            case "setCell" -> doSetCell(wb, op);
            case "addRow" -> doAddRow(wb, op);
            case "deleteRow" -> doDeleteRow(wb, op);
            case "addSheet" -> doAddSheet(wb, op, styles);
            case "deleteSheet" -> doDeleteSheet(wb, op);
            case "renameSheet" -> doRenameSheet(wb, op);
            case "setStyle" -> doSetStyle(wb, op, styles);
            case "sort" -> doSort(wb, op);
            default -> throw new IllegalArgumentException("非法操作类型：" + op.getOp());
        };
    }

    /**
     * 按条件定位并修改单元格
     * @param wb
     * @param op
     * @return
     */
    private static String doSetCell(Workbook wb, ExcelEditDsl.EditOperation op) {
        Sheet sheet = requireSheet(wb, op.getSheet());
        List<String> header = readHeader(sheet);
        int col = ExcelDslValidator.resolveColumn(op.getColumn(), header);
        if (col < 0) {
            throw new IllegalArgumentException("目标列不存在：" + op.getColumn());
        }
        List<Integer> matched = findMatchedRows(sheet, op.getMatch(), header);
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("match未命中任何行：" + op.getMatch());
        }
        Row row = sheet.getRow(matched.get(0));
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
        }
        writeEditValue(cell, op.getValue());
        String hint = matched.size() > 1 ? "（命中" + matched.size() + "行，已修改第1行）" : "";
        return "setCell " + op.getSheet() + "!" + op.getColumn() + "=" + op.getValue() + hint;
    }

    /**
     * 末尾追加一行
     * @param wb
     * @param op
     * @return
     */
    private static String doAddRow(Workbook wb, ExcelEditDsl.EditOperation op) {
        Sheet sheet = requireSheet(wb, op.getSheet());
        int rowIdx = sheet.getLastRowNum() + 1;
        Row row = sheet.createRow(rowIdx);
        List<Object> values = op.getValues();
        for (int c = 0; c < values.size(); c++) {
            Object value = values.get(c);
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(c);
            writeEditValue(cell, value);
        }
        return "addRow " + op.getSheet() + " 第" + (rowIdx + 1) + "行";
    }

    /**
     * 删除所有命中行（自下而上删除保持行号稳定）
     * @param wb
     * @param op
     * @return
     */
    private static String doDeleteRow(Workbook wb, ExcelEditDsl.EditOperation op) {
        Sheet sheet = requireSheet(wb, op.getSheet());
        List<String> header = readHeader(sheet);
        List<Integer> matched = findMatchedRows(sheet, op.getMatch(), header);
        if (matched.isEmpty()) {
            throw new IllegalArgumentException("match未命中任何行：" + op.getMatch());
        }
        for (int i = matched.size() - 1; i >= 0; i--) {
            int rowIdx = matched.get(i);
            sheet.removeRow(sheet.getRow(rowIdx));
            int lastRow = sheet.getLastRowNum();
            if (rowIdx < lastRow) {
                sheet.shiftRows(rowIdx + 1, lastRow, -1);
            }
        }
        return "deleteRow " + op.getSheet() + " 删除" + matched.size() + "行";
    }

    /**
     * 新增sheet并写入表头与数据
     * @param wb
     * @param op
     * @param styles
     * @return
     */
    private static String doAddSheet(Workbook wb, ExcelEditDsl.EditOperation op, ExcelGenerator.StyleFactory styles) {
        if (wb.getSheetIndex(op.getSheet()) >= 0) {
            throw new IllegalArgumentException("sheet已存在：" + op.getSheet());
        }
        Sheet sheet = wb.createSheet(op.getSheet());
        ExcelDsl.SheetDsl sheetDsl = new ExcelDsl.SheetDsl();
        sheetDsl.setName(op.getSheet());
        sheetDsl.setHeader(op.getHeader());
        sheetDsl.setRows(op.getRows());
        sheetDsl.setColumnTypes(op.getColumnTypes());
        long count = ExcelGenerator.writeSheet(wb, sheet, sheetDsl, styles, true);
        return "addSheet " + op.getSheet() + "（" + count + "单元格）";
    }

    /**
     * 删除sheet（至少保留一个）
     * @param wb
     * @param op
     * @return
     */
    private static String doDeleteSheet(Workbook wb, ExcelEditDsl.EditOperation op) {
        int index = wb.getSheetIndex(op.getSheet());
        if (index < 0) {
            throw new IllegalArgumentException("sheet不存在：" + op.getSheet());
        }
        if (wb.getNumberOfSheets() <= 1) {
            throw new IllegalArgumentException("工作簿至少保留一个sheet，无法删除");
        }
        wb.removeSheetAt(index);
        wb.setActiveSheet(0);
        return "deleteSheet " + op.getSheet();
    }

    /**
     * 重命名sheet
     * @param wb
     * @param op
     * @return
     */
    private static String doRenameSheet(Workbook wb, ExcelEditDsl.EditOperation op) {
        int index = wb.getSheetIndex(op.getFrom());
        if (index < 0) {
            throw new IllegalArgumentException("sheet不存在：" + op.getFrom());
        }
        if (wb.getSheetIndex(op.getTo()) >= 0) {
            throw new IllegalArgumentException("目标sheet名已存在：" + op.getTo());
        }
        wb.setSheetName(index, op.getTo());
        return "renameSheet " + op.getFrom() + "→" + op.getTo();
    }

    /**
     * 区域设置样式
     * @param wb
     * @param op
     * @param styles
     * @return
     */
    private static String doSetStyle(Workbook wb, ExcelEditDsl.EditOperation op, ExcelGenerator.StyleFactory styles) {
        Sheet sheet = requireSheet(wb, op.getSheet());
        int[] range = ExcelDslValidator.parseRange(op.getRange());
        var style = styles.customStyle(op.getStyle());
        for (int r = range[0]; r <= range[2]; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }
            for (int c = range[1]; c <= range[3]; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                cell.setCellStyle(style);
            }
        }
        return "setStyle " + op.getSheet() + "!" + op.getRange();
    }

    /**
     * 数据区按列排序（表头不动，样式随行移动）
     * @param wb
     * @param op
     * @return
     */
    private static String doSort(Workbook wb, ExcelEditDsl.EditOperation op) {
        Sheet sheet = requireSheet(wb, op.getSheet());
        List<String> header = readHeader(sheet);
        int col = ExcelDslValidator.resolveColumn(op.getBy(), header);
        if (col < 0) {
            throw new IllegalArgumentException("排序列不存在：" + op.getBy());
        }
        boolean desc = "desc".equalsIgnoreCase(op.getOrder());
        int firstRow = 1;
        int lastRow = sheet.getLastRowNum();
        if (lastRow < firstRow) {
            return "sort " + op.getSheet() + "（无数据行）";
        }
        int maxCol = 0;
        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                maxCol = Math.max(maxCol, row.getLastCellNum());
            }
        }
        List<List<CellSnapshot>> snapshots = new ArrayList<>();
        for (int r = firstRow; r <= lastRow; r++) {
            snapshots.add(snapshotRow(sheet.getRow(r), maxCol));
        }
        Comparator<List<CellSnapshot>> comparator = (a, b) -> compareValues(a.get(col), b.get(col));
        if (desc) {
            comparator = comparator.reversed();
        }
        snapshots.sort(comparator);
        for (int i = 0; i < snapshots.size(); i++) {
            Row row = sheet.getRow(firstRow + i);
            if (row == null) {
                row = sheet.createRow(firstRow + i);
            }
            restoreRow(row, snapshots.get(i));
        }
        return "sort " + op.getSheet() + " 按" + op.getBy() + (desc ? "降序" : "升序") + "（" + snapshots.size() + "行）";
    }

    /**
     * 行值比较（数值优先，空值排最后）
     * @param a
     * @param b
     * @return
     */
    private static int compareValues(CellSnapshot a, CellSnapshot b) {
        boolean aEmpty = a == null || !a.present || (!a.numeric && (a.text == null || a.text.isEmpty()));
        boolean bEmpty = b == null || !b.present || (!b.numeric && (b.text == null || b.text.isEmpty()));
        if (aEmpty && bEmpty) {
            return 0;
        }
        if (aEmpty) {
            return 1;
        }
        if (bEmpty) {
            return -1;
        }
        if (a.numeric && b.numeric) {
            return Double.compare(a.num, b.num);
        }
        String sa = a.numeric ? String.valueOf(a.num) : a.text;
        String sb = b.numeric ? String.valueOf(b.num) : b.text;
        return sa.compareToIgnoreCase(sb);
    }

    /**
     * 行快照（值+样式，用于排序还原）
     */
    private static class CellSnapshot {

        boolean present;

        boolean numeric;

        double num;

        String text;

        CellStyle style;
    }

    private static List<CellSnapshot> snapshotRow(Row row, int maxCol) {
        List<CellSnapshot> cells = new ArrayList<>();
        for (int c = 0; c < maxCol; c++) {
            Cell cell = row == null ? null : row.getCell(c);
            if (cell == null) {
                cells.add(null);
                continue;
            }
            CellSnapshot snapshot = new CellSnapshot();
            snapshot.present = true;
            snapshot.style = cell.getCellStyle();
            switch (cell.getCellType()) {
                case NUMERIC -> {
                    snapshot.numeric = true;
                    snapshot.num = cell.getNumericCellValue();
                }
                case BOOLEAN -> snapshot.text = String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> snapshot.text = "=" + cell.getCellFormula();
                default -> snapshot.text = cell.getStringCellValue();
            }
            cells.add(snapshot);
        }
        return cells;
    }

    private static void restoreRow(Row row, List<CellSnapshot> snapshots) {
        for (int c = 0; c < snapshots.size(); c++) {
            CellSnapshot snapshot = snapshots.get(c);
            Cell cell = row.getCell(c);
            if (snapshot == null || !snapshot.present) {
                if (cell != null) {
                    row.removeCell(cell);
                }
                continue;
            }
            if (cell == null) {
                cell = row.createCell(c);
            }
            cell.setCellStyle(snapshot.style);
            if (snapshot.numeric) {
                cell.setCellValue(snapshot.num);
            } else {
                cell.setCellValue(snapshot.text == null ? "" : snapshot.text);
            }
        }
    }

    /**
     * 编辑值写入（数值/布尔直写，文本转义，日期单元格优先按日期解析）
     * @param cell
     * @param value
     */
    private static void writeEditValue(Cell cell, Object value) {
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        String text = String.valueOf(value);
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            var dateTime = ExcelGenerator.parseDateTime(text.trim());
            if (dateTime != null) {
                if (text.length() <= 10) {
                    cell.setCellValue(dateTime.toLocalDate());
                } else {
                    cell.setCellValue(dateTime);
                }
                return;
            }
        }
        cell.setCellValue(ExcelGenerator.escapeText(text));
    }

    /**
     * 读取表头行文本
     * @param sheet
     * @return
     */
    private static List<String> readHeader(Sheet sheet) {
        List<String> header = new ArrayList<>();
        Row row = sheet.getRow(0);
        if (row == null) {
            return header;
        }
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            header.add(cell == null ? "" : FORMATTER.formatCellValue(cell));
        }
        return header;
    }

    /**
     * 按match条件（表头名或列字母→期望值）定位数据行（0基行号列表）
     * @param sheet
     * @param match
     * @param header
     * @return
     */
    private static List<Integer> findMatchedRows(Sheet sheet, Map<String, Object> match, List<String> header) {
        List<Integer> matched = new ArrayList<>();
        if (match == null || match.isEmpty()) {
            return matched;
        }
        Map<Integer, String> conditions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : match.entrySet()) {
            int col = ExcelDslValidator.resolveColumn(entry.getKey(), header);
            if (col < 0) {
                throw new IllegalArgumentException("match条件列不存在：" + entry.getKey());
            }
            conditions.put(col, entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim());
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            boolean all = true;
            for (Map.Entry<Integer, String> condition : conditions.entrySet()) {
                Cell cell = row.getCell(condition.getKey());
                String actual = cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
                if (!actual.equals(condition.getValue())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                matched.add(r);
            }
        }
        return matched;
    }

    /**
     * 取指定sheet（不存在报错）
     * @param wb
     * @param name
     * @return
     */
    private static Sheet requireSheet(Workbook wb, String name) {
        int index = wb.getSheetIndex(name);
        if (index < 0) {
            throw new IllegalArgumentException("sheet不存在：" + name);
        }
        return wb.getSheetAt(index);
    }
}
