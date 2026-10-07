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
package com.yangqiongai.ai.agent.local.workspace.docx;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WordDSL解析校验测试
 * @author yangqiong
 */
class DocxValidatorTest {

    /**
     * 构建最小可用创建DSL
     * @param blocks
     * @return
     */
    private DocxDsl createDsl(DocxDsl.Block... blocks) {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("纪要.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        document.setBlocks(List.of(blocks));
        dsl.setDocument(document);
        return dsl;
    }

    private DocxDsl.Block block(String type) {
        DocxDsl.Block block = new DocxDsl.Block();
        block.setType(type);
        return block;
    }

    private DocxDsl.Block heading(String text, Integer level) {
        DocxDsl.Block block = block("heading");
        block.setText(text);
        block.setLevel(level);
        return block;
    }

    private DocxDsl.Block paragraph(String text) {
        DocxDsl.Block block = block("paragraph");
        block.setText(text);
        return block;
    }

    /**
     * 构建编辑DSL
     * @param ops
     * @return
     */
    private DocxEditDsl editDsl(DocxEditDsl.EditOperation... ops) {
        DocxEditDsl dsl = new DocxEditDsl();
        dsl.setPath("纪要.docx");
        dsl.setOperations(List.of(ops));
        return dsl;
    }

    private DocxEditDsl.EditOperation op(String type) {
        DocxEditDsl.EditOperation op = new DocxEditDsl.EditOperation();
        op.setOp(type);
        return op;
    }

    @Test
    void 合法创建DSL通过校验() {
        DocxDsl.Block table = block("table");
        table.setHeader(List.of("事项", "负责人"));
        table.setRows(List.of(List.of("联调", "张三")));
        DocxDsl.Block image = block("image");
        image.setImagePath("图/架构.png");
        image.setWidth(300);
        DocxDsl dsl = createDsl(heading("一级标题", 1), paragraph("正文"), table, image);
        dsl.getDocument().getBlocks().get(1).setAlign("center");
        dsl.getDocument().getBlocks().get(1).setBold(true);
        DocxValidator.validateCreate(dsl);
    }

    @Test
    void path缺失或后缀非法被拒绝() {
        DocxDsl dsl = createDsl(paragraph("正文"));
        dsl.setPath(null);
        assertThatThrownBy(() -> DocxValidator.validateCreate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("path不能为空");

        dsl.setPath("文档.txt");
        assertThatThrownBy(() -> DocxValidator.validateCreate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".docx");
    }

    @Test
    void document或blocks为空被拒绝() {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("a.docx");
        assertThatThrownBy(() -> DocxValidator.validateCreate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("document不能为空");

        dsl.setDocument(new DocxDsl.DocumentDsl());
        assertThatThrownBy(() -> DocxValidator.validateCreate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blocks不能为空");
    }

    @Test
    void 内容块超上限被拒绝() {
        List<DocxDsl.Block> blocks = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            blocks.add(paragraph("段" + i));
        }
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("大.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        document.setBlocks(blocks);
        dsl.setDocument(document);
        assertThatThrownBy(() -> DocxValidator.validateCreate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内容块数量超上限").hasMessageContaining("501");
    }

    @Test
    void 非法type被拒绝且错误含字段路径() {
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(block("list"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document.blocks[0].type").hasMessageContaining("list");

        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(block(null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type不能为空");
    }

    @Test
    void 标题级别越界或文本为空被拒绝() {
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(heading("标题", 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".level");

        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(heading("标题", 5))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".level");

        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(heading("标题", null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".level");

        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(heading(" ", 2))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".text不能为空");
    }

    @Test
    void 正文文本为空被拒绝() {
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(paragraph(null))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".text不能为空");

        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(paragraph(""))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".text不能为空");
    }

    @Test
    void 表格表头缺失或超列被拒绝() {
        DocxDsl.Block noHeader = block("table");
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(noHeader)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".header不能为空");

        DocxDsl.Block wide = block("table");
        List<String> header = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            header.add("列" + i);
        }
        wide.setHeader(header);
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(wide)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("列数超上限").hasMessageContaining("51");
    }

    @Test
    void 表格行数超上限被拒绝() {
        DocxDsl.Block table = block("table");
        table.setHeader(List.of("列A"));
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 10001; i++) {
            rows.add(List.of());
        }
        table.setRows(rows);
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(table)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("行数超上限").hasMessageContaining("10001");
    }

    @Test
    void 图片路径为空被拒绝() {
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(block("image"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".imagePath不能为空");
    }

    @Test
    void 对齐非法被拒绝() {
        DocxDsl.Block p = paragraph("正文");
        p.setAlign("middle");
        assertThatThrownBy(() -> DocxValidator.validateCreate(createDsl(p)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".align").hasMessageContaining("middle");
    }

    @Test
    void 编辑DSL操作校验() {
        DocxEditDsl dsl = new DocxEditDsl();
        dsl.setPath("a.docx");
        DocxEditDsl.EditOperation op = new DocxEditDsl.EditOperation();
        dsl.setOperations(List.of(op));
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("op不能为空");

        op.setOp("unknown");
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("op非法");

        op.setOp("replaceText");
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("from不能为空");

        op.setFrom("旧词");
        DocxValidator.validateEdit(dsl);

        op.setOp("insertPara");
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text不能为空");

        op.setText("新段");
        op.setType("table");
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".type非法");

        op.setType("heading");
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".level");

        op.setLevel(4);
        DocxValidator.validateEdit(dsl);

        // type缺省视为paragraph
        op.setType(null);
        op.setLevel(null);
        DocxValidator.validateEdit(dsl);

        op.setOp("addTable");
        op.setText(null);
        assertThatThrownBy(() -> DocxValidator.validateEdit(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("header不能为空");

        op.setHeader(List.of("列A"));
        op.setRows(List.of(List.of("a")));
        DocxValidator.validateEdit(dsl);
    }

    @Test
    void 操作条数超上限被拒绝() {
        DocxEditDsl.EditOperation[] ops = new DocxEditDsl.EditOperation[201];
        for (int i = 0; i < ops.length; i++) {
            ops[i] = op("replaceText");
            ops[i].setFrom("旧" + i);
        }
        assertThatThrownBy(() -> DocxValidator.validateEdit(editDsl(ops)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("操作数量超上限").hasMessageContaining("201");
    }

    @Test
    void 解析失败报含上下文的错误() {
        assertThatThrownBy(() -> DocxValidator.parseCreate("{not json"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON解析失败");
        assertThatThrownBy(() -> DocxValidator.parseEdit("[]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON解析失败");
    }

    @Test
    void 解析容忍未知字段() {
        DocxDsl dsl = DocxValidator.parseCreate(
                "{\"path\":\"a.docx\",\"document\":{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"正文\",\"extra\":1}]},\"extra2\":true}");
        assertThat(dsl.getDocument().getBlocks()).hasSize(1);
        assertThat(dsl.getDocument().getBlocks().get(0).getText()).isEqualTo("正文");
    }
}
