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

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Excel文本提取单元测试")
class ExcelTextExtractorTest {

    @Test
    @DisplayName("xlsx按sheet拆分且数据块携带表头与行区间")
    void shouldChunkXlsxBySheetWithHeader() throws Exception {
        byte[] xlsx = createXlsx();
        ExcelTextExtractor extractor = new ExcelTextExtractor();

        List<DocSegment> segments = extractor.extract(xlsx);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).getType()).isEqualTo("sheet");
        assertThat(segments.get(0).getMetadata().get("sheetName")).isEqualTo("库存");
        assertThat(segments.get(0).getMetadata().get("rows")).isEqualTo("2-4");
        assertThat(segments.get(0).getText()).isEqualTo("商品 | 数量\n苹果 | 10\n香蕉 | 20\n橙子 | 30");
        assertThat(segments.get(1).getMetadata().get("sheetName")).isEqualTo("大表");
    }

    @Test
    @DisplayName("超出行块尺寸的数据按行块拆分且每块带表头")
    void shouldSplitLargeSheetIntoRowBlocks() throws Exception {
        byte[] xlsx = createXlsx();

        List<DocSegment> segments = new ExcelTextExtractor().extract(xlsx);

        DocSegment firstBlock = segments.get(1);
        assertThat(firstBlock.getText()).startsWith("序号 | 数值");
        String[] lines = firstBlock.getText().split("\n");
        // 首行表头+50行数据
        assertThat(lines).hasSize(51);
        assertThat(firstBlock.getMetadata().get("rows")).isEqualTo("2-51");

        DocSegment secondBlock = segments.get(2);
        assertThat(secondBlock.getText().split("\n")).hasSize(11);
        assertThat(secondBlock.getMetadata().get("rows")).isEqualTo("52-61");
    }

    @Test
    @DisplayName("xls旧格式与空sheet兼容")
    void shouldSupportXlsAndSkipEmptySheet() throws Exception {
        byte[] xls;
        try (HSSFWorkbook workbook = new HSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("数据");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("名称");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("值甲");
            workbook.createSheet("空表");
            workbook.write(bos);
            xls = bos.toByteArray();
        }

        List<DocSegment> segments = new ExcelTextExtractor().extract(xls);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).getMetadata().get("sheetName")).isEqualTo("数据");
        assertThat(segments.get(0).getText()).isEqualTo("名称\n值甲");
    }

    @Test
    @DisplayName("仅表头无数据的sheet不产出分段")
    void shouldSkipHeaderOnlySheet() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("仅表头");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("列甲");
            workbook.write(bos);
            xlsx = bos.toByteArray();
        }

        List<DocSegment> segments = new ExcelTextExtractor().extract(xlsx);

        assertThat(segments).isEmpty();
    }

    /**
     * 生成测试xlsx：库存表3行数据、大表60行数据（拆两块）、空表
     * @return
     */
    private static byte[] createXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("商品");
            header.createCell(1).setCellValue("数量");
            String[] items = {"苹果", "香蕉", "橙子"};
            int[] counts = {10, 20, 30};
            for (int i = 0; i < items.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(items[i]);
                row.createCell(1).setCellValue(counts[i]);
            }

            Sheet large = workbook.createSheet("大表");
            Row largeHeader = large.createRow(0);
            largeHeader.createCell(0).setCellValue("序号");
            largeHeader.createCell(1).setCellValue("数值");
            for (int i = 1; i <= 60; i++) {
                Row row = large.createRow(i);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue(i * 2);
            }

            workbook.createSheet("备注空表");
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}
