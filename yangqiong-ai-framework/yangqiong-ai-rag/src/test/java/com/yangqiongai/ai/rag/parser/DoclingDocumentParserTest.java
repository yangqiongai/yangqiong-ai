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
package com.yangqiongai.ai.rag.parser;

import com.yangqiongai.ai.common.util.DoclingPayloadMapper;
import com.yangqiongai.ai.rag.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docling文档解析器单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DoclingDocumentParserTest {

    @Mock
    private TikaDocumentParser tikaDocumentParser;

    private RagProperties ragProperties;

    private DoclingDocumentParser parser;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        parser = new DoclingDocumentParser();
        ReflectionTestUtils.setField(parser, "ragProperties", ragProperties);
        ReflectionTestUtils.setField(parser, "doclingUrl", "http://localhost:9000");
        ReflectionTestUtils.setField(parser, "doclingPayloadMapper", new DoclingPayloadMapper());
        ReflectionTestUtils.setField(parser, "tikaDocumentParser", tikaDocumentParser);
        ReflectionTestUtils.setField(parser, "retryTemplate", RetryTemplate.builder().maxAttempts(1).build());
    }

    /**
     * 构建Docling响应JSON, 包含实际页数与单个文本段
     * @param pageCount
     * @param text
     * @return
     */
    private String doclingJson(int pageCount, String text) {
        StringBuilder pages = new StringBuilder("{");
        for (int i = 1; i <= pageCount; i++) {
            if (i > 1) {
                pages.append(",");
            }
            pages.append("\"").append(i).append("\": {\"size\": {\"width\": 612.0, \"height\": 792.0}}");
        }
        pages.append("}");
        return "{\"document\": {\"json_content\": {\"pages\": " + pages
                + ", \"texts\": [{\"text\": \"" + text + "\"}]}, \"md_content\": \"\"}, \"status\": \"ok\"}";
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    @DisplayName("buildPageRange - 构建页码范围字符串")
    void buildPageRange_formatsCorrectly() {
        assertThat(DoclingDocumentParser.buildPageRange(1, 10)).isEqualTo("1-10");
        assertThat(DoclingDocumentParser.buildPageRange(11, 10)).isEqualTo("11-20");
        assertThat(DoclingDocumentParser.buildPageRange(5, 1)).isEqualTo("5-5");
    }

    @Test
    @DisplayName("extractPageCount - 从响应中提取实际页数")
    void extractPageCount_readsPagesFromResponse() {
        assertThat(parser.extractPageCount(doclingJson(3, "内容"))).isEqualTo(3);
        assertThat(parser.extractPageCount("{\"pages\": {\"1\": {}, \"2\": {}}}")).isEqualTo(2);
        assertThat(parser.extractPageCount("{\"document\": {}}")).isEqualTo(-1);
        assertThat(parser.extractPageCount("非JSON内容")).isEqualTo(-1);
    }

    @Test
    @DisplayName("supports - 常规文档格式与docling json支持判断")
    void supports_checksFormats() {
        assertThat(parser.supports("application/pdf", "test.pdf")).isTrue();
        assertThat(parser.supports(null, "test.docx")).isTrue();
        assertThat(parser.supports(null, "test.docling.json")).isTrue();
        assertThat(parser.supports("application/json", "a-docling.json")).isTrue();
        assertThat(parser.supports("text/plain", "test.txt")).isFalse();
        assertThat(parser.supports(null, "test.txt")).isFalse();
    }

    @Test
    @DisplayName("parse - URL未配置时降级Tika")
    void parse_fallsBackToTika_whenUrlBlank() {
        ReflectionTestUtils.setField(parser, "doclingUrl", "");
        InputStream input = new ByteArrayInputStream("内容".getBytes(StandardCharsets.UTF_8));
        ParsedDocument fallback = new ParsedDocument();
        fallback.setContent("tika内容");
        when(tikaDocumentParser.parse(any(InputStream.class), any(), any())).thenReturn(fallback);

        ParsedDocument result = parser.parse(input, "test.pdf", "path");

        assertThat(result.getContent()).isEqualTo("tika内容");
        verify(tikaDocumentParser, times(1)).parse(any(InputStream.class), any(), any());
    }

    @Test
    @DisplayName("parse - 文件超过大小限制时降级Tika")
    void parse_fallsBackToTika_whenFileTooLarge() {
        ragProperties.getParser().setDoclingMaxFileSize(10);
        InputStream input = new ByteArrayInputStream("超出限制的文件内容".getBytes(StandardCharsets.UTF_8));
        ParsedDocument fallback = new ParsedDocument();
        fallback.setContent("tika内容");
        when(tikaDocumentParser.parse(any(InputStream.class), any(), any())).thenReturn(fallback);

        ParsedDocument result = parser.parse(input, "test.pdf", "path");

        assertThat(result.getContent()).isEqualTo("tika内容");
        verify(tikaDocumentParser, times(1)).parse(any(InputStream.class), any(), any());
    }

    @Test
    @DisplayName("parse - PDF分批调用并合并各批次内容")
    void parse_pagedConversion_mergesBatches() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        parser.setHttpClientForTest(httpClient);
        HttpResponse<String> firstBatch = mockResponse(doclingJson(10, "第一批内容"));
        HttpResponse<String> lastBatch = mockResponse(doclingJson(2, "尾批内容"));
        doReturn(firstBatch).doReturn(lastBatch).when(httpClient).send(any(), any());

        ParsedDocument result = parser.parse(
                new ByteArrayInputStream("文件内容".getBytes(StandardCharsets.UTF_8)), "test.pdf", "path");

        assertThat(result.getContent()).contains("第一批内容");
        assertThat(result.getContent()).contains("尾批内容");
        assertThat(result.getBody().get("source")).isEqualTo("docling");
        verify(httpClient, times(2)).send(any(), any());
        verify(tikaDocumentParser, never()).parse(any(InputStream.class), any(), any());
    }

    @Test
    @DisplayName("parse - 达到配置的最大页数后停止分页解析")
    void parse_pagedConversion_stopsAtConfiguredMaxPages() throws Exception {
        ragProperties.getParser().setDoclingMaxPages(20);
        HttpClient httpClient = mock(HttpClient.class);
        parser.setHttpClientForTest(httpClient);
        HttpResponse<String> firstBatch = mockResponse(doclingJson(10, "第一批内容"));
        HttpResponse<String> secondBatch = mockResponse(doclingJson(10, "第二批内容"));
        doReturn(firstBatch).doReturn(secondBatch).when(httpClient).send(any(), any());

        ParsedDocument result = parser.parse(
                new ByteArrayInputStream("文件内容".getBytes(StandardCharsets.UTF_8)), "test.pdf", "path");

        assertThat(result.getContent()).contains("第一批内容");
        assertThat(result.getContent()).contains("第二批内容");
        verify(httpClient, times(2)).send(any(), any());
        verify(tikaDocumentParser, never()).parse(any(InputStream.class), any(), any());
    }

    @Test
    @DisplayName("parse - 最大页数为0时不限制解析页数")
    void parse_pagedConversion_unlimitedWhenMaxPagesZero() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        parser.setHttpClientForTest(httpClient);
        HttpResponse<String> firstBatch = mockResponse(doclingJson(10, "第一批内容"));
        HttpResponse<String> lastBatch = mockResponse(doclingJson(3, "尾批内容"));
        doReturn(firstBatch).doReturn(lastBatch).when(httpClient).send(any(), any());

        ParsedDocument result = parser.parse(
                new ByteArrayInputStream("文件内容".getBytes(StandardCharsets.UTF_8)), "test.pdf", "path");

        assertThat(result.getContent()).contains("尾批内容");
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("parse - 非PDF文件单次调用不启用分页")
    void parse_nonPdf_singleCall() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        parser.setHttpClientForTest(httpClient);
        doReturn(mockResponse(doclingJson(2, "docx内容"))).when(httpClient).send(any(), any());

        ParsedDocument result = parser.parse(
                new ByteArrayInputStream("文件内容".getBytes(StandardCharsets.UTF_8)), "test.docx", "path");

        assertThat(result.getContent()).contains("docx内容");
        verify(httpClient, times(1)).send(any(), any());
        verify(tikaDocumentParser, never()).parse(any(InputStream.class), any(), any());
    }

    @Test
    @DisplayName("parse - Docling调用失败时降级Tika")
    void parse_fallsBackToTika_whenDoclingThrows() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        parser.setHttpClientForTest(httpClient);
        doThrow(new java.io.IOException("连接失败")).when(httpClient).send(any(), any());
        ParsedDocument fallback = new ParsedDocument();
        fallback.setContent("tika内容");
        when(tikaDocumentParser.parse(any(InputStream.class), any(), any())).thenReturn(fallback);

        ParsedDocument result = parser.parse(
                new ByteArrayInputStream("文件内容".getBytes(StandardCharsets.UTF_8)), "test.pdf", "path");

        assertThat(result.getContent()).isEqualTo("tika内容");
        verify(tikaDocumentParser, times(1)).parse(any(InputStream.class), any(), any());
    }

    @Test
    @DisplayName("parse - 解析结果为空时降级Tika")
    void parse_fallsBackToTika_whenContentBlank() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        parser.setHttpClientForTest(httpClient);
        doReturn(mockResponse(doclingJson(2, ""))).when(httpClient).send(any(), any());
        ParsedDocument fallback = new ParsedDocument();
        fallback.setContent("tika内容");
        when(tikaDocumentParser.parse(any(InputStream.class), any(), any())).thenReturn(fallback);

        ParsedDocument result = parser.parse(
                new ByteArrayInputStream("文件内容".getBytes(StandardCharsets.UTF_8)), "test.docx", "path");

        assertThat(result.getContent()).isEqualTo("tika内容");
        verify(tikaDocumentParser, times(1)).parse(any(InputStream.class), any(), any());
    }
}
