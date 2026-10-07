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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel文本提取
 * <p>
 * 兼容xlsx与xls，按sheet拆分：首个非空行作为表头，
 * 数据行按块分组，每块携带表头行与行区间元数据。
 * </p>
 * @author yangqiong
 */
public class ExcelTextExtractor {

    /**
     * 每块包含的数据行数
     */
    static final int ROWS_PER_CHUNK = 50;

    /**
     * 单元格格式化（数值/日期统一按显示值）
     */
    private static final DataFormatter FORMATTER = new DataFormatter();

    /**
     * 提取Excel分段
     * @param content 文件字节
     * @return 按sheet与行块的分段清单
     */
    public List<DocSegment> extract(byte[] content) {
        List<DocSegment> segments = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (Sheet sheet : workbook) {
                extractSheet(sheet, segments);
            }
            return segments;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Excel解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取单个sheet为分段
     * @param sheet 工作表
     * @param segments 分段输出
     */
    private void extractSheet(Sheet sheet, List<DocSegment> segments) {
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();
        if (firstRow < 0 || lastRow < firstRow || sheet.getRow(firstRow) == null) {
            return;
        }
        String headerLine = linearizeRow(sheet.getRow(firstRow));
        if (headerLine.isBlank()) {
            return;
        }
        for (int blockStart = firstRow + 1; blockStart <= lastRow; blockStart += ROWS_PER_CHUNK) {
            int blockEnd = Math.min(blockStart + ROWS_PER_CHUNK - 1, lastRow);
            StringBuilder sb = new StringBuilder(headerLine).append('\n');
            for (int rowIndex = blockStart; rowIndex <= blockEnd; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String line = linearizeRow(row);
                if (!line.isBlank()) {
                    sb.append(line).append('\n');
                }
            }
            if (sb.length() > headerLine.length() + 1) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("sheetName", sheet.getSheetName());
                metadata.put("rows", (blockStart + 1) + "-" + (blockEnd + 1));
                segments.add(new DocSegment("sheet", sb.toString().trim(), metadata));
            }
        }
    }

    /**
     * 行线性化为单元格拼接文本
     * @param row 行
     * @return 拼接文本
     */
    private String linearizeRow(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            String value = cell == null ? "" : FORMATTER.formatCellValue(cell).trim();
            if (i > 0) {
                sb.append(WordTextExtractor.CELL_SEPARATOR);
            }
            sb.append(value);
        }
        return sb.toString().stripTrailing();
    }
}
