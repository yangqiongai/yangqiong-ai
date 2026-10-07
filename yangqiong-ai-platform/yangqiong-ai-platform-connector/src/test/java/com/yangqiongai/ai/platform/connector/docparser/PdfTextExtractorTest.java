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
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PDF文本提取单元测试")
class PdfTextExtractorTest {

    @Test
    @DisplayName("文本层按页提取并携带页码元数据")
    void shouldExtractTextLayerPerPage() throws Exception {
        byte[] pdf = createPdf("first page content", "second page content");
        PdfTextExtractor extractor = new PdfTextExtractor(true, null);

        List<DocSegment> segments = extractor.extract(pdf);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getType()).isEqualTo("text");
        assertThat(segments.get(0).getMetadata().get("page")).isEqualTo(1);
        assertThat(segments.get(0).getText()).contains("first page content");
        assertThat(segments.get(1).getMetadata().get("page")).isEqualTo(2);
        assertThat(segments.get(1).getMetadata()).doesNotContainKey("ocr");
    }

    @Test
    @DisplayName("多行文本完整提取")
    void shouldExtractMultiLineText() throws Exception {
        byte[] pdf = createPdf("line one\nline two");

        List<DocSegment> segments = new PdfTextExtractor(true, null).extract(pdf);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).getText()).contains("line one");
        assertThat(segments.get(0).getText()).contains("line two");
    }

    @Test
    @DisplayName("空白页无OCR引擎时跳过")
    void shouldSkipBlankPageWithoutOcrEngine() throws Exception {
        byte[] pdf = createPdf("has text", null, "more text");
        PdfTextExtractor extractor = new PdfTextExtractor(true, null);

        List<DocSegment> segments = extractor.extract(pdf);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).getMetadata().get("page")).isEqualTo(1);
        assertThat(segments.get(1).getMetadata().get("page")).isEqualTo(3);
    }

    @Test
    @DisplayName("空白页OCR开启且有引擎时执行识别兜底")
    void shouldFallbackToOcrWhenEnabled() throws Exception {
        byte[] pdf = createPdf((String) null);
        PdfTextExtractor extractor = new PdfTextExtractor(true, imageBytes -> "扫描件识别文本");

        List<DocSegment> segments = extractor.extract(pdf);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).getText()).isEqualTo("扫描件识别文本");
        assertThat(segments.get(0).getMetadata().get("ocr")).isEqualTo(true);
    }

    @Test
    @DisplayName("OCR关闭时空白页跳过")
    void shouldSkipOcrWhenDisabled() throws Exception {
        byte[] pdf = createPdf((String) null);
        PdfTextExtractor extractor = new PdfTextExtractor(false, imageBytes -> "不应调用");

        List<DocSegment> segments = extractor.extract(pdf);

        assertThat(segments).isEmpty();
    }

    /**
     * 供服务测试复用：生成单页PDF（null为空白扫描页）
     * @param text 页面文本
     * @return
     */
    static byte[] createPdfForService(String text) {
        try {
            return createPdf(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 生成测试PDF（null表示空白页，文本仅支持ASCII）
     * @param pageTexts 各页文本
     * @return
     */
    private static byte[] createPdf(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                if (pageText != null && !pageText.isEmpty()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.setFont(PDType1Font.HELVETICA, 12);
                        float y = 700;
                        for (String line : pageText.split("\n")) {
                            stream.beginText();
                            stream.newLineAtOffset(50, y);
                            stream.showText(line);
                            stream.endText();
                            y -= 16;
                        }
                    }
                }
            }
            document.save(bos);
            return bos.toByteArray();
        }
    }
}
