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

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("文档解析服务单元测试")
class DocParserServiceTest {

    /**
     * 无OCR引擎的解析服务
     */
    private DocParserService service;

    /**
     * 可调配置
     */
    private ConnectorProperties properties;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new ConnectorProperties();
        ObjectProvider<OcrEngine> provider = mock(ObjectProvider.class);
        service = new DocParserService(properties, provider);
    }

    @Test
    @DisplayName("空文件与超限文件拒绝解析")
    void shouldRejectEmptyAndOversizedFile() {
        assertThatThrownBy(() -> service.parse("a.txt", null, new byte[0]))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("文件内容不能为空");

        properties.setDocparserMaxFileSizeMb(1);
        assertThatThrownBy(() -> service.parse("a.txt", null, new byte[1024 * 1024 + 1]))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("最大限制");
    }

    @Test
    @DisplayName("不支持的文件类型抛出参数异常")
    void shouldRejectUnsupportedType() {
        assertThatThrownBy(() -> service.parse("a.exe", null, "x".getBytes()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    @DisplayName("旧版doc格式明确提示另存为docx")
    void shouldRejectLegacyDoc() {
        assertThatThrownBy(() -> service.parse("a.doc", null, "x".getBytes()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("docx");
    }

    @Test
    @DisplayName("无扩展名时按内容类型识别")
    void shouldFallbackToContentType() {
        DocParseResult result = service.parse("download", "application/pdf",
                PdfTextExtractorTest.createPdfForService("download content"));

        assertThat(result.getFileType()).isEqualTo("pdf");
        assertThat(result.getChunkCount()).isPositive();
    }

    @Test
    @DisplayName("纯文本按配置尺寸分块并顺序编号")
    void shouldChunkPlainTextWithConfiguredSize() {
        properties.setDocparserChunkSize(5);
        properties.setDocparserChunkOverlap(0);

        DocParseResult result = service.parse("note.txt", null,
                "aaaa\nbbbb\ncccc\ndddd".getBytes(StandardCharsets.UTF_8));

        assertThat(result.getFileType()).isEqualTo("txt");
        assertThat(result.getChunks()).allSatisfy(chunk -> assertThat(chunk.getText().length()).isLessThanOrEqualTo(5));
        assertThat(result.getChunks()).extracting(DocParseChunk::getIndex)
                .containsExactly(0, 1, 2, 3);
        assertThat(result.getTotalChars()).isEqualTo(
                result.getChunks().stream().mapToInt(chunk -> chunk.getText().length()).sum());
    }

    @Test
    @DisplayName("PDF端到端解析含页码元数据")
    void shouldParsePdfEndToEnd() {
        byte[] pdf = PdfTextExtractorTest.createPdfForService("line one\nline two");

        DocParseResult result = service.parse("doc.pdf", null, pdf);

        assertThat(result.getFileType()).isEqualTo("pdf");
        assertThat(result.getOcrPageCount()).isZero();
        assertThat(result.getChunks()).isNotEmpty();
        assertThat(result.getChunks().get(0).getMetadata()).containsEntry("page", 1);
    }

    @Test
    @DisplayName("PDF扫描页经OCR引擎兜底并计数")
    void shouldCountOcrPages() {
        properties.setDocparserOcrEnabled(true);
        ObjectProvider<OcrEngine> provider = mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(imageBytes -> "OCR识别结果");
        DocParserService ocrService = new DocParserService(properties, provider);

        DocParseResult result = ocrService.parse("scan.pdf", null,
                PdfTextExtractorTest.createPdfForService(null));

        assertThat(result.getOcrPageCount()).isEqualTo(1);
        assertThat(result.getChunks()).anySatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry("ocr", true));
    }

    @Test
    @DisplayName("Excel端到端解析输出sheet分块")
    void shouldParseExcelEndToEnd() throws Exception {
        byte[] xlsx = createSimpleXlsx();

        DocParseResult result = service.parse("stock.xlsx", null, xlsx);

        assertThat(result.getFileType()).isEqualTo("xlsx");
        assertThat(result.getChunks()).hasSize(1);
        assertThat(result.getChunks().get(0).getType()).isEqualTo("sheet");
        assertThat(result.getChunks().get(0).getText()).contains("商品 | 数量", "苹果 | 10");
    }

    /**
     * 生成单sheet测试xlsx
     * @return
     */
    private static byte[] createSimpleXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("库存");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("商品");
            header.createCell(1).setCellValue("数量");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("苹果");
            row.createCell(1).setCellValue(10);
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}
