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
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Excel工作簿生成
 * <p>
 * 大表（单sheet超5000行）自动切换SXSSF流式写，条件格式仅在XSSF分支生效。
 * </p>
 * @author yangqiong
 */
public class ExcelGenerator {

    private static final Logger log = LoggerFactory.getLogger(ExcelGenerator.class);

    /**
     * 流式写切换阈值（单sheet数据行数）
     */
    private static final int SXSSF_ROW_THRESHOLD = 5000;

    /**
     * 默认表头底色
     */
    private static final String DEFAULT_HEADER_BG = "D9E2F3";

    /**
     * 数据条颜色
     */
    private static final byte[] DATABAR_RGB = {(byte) 0x63, (byte) 0x83, (byte) 0x8B};

    private ExcelGenerator() {
    }

    /**
     * 生成结果
     */
    public static class GenerateResult {

        /**
         * sheet数量
         */
        private final int sheetCount;

        /**
         * 实际写入单元格数
         */
        private final long cellCount;

        /**
         * 文件大小（字节）
         */
        private final long fileSize;

        GenerateResult(int sheetCount, long cellCount, long fileSize) {
            this.sheetCount = sheetCount;
            this.cellCount = cellCount;
            this.fileSize = fileSize;
        }

        public int getSheetCount() {
            return sheetCount;
        }

        public long getCellCount() {
            return cellCount;
        }

        public long getFileSize() {
            return fileSize;
        }
    }

    /**
     * 按DSL生成xlsx文件并落盘
     * @param dsl
     * @param target
     * @param limits
     * @return
     * @throws Exception
     */
    public static GenerateResult generate(ExcelDsl dsl, Path target, ExcelLimits limits) throws Exception {
        List<ExcelDsl.SheetDsl> sheets = dsl.getWorkbook().getSheets();
        boolean streaming = sheets.stream()
                .anyMatch(s -> s.getRows() != null && s.getRows().size() > SXSSF_ROW_THRESHOLD);
        Workbook wb = streaming ? new SXSSFWorkbook() : new XSSFWorkbook();
        try (wb) {
            StyleFactory styles = new StyleFactory(wb);
            long cellCount = 0;
            for (ExcelDsl.SheetDsl sheetDsl : sheets) {
                Sheet sheet = wb.createSheet(sheetDsl.getName());
                cellCount += writeSheet(wb, sheet, sheetDsl, styles, !streaming);
            }
            applyActiveSheet(wb, dsl.getWorkbook().getActiveSheet(), sheets);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            wb.write(buffer);
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > limits.getMaxFileSize()) {
                throw new IllegalArgumentException("生成文件大小超上限：" + (bytes.length / 1024) + "KB > "
                        + (limits.getMaxFileSize() / 1024) + "KB，请减少数据量");
            }
            Files.write(target, bytes);
            log.info("Excel已生成: {}（sheets={}，cells={}，{}字节，streaming={}）",
                    target, sheets.size(), cellCount, bytes.length, streaming);
            return new GenerateResult(sheets.size(), cellCount, bytes.length);
        }
    }

    /**
     * 写入单个sheet，返回写入单元格数
     * @param wb
     * @param sheet
     * @param sheetDsl
     * @param styles
     * @param withConditionalFormat 是否写条件格式（仅XSSF分支支持）
     * @return
     */
    static long writeSheet(Workbook wb, Sheet sheet, ExcelDsl.SheetDsl sheetDsl,
                           StyleFactory styles, boolean withConditionalFormat) {
        long count = 0;
        List<String> header = sheetDsl.getHeader();
        List<List<Object>> rows = sheetDsl.getRows();
        List<String> columnTypes = sheetDsl.getColumnTypes();

        if (header != null && !header.isEmpty()) {
            Row headerRow = sheet.createRow(0);
            CellStyle style = styles.headerStyle(sheetDsl.getHeaderStyle());
            for (int c = 0; c < header.size(); c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(header.get(c));
                cell.setCellStyle(style);
                count++;
            }
        }
        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                if (values == null) {
                    continue;
                }
                for (int c = 0; c < values.size(); c++) {
                    String type = columnTypes != null && c < columnTypes.size() ? columnTypes.get(c) : null;
                    Cell cell = row.createCell(c);
                    if (writeValue(cell, values.get(c), type, styles)) {
                        count++;
                    }
                }
            }
        }
        if (sheetDsl.getColumnWidths() != null) {
            List<Integer> widths = sheetDsl.getColumnWidths();
            for (int c = 0; c < widths.size(); c++) {
                if (widths.get(c) != null) {
                    sheet.setColumnWidth(c, Math.min(widths.get(c), 255) * 256);
                }
            }
        }
        if (sheetDsl.getMerges() != null) {
            for (String merge : sheetDsl.getMerges()) {
                int[] range = ExcelDslValidator.parseRange(merge);
                if (range != null) {
                    sheet.addMergedRegion(new CellRangeAddress(range[0], range[2], range[1], range[3]));
                }
            }
        }
        if (sheetDsl.getFormulas() != null) {
            for (ExcelDsl.FormulaDsl formula : sheetDsl.getFormulas()) {
                int[] ref = ExcelDslValidator.parseCell(formula.getCell());
                if (ref == null) {
                    continue;
                }
                Row row = sheet.getRow(ref[0]);
                if (row == null) {
                    row = sheet.createRow(ref[0]);
                }
                Cell cell = row.getCell(ref[1]);
                if (cell == null) {
                    cell = row.createCell(ref[1]);
                }
                cell.setCellFormula(formula.getExpr().trim());
                count++;
            }
        }
        if (sheetDsl.getFreezeHeader() != null && sheetDsl.getFreezeHeader()) {
            sheet.createFreezePane(0, 1);
        }
        if (withConditionalFormat && sheetDsl.getConditionalFormat() != null && sheet instanceof XSSFSheet xssfSheet) {
            applyConditionalFormat(xssfSheet, sheetDsl.getConditionalFormat());
        }
        return count;
    }

    /**
     * 按列类型写单元格值（text前缀注入转义），返回是否写入
     * @param cell
     * @param value
     * @param columnType
     * @param styles
     * @return
     */
    static boolean writeValue(Cell cell, Object value, String columnType, StyleFactory styles) {
        if (value == null) {
            return false;
        }
        // 未声明列类型时按值类型推断；声明为 text 时一律按文本写入
        if (columnType == null) {
            return writeByValueType(cell, value, styles);
        }
        switch (columnType) {
            case "number":
            case "currency":
            case "percent":
                Number number = toNumber(value);
                if (number == null) {
                    cell.setCellValue(escapeText(String.valueOf(value)));
                    return true;
                }
                cell.setCellValue(number.doubleValue());
                if ("currency".equals(columnType)) {
                    cell.setCellStyle(styles.currencyStyle());
                } else if ("percent".equals(columnType)) {
                    cell.setCellStyle(styles.percentStyle());
                }
                return true;
            case "date":
                String dateText = String.valueOf(value).trim();
                LocalDateTime dateTime = parseDateTime(dateText);
                if (dateTime == null) {
                    cell.setCellValue(escapeText(dateText));
                    return true;
                }
                if (dateText.length() <= 10) {
                    cell.setCellValue(dateTime.toLocalDate());
                    cell.setCellStyle(styles.dateStyle());
                } else {
                    cell.setCellValue(dateTime);
                    cell.setCellStyle(styles.dateTimeStyle());
                }
                return true;
            case "text":
            default:
                cell.setCellValue(escapeText(String.valueOf(value)));
                return true;
        }
    }

    /**
     * 未声明列类型时按值类型推断写入（数值/布尔直写，其他按文本转义）
     * @param cell
     * @param value
     * @param styles
     * @return
     */
    private static boolean writeByValueType(Cell cell, Object value, StyleFactory styles) {
        if (value instanceof Number numeric) {
            cell.setCellValue(numeric.doubleValue());
            return true;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return true;
        }
        cell.setCellValue(escapeText(String.valueOf(value)));
        return true;
    }

    /**
     * 文本注入转义（= + - @ 前缀补单引号）
     * @param text
     * @return
     */
    public static String escapeText(String text) {
        if (text != null && !text.isEmpty()
                && (text.charAt(0) == '=' || text.charAt(0) == '+' || text.charAt(0) == '-' || text.charAt(0) == '@')) {
            return "'" + text;
        }
        return text;
    }

    /**
     * 解析日期文本（ISO与斜杠格式），失败返回null
     * @param text
     * @return
     */
    static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
            // 继续尝试其他格式
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy/M/d")).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 宽松数值转换（支持数字字符串），失败返回null
     * @param value
     * @return
     */
    static Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof Boolean) {
            return null;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置打开时激活的sheet
     * @param wb
     * @param activeSheet
     * @param sheets
     */
    private static void applyActiveSheet(Workbook wb, String activeSheet, List<ExcelDsl.SheetDsl> sheets) {
        int index = 0;
        if (activeSheet != null) {
            for (int i = 0; i < sheets.size(); i++) {
                if (sheets.get(i).getName().equals(activeSheet)) {
                    index = i;
                    break;
                }
            }
        }
        wb.setActiveSheet(index);
    }

    /**
     * 应用条件格式（dataBar数据条）
     * @param sheet
     * @param cf
     */
    private static void applyConditionalFormat(XSSFSheet sheet, ExcelDsl.ConditionalFormatDsl cf) {
        int[] range = ExcelDslValidator.parseRange(cf.getRange());
        if (range == null) {
            return;
        }
        XSSFSheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();
        CellRangeAddress[] regions = {new CellRangeAddress(range[0], range[2], range[1], range[3])};
        XSSFConditionalFormattingRule rule = scf.createConditionalFormattingRule(new XSSFColor(DATABAR_RGB, null));
        scf.addConditionalFormatting(regions, rule);
    }

    /**
     * 样式工厂（工作簿级缓存，避免样式数膨胀）
     */
    static class StyleFactory {

        private final Workbook wb;

        private final Map<String, CellStyle> cache = new HashMap<>();

        StyleFactory(Workbook wb) {
            this.wb = wb;
        }

        /**
         * 表头样式（缺省加粗+浅蓝底+居中）
         * @param styleDsl
         * @return
         */
        CellStyle headerStyle(ExcelDsl.StyleDsl styleDsl) {
            String bg = styleDsl != null && styleDsl.getBackground() != null
                    ? styleDsl.getBackground().toUpperCase(Locale.ROOT) : DEFAULT_HEADER_BG;
            String fontColor = styleDsl != null && styleDsl.getFontColor() != null
                    ? styleDsl.getFontColor().toUpperCase(Locale.ROOT) : "000000";
            boolean bold = styleDsl == null || styleDsl.getBold() == null || styleDsl.getBold();
            boolean italic = styleDsl != null && styleDsl.getItalic() != null && styleDsl.getItalic();
            String align = styleDsl != null && styleDsl.getAlign() != null ? styleDsl.getAlign() : "center";
            String key = "header|" + bg + "|" + fontColor + "|" + bold + "|" + italic + "|" + align;
            return cache.computeIfAbsent(key, k -> {
                XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
                XSSFFont font = (XSSFFont) wb.createFont();
                font.setBold(bold);
                font.setItalic(italic);
                font.setColor(new XSSFColor(hexToBytes(fontColor), null));
                style.setFont(font);
                style.setFillForegroundColor(new XSSFColor(hexToBytes(bg), null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                style.setAlignment(resolveAlign(align));
                return style;
            });
        }

        CellStyle currencyStyle() {
            return cachedStyle("currency", "¥#,##0.00");
        }

        /**
         * 通用自定义样式（仅应用显式给出的属性）
         * @param styleDsl
         * @return
         */
        CellStyle customStyle(ExcelDsl.StyleDsl styleDsl) {
            boolean bold = styleDsl.getBold() != null && styleDsl.getBold();
            boolean italic = styleDsl.getItalic() != null && styleDsl.getItalic();
            String bg = styleDsl.getBackground() != null ? styleDsl.getBackground().toUpperCase(Locale.ROOT) : null;
            String fontColor = styleDsl.getFontColor() != null
                    ? styleDsl.getFontColor().toUpperCase(Locale.ROOT) : null;
            String align = styleDsl.getAlign() != null ? styleDsl.getAlign() : null;
            String key = "custom|" + bg + "|" + fontColor + "|" + bold + "|" + italic + "|" + align;
            return cache.computeIfAbsent(key, k -> {
                XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
                if (bold || italic || fontColor != null) {
                    XSSFFont font = (XSSFFont) wb.createFont();
                    font.setBold(bold);
                    font.setItalic(italic);
                    if (fontColor != null) {
                        font.setColor(new XSSFColor(hexToBytes(fontColor), null));
                    }
                    style.setFont(font);
                }
                if (bg != null) {
                    style.setFillForegroundColor(new XSSFColor(hexToBytes(bg), null));
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                if (align != null) {
                    style.setAlignment(resolveAlign(align));
                }
                return style;
            });
        }

        CellStyle percentStyle() {
            return cachedStyle("percent", "0.00%");
        }

        CellStyle dateStyle() {
            return cachedStyle("date", "yyyy-mm-dd");
        }

        CellStyle dateTimeStyle() {
            return cachedStyle("dateTime", "yyyy-mm-dd hh:mm:ss");
        }

        /**
         * 按用途缓存带数据格式的样式
         * @param purpose
         * @param format
         * @return
         */
        private CellStyle cachedStyle(String purpose, String format) {
            return cache.computeIfAbsent(purpose, k -> {
                CellStyle style = wb.createCellStyle();
                style.setDataFormat(wb.createDataFormat().getFormat(format));
                return style;
            });
        }

        private byte[] hexToBytes(String rrggbb) {
            byte[] rgb = new byte[3];
            for (int i = 0; i < 3; i++) {
                rgb[i] = (byte) Integer.parseInt(rrggbb.substring(i * 2, i * 2 + 2), 16);
            }
            return rgb;
        }

        private HorizontalAlignment resolveAlign(String align) {
            return switch (align) {
                case "left" -> HorizontalAlignment.LEFT;
                case "right" -> HorizontalAlignment.RIGHT;
                default -> HorizontalAlignment.CENTER;
            };
        }
    }
}
