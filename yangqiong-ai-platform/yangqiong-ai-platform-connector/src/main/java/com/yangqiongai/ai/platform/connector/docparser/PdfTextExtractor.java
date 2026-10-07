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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF文本提取
 * <p>
 * 逐页提取文本层（按位置排序保证阅读顺序），文本层为空的扫描页在OCR开启
 * 且存在OcrEngine实现时渲染页面图片执行识别兜底。
 * </p>
 * @author yangqiong
 */
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    /**
     * OCR渲染分辨率
     */
    private static final float OCR_RENDER_DPI = 150;

    /**
     * OCR兜底开关
     */
    private final boolean ocrEnabled;

    /**
     * OCR引擎（可为null）
     */
    private final OcrEngine ocrEngine;

    public PdfTextExtractor(boolean ocrEnabled, OcrEngine ocrEngine) {
        this.ocrEnabled = ocrEnabled;
        this.ocrEngine = ocrEngine;
    }

    /**
     * 提取PDF分段
     * @param content 文件字节
     * @return 按页分段清单
     */
    public List<DocSegment> extract(byte[] content) {
        List<DocSegment> segments = new ArrayList<>();
        try (PDDocument document = PDDocument.load(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDFRenderer renderer = ocrEnabled && ocrEngine != null ? new PDFRenderer(document) : null;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                boolean usedOcr = false;
                if ((text == null || text.isBlank()) && renderer != null) {
                    text = recognizeByOcr(renderer, page);
                    usedOcr = true;
                }
                if (text != null && !text.isBlank()) {
                    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
                    metadata.put("page", page);
                    if (usedOcr) {
                        metadata.put("ocr", true);
                    }
                    segments.add(new DocSegment("text", text.trim(), metadata));
                }
            }
            return segments;
        } catch (Exception e) {
            throw new IllegalStateException("PDF解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 渲染页面并执行OCR识别
     * @param renderer 页面渲染器
     * @param page 页码（从1开始）
     * @return 识别文本，失败返回null
     */
    private String recognizeByOcr(PDFRenderer renderer, int page) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(page - 1, OCR_RENDER_DPI);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bos);
            return ocrEngine.recognize(bos.toByteArray());
        } catch (Exception e) {
            log.warn("OCR识别失败: page={}, 原因: {}", page, e.getMessage());
            return null;
        }
    }
}
