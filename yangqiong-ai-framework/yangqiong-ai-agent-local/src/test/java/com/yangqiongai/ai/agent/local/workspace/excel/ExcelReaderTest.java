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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Excel工作簿读回测试
 * @author yangqiong
 */
class ExcelReaderTest {

    @TempDir
    Path tempDir;

    private final ExcelLimits limits = ExcelLimits.of(null, null, null);

    /**
     * 构建双sheet工作簿并落盘
     * @param extraRows 额外数据行数（用于截断测试）
     * @return
     */
    private Path buildWorkbook(int extraRows) throws Exception {
        ExcelDsl dsl = new ExcelDsl();
        dsl.setPath("读回.xlsx");
        ExcelDsl.WorkbookDsl workbook = new ExcelDsl.WorkbookDsl();

        ExcelDsl.SheetDsl main = new ExcelDsl.SheetDsl();
        main.setName("节假日安排");
        main.setHeader(List.of("节日名称", "天数", "日期"));
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("元旦", 3, "2026-01-01"));
        for (int i = 0; i < extraRows; i++) {
            rows.add(List.of("行" + i, 1, "2026-01-02"));
        }
        main.setRows(rows);
        main.setColumnTypes(List.of("text", "number", "date"));
        ExcelDsl.FormulaDsl formula = new ExcelDsl.FormulaDsl();
        formula.setCell("B3");
        formula.setExpr("SUM(B2:B2)");
        main.setFormulas(List.of(formula));

        ExcelDsl.SheetDsl second = new ExcelDsl.SheetDsl();
        second.setName("调休上班日");
        second.setHeader(List.of("日期", "所属节假日"));
        second.setRows(List.of(List.of("2026-02-14", "春节")));

        workbook.setSheets(List.of(main, second));
        dsl.setWorkbook(workbook);
        Path target = tempDir.resolve("读回.xlsx");
        ExcelGenerator.generate(dsl, target, limits);
        return target;
    }

    @Test
    void 多sheet读回含类型与公式() throws Exception {
        Path target = buildWorkbook(1);
        String json = ExcelReader.readAsJson(target);
        JsonNode root = new ObjectMapper().readTree(json);

        assertThat(root.get("sheets").size()).isEqualTo(2);
        JsonNode main = root.get("sheets").get(0);
        assertThat(main.get("name").asText()).isEqualTo("节假日安排");
        assertThat(main.get("rowCount").asInt()).isEqualTo(3);
        assertThat(main.get("header").get(0).asText()).isEqualTo("节日名称");
        assertThat(main.get("rows").get(0).get(0).asText()).isEqualTo("元旦");
        // 数值整数化
        assertThat(main.get("rows").get(0).get(1).asLong()).isEqualTo(3);
        // 真日期格式化文本
        assertThat(main.get("rows").get(0).get(2).asText()).isEqualTo("2026-01-01");
        // 公式单元格读回表达式（B3覆盖第2数据行）
        assertThat(main.get("rows").get(1).get(1).asText()).startsWith("=SUM(");
        assertThat(main.get("truncated").asBoolean()).isFalse();

        JsonNode second = root.get("sheets").get(1);
        assertThat(second.get("name").asText()).isEqualTo("调休上班日");
        assertThat(second.get("rows").get(0).get(1).asText()).isEqualTo("春节");
    }

    @Test
    void 超百行截断并标记() throws Exception {
        Path target = buildWorkbook(120);
        JsonNode root = new ObjectMapper().readTree(ExcelReader.readAsJson(target));
        JsonNode main = root.get("sheets").get(0);
        assertThat(main.get("rowCount").asInt()).isEqualTo(122);
        assertThat(main.get("rows").size()).isEqualTo(100);
        assertThat(main.get("truncated").asBoolean()).isTrue();
        assertThat(main.get("rows").get(99).get(0).asText()).isEqualTo("行98");
    }

    @Test
    void 公式单元格读回表达式() throws Exception {
        Path target = buildWorkbook(0);
        JsonNode root = new ObjectMapper().readTree(ExcelReader.readAsJson(target));
        // B3为公式单元格（第2数据行位置），读回为"=SUM(...)"
        assertThat(root.get("sheets").get(0).get("rows").get(1).get(1).asText())
                .startsWith("=SUM(");
    }

    @Test
    void 中文内容读回编码正确() throws Exception {
        Path target = buildWorkbook(0);
        String json = ExcelReader.readAsJson(target);
        assertThat(json).contains("节假日安排").contains("元旦").contains("春节");
    }
}
