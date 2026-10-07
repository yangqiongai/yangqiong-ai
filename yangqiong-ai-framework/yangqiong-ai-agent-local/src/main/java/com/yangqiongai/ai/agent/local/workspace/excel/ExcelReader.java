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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel工作簿读回
 * @author yangqiong
 */
public class ExcelReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DataFormatter FORMATTER = new DataFormatter();

    /**
     * 读回给模型的数据行数上限
     */
    private static final int MAX_ROWS = 100;

    /**
     * 日期输出格式
     */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 日期时间输出格式
     */
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExcelReader() {
    }

    /**
     * 解析xlsx/xls为结构化JSON文本（多sheet全量，数据行超100行截断）
     * @param file
     * @return
     * @throws Exception
     */
    public static String readAsJson(Path file) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(readAsMap(file));
    }

    /**
     * 解析xlsx/xls为结构化Map（多sheet全量，数据行超100行截断）
     * @param file
     * @return
     * @throws Exception
     */
    public static Map<String, Object> readAsMap(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file); Workbook wb = WorkbookFactory.create(in)) {
            List<Map<String, Object>> sheets = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheets.add(readSheet(wb.getSheetAt(i)));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sheets", sheets);
            return result;
        }
    }

    /**
     * 读回单个sheet（首行为表头）
     * @param sheet
     * @return
     */
    private static Map<String, Object> readSheet(Sheet sheet) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", sheet.getSheetName());
        int lastRow = sheet.getLastRowNum();
        result.put("rowCount", lastRow + 1);

        List<Object> header = new ArrayList<>();
        if (lastRow >= 0 && sheet.getRow(0) != null) {
            for (Cell cell : sheet.getRow(0)) {
                header.add(cellValue(cell));
            }
        }
        result.put("header", header);

        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        int dataRows = Math.max(lastRow, 0);
        for (int r = 1; r <= dataRows; r++) {
            if (rows.size() >= MAX_ROWS) {
                truncated = true;
                break;
            }
            Row row = sheet.getRow(r);
            List<Object> values = new ArrayList<>();
            if (row != null) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    values.add(cellValue(row.getCell(c)));
                }
            }
            rows.add(values);
        }
        result.put("rows", rows);
        result.put("truncated", truncated);
        return result;
    }

    /**
     * 单元格值转JSON友好对象（公式输出表达式，日期格式化文本）
     * @param cell
     * @return
     */
    private static Object cellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? formatDate(cell.getDateCellValue())
                    : normalizeNumber(cell.getNumericCellValue());
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> "=" + cell.getCellFormula();
            case BLANK -> null;
            default -> FORMATTER.formatCellValue(cell);
        };
    }

    /**
     * 数值规整（整数去掉小数点显示）
     * @param value
     * @return
     */
    private static Object normalizeNumber(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return (long) value;
        }
        return value;
    }

    private static String formatDate(java.util.Date date) {
        var dateTime = java.time.LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        return dateTime.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                ? dateTime.format(DATE_FORMAT) : dateTime.format(DATE_TIME_FORMAT);
    }
}
