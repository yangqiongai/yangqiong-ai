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

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Excel工作簿增量编辑测试
 * @author yangqiong
 */
class ExcelEditorTest {

    @TempDir
    Path tempDir;

    private final ExcelLimits limits = ExcelLimits.of(null, null, null);

    /**
     * 预置一份节假日工作簿作为编辑对象
     * @return
     */
    private Path prepareWorkbook() throws Exception {
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("报表.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        ExcelDsl.SheetDsl main = new ExcelDsl.SheetDsl();
        main.setName("节假日安排");
        main.setHeader(List.of("节日名称", "放假开始", "放假天数"));
        main.setRows(List.of(
                List.of("元旦", "2026-01-01", 1),
                List.of("春节", "2026-02-15", 7),
                List.of("元旦", "2027-01-01", 2)));
        ExcelDsl.SheetDsl temp = new ExcelDsl.SheetDsl();
        temp.setName("临时");
        temp.setHeader(List.of("x"));
        temp.setRows(List.of(List.of(1)));
        workbook.setSheets(List.of(main, temp));
        dsl.setWorkbook(workbook);
        Path target = tempDir.resolve("报表.xlsx");
        ExcelGenerator.generate(dsl, target, limits);
        return target;
    }

    /**
     * 构建编辑DSL
     * @param ops
     * @return
     */
    private ExcelEditDsl editDsl(Path path, ExcelEditDsl.EditOperation... ops) {
        ExcelEditDsl dsl = new ExcelEditDsl();
        dsl.setPath(tempDir.relativize(path).toString());
        dsl.setOperations(List.of(ops));
        return dsl;
    }

    private ExcelEditDsl.EditOperation op(String type) {
        ExcelEditDsl.EditOperation op = new ExcelEditDsl.EditOperation();
        op.setOp(type);
        return op;
    }

    @Test
    void setCell按表头条件定位修改() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("setCell");
        op.setSheet("节假日安排");
        op.setMatch(Map.of("节日名称", "春节"));
        op.setColumn("放假天数");
        op.setValue(8);
        ExcelEditor.EditResult result = ExcelEditor.apply(editDsl(target, op), target, limits);

        assertThat(result.getApplied()).isEqualTo(1);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getSheet("节假日安排").getRow(2).getCell(2).getNumericCellValue()).isEqualTo(8.0);
            // 未触及行保留
            assertThat(wb.getSheet("节假日安排").getRow(1).getCell(2).getNumericCellValue()).isEqualTo(1.0);
        }
    }

    @Test
    void setCell按列字母条件定位且多行命中改第一行() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("setCell");
        op.setSheet("节假日安排");
        op.setMatch(Map.of("A", "元旦"));
        op.setColumn("C");
        op.setValue(9);
        ExcelEditor.apply(editDsl(target, op), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getSheet("节假日安排").getRow(1).getCell(2).getNumericCellValue()).isEqualTo(9.0);
            assertThat(wb.getSheet("节假日安排").getRow(3).getCell(2).getNumericCellValue()).isEqualTo(2.0);
        }
    }

    @Test
    void setCell零命中报错且文件不被改动() throws Exception {
        Path target = prepareWorkbook();
        byte[] before = Files.readAllBytes(target);
        ExcelEditDsl.EditOperation op = op("setCell");
        op.setSheet("节假日安排");
        op.setMatch(Map.of("节日名称", "不存在"));
        op.setColumn("放假天数");
        op.setValue(5);
        assertThatThrownBy(() -> ExcelEditor.apply(editDsl(target, op), target, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第1条操作失败").hasMessageContaining("未命中");
        assertThat(Files.readAllBytes(target)).isEqualTo(before);
    }

    @Test
    void addRow追加到末尾() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("addRow");
        op.setSheet("节假日安排");
        op.setValues(List.of("元旦补假", "2027-01-02", 1));
        ExcelEditor.apply(editDsl(target, op), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            Sheet s = wb.getSheet("节假日安排");
            assertThat(s.getRow(4).getCell(0).getStringCellValue()).isEqualTo("元旦补假");
            assertThat(s.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(1.0);
        }
    }

    @Test
    void deleteRow删除所有命中行() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("deleteRow");
        op.setSheet("节假日安排");
        op.setMatch(Map.of("节日名称", "元旦"));
        ExcelEditor.EditResult result = ExcelEditor.apply(editDsl(target, op), target, limits);
        assertThat(result.getDetail()).contains("删除2行");
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            Sheet s = wb.getSheet("节假日安排");
            assertThat(s.getLastRowNum()).isEqualTo(1);
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("春节");
        }
    }

    @Test
    void addSheet新增deleteSheet保留至少一个sheet() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation add = op("addSheet");
        add.setSheet("统计");
        add.setHeader(List.of("指标", "值"));
        add.setRows(List.of(List.of("总天数", 10)));
        ExcelEditDsl.EditOperation delete = op("deleteSheet");
        delete.setSheet("临时");
        ExcelEditor.apply(editDsl(target, add, delete), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheet("统计").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(10.0);
        }

        // 仅剩一个sheet时删除被拒绝
        Path single = tempDir.resolve("单表.xlsx");
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("单表.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        ExcelDsl.SheetDsl only = new ExcelDsl.SheetDsl();
        only.setName("唯一");
        only.setHeader(List.of("a"));
        workbook.setSheets(List.of(only));
        dsl.setWorkbook(workbook);
        ExcelGenerator.generate(dsl, single, limits);
        ExcelEditDsl.EditOperation del = op("deleteSheet");
        del.setSheet("唯一");
        assertThatThrownBy(() -> ExcelEditor.apply(editDsl(single, del), single, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少保留一个sheet");
    }

    @Test
    void renameSheet正反用例() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("renameSheet");
        op.setFrom("临时");
        op.setTo("汇总");
        ExcelEditor.apply(editDsl(target, op), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(wb.getSheet("汇总")).isNotNull();
            assertThat(wb.getSheet("临时")).isNull();
        }

        ExcelEditDsl.EditOperation dup = op("renameSheet");
        dup.setFrom("节假日安排");
        dup.setTo("汇总");
        assertThatThrownBy(() -> ExcelEditor.apply(editDsl(target, dup), target, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目标sheet名已存在");
    }

    @Test
    void setStyle按区域生效() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("setStyle");
        op.setSheet("节假日安排");
        op.setRange("A1:C1");
        ExcelDsl.StyleDsl style = new ExcelDsl.StyleDsl();
        style.setBold(true);
        op.setStyle(style);
        ExcelEditor.apply(editDsl(target, op), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            assertThat(((org.apache.poi.xssf.usermodel.XSSFCellStyle) wb.getSheet("节假日安排")
                    .getRow(0).getCell(0).getCellStyle()).getFont().getBold()).isTrue();
        }
    }

    @Test
    void sort升降序与空值处理() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("sort");
        op.setSheet("节假日安排");
        op.setBy("放假天数");
        op.setOrder("asc");
        ExcelEditor.apply(editDsl(target, op), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            Sheet s = wb.getSheet("节假日安排");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("元旦");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("元旦");
            assertThat(s.getRow(3).getCell(0).getStringCellValue()).isEqualTo("春节");
            // 表头不动
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("节日名称");
        }

        ExcelEditDsl.EditOperation desc = op("sort");
        desc.setSheet("节假日安排");
        desc.setBy("放假天数");
        desc.setOrder("desc");
        ExcelEditor.apply(editDsl(target, desc), target, limits);
        try (Workbook wb = WorkbookFactory.create(Files.newInputStream(target))) {
            Sheet s = wb.getSheet("节假日安排");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("春节");
        }
    }

    @Test
    void 中间操作失败整体不落盘() throws Exception {
        Path target = prepareWorkbook();
        byte[] before = Files.readAllBytes(target);
        ExcelEditDsl.EditOperation ok = op("addRow");
        ok.setSheet("节假日安排");
        ok.setValues(List.of("新增行", "2027-01-03", 1));
        ExcelEditDsl.EditOperation bad = op("setCell");
        bad.setSheet("不存在的sheet");
        bad.setMatch(Map.of("A", "x"));
        bad.setColumn("A");
        bad.setValue("y");
        assertThatThrownBy(() -> ExcelEditor.apply(editDsl(target, ok, bad), target, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第2条操作失败");
        assertThat(Files.readAllBytes(target)).isEqualTo(before);
    }

    @Test
    void sheet不存在报错() throws Exception {
        Path target = prepareWorkbook();
        ExcelEditDsl.EditOperation op = op("addRow");
        op.setSheet("不存在");
        op.setValues(List.of("a"));
        assertThatThrownBy(() -> ExcelEditor.apply(editDsl(target, op), target, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sheet不存在");
    }
}
