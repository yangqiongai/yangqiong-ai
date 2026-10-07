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
package com.yangqiongai.ai.rag.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.rag.model.SliceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 版本Payload构建器
 * @author yangqiong
 */
@Service
public class VersionPayloadBuilder {

    private static final Logger log = LoggerFactory.getLogger(VersionPayloadBuilder.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建索引Payload（兼容旧接口）
     * @param slice
     * @param kbId
     * @param version
     * @return
     */
    public Map<String, Object> buildPayload(SliceRecord slice, String kbId, String version) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sliceId", slice.getSliceId());
        payload.put("docId", slice.getDocId());
        payload.put("kbId", kbId);
        payload.put("content", slice.getContent());
        payload.put("sliceType", slice.getSliceType());
        payload.put("parentId", slice.getParentId() != null ? slice.getParentId() : "");
        payload.put("chunkKey", slice.getChunkKey() != null ? slice.getChunkKey() : "");
        payload.put("version", version != null ? version : "");
        payload.put("tokenCount", slice.getTokenCount() != null ? String.valueOf(slice.getTokenCount()) : "0");
        payload.put("sortNum", slice.getSortNum() != null ? String.valueOf(slice.getSortNum()) : "0");
        payload.put("metadata", slice.getMetadata() != null ? slice.getMetadata() : "");
        return payload;
    }

    /**
     * 构建完整版本Payload（含文档请求数据体）
     * @param slice
     * @param document
     * @return
     */
    public Map<String, Object> buildPayload(SliceRecord slice, IngestDocument document) {
        Map<String, Object> payload = buildPayload(slice, document.getKbId(), document.getVersionTag());

        // 文档级请求数据体
        payload.put("docName", document.getDocName() != null ? document.getDocName() : "");
        payload.put("fileType", document.getFileType() != null ? document.getFileType() : "");
        payload.put("fileSize", document.getFileSize() != null ? String.valueOf(document.getFileSize()) : "0");
        payload.put("docStatus", document.getDocStatus() != null ? document.getDocStatus().name() : "");

        // 切片级请求数据体
        payload.put("pageNo", extractPageNo(slice));
        payload.put("isTable", detectTableContent(slice.getContent()));
        payload.put("tableId", extractTableId(slice));
        payload.put("sectionHeading", extractSectionHeading(slice));

        return payload;
    }

    /**
     * 计算ChunkKey SHA-256
     * @param content
     * @param docId
     * @return
     */
    public String computeChunkKey(String content, String docId) {
        String raw = docId + ":" + content;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            return String.valueOf(raw.hashCode());
        }
    }

    /**
     * 提取页码信息
     * @param slice
     * @return
     */
    private String extractPageNo(SliceRecord slice) {
        Map<String, Object> metadataMap = parseMetadata(slice.getMetadata());
        Object pageNo = metadataMap.get("pageNo");
        return pageNo != null ? String.valueOf(pageNo) : "";
    }

    /**
     * 检测内容是否为表格
     * @param content
     * @return
     */
    private String detectTableContent(String content) {
        if (content == null) {
            return "false";
        }
        // 检测Markdown表格或键值对格式
        boolean hasTableMarker = content.contains("|") && content.contains("---");
        boolean hasKeyValueFormat = content.contains(": ") && content.contains("\n")
                && content.lines().filter(line -> line.contains(": ")).count() > 2;
        return String.valueOf(hasTableMarker || hasKeyValueFormat);
    }

    /**
     * 提取表格ID
     * @param slice
     * @return
     */
    private String extractTableId(SliceRecord slice) {
        Map<String, Object> metadataMap = parseMetadata(slice.getMetadata());
        Object tableId = metadataMap.get("tableId");
        return tableId != null ? String.valueOf(tableId) : "";
    }

    /**
     * 使用Jackson解析metadata JSON字符串
     * @param metadata
     * @return
     */
    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("metadata JSON解析失败, 降级为空Map: {}", metadata);
            return Collections.emptyMap();
        }
    }

    /**
     * 提取章节标题
     * @param slice
     * @return
     */
    private String extractSectionHeading(SliceRecord slice) {
        String content = slice.getContent();
        if (content == null) {
            return "";
        }
        // 检测Markdown标题
        String firstLine = content.split("\\n")[0].trim();
        if (firstLine.startsWith("#")) {
            return firstLine.replaceAll("^#+\\s*", "");
        }
        // 检测【】格式的上下文头
        if (firstLine.startsWith("【") && firstLine.contains("】")) {
            return firstLine;
        }
        return "";
    }
}
