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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ExcelDSL解析校验
 * @author yangqiong
 */
public class ExcelDslValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 合法列类型
     */
    private static final Set<String> COLUMN_TYPES = Set.of("text", "number", "date", "currency", "percent");

    /**
     * 合法对齐方式
     */
    private static final Set<String> ALIGNS = Set.of("left", "center", "right");

    /**
     * 合法条件格式规则
     */
    private static final Set<String> CF_RULES = Set.of("dataBar");

    /**
     * 单元格引用（如 A1 / AA12）
     */
    private static final Pattern CELL_REF = Pattern.compile("^([A-Za-z]{1,3})([1-9][0-9]{0,6})$");

    /**
     * Excel最大行号
     */
    private static final int MAX_ROWS = 1048576;

    /**
     * Excel最大列号
     */
    private static final int MAX_COLS = 16384;

    private ExcelDslValidator() {
    }

    /**
     * 解析创建DSL的JSON文本
     * @param json
     * @return
     */
    public static ExcelDsl parseCreate(String json) {
        try {
            return MAPPER.readValue(json, ExcelDsl.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("创建DSL的JSON解析失败：" + e.getMessage());
        }
    }

    /**
     * 解析编辑DSL的JSON文本
     * @param json
     * @return
     */
    public static ExcelEditDsl parseEdit(String json) {
        try {
            return MAPPER.readValue(json, ExcelEditDsl.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("编辑DSL的JSON解析失败：" + e.getMessage());
        }
    }

    /**
     * 校验创建DSL（错误信息含字段路径，供Agent自我修正）
     * @param dsl
     * @param limits
     */
    public static void validateCreate(ExcelDsl dsl, ExcelLimits limits) {
        if (dsl == null || isBlank(dsl.getPath())) {
            throw new IllegalArgumentException("path不能为空");
        }
        if (!dsl.getPath().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("path必须以.xlsx结尾：" + dsl.getPath());
        }
        ExcelDsl.WorkbookDsl workbook = dsl.getWorkbook();
        if (workbook == null) {
            throw new IllegalArgumentException("workbook不能为空");
        }
        List<ExcelDsl.SheetDsl> sheets = workbook.getSheets();
        if (sheets == null || sheets.isEmpty()) {
            throw new IllegalArgumentException("workbook.sheets不能为空");
        }
        if (sheets.size() > limits.getMaxSheets()) {
            throw new IllegalArgumentException("sheet数量超上限：" + sheets.size() + " > " + limits.getMaxSheets());
        }
        Set<String> names = new HashSet<>();
        long totalCells = 0;
        boolean foundActive = false;
        for (int i = 0; i < sheets.size(); i++) {
            ExcelDsl.SheetDsl sheet = sheets.get(i);
            String where = "workbook.sheets[" + i + "]";
            checkSheet(sheet, where, names);
            if (sheet.getName().equals(workbook.getActiveSheet())) {
                foundActive = true;
            }
            totalCells += calcCellCount(sheet);
        }
        if (workbook.getActiveSheet() != null && !foundActive) {
            throw new IllegalArgumentException("workbook.activeSheet引用了不存在的sheet：" + workbook.getActiveSheet());
        }
        if (totalCells > limits.getMaxCells()) {
            throw new IllegalArgumentException("单元格总数超上限：" + totalCells + " > " + limits.getMaxCells()
                    + "，请减少行数或拆分文件");
        }
    }

    /**
     * 校验编辑DSL
     * @param dsl
     * @param limits
     */
    public static void validateEdit(ExcelEditDsl dsl, ExcelLimits limits) {
        if (dsl == null || isBlank(dsl.getPath())) {
            throw new IllegalArgumentException("path不能为空");
        }
        if (!dsl.getPath().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("path必须以.xlsx结尾：" + dsl.getPath());
        }
        List<ExcelEditDsl.EditOperation> operations = dsl.getOperations();
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("operations不能为空");
        }
        for (int i = 0; i < operations.size(); i++) {
            checkOperation(operations.get(i), "operations[" + i + "]");
        }
    }

    /**
     * 校验单个sheet定义
     * @param sheet
     * @param where
     * @param names 已登记的sheet名（用于重名检测）
     */
    private static void checkSheet(ExcelDsl.SheetDsl sheet, String where, Set<String> names) {
        String name = sheet.getName();
        if (isBlank(name)) {
            throw new IllegalArgumentException(where + ".name不能为空");
        }
        checkSheetName(name, where + ".name");
        if (!names.add(name)) {
            throw new IllegalArgumentException(where + ".name：sheet名称重复：" + name);
        }
        if (sheet.getHeader() != null) {
            for (String h : sheet.getHeader()) {
                if (h == null || h.isBlank()) {
                    throw new IllegalArgumentException(where + ".header含空表头，请用占位名称");
                }
            }
        }
        if (sheet.getColumnTypes() != null) {
            for (String type : sheet.getColumnTypes()) {
                if (!COLUMN_TYPES.contains(type)) {
                    throw new IllegalArgumentException(where + ".columnTypes含非法类型：" + type
                            + "（允许：text/number/date/currency/percent）");
                }
            }
        }
        if (sheet.getHeaderStyle() != null) {
            checkStyle(sheet.getHeaderStyle(), where + ".headerStyle");
        }
        if (sheet.getMerges() != null) {
            for (String merge : sheet.getMerges()) {
                if (parseRange(merge) == null) {
                    throw new IllegalArgumentException(where + ".merges含非法区域：" + merge);
                }
            }
        }
        if (sheet.getFormulas() != null) {
            for (ExcelDsl.FormulaDsl formula : sheet.getFormulas()) {
                if (formula == null || isBlank(formula.getCell()) || isBlank(formula.getExpr())) {
                    throw new IllegalArgumentException(where + ".formulas的cell和expr均不能为空");
                }
                if (parseCell(formula.getCell()) == null) {
                    throw new IllegalArgumentException(where + ".formulas.cell非法：" + formula.getCell());
                }
                String expr = formula.getExpr().trim();
                if (expr.startsWith("=")) {
                    throw new IllegalArgumentException(where + ".formulas.expr不需以=开头：" + expr);
                }
            }
        }
        if (sheet.getConditionalFormat() != null) {
            ExcelDsl.ConditionalFormatDsl cf = sheet.getConditionalFormat();
            if (parseRange(cf.getRange()) == null) {
                throw new IllegalArgumentException(where + ".conditionalFormat.range非法：" + cf.getRange());
            }
            if (!CF_RULES.contains(cf.getRule())) {
                throw new IllegalArgumentException(where + ".conditionalFormat.rule非法：" + cf.getRule()
                        + "（允许：dataBar）");
            }
        }
    }

    /**
     * 校验单个编辑操作
     * @param op
     * @param where
     */
    private static void checkOperation(ExcelEditDsl.EditOperation op, String where) {
        if (op == null || isBlank(op.getOp())) {
            throw new IllegalArgumentException(where + ".op不能为空");
        }
        switch (op.getOp()) {
            case "setCell":
                require(where, op.getSheet(), "sheet");
                if (op.getMatch() == null || op.getMatch().isEmpty()) {
                    throw new IllegalArgumentException(where + ".match不能为空（按条件定位目标行）");
                }
                require(where, op.getColumn(), "column");
                if (op.getValue() == null) {
                    throw new IllegalArgumentException(where + ".value不能为空");
                }
                break;
            case "addRow":
                require(where, op.getSheet(), "sheet");
                if (op.getValues() == null || op.getValues().isEmpty()) {
                    throw new IllegalArgumentException(where + ".values不能为空");
                }
                break;
            case "deleteRow":
                require(where, op.getSheet(), "sheet");
                if (op.getMatch() == null || op.getMatch().isEmpty()) {
                    throw new IllegalArgumentException(where + ".match不能为空");
                }
                break;
            case "addSheet":
                require(where, op.getSheet(), "sheet");
                checkSheetName(op.getSheet(), where + ".sheet");
                break;
            case "deleteSheet":
                require(where, op.getSheet(), "sheet");
                break;
            case "renameSheet":
                require(where, op.getFrom(), "from");
                require(where, op.getTo(), "to");
                checkSheetName(op.getTo(), where + ".to");
                break;
            case "setStyle":
                require(where, op.getSheet(), "sheet");
                if (parseRange(op.getRange()) == null) {
                    throw new IllegalArgumentException(where + ".range非法：" + op.getRange());
                }
                if (op.getStyle() == null) {
                    throw new IllegalArgumentException(where + ".style不能为空");
                }
                checkStyle(op.getStyle(), where + ".style");
                break;
            case "sort":
                require(where, op.getSheet(), "sheet");
                require(where, op.getBy(), "by");
                if (op.getOrder() != null && !Set.of("asc", "desc").contains(op.getOrder())) {
                    throw new IllegalArgumentException(where + ".order非法：" + op.getOrder() + "（允许：asc/desc）");
                }
                break;
            default:
                throw new IllegalArgumentException(where + ".op非法：" + op.getOp()
                        + "（允许：setCell/addRow/deleteRow/addSheet/deleteSheet/renameSheet/setStyle/sort）");
        }
    }

    /**
     * 校验样式取值
     * @param style
     * @param where
     */
    private static void checkStyle(ExcelDsl.StyleDsl style, String where) {
        if (style.getBackground() != null && !style.getBackground().matches("(?i)^[0-9A-F]{6}$")) {
            throw new IllegalArgumentException(where + ".background须为RRGGBB十六进制：" + style.getBackground());
        }
        if (style.getFontColor() != null && !style.getFontColor().matches("(?i)^[0-9A-F]{6}$")) {
            throw new IllegalArgumentException(where + ".fontColor须为RRGGBB十六进制：" + style.getFontColor());
        }
        if (style.getAlign() != null && !ALIGNS.contains(style.getAlign())) {
            throw new IllegalArgumentException(where + ".align非法：" + style.getAlign() + "（允许：left/center/right）");
        }
    }

    /**
     * 校验sheet名称（长度与Excel非法字符）
     * @param name
     * @param where
     */
    private static void checkSheetName(String name, String where) {
        if (name.length() > 31) {
            throw new IllegalArgumentException(where + "超长（Excel上限31字符）：" + name);
        }
        if (name.matches(".*[\\[\\]\\*\\?\\\\/].*")) {
            throw new IllegalArgumentException(where + "含Excel非法字符（[]*?:\\/）：" + name);
        }
    }

    /**
     * 计算sheet占用的单元格总数（表头+数据行列乘积+公式）
     * @param sheet
     * @return
     */
    static long calcCellCount(ExcelDsl.SheetDsl sheet) {
        int cols = sheet.getHeader() != null ? sheet.getHeader().size() : 0;
        int rows = sheet.getRows() != null ? sheet.getRows().size() : 0;
        int formulas = sheet.getFormulas() != null ? sheet.getFormulas().size() : 0;
        if (cols == 0 && rows == 0) {
            return formulas;
        }
        return (long) cols * (rows + 1) + formulas;
    }

    /**
     * 解析单元格引用为(行,列)下标，非法返回null
     * @param ref 如 D9 / BA999
     * @return int[]{rowIndex0, colIndex0}
     */
    static int[] parseCell(String ref) {
        if (ref == null) {
            return null;
        }
        Matcher m = CELL_REF.matcher(ref.trim());
        if (!m.matches()) {
            return null;
        }
        int col = colLettersToIndex(m.group(1));
        int row = Integer.parseInt(m.group(2)) - 1;
        if (col < 0 || col >= MAX_COLS || row < 0 || row >= MAX_ROWS) {
            return null;
        }
        return new int[]{row, col};
    }

    /**
     * 解析区域引用（如 A1:D1 或 A1），非法返回null
     * @param range
     * @return int[]{firstRow, firstCol, lastRow, lastCol}（0基）
     */
    static int[] parseRange(String range) {
        if (isBlank(range)) {
            return null;
        }
        String[] parts = range.trim().split(":");
        if (parts.length > 2) {
            return null;
        }
        int[] first = parseCell(parts[0]);
        if (first == null) {
            return null;
        }
        if (parts.length == 1) {
            return new int[]{first[0], first[1], first[0], first[1]};
        }
        int[] last = parseCell(parts[1]);
        if (last == null) {
            return null;
        }
        return new int[]{
                Math.min(first[0], last[0]), Math.min(first[1], last[1]),
                Math.max(first[0], last[0]), Math.max(first[1], last[1])};
    }

    /**
     * 列字母转0基下标（A=0，AA=26）
     * @param letters
     * @return
     */
    static int colLettersToIndex(String letters) {
        int col = 0;
        for (char c : letters.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c < 'A' || c > 'Z') {
                return -1;
            }
            col = col * 26 + (c - 'A' + 1);
        }
        return col - 1;
    }

    /**
     * 表头名或列字母解析为0基列号，未命中返回-1
     * @param key
     * @param header
     * @return
     */
    static int resolveColumn(String key, List<String> header) {
        if (isBlank(key)) {
            return -1;
        }
        String trimmed = key.trim();
        if (header != null) {
            for (int i = 0; i < header.size(); i++) {
                if (trimmed.equals(header.get(i) == null ? "" : header.get(i).trim())) {
                    return i;
                }
            }
        }
        int[] ref = parseCell(trimmed + "1");
        if (ref != null) {
            return ref[1];
        }
        return -1;
    }

    private static void require(String where, String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(where + "." + field + "不能为空");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
