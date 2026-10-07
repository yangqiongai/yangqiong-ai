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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文档解析服务
 * <p>
 * 管理面解析管线：按格式分发提取器，PDF做页眉页脚清洗与OCR兜底，
 * 统一按尺寸/重叠分块，输出标准分块结构供调用方走既有KbDocument摄取链路。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class DocParserService {

    private static final Logger log = LoggerFactory.getLogger(DocParserService.class);

    /**
     * 支持的文件类型
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "docx", "xlsx", "xls", "txt", "md", "csv", "json");

    /**
     * 连接器配置
     */
    private final ConnectorProperties properties;

    /**
     * OCR引擎（未接入时为null）
     */
    private final OcrEngine ocrEngine;

    public DocParserService(ConnectorProperties properties, ObjectProvider<OcrEngine> ocrEngineProvider) {
        this.properties = properties;
        this.ocrEngine = ocrEngineProvider.getIfAvailable();
    }

    /**
     * 解析文件为结构化分块
     * @param fileName 文件名
     * @param contentType 内容类型
     * @param content 文件字节
     * @return 解析结果
     */
    public DocParseResult parse(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "文件内容不能为空");
        }
        int maxBytes = properties.getDocparserMaxFileSizeMb() * 1024 * 1024;
        if (content.length > maxBytes) {
            throw new AiException(AiErrorCode.PARAM_ERROR,
                    "文件超过最大限制" + properties.getDocparserMaxFileSizeMb() + "MB");
        }
        String fileType = resolveFileType(fileName, contentType);
        List<DocSegment> segments = extractSegments(fileType, content);
        if (segments.isEmpty()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "未从文件中解析出有效内容");
        }

        List<DocSegment> cleaned = "pdf".equals(fileType)
                ? cleanPdfSegments(segments) : segments;
        List<DocParseChunk> chunks = buildChunks(cleaned);

        DocParseResult result = new DocParseResult();
        result.setFileName(fileName);
        result.setFileType(fileType);
        result.setChunks(chunks);
        result.setChunkCount(chunks.size());
        result.setTotalChars(chunks.stream().mapToInt(chunk -> chunk.getText().length()).sum());
        result.setOcrPageCount((int) segments.stream()
                .filter(segment -> Boolean.TRUE.equals(segment.getMetadata().get("ocr")))
                .count());
        log.info("文档解析完成: fileName={}, type={}, chunks={}, ocrPages={}",
                fileName, fileType, result.getChunkCount(), result.getOcrPageCount());
        return result;
    }

    /**
     * 按扩展名解析文件类型，无扩展名时回退内容类型
     * @param fileName 文件名
     * @param contentType 内容类型
     * @return 文件类型
     */
    private String resolveFileType(String fileName, String contentType) {
        String type = null;
        if (fileName != null) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
                type = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
            }
        }
        if (type == null && contentType != null) {
            if ("application/pdf".equalsIgnoreCase(contentType)) {
                type = "pdf";
            } else if (contentType.contains("spreadsheetml") || "application/vnd.ms-excel".equalsIgnoreCase(contentType)) {
                type = "xlsx";
            } else if (contentType.contains("wordprocessingml") || "application/msword".equalsIgnoreCase(contentType)) {
                type = "docx";
            }
        }
        if (type == null || !SUPPORTED_TYPES.contains(type)) {
            throw new AiException(AiErrorCode.PARAM_ERROR,
                    "不支持的文件类型，支持: " + String.join("/", SUPPORTED_TYPES));
        }
        if ("doc".equals(type)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "旧版doc格式暂不支持，请另存为docx后上传");
        }
        return type;
    }

    /**
     * 按类型分发提取器
     * @param fileType 文件类型
     * @param content 文件字节
     * @return 分段清单
     */
    private List<DocSegment> extractSegments(String fileType, byte[] content) {
        return switch (fileType) {
            case "pdf" -> new PdfTextExtractor(properties.isDocparserOcrEnabled(), ocrEngine).extract(content);
            case "docx" -> new WordTextExtractor().extract(content);
            case "xlsx", "xls" -> new ExcelTextExtractor().extract(content);
            default -> new PlainTextExtractor().extract(content);
        };
    }

    /**
     * PDF分段页眉页脚清洗
     * @param segments 按页分段
     * @return 清洗后分段
     */
    private List<DocSegment> cleanPdfSegments(List<DocSegment> segments) {
        List<String> pageTexts = segments.stream().map(DocSegment::getText).toList();
        List<String> cleaned = HeaderFooterCleaner.clean(pageTexts);
        List<DocSegment> result = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            if (!cleaned.get(i).isBlank()) {
                result.add(new DocSegment(segments.get(i).getType(), cleaned.get(i), segments.get(i).getMetadata()));
            }
        }
        return result;
    }

    /**
     * 分段统一分块并编号
     * @param segments 分段清单
     * @return 分块清单
     */
    private List<DocParseChunk> buildChunks(List<DocSegment> segments) {
        List<DocParseChunk> chunks = new ArrayList<>();
        for (DocSegment segment : segments) {
            List<String> parts = TextChunker.chunk(segment.getText(),
                    properties.getDocparserChunkSize(), properties.getDocparserChunkOverlap());
            for (String part : parts) {
                Map<String, Object> metadata = segment.getMetadata().isEmpty() ? null : segment.getMetadata();
                chunks.add(new DocParseChunk(chunks.size(), segment.getType(), part, metadata));
            }
        }
        return chunks;
    }
}
