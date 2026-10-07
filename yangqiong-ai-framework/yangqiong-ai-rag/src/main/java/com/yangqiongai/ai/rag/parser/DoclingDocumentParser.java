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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.common.util.DoclingPayloadMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Docling文档解析器
 * @author yangqiong
 */
@Service
public class DoclingDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    private static final String PARSER_NAME = "docling";

    private static final String CONVERT_ENDPOINT = "/v1/convert/file";

    /**
     * 分页解析的最大总页数保护上限
     */
    private static final int MAX_TOTAL_PAGES = 10000;

    /**
     * 常规文档扩展名（与Tika支持范围一致，优先走Docling）
     */
    private static final java.util.Set<String> OFFICE_EXTENSIONS = java.util.Set.of(
            "pdf", "docx", "doc", "pptx", "ppt", "xlsx", "xls", "rtf", "odt"
    );

    /**
     * 常规文档Content-Type（与Tika支持范围一致）
     */
    private static final java.util.Set<String> OFFICE_CONTENT_TYPES = java.util.Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/rtf",
            "application/vnd.oasis.opendocument.text"
    );

    @Autowired
    private RagProperties ragProperties;

    @Value("${ai.rag.docling.url:}")
    private String doclingUrl;

    @Autowired
    private DoclingPayloadMapper doclingPayloadMapper;

    @Autowired
    private TikaDocumentParser tikaDocumentParser;

    @Autowired
    @Qualifier("ragRetryTemplate")
    private RetryTemplate retryTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpClient httpClient = HttpClient.newBuilder()
            // docling-serve(uvicorn)不支持h2c升级, 升级请求会导致请求体被丢弃返回422
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 测试注入HttpClient使用
     * @param httpClient
     */
    void setHttpClientForTest(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getParserName() {
        return PARSER_NAME;
    }

    @Override
    public boolean supports(String contentType, String fileName) {
        if ("application/json".equalsIgnoreCase(contentType) && fileName != null
                && fileName.toLowerCase().contains("docling")) {
            return true;
        }
        if (fileName != null && fileName.toLowerCase().endsWith(".docling.json")) {
            return true;
        }
        // 常规文档优先走Docling, 解析失败时由parse内部降级Tika
        if (contentType != null && OFFICE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return true;
        }
        if (fileName != null) {
            String extension = getFileExtension(fileName).toLowerCase();
            return OFFICE_EXTENSIONS.contains(extension);
        }
        return false;
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }

    /**
     * 解析文档
     * @param inputStream
     * @param fileName
     * @param fileUrl
     * @return
     */
    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName, String fileUrl) {
        if (doclingUrl == null || doclingUrl.isBlank()) {
            log.warn("Docling服务URL未配置, 降级使用Tika解析器: fileName={}", fileName);
            return tikaDocumentParser.parse(inputStream, fileName, fileUrl);
        }

        byte[] fileBytes;
        try {
            fileBytes = inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("读取输入流失败: fileName={}", fileName, e);
            throw new RuntimeException("读取输入流失败: " + fileName, e);
        }

        if (fileBytes.length > ragProperties.getParser().getDoclingMaxFileSize()) {
            log.warn("文件过大, 超过{}MB限制, 降级使用Tika解析器: fileName={}, size={}",
                    ragProperties.getParser().getDoclingMaxFileSize() / 1024 / 1024, fileName, fileBytes.length);
            return tikaDocumentParser.parse(new ByteArrayInputStream(fileBytes), fileName, fileUrl);
        }

        try {
            // PDF按页分批调用Docling, 避免大文档单次转换超时
            ParsedDocument parsed;
            if (isPdfFile(fileName)) {
                parsed = parseByPagedConversion(fileBytes, fileName, fileUrl);
            } else {
                String jsonResponse = callDoclingService(fileBytes, fileName, null);
                parsed = buildParsedDocument(jsonResponse, fileName, fileUrl);
            }
            if (parsed.getContent() == null || parsed.getContent().isBlank()) {
                log.warn("Docling解析结果为空, 降级使用Tika解析器: fileName={}", fileName);
                return tikaDocumentParser.parse(new ByteArrayInputStream(fileBytes), fileName, fileUrl);
            }
            return parsed;
        } catch (Exception e) {
            log.error("Docling解析失败, 降级使用Tika解析器: fileName={}", fileName, e);
            return tikaDocumentParser.parse(new ByteArrayInputStream(fileBytes), fileName, fileUrl);
        }
    }

    /**
     * 判断是否为PDF文件
     * @param fileName
     * @return
     */
    private boolean isPdfFile(String fileName) {
        return fileName != null && "pdf".equalsIgnoreCase(getFileExtension(fileName));
    }

    /**
     * PDF分页调用Docling并合并各批次解析结果
     * @param fileBytes
     * @param fileName
     * @param fileUrl
     * @return
     * @throws Exception
     */
    private ParsedDocument parseByPagedConversion(byte[] fileBytes, String fileName, String fileUrl) throws Exception {
        int pageSize = Math.max(1, ragProperties.getParser().getDoclingPageSize());
        // 最大解析页数: 配置为0时不限制, 使用内置保护上限
        int configuredMaxPages = ragProperties.getParser().getDoclingMaxPages();
        int maxPages = configuredMaxPages > 0 ? Math.min(configuredMaxPages, MAX_TOTAL_PAGES) : MAX_TOTAL_PAGES;
        List<DoclingPayloadMapper.SegmentRecord> allSegments = new ArrayList<>();
        int startPage = 1;

        while (startPage <= maxPages) {
            String pageRange = buildPageRange(startPage, pageSize);
            String jsonResponse = callDoclingService(fileBytes, fileName, pageRange);
            List<DoclingPayloadMapper.SegmentRecord> batchSegments = doclingPayloadMapper.mapToSegments(jsonResponse);
            allSegments.addAll(batchSegments);

            // 本批实际返回页数不足一页容量时, 说明已到文档末尾
            int batchPages = extractPageCount(jsonResponse);
            if (batchPages >= 0 && batchPages < pageSize) {
                break;
            }
            // 无法识别页数且本批无内容时, 视为已到末尾
            if (batchPages < 0 && batchSegments.isEmpty()) {
                break;
            }
            startPage += pageSize;
        }

        if (startPage > MAX_TOTAL_PAGES) {
            log.warn("Docling分页解析达到最大页数限制{}, 内容可能截断: fileName={}", MAX_TOTAL_PAGES, fileName);
        } else if (startPage > maxPages && configuredMaxPages > 0) {
            log.info("Docling分页解析达到配置的最大页数{}, 仅解析前{}页: fileName={}", configuredMaxPages, maxPages, fileName);
        }
        log.info("Docling分页解析完成, fileName={}, 每批页数={}, 合并段数={}", fileName, pageSize, allSegments.size());
        return buildParsedDocument(allSegments, fileName, fileUrl);
    }

    /**
     * 构建页码范围字符串
     * @param startPage
     * @param pageSize
     * @return
     */
    static String buildPageRange(int startPage, int pageSize) {
        return startPage + "-" + (startPage + pageSize - 1);
    }

    /**
     * 从Docling响应JSON中提取实际处理页数, 无法识别时返回-1
     * @param jsonResponse
     * @return
     */
    int extractPageCount(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode pages = root.path("document").path("json_content").path("pages");
            if (!pages.isObject()) {
                pages = root.path("pages");
            }
            if (pages.isObject()) {
                return pages.size();
            }
        } catch (Exception e) {
            log.warn("解析Docling响应页数失败, 忽略页数判断: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * 调用Docling HTTP服务（带重试）, pageRange非空时按页码范围转换
     * @param fileBytes
     * @param fileName
     * @param pageRange
     * @return
     * @throws Exception
     */
    private String callDoclingService(byte[] fileBytes, String fileName, String pageRange) throws Exception {
        String boundary = "----DoclingBoundary" + System.currentTimeMillis();
        byte[] multipartBody = buildMultipartBody(boundary, fileBytes, fileName, pageRange);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(doclingUrl.replaceAll("/+$", "") + CONVERT_ENDPOINT))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .timeout(Duration.ofMinutes(5))
                .build();

        return retryTemplate.execute(context -> {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Docling服务返回异常状态码: " + response.statusCode());
            }
            return response.body();
        });
    }

    /**
     * 构建multipart请求体（二进制安全）
     * @param boundary
     * @param fileBytes
     * @param fileName
     * @param pageRange
     * @return
     */
    private byte[] buildMultipartBody(String boundary, byte[] fileBytes, String fileName, String pageRange) {
        try {
            String safeFileName = sanitizeFileName(fileName);
            StringBuilder headBuilder = new StringBuilder();
            // to_formats=json: docling-serve默认仅返回md, 页数统计与结构化段提取依赖json_content
            headBuilder.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"to_formats\"\r\n\r\n")
                    .append("json").append("\r\n");
            if (pageRange != null && !pageRange.isBlank()) {
                // docling-serve的page_range为整数数组, 拆为起止两个同名字段提交
                String[] range = pageRange.split("-", 2);
                headBuilder.append("--").append(boundary).append("\r\n")
                        .append("Content-Disposition: form-data; name=\"page_range\"\r\n\r\n")
                        .append(range[0].trim()).append("\r\n")
                        .append("--").append(boundary).append("\r\n")
                        .append("Content-Disposition: form-data; name=\"page_range\"\r\n\r\n")
                        .append(range[1].trim()).append("\r\n");
            }
            headBuilder.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"files\"; filename=\"")
                    .append(safeFileName).append("\"\r\n")
                    .append("Content-Type: application/octet-stream\r\n\r\n");
            String footer = "\r\n--" + boundary + "--\r\n";

            byte[] headBytes = headBuilder.toString().getBytes(StandardCharsets.UTF_8);
            byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);

            byte[] result = new byte[headBytes.length + fileBytes.length + footerBytes.length];
            System.arraycopy(headBytes, 0, result, 0, headBytes.length);
            System.arraycopy(fileBytes, 0, result, headBytes.length, fileBytes.length);
            System.arraycopy(footerBytes, 0, result, headBytes.length + fileBytes.length, footerBytes.length);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("构建multipart请求体失败", e);
        }
    }

    /**
     * 清理文件名中的特殊字符，防止HTTP头注入
     * @param fileName
     * @return
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "document";
        }
        return fileName.replaceAll("[\\r\\n\"]", "_");
    }

    /**
     * 解析Docling JSON响应构建文档对象
     * @param jsonResponse
     * @param fileName
     * @param fileUrl
     * @return
     */
    private ParsedDocument buildParsedDocument(String jsonResponse, String fileName, String fileUrl) throws Exception {
        return buildParsedDocument(doclingPayloadMapper.mapToSegments(jsonResponse), fileName, fileUrl);
    }

    /**
     * 基于文本段列表构建文档对象
     * @param segments
     * @param fileName
     * @param fileUrl
     * @return
     */
    private ParsedDocument buildParsedDocument(List<DoclingPayloadMapper.SegmentRecord> segments,
                                               String fileName, String fileUrl) {
        StringBuilder contentBuilder = new StringBuilder();
        List<DocumentSection> sections = new ArrayList<>();
        Map<String, Object> body = new HashMap<>();
        body.put("source", "docling");
        if (fileUrl != null) {
            body.put("fileUrl", fileUrl);
        }

        for (DoclingPayloadMapper.SegmentRecord segment : segments) {
            String text = segment.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            contentBuilder.append(text).append("\n\n");

            Map<String, Object> segMeta = segment.body();
            String heading = segMeta != null && segMeta.containsKey("parent_header")
                    ? String.valueOf(segMeta.get("parent_header")) : null;
            int level = segMeta != null && segMeta.containsKey("content_type")
                    ? ("table".equals(segMeta.get("content_type")) ? 2 : 1) : 1;
            sections.add(new DocumentSection(heading, text, level, null));
        }

        ParsedDocument doc = new ParsedDocument();
        doc.setTitle(fileName);
        doc.setContent(contentBuilder.toString().trim());
        doc.setMetadata(body);
        doc.setSections(sections);
        return doc;
    }

}
