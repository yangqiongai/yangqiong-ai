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

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Excel工作簿生成测试
 * @author yangqiong
 */
class ExcelGeneratorTest {

    @TempDir
    Path tempDir;

    private final ExcelLimits limits = ExcelLimits.of(null, null, null);

    /**
     * 构建带完整特性的双sheet工作簿DSL
     * @return
     */
    private ExcelDsl buildMultiSheetDsl() {
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("报表.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();

        ExcelDsl.SheetDsl main = new ExcelDsl.SheetDsl();
        main.setName("节假日安排");
        main.setHeader(List.of("节日名称", "放假开始", "放假结束", "放假天数"));
        main.setRows(List.of(
                List.of("元旦", "2026-01-01", "2026-01-03", 3),
                List.of("春节", "2026-02-15", "2026-02-21", 7)));
        main.setColumnTypes(List.of("text", "date", "date", "number"));
        main.setColumnWidths(List.of(14, 12, 12, 10));
        main.setMerges(List.of("A1:D1"));
        ExcelDsl.FormulaDsl formula = new ExcelDsl.FormulaDsl();
        formula.setCell("D4");
        formula.setExpr("SUM(D2:D3)");
        main.setFormulas(List.of(formula));
        main.setFreezeHeader(true);
        ExcelDsl.ConditionalFormatDsl cf = new ExcelDsl.ConditionalFormatDsl();
        cf.setRange("D2:D3");
        cf.setRule("dataBar");
        main.setConditionalFormat(cf);

        ExcelDsl.SheetDsl second = new ExcelDsl.SheetDsl();
        second.setName("调休上班日");
        second.setHeader(List.of("日期", "星期", "所属节假日"));
        second.setRows(List.of(List.of("2026-02-14", "星期六", "春节")));

        workbook.setSheets(List.of(main, second));
        workbook.setActiveSheet("调休上班日");
        dsl.setWorkbook(workbook);
        return dsl;
    }

    @Test
    void 多sheet与真类型落盘后可回读() throws Exception {
        Path target = tempDir.resolve("报表.xlsx");
        ExcelGenerator.GenerateResult result = ExcelGenerator.generate(buildMultiSheetDsl(), target, limits);

        assertThat(Files.exists(target)).isTrue();
        assertThat(result.getSheetCount()).isEqualTo(2);
        assertThat(result.getFileSize()).isGreaterThan(0);
        // xlsx本质是ZIP（OOXML），产出必须为二进制
        assertThat(Files.readAllBytes(target)[0]).isEqualTo((byte) 'P');

        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetName(wb.getActiveSheetIndex())).isEqualTo("调休上班日");

            Sheet main = wb.getSheet("节假日安排");
            assertThat(main.getRow(0).getCell(0).getStringCellValue()).isEqualTo("节日名称");
            assertThat(main.getPaneInformation()).isNotNull();

            Row row1 = main.getRow(1);
            assertThat(row1.getCell(0).getStringCellValue()).isEqualTo("元旦");
            assertThat(row1.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row1.getCell(1).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(row1.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(row1.getCell(3))).isFalse();
            assertThat(row1.getCell(3).getNumericCellValue()).isEqualTo(3.0);

            Sheet second = wb.getSheet("调休上班日");
            assertThat(second.getRow(1).getCell(2).getStringCellValue()).isEqualTo("春节");

            // 公式单元格
            assertThat(main.getRow(3).getCell(3).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(main.getRow(3).getCell(3).getCellFormula()).isEqualTo("SUM(D2:D3)");

            // 合并区域与列宽
            assertThat(main.getNumMergedRegions()).isEqualTo(1);
            assertThat(main.getColumnWidth(0)).isGreaterThan(8 * 256);
            // 条件格式
            assertThat(((XSSFSheet) main).getSheetConditionalFormatting().getNumConditionalFormattings())
                    .isEqualTo(1);
        }
    }

    @Test
    void 货币与百分比类型落盘() throws Exception {
        ExcelDsl.SheetDsl sheet = new ExcelDsl.SheetDsl();
        sheet.setName("数据");
        sheet.setHeader(List.of("名称", "金额", "占比"));
        sheet.setRows(List.of(List.of("A", 1234.5, 0.5)));
        sheet.setColumnTypes(List.of("text", "currency", "percent"));
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("类型.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        workbook.setSheets(List.of(sheet));
        dsl.setWorkbook(workbook);

        Path target = tempDir.resolve("类型.xlsx");
        ExcelGenerator.generate(dsl, target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            Sheet s = wb.getSheet("数据");
            assertThat(s.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1234.5);
            assertThat(s.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(0.5);
            assertThat(s.getRow(1).getCell(2).getCellStyle().getDataFormatString()).isEqualTo("0.00%");
        }
    }

    @Test
    void 日期解析失败时按文本转义写入() throws Exception {
        ExcelDsl.SheetDsl sheet = new ExcelDsl.SheetDsl();
        sheet.setName("s");
        sheet.setHeader(List.of("日期"));
        sheet.setRows(List.of(List.of("not-a-date")));
        sheet.setColumnTypes(List.of("date"));
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("日期.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        workbook.setSheets(List.of(sheet));
        dsl.setWorkbook(workbook);

        Path target = tempDir.resolve("日期.xlsx");
        ExcelGenerator.generate(dsl, target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getSheet("s").getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
        }
    }

    @Test
    void 注入前缀四类字符被转义() throws Exception {
        ExcelDsl.SheetDsl sheet = new ExcelDsl.SheetDsl();
        sheet.setName("s");
        sheet.setHeader(List.of("文本"));
        sheet.setRows(List.of(
                List.of("=HYPERLINK(\"http://evil\")"),
                List.of("+cmd"),
                List.of("-正常负文本"),
                List.of("@cmd")));
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("注入.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        workbook.setSheets(List.of(sheet));
        dsl.setWorkbook(workbook);

        Path target = tempDir.resolve("注入.xlsx");
        ExcelGenerator.generate(dsl, target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            Sheet s = wb.getSheet("s");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).startsWith("'=");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).startsWith("'+");
            assertThat(s.getRow(3).getCell(0).getStringCellValue()).startsWith("'-");
            assertThat(s.getRow(4).getCell(0).getStringCellValue()).startsWith("'@");
        }
    }

    @Test
    void 文件大小超上限报错() {
        ExcelLimits tiny = ExcelLimits.of(null, 10L, null);
        assertThatThrownBy(() -> ExcelGenerator.generate(buildMultiSheetDsl(), tempDir.resolve("大.xlsx"), tiny))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件大小超上限");
    }

    @Test
    void 空行sheet正常生成() throws Exception {
        ExcelDsl.SheetDsl sheet = new ExcelDsl.SheetDsl();
        sheet.setName("空");
        sheet.setHeader(List.of("A", "B"));
        sheet.setRows(new ArrayList<>());
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("空.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        workbook.setSheets(List.of(sheet));
        dsl.setWorkbook(workbook);

        Path target = tempDir.resolve("空.xlsx");
        ExcelGenerator.GenerateResult result = ExcelGenerator.generate(dsl, target, limits);
        assertThat(result.getCellCount()).isEqualTo(2);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getSheet("空").getLastRowNum()).isEqualTo(0);
        }
    }
}
