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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExcelDSL解析校验测试
 * @author yangqiong
 */
class ExcelDslValidatorTest {

    private final ExcelLimits limits = ExcelLimits.of(null, null, null);

    /**
     * 构建最小可用创建DSL
     * @param sheets
     * @return
     */
    private ExcelDsl createDsl(ExcelDsl.SheetDsl... sheets) {
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("测试.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();
        workbook.setSheets(List.of(sheets));
        dsl.setWorkbook(workbook);
        return dsl;
    }

    private ExcelDsl.SheetDsl sheet(String name) {
        ExcelDsl.SheetDsl sheet = new ExcelDsl.SheetDsl();
        sheet.setName(name);
        sheet.setHeader(List.of("列A", "列B"));
        sheet.setRows(List.of(List.of("值1", 1)));
        return sheet;
    }

    @Test
    void 合法创建DSL通过校验() {
        ExcelDsl dsl = createDsl(sheet("节假日安排"), sheet("调休上班日"));
        dsl.getWorkbook().setActiveSheet("调休上班日");
        ExcelDslValidator.validateCreate(dsl, limits);
    }

    @Test
    void path缺失或后缀非法被拒绝() {
        ExcelDsl dsl = createDsl(sheet("s1"));
        dsl.setPath(null);
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("path不能为空");

        dsl.setPath("报表.csv");
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".xlsx");
    }

    @Test
    void 空sheets被拒绝() {
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("a.xlsx");
        dsl.setWorkbook(new ExcelDsl.WorkbookDsl());
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sheets不能为空");
    }

    @Test
    void sheet重名被拒绝且错误含字段路径() {
        ExcelDsl dsl = createDsl(sheet("重复"), sheet("重复"));
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workbook.sheets[1].name").hasMessageContaining("重复");
    }

    @Test
    void 非法列类型被拒绝() {
        ExcelDsl.SheetDsl s = sheet("s1");
        s.setColumnTypes(List.of("text", "float"));
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(s), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("columnTypes").hasMessageContaining("float");
    }

    @Test
    void 公式以等号开头被拒绝() {
        ExcelDsl.SheetDsl s = sheet("s1");
        ExcelDsl.FormulaDsl formula = new ExcelDsl.FormulaDsl();
        formula.setCell("D9");
        formula.setExpr("=SUM(D2:D8)");
        s.setFormulas(List.of(formula));
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(s), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formulas.expr").hasMessageContaining("不需以=开头");
    }

    @Test
    void 非法公式单元格引用被拒绝() {
        ExcelDsl.SheetDsl s = sheet("s1");
        ExcelDsl.FormulaDsl formula = new ExcelDsl.FormulaDsl();
        formula.setCell("XFE1");
        formula.setExpr("SUM(A1:A2)");
        s.setFormulas(List.of(formula));
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(s), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formulas.cell").hasMessageContaining("XFE1");
    }

    @Test
    void activeSheet引用不存在的sheet被拒绝() {
        ExcelDsl dsl = createDsl(sheet("s1"));
        dsl.getWorkbook().setActiveSheet("不存在");
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activeSheet").hasMessageContaining("不存在");
    }

    @Test
    void 非法背景色被拒绝() {
        ExcelDsl.SheetDsl s = sheet("s1");
        ExcelDsl.StyleDsl style = new ExcelDsl.StyleDsl();
        style.setBackground("RED");
        s.setHeaderStyle(style);
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(s), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headerStyle.background").hasMessageContaining("RRGGBB");
    }

    @Test
    void sheet数超上限被拒绝() {
        ExcelDsl.SheetDsl[] sheets = new ExcelDsl.SheetDsl[21];
        for (int i = 0; i < sheets.length; i++) {
            sheets[i] = sheet("s" + i);
        }
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(sheets), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sheet数量超上限").hasMessageContaining("21");
    }

    @Test
    void 单元格总数超上限被拒绝() {
        ExcelDsl.SheetDsl s = new ExcelDsl.SheetDsl();
        s.setName("big");
        s.setHeader(List.of("a", "b"));
        // 2列 x (60001+1行) = 120004 超过默认100000
        Object[] row = List.of("x", 1).toArray();
        s.setRows(new java.util.ArrayList<>());
        for (int i = 0; i < 60001; i++) {
            s.getRows().add(List.of(row));
        }
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(s), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单元格总数超上限");
    }

    @Test
    void 条件格式规则非法被拒绝() {
        ExcelDsl.SheetDsl s = sheet("s1");
        ExcelDsl.ConditionalFormatDsl cf = new ExcelDsl.ConditionalFormatDsl();
        cf.setRange("B2:B10");
        cf.setRule("iconSet");
        s.setConditionalFormat(cf);
        assertThatThrownBy(() -> ExcelDslValidator.validateCreate(createDsl(s), limits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conditionalFormat.rule").hasMessageContaining("iconSet");
    }

    @Test
    void 编辑DSL操作校验() {
        ExcelEditDsl dsl = new ExcelEditDsl();
        dsl.setPath("a.xlsx");
        ExcelEditDsl.EditOperation op = new ExcelEditDsl.EditOperation();
        dsl.setOperations(List.of(op));
        assertThatThrownBy(() -> ExcelDslValidator.validateEdit(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("op不能为空");

        op.setOp("unknown");
        assertThatThrownBy(() -> ExcelDslValidator.validateEdit(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("op非法");

        op.setOp("setCell");
        assertThatThrownBy(() -> ExcelDslValidator.validateEdit(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sheet不能为空");

        op.setSheet("s1");
        assertThatThrownBy(() -> ExcelDslValidator.validateEdit(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("match不能为空");

        op.setMatch(Map.of("列A", "值1"));
        assertThatThrownBy(() -> ExcelDslValidator.validateEdit(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("column不能为空");

        op.setColumn("列B");
        op.setValue(2);
        ExcelDslValidator.validateEdit(dsl, limits);

        op.setOp("sort");
        op.setBy("列A");
        op.setOrder("down");
        assertThatThrownBy(() -> ExcelDslValidator.validateEdit(dsl, limits))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("order非法");
    }

    @Test
    void 解析失败报含上下文的错误() {
        assertThatThrownBy(() -> ExcelDslValidator.parseCreate("{not json"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON解析失败");
        assertThatThrownBy(() -> ExcelDslValidator.parseEdit("[]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON解析失败");
    }

    @Test
    void 解析容忍未知字段() {
        ExcelDsl dsl = ExcelDslValidator.parseCreate(
                "{\"path\":\"a.xlsx\",\"workbook\":{\"sheets\":[{\"name\":\"s1\",\"extra\":1}]},\"extra2\":true}");
        assertThat(dsl.getWorkbook().getSheets()).hasSize(1);
    }

    @Test
    void 单元格引用解析边界() {
        assertThat(ExcelDslValidator.parseCell("A1")).containsExactly(0, 0);
        assertThat(ExcelDslValidator.parseCell("BA999")).containsExactly(998, 52);
        assertThat(ExcelDslValidator.parseCell("A0")).isNull();
        assertThat(ExcelDslValidator.parseCell("XFE1")).isNull();
        assertThat(ExcelDslValidator.parseCell("A1048577")).isNull();
        assertThat(ExcelDslValidator.parseCell("1A")).isNull();
        assertThat(ExcelDslValidator.parseRange("A1:D4")).containsExactly(0, 0, 3, 3);
        assertThat(ExcelDslValidator.parseRange("D4:A1")).containsExactly(0, 0, 3, 3);
        assertThat(ExcelDslValidator.parseRange("A1:E1:F1")).isNull();
        assertThat(ExcelDslValidator.parseRange("abc")).isNull();
    }

    @Test
    void 列定位支持表头名与列字母() {
        assertThat(ExcelDslValidator.resolveColumn("列B", List.of("列A", "列B"))).isEqualTo(1);
        assertThat(ExcelDslValidator.resolveColumn("C", List.of("列A", "列B"))).isEqualTo(2);
        assertThat(ExcelDslValidator.resolveColumn("不存在", List.of("列A"))).isEqualTo(-1);
        assertThat(ExcelDslValidator.resolveColumn("  列A ", List.of("列A"))).isEqualTo(0);
    }
}
