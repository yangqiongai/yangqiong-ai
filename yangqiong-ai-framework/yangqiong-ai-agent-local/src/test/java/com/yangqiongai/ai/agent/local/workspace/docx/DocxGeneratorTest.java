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

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Word文档生成测试
 * @author yangqiong
 */
class DocxGeneratorTest {

    @TempDir
    Path tempDir;

    /**
     * 构建带标题/正文/表格的创建DSL
     * @return
     */
    private DocxDsl buildDocDsl() {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("纪要.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();

        DocxDsl.Block heading = new DocxDsl.Block();
        heading.setType("heading");
        heading.setText("项目会议纪要");
        heading.setLevel(1);

        DocxDsl.Block center = new DocxDsl.Block();
        center.setType("paragraph");
        center.setText("居中加粗导语");
        center.setAlign("center");
        center.setBold(true);

        DocxDsl.Block normal = new DocxDsl.Block();
        normal.setType("paragraph");
        normal.setText("正文内容");

        DocxDsl.Block table = new DocxDsl.Block();
        table.setType("table");
        table.setHeader(List.of("事项", "负责人"));
        table.setRows(List.of(List.of("联调测试", "张三"), List.of("上线", "李四")));

        document.setBlocks(List.of(heading, center, normal, table));
        dsl.setDocument(document);
        return dsl;
    }

    /**
     * 在工作区根生成一张200x100的测试图片
     * @param name
     * @return
     */
    private Path prepareImage(String name) throws Exception {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Path image = tempDir.resolve(name);
        ImageIO.write(img, "png", image.toFile());
        return image;
    }

    @Test
    void 标题正文表格落盘后可回读() throws Exception {
        Path target = tempDir.resolve("纪要.docx");
        DocxGenerator.GenerateResult result = DocxGenerator.generate(buildDocDsl(), target, tempDir);

        assertThat(Files.exists(target)).isTrue();
        assertThat(result.getBlockCount()).isEqualTo(4);
        assertThat(Files.size(target)).isGreaterThan(0);
        // docx本质是ZIP（OOXML），产出必须为二进制
        assertThat(Files.readAllBytes(target)[0]).isEqualTo((byte) 'P');

        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            assertThat(doc.getParagraphs()).hasSize(3);

            XWPFParagraph heading = doc.getParagraphs().get(0);
            assertThat(heading.getStyleID()).isEqualTo("Heading1");
            assertThat(heading.getText()).isEqualTo("项目会议纪要");

            XWPFParagraph center = doc.getParagraphs().get(1);
            assertThat(center.getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(center.getRuns().get(0).isBold()).isTrue();

            XWPFParagraph normal = doc.getParagraphs().get(2);
            // 未声明对齐时缺省左对齐
            assertThat(normal.getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(normal.getRuns().get(0).isBold()).isFalse();

            assertThat(doc.getTables()).hasSize(1);
            XWPFTable table = doc.getTables().get(0);
            assertThat(table.getRow(0).getCell(0).getText()).isEqualTo("事项");
            assertThat(table.getRow(0).getCell(1).getText()).isEqualTo("负责人");
            assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("联调测试");
            assertThat(table.getRow(2).getCell(1).getText()).isEqualTo("李四");
            // 表头加粗、数据行不加粗
            assertThat(firstRunBold(table.getRow(0).getCell(0))).isTrue();
            assertThat(firstRunBold(table.getRow(1).getCell(0))).isFalse();
        }
    }

    private boolean firstRunBold(XWPFTableCell cell) {
        return cell.getParagraphs().get(0).getRuns().get(0).isBold();
    }

    @Test
    void 行单元格数与表头列数不同时按最大列生成() throws Exception {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("不规则.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        DocxDsl.Block table = new DocxDsl.Block();
        table.setType("table");
        table.setHeader(List.of("列A", "列B"));
        table.setRows(List.of(List.of("仅一列"), List.of("a", "b", "c")));
        document.setBlocks(List.of(table));
        dsl.setDocument(document);

        Path target = tempDir.resolve("不规则.docx");
        DocxGenerator.generate(dsl, target, tempDir);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            XWPFTable table1 = doc.getTables().get(0);
            assertThat(table1.getRow(0).getTableCells()).hasSize(3);
            assertThat(table1.getRow(0).getCell(0).getText()).isEqualTo("列A");
            assertThat(table1.getRow(0).getCell(2).getText()).isEqualTo("");
            assertThat(table1.getRow(1).getCell(0).getText()).isEqualTo("仅一列");
            assertThat(table1.getRow(2).getCell(2).getText()).isEqualTo("c");
        }
    }

    @Test
    void 图片按宽度等比缩放嵌入() throws Exception {
        prepareImage("架构.png");
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("带图.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        DocxDsl.Block image = new DocxDsl.Block();
        image.setType("image");
        image.setImagePath("架构.png");
        image.setWidth(100);
        document.setBlocks(List.of(image));
        dsl.setDocument(document);

        Path target = tempDir.resolve("带图.docx");
        DocxGenerator.generate(dsl, target, tempDir);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            assertThat(doc.getAllPictures()).hasSize(1);
            XWPFParagraph p = doc.getParagraphs().get(0);
            assertThat(p.getRuns().get(0).getEmbeddedPictures()).hasSize(1);
            // 200x100缩放到宽100pt，高50pt（EMU）
            CTDrawing drawing = p.getRuns().get(0).getCTR().getDrawingList().get(0);
            assertThat(drawing.getInlineList().get(0).getExtent().getCx()).isEqualTo(Units.toEMU(100));
            assertThat(drawing.getInlineList().get(0).getExtent().getCy()).isEqualTo(Units.toEMU(50));
        }
    }

    @Test
    void 图片宽度缺省450pt() throws Exception {
        prepareImage("默认宽.png");
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("默认宽.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        DocxDsl.Block image = new DocxDsl.Block();
        image.setType("image");
        image.setImagePath("默认宽.png");
        document.setBlocks(List.of(image));
        dsl.setDocument(document);

        Path target = tempDir.resolve("默认宽.docx");
        DocxGenerator.generate(dsl, target, tempDir);
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(target))) {
            CTDrawing drawing = doc.getParagraphs().get(0).getRuns().get(0).getCTR().getDrawingList().get(0);
            assertThat(drawing.getInlineList().get(0).getExtent().getCx()).isEqualTo(Units.toEMU(450));
        }
    }

    @Test
    void 图片路径越界被拒绝() {
        assertThatThrownBy(() -> DocxGenerator.generate(imageDsl("../外部.png"),
                tempDir.resolve("越界.docx"), tempDir))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("越界");
    }

    @Test
    void 图片不存在被拒绝() {
        assertThatThrownBy(() -> DocxGenerator.generate(imageDsl("不存在.png"),
                tempDir.resolve("缺图.docx"), tempDir))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("图片文件不存在");
    }

    @Test
    void 图片后缀非法被拒绝() throws Exception {
        Files.write(tempDir.resolve("假图.txt"), "文本".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DocxGenerator.generate(imageDsl("假图.txt"),
                tempDir.resolve("后缀.docx"), tempDir))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("png/jpg/jpeg/gif");
    }

    @Test
    void 图片超5MB被拒绝() throws Exception {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        Files.write(tempDir.resolve("大图.png"), big);
        assertThatThrownBy(() -> DocxGenerator.generate(imageDsl("大图.png"),
                tempDir.resolve("超限.docx"), tempDir))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("图片大小超上限");
    }

    private DocxDsl imageDsl(String imagePath) {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("图.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        DocxDsl.Block image = new DocxDsl.Block();
        image.setType("image");
        image.setImagePath(imagePath);
        document.setBlocks(List.of(image));
        dsl.setDocument(document);
        return dsl;
    }
}
