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

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Word文档增量编辑测试
 * @author yangqiong
 */
class DocxEditorTest {

    @TempDir
    Path tempDir;

    /**
     * 预置一份带段落与表格的docx作为编辑对象
     * @return
     * @throws Exception
     */
    private Path prepareDoc() throws Exception {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("纪要.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        DocxDsl.Block h1 = new DocxDsl.Block();
        h1.setType("heading");
        h1.setLevel(1);
        h1.setText("项目纪要");
        DocxDsl.Block p1 = new DocxDsl.Block();
        p1.setType("paragraph");
        p1.setText("原定本周完成联调测试");
        DocxDsl.Block tableBlock = new DocxDsl.Block();
        tableBlock.setType("table");
        tableBlock.setHeader(List.of("事项", "负责人"));
        tableBlock.setRows(List.of(List.of("联调", "张三"), List.of("上线", "李四")));
        document.setBlocks(List.of(h1, p1, tableBlock));
        dsl.setDocument(document);
        Path target = tempDir.resolve("纪要.docx");
        DocxGenerator.generate(dsl, target, tempDir);
        return target;
    }

    /**
     * 构建编辑DSL
     * @param path
     * @param ops
     * @return
     */
    private DocxEditDsl editDsl(Path path, DocxEditDsl.EditOperation... ops) {
        DocxEditDsl dsl = new DocxEditDsl();
        dsl.setPath(tempDir.relativize(path).toString());
        dsl.setOperations(List.of(ops));
        return dsl;
    }

    private DocxEditDsl.EditOperation op(String type) {
        DocxEditDsl.EditOperation op = new DocxEditDsl.EditOperation();
        op.setOp(type);
        return op;
    }

    @Test
    void replaceText命中段落与表格文本() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("replaceText");
        op.setFrom("联调");
        op.setTo("集成测试");
        DocxEditor.EditResult result = DocxEditor.apply(editDsl(target, op), target);

        assertThat(result.getApplied()).isEqualTo(1);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            assertThat(doc.getParagraphs().get(1).getText()).isEqualTo("原定本周完成集成测试测试");
            XWPFTable table = doc.getTables().get(0);
            assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("集成测试");
            // 未命中行不动
            assertThat(table.getRow(2).getCell(1).getText()).isEqualTo("李四");
        }
    }

    @Test
    void replaceText缺省to删除文本() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("replaceText");
        op.setFrom("本周");
        DocxEditor.apply(editDsl(target, op), target);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            assertThat(doc.getParagraphs().get(1).getText()).isEqualTo("原定完成联调测试");
        }
    }

    @Test
    void replaceText未命中不报错且内容不被改动() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("replaceText");
        op.setFrom("不存在的词");
        op.setTo("x");
        DocxEditor.EditResult result = DocxEditor.apply(editDsl(target, op), target);
        assertThat(result.getApplied()).isEqualTo(1);
        assertThat(result.getDetail()).contains("0处");
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            // 内容原样保留
            assertThat(doc.getParagraphs().get(1).getText()).isEqualTo("原定本周完成联调测试");
        }
    }

    @Test
    void insertPara命中after后插入() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("insertPara");
        op.setAfter("原定");
        op.setText("插入的新段落");
        op.setType("paragraph");
        DocxEditor.apply(editDsl(target, op), target);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            // 段落顺序：标题、原段落、新段落、（表格）
            assertThat(doc.getParagraphs()).hasSize(3);
            assertThat(doc.getParagraphs().get(1).getText()).isEqualTo("原定本周完成联调测试");
            assertThat(doc.getParagraphs().get(2).getText()).isEqualTo("插入的新段落");
        }
    }

    @Test
    void insertPara未命中或after为空追加文末() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("insertPara");
        op.setAfter("不存在的锚点");
        op.setText("文末段");
        op.setType("paragraph");
        DocxEditor.EditResult result = DocxEditor.apply(editDsl(target, op), target);
        assertThat(result.getDetail()).contains("文末追加");

        DocxEditDsl.EditOperation op2 = op("insertPara");
        op2.setText("再次追加");
        op2.setType("paragraph");
        DocxEditor.apply(editDsl(target, op2), target);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            assertThat(doc.getParagraphs().get(doc.getParagraphs().size() - 2).getText()).isEqualTo("文末段");
            assertThat(doc.getParagraphs().get(doc.getParagraphs().size() - 1).getText()).isEqualTo("再次追加");
        }
    }

    @Test
    void insertPara标题类型应用Heading样式() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("insertPara");
        op.setAfter("原定");
        op.setText("二级标题");
        op.setType("heading");
        op.setLevel(2);
        DocxEditor.apply(editDsl(target, op), target);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            XWPFParagraph newPara = doc.getParagraphs().get(2);
            assertThat(newPara.getText()).isEqualTo("二级标题");
            assertThat(newPara.getStyleID()).isEqualTo("Heading2");
        }
    }

    @Test
    void addTable文末追加表格() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("addTable");
        op.setHeader(List.of("指标", "值"));
        op.setRows(List.of(List.of("完成率", "90%")));
        DocxEditor.apply(editDsl(target, op), target);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            assertThat(doc.getTables()).hasSize(2);
            XWPFTable newTable = doc.getTables().get(1);
            assertThat(newTable.getRow(0).getCell(0).getText()).isEqualTo("指标");
            assertThat(newTable.getRow(1).getCell(1).getText()).isEqualTo("90%");
        }
    }

    @Test
    void 中间操作失败整体不落盘() throws Exception {
        Path target = prepareDoc();
        byte[] before = Files.readAllBytes(target);
        DocxEditDsl.EditOperation ok = op("replaceText");
        ok.setFrom("联调");
        ok.setTo("集成");
        DocxEditDsl.EditOperation bad = op("replaceText");
        // from为空在执行时触发
        bad.setFrom("");
        bad.setTo("x");
        assertThatThrownBy(() -> DocxEditor.apply(editDsl(target, ok, bad), target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第2条操作失败");
        assertThat(Files.readAllBytes(target)).isEqualTo(before);
    }

    @Test
    void from为空报错() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("replaceText");
        op.setFrom("");
        op.setTo("x");
        assertThatThrownBy(() -> DocxEditor.apply(editDsl(target, op), target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from不能为空");
    }

    @Test
    void 非法操作类型报错() throws Exception {
        Path target = prepareDoc();
        DocxEditDsl.EditOperation op = op("unknown");
        assertThatThrownBy(() -> DocxEditor.apply(editDsl(target, op), target))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法操作类型");
    }
}
