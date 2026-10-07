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
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Word文档生成
 * <p>
 * 按DSL用XWPF确定性产出真实二进制docx，标题用内置Heading样式，图片限定在工作区根内。
 * </p>
 * @author yangqiong
 */
public class DocxGenerator {

    private static final Logger log = LoggerFactory.getLogger(DocxGenerator.class);

    /**
     * 生成文件大小上限（字节）
     */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    /**
     * 图片大小上限（字节）
     */
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    /**
     * 图片默认宽度（pt）
     */
    private static final int DEFAULT_IMAGE_WIDTH = 450;

    private DocxGenerator() {
    }

    /**
     * 生成结果
     */
    public static class GenerateResult {

        /**
         * 写入的内容块数量
         */
        private final int blockCount;

        GenerateResult(int blockCount) {
            this.blockCount = blockCount;
        }

        public int getBlockCount() {
            return blockCount;
        }
    }

    /**
     * 按DSL生成docx文件并落盘
     * @param dsl
     * @param target
     * @param root 工作区根（图片路径限定在其内）
     * @return
     * @throws Exception
     */
    public static GenerateResult generate(DocxDsl dsl, Path target, Path root) throws Exception {
        List<DocxDsl.Block> blocks = dsl.getDocument().getBlocks();
        try (XWPFDocument document = new XWPFDocument()) {
            for (DocxDsl.Block block : blocks) {
                switch (block.getType()) {
                    case "heading" -> writeHeading(document, block);
                    case "paragraph" -> writeParagraph(document, block);
                    case "table" -> writeTable(document, block);
                    case "image" -> writeImage(document, block, root);
                    default -> throw new IllegalArgumentException("非法内容块类型：" + block.getType());
                }
            }
            try (OutputStream out = Files.newOutputStream(target)) {
                document.write(out);
            }
        }
        // 超限文件不保留，直接删除并报错
        long fileSize = Files.size(target);
        if (fileSize > MAX_FILE_SIZE) {
            Files.deleteIfExists(target);
            throw new IllegalArgumentException("生成文件大小超上限：" + (fileSize / 1024 / 1024) + "MB > "
                    + (MAX_FILE_SIZE / 1024 / 1024) + "MB，请减少内容量");
        }
        log.info("Word已生成: {}（blocks={}）", target, blocks.size());
        return new GenerateResult(blocks.size());
    }

    /**
     * 写标题段落（Heading1-4内置样式）
     * @param document
     * @param block
     */
    private static void writeHeading(XWPFDocument document, DocxDsl.Block block) {
        XWPFParagraph p = document.createParagraph();
        p.setStyle("Heading" + block.getLevel());
        applyAlign(p, block.getAlign());
        XWPFRun run = p.createRun();
        run.setText(block.getText());
        if (block.getBold() != null && block.getBold()) {
            run.setBold(true);
        }
    }

    /**
     * 写正文段落
     * @param document
     * @param block
     */
    private static void writeParagraph(XWPFDocument document, DocxDsl.Block block) {
        XWPFParagraph p = document.createParagraph();
        applyAlign(p, block.getAlign());
        XWPFRun run = p.createRun();
        run.setText(block.getText());
        if (block.getBold() != null && block.getBold()) {
            run.setBold(true);
        }
    }

    /**
     * 写表格（首行表头加粗，行单元格数不要求等于表头列数）
     * @param document
     * @param block
     */
    private static void writeTable(XWPFDocument document, DocxDsl.Block block) {
        List<String> header = block.getHeader();
        List<List<String>> rows = block.getRows() != null ? block.getRows() : List.of();
        int cols = header.size();
        for (List<String> row : rows) {
            if (row != null) {
                cols = Math.max(cols, row.size());
            }
        }
        XWPFTable table = document.createTable(rows.size() + 1, cols);
        for (int c = 0; c < cols; c++) {
            setCellText(table.getRow(0).getCell(c), c < header.size() ? header.get(c) : "", true);
        }
        for (int r = 0; r < rows.size(); r++) {
            List<String> values = rows.get(r);
            if (values == null) {
                continue;
            }
            for (int c = 0; c < values.size() && c < cols; c++) {
                setCellText(table.getRow(r + 1).getCell(c), values.get(c), false);
            }
        }
    }

    /**
     * 写图片段落（工作区内png/jpg/jpeg/gif，≤5MB，按width等比缩放）
     * @param document
     * @param block
     * @param root
     * @throws Exception
     */
    private static void writeImage(XWPFDocument document, DocxDsl.Block block, Path root) throws Exception {
        Path image = resolveImage(root, block.getImagePath());
        byte[] data;
        try (InputStream in = Files.newInputStream(image)) {
            data = in.readAllBytes();
        }
        if (data.length > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片大小超上限：" + (data.length / 1024 / 1024) + "MB > 5MB，"
                    + block.getImagePath());
        }
        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(data));
        if (buffered == null) {
            throw new IllegalArgumentException("图片无法解析：" + block.getImagePath());
        }
        int width = block.getWidth() != null && block.getWidth() > 0 ? block.getWidth() : DEFAULT_IMAGE_WIDTH;
        int height = Math.max(1, (int) Math.round((double) buffered.getHeight() * width / buffered.getWidth()));
        int pictureType = pictureType(block.getImagePath());
        XWPFParagraph p = document.createParagraph();
        applyAlign(p, block.getAlign());
        XWPFRun run = p.createRun();
        try (InputStream in = new ByteArrayInputStream(data)) {
            run.addPicture(in, pictureType, image.getFileName().toString(),
                    Units.toEMU(width), Units.toEMU(height));
        }
    }

    /**
     * 校验并解析图片路径（限定工作区根内，png/jpg/jpeg/gif后缀）
     * @param root
     * @param imagePath
     * @return
     */
    private static Path resolveImage(Path root, String imagePath) throws java.io.IOException {
        if (imagePath.contains("..")) {
            throw new IllegalArgumentException("image.imagePath非法或越界：" + imagePath);
        }
        Path resolved = root.resolve(imagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("image.imagePath非法或越界：" + imagePath);
        }
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("图片文件不存在：" + imagePath);
        }
        String lower = imagePath.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".png") && !lower.endsWith(".jpg")
                && !lower.endsWith(".jpeg") && !lower.endsWith(".gif")) {
            throw new IllegalArgumentException("image.imagePath仅支持png/jpg/jpeg/gif：" + imagePath);
        }
        return resolved;
    }

    /**
     * 按后缀解析图片类型常量
     * @param imagePath
     * @return
     */
    private static int pictureType(String imagePath) {
        String lower = imagePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        }
        return XWPFDocument.PICTURE_TYPE_JPEG;
    }

    /**
     * 应用水平对齐（缺省左对齐）
     * @param p
     * @param align
     */
    private static void applyAlign(XWPFParagraph p, String align) {
        if (align == null) {
            return;
        }
        p.setAlignment(switch (align) {
            case "center" -> ParagraphAlignment.CENTER;
            case "right" -> ParagraphAlignment.RIGHT;
            default -> ParagraphAlignment.LEFT;
        });
    }

    /**
     * 写入单元格文本
     * @param cell
     * @param text
     * @param bold
     */
    private static void setCellText(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph p = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
    }
}
