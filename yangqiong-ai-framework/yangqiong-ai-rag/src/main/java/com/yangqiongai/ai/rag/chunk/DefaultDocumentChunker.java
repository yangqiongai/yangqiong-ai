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
package com.yangqiongai.ai.rag.chunk;

import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.rag.model.ChunkConfig;
import com.yangqiongai.ai.rag.parser.DocumentSection;
import com.yangqiongai.ai.rag.parser.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档切片
 * @author yangqiong
 */
@Service
public class DefaultDocumentChunker implements DocumentChunker {

    private static final Logger log = LoggerFactory.getLogger(DefaultDocumentChunker.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String MARKDOWN_TABLE_ROW_SEP = "\n";
    private static final String TABLE_HEADER_SEP = "|";
    private static final String TABLE_ALIGN_REGEX = "^\\|?\\s*[:\\-]+\\s*\\|\\s*[:\\-]+\\s*(\\|\\s*[:\\-]+\\s*)*\\|?$";

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SliceRecord> chunkAndSave(ParsedDocument doc, IngestDocument ingestDoc, ChunkConfig config) {
        List<SliceRecord> slices = new ArrayList<>();
        String content = doc.getContent();
        if (content == null || content.isBlank()) {
            log.warn("文档内容为空, docId: {}", ingestDoc.getDocId());
            return slices;
        }

        String kbId = ingestDoc.getKbId();
        Set<String> existingHashes = loadExistingHashes(ingestDoc.getDocId(), kbId);

        List<String> rawChunks = segmentByTableAware(content, config);
        List<SliceRecord> parentChunks = assembleParentChunks(rawChunks, ingestDoc, kbId, config, existingHashes);
        assignSortNum(parentChunks);
        batchInsertSafely(parentChunks);
        slices.addAll(parentChunks);

        List<SliceRecord> childChunks = deriveChildChunks(parentChunks, ingestDoc, kbId, config, existingHashes);
        batchInsertSafely(childChunks);
        slices.addAll(childChunks);

        linkParentChunks(parentChunks, childChunks);
        batchUpdateMetadata(parentChunks);

        injectContextHeaders(slices, ingestDoc.getDocName());

        log.info("通用切片完成, docId: {}, 父块+子块总数: {}", ingestDoc.getDocId(), slices.size());
        return slices;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SliceRecord> chunkDoclingAndSave(ParsedDocument doc, IngestDocument ingestDoc, ChunkConfig config) {
        List<SliceRecord> slices = new ArrayList<>();
        List<DocumentSection> sections = doc.getSections();
        if (sections == null || sections.isEmpty()) {
            log.warn("Docling文档无章节信息, 降级为通用切片, docId: {}", ingestDoc.getDocId());
            return chunkAndSave(doc, ingestDoc, config);
        }

        String kbId = ingestDoc.getKbId();
        Set<String> existingHashes = loadExistingHashes(ingestDoc.getDocId(), kbId);

        List<SliceRecord> parentChunks = new ArrayList<>();
        List<SliceRecord> allChildChunks = new ArrayList<>();

        for (DocumentSection section : sections) {
            String sectionContent = section.getContent();
            if (sectionContent == null || sectionContent.isBlank()) {
                continue;
            }

            String contentHash = computeContentHash(sectionContent);
            if (existingHashes.contains(contentHash)) {
                log.debug("章节内容重复, 跳过切片, heading: {}", section.getHeading());
                continue;
            }
            existingHashes.add(contentHash);

            SliceRecord slice = assembleSliceRecord(sectionContent, ingestDoc.getDocId(), kbId,
                    "parent", null, ingestDoc.getVersionTag());
            slice.setSliceId(UUID.randomUUID().toString());
            slice.setChunkKey(contentHash);
            slice.setTokenCount(estimateTokenCount(sectionContent));
            slice.setCreateTime(LocalDateTime.now());
            parentChunks.add(slice);
            slices.add(slice);

            if (sectionContent.length() > config.getChildChunkSize()) {
                List<SliceRecord> childSlices = deriveChildChunks(
                        List.of(slice), ingestDoc, kbId, config, existingHashes);
                allChildChunks.addAll(childSlices);
                slices.addAll(childSlices);
            }
        }

        batchInsertSafely(parentChunks);
        batchInsertSafely(allChildChunks);

        linkParentChunks(parentChunks, allChildChunks);
        batchUpdateMetadata(parentChunks);

        injectContextHeaders(slices, ingestDoc.getDocName());

        log.info("Docling语义切片完成, docId: {}, 父块+子块总数: {}", ingestDoc.getDocId(), slices.size());
        return slices;
    }

    /**
     * 为父块列表分配有序排序号
     * @param parentChunks
     */
    private void assignSortNum(List<SliceRecord> parentChunks) {
        if (parentChunks == null || parentChunks.isEmpty()) {
            return;
        }
        for (int i = 0; i < parentChunks.size(); i++) {
            parentChunks.get(i).setSortNum(i + 1);
        }
    }

    /**
     * 表格感知的内容分段
     * @param content
     * @param config
     * @return
     */
    private List<String> segmentByTableAware(String content, ChunkConfig config) {
        List<String> segments = new ArrayList<>();
        String[] lines = content.split("\\n");
        StringBuilder currentSegment = new StringBuilder();
        boolean inTable = false;
        List<String> tableBuffer = new ArrayList<>();

        for (String line : lines) {
            boolean isTableRow = line.trim().startsWith(TABLE_HEADER_SEP) && line.trim().endsWith(TABLE_HEADER_SEP);

            if (isTableRow) {
                if (!inTable) {
                    if (currentSegment.length() > 0) {
                        flushSegment(currentSegment, segments, config);
                        currentSegment = new StringBuilder();
                    }
                    inTable = true;
                }
                tableBuffer.add(line);
            } else {
                if (inTable) {
                    List<String> tableChunks = splitMarkdownTableByRows(
                            String.join(MARKDOWN_TABLE_ROW_SEP, tableBuffer));
                    segments.addAll(tableChunks);
                    tableBuffer.clear();
                    inTable = false;
                }
                if (currentSegment.length() > 0) {
                    currentSegment.append("\n");
                }
                currentSegment.append(line);

                if (currentSegment.length() >= config.getMaxChunkSize()) {
                    flushSegment(currentSegment, segments, config);
                    currentSegment = new StringBuilder();
                }
            }
        }

        if (inTable && !tableBuffer.isEmpty()) {
            List<String> tableChunks = splitMarkdownTableByRows(
                    String.join(MARKDOWN_TABLE_ROW_SEP, tableBuffer));
            segments.addAll(tableChunks);
        }
        if (currentSegment.length() > 0) {
            flushSegment(currentSegment, segments, config);
        }

        return segments;
    }

    /**
     * 刷新当前分段到列表
     * @param currentSegment
     * @param segments
     * @param config
     */
    private void flushSegment(StringBuilder currentSegment, List<String> segments, ChunkConfig config) {
        String text = currentSegment.toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > config.getMaxChunkSize()) {
            String[] subChunks = splitBySize(text, config.getMaxChunkSize(), config.getOverlapSize(), "\n\n");
            segments.addAll(Arrays.asList(subChunks));
        } else {
            segments.add(text);
        }
    }

    /**
     * 按行拆分Markdown表格
     * @param markdown
     * @return
     */
    List<String> splitMarkdownTableByRows(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new ArrayList<>();
        }

        String[] rows = markdown.split(MARKDOWN_TABLE_ROW_SEP);
        List<String> headerRows = new ArrayList<>();
        List<String> dataRows = new ArrayList<>();
        boolean pastAlignRow = false;

        for (String row : rows) {
            String trimmed = row.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!pastAlignRow && trimmed.matches(TABLE_ALIGN_REGEX)) {
                headerRows.add(trimmed);
                pastAlignRow = true;
                continue;
            }
            if (!pastAlignRow) {
                headerRows.add(trimmed);
            } else {
                dataRows.add(trimmed);
            }
        }

        List<String> headers = parseTableHeaders(headerRows);
        String tableName = extractTableName(headerRows);

        List<String> result = new ArrayList<>();
        String headerChunk = String.join(MARKDOWN_TABLE_ROW_SEP, headerRows);
        result.add(headerChunk);

        for (String dataRow : dataRows) {
            String tableContext = buildTableContext(tableName, headers, dataRow);
            result.add(tableContext);
        }

        return result;
    }

    /**
     * 构建表格行上下文
     * @param tableName
     * @param headers
     * @param row
     * @return
     */
    String buildTableContext(String tableName, List<String> headers, String row) {
        StringBuilder context = new StringBuilder();
        if (tableName != null && !tableName.isEmpty()) {
            context.append("表: ").append(tableName).append("\n");
        }

        String[] cells = row.split("\\s*" + TABLE_HEADER_SEP + "\\s*");
        List<String> cellValues = Arrays.stream(cells)
                .filter(cell -> !cell.isEmpty())
                .collect(Collectors.toList());

        for (int i = 0; i < cellValues.size() && i < headers.size(); i++) {
            context.append(headers.get(i)).append(": ").append(cellValues.get(i)).append("\n");
        }

        return context.toString().trim();
    }

    /**
     * 注入上下文头信息
     * @param chunks
     * @param documentTitle
     */
    void injectContextHeaders(List<SliceRecord> chunks, String documentTitle) {
        if (chunks == null || chunks.isEmpty() || documentTitle == null || documentTitle.isEmpty()) {
            return;
        }

        String headerPrefix = "【" + documentTitle + "】";
        chunks.forEach(chunk -> {
            String content = chunk.getContent();
            if (content != null && !content.startsWith(headerPrefix)) {
                chunk.setContent(headerPrefix + "\n" + content);
            }
        });
    }

    /**
     * 链接父子分块
     * @param parentChunks
     * @param childChunks
     */
    void linkParentChunks(List<SliceRecord> parentChunks, List<SliceRecord> childChunks) {
        if (parentChunks == null || childChunks == null) {
            return;
        }
        parentChunks.forEach(parent -> {
            long childCount = childChunks.stream()
                    .filter(child -> parent.getSliceId().equals(child.getParentId()))
                    .count();
            if (childCount > 0) {
                try {
                    String meta = parent.getMetadata();
                    java.util.Map<String, Object> metaMap;
                    if (meta == null || meta.isEmpty()) {
                        metaMap = new java.util.LinkedHashMap<>();
                    } else {
                        metaMap = objectMapper.readValue(meta, java.util.Map.class);
                    }
                    metaMap.put("childCount", childCount);
                    parent.setMetadata(objectMapper.writeValueAsString(metaMap));
                } catch (Exception e) {
                    log.warn("更新父块body元数据失败, sliceId: {}", parent.getSliceId(), e);
                }
            }
        });
    }

    /**
     * 计算内容SHA-256哈希
     * @param content
     * @return
     */
    String computeContentHash(String content) {
        if (content == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
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
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * 组装父块列表
     * @param rawChunks
     * @param ingestDoc
     * @param kbId
     * @param config
     * @param existingHashes
     * @return
     */
    private List<SliceRecord> assembleParentChunks(List<String> rawChunks, IngestDocument ingestDoc,
                                                      String kbId, ChunkConfig config, Set<String> existingHashes) {
        return rawChunks.stream()
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .filter(text -> {
                    String hash = computeContentHash(text);
                    if (existingHashes.contains(hash)) {
                        log.debug("父块内容重复, 跳过, hash: {}", hash.substring(0, 8));
                        return false;
                    }
                    existingHashes.add(hash);
                    return true;
                })
                .map(text -> {
                    SliceRecord slice = assembleSliceRecord(text, ingestDoc.getDocId(), kbId,
                            "parent", null, ingestDoc.getVersionTag());
                    slice.setSliceId(UUID.randomUUID().toString());
                    slice.setChunkKey(computeContentHash(text));
                    slice.setTokenCount(estimateTokenCount(text));
                    slice.setCreateTime(LocalDateTime.now());
                    return slice;
                })
                .collect(Collectors.toList());
    }

    /**
     * 派生子块
     * @param parentChunks
     * @param ingestDoc
     * @param kbId
     * @param config
     * @param existingHashes
     * @return
     */
    private List<SliceRecord> deriveChildChunks(List<SliceRecord> parentChunks, IngestDocument ingestDoc,
                                                   String kbId, ChunkConfig config, Set<String> existingHashes) {
        return parentChunks.stream()
                .filter(parent -> parent.getContent().length() > config.getChildChunkSize())
                .flatMap(parent -> {
                    String[] childTexts = splitBySize(parent.getContent(),
                            config.getChildChunkSize(), config.getChildOverlap(), "\n");
                    return Arrays.stream(childTexts)
                            .map(String::trim)
                            .filter(text -> !text.isEmpty())
                            .filter(text -> {
                                String hash = computeContentHash(text);
                                if (existingHashes.contains(hash)) {
                                    return false;
                                }
                                existingHashes.add(hash);
                                return true;
                            })
                            .map(text -> {
                                SliceRecord childSlice = assembleSliceRecord(text, ingestDoc.getDocId(), kbId,
                                        "child", parent.getSliceId(), ingestDoc.getVersionTag());
                                childSlice.setSliceId(UUID.randomUUID().toString());
                                childSlice.setChunkKey(computeContentHash(text));
                                childSlice.setTokenCount(estimateTokenCount(text));
                                childSlice.setCreateTime(LocalDateTime.now());
                                return childSlice;
                            });
                })
                .collect(Collectors.toList());
    }

    /**
     * 加载已有切片哈希集合用于去重
     * @param docId
     * @param kbId
     * @return
     */
    private Set<String> loadExistingHashes(String docId, String kbId) {
        return sliceRecordRepository.loadExistingChunkKeys(docId, kbId);
    }

    /**
     * 解析表格表头
     * @param headerRows
     * @return
     */
    private List<String> parseTableHeaders(List<String> headerRows) {
        if (headerRows.isEmpty()) {
            return new ArrayList<>();
        }
        String firstRow = headerRows.get(0);
        return Arrays.stream(firstRow.split("\\s*" + TABLE_HEADER_SEP + "\\s*"))
                .filter(cell -> !cell.isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
    }

    /**
     * 提取表格名称
     * @param headerRows
     * @return
     */
    private String extractTableName(List<String> headerRows) {
        if (headerRows.isEmpty()) {
            return "";
        }
        String firstRow = headerRows.get(0).trim();
        if (firstRow.startsWith(TABLE_HEADER_SEP)) {
            return "";
        }
        return firstRow.replaceAll(TABLE_HEADER_SEP, "").trim();
    }

    /**
     * 按大小切片
     * @param content
     * @param maxChunkSize
     * @param overlapSize
     * @param separator
     * @return
     */
    private String[] splitBySize(String content, int maxChunkSize, int overlapSize, String separator) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split(java.util.regex.Pattern.quote(separator));
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                continue;
            }

            if (currentChunk.length() + paragraph.length() > maxChunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
                if (overlapSize > 0 && currentChunk.length() > overlapSize) {
                    String overlapText = currentChunk.substring(currentChunk.length() - overlapSize);
                    currentChunk = new StringBuilder(overlapText);
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append(separator);
            }
            currentChunk.append(paragraph);
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks.toArray(new String[0]);
    }

    /**
     * 批量插入切片记录，捕获唯一键冲突跳过重复记录
     * @param slices
     */
    private void batchInsertSafely(List<SliceRecord> slices) {
        if (slices == null || slices.isEmpty()) {
            return;
        }
        for (SliceRecord slice : slices) {
            try {
                sliceRecordRepository.insert(slice);
            } catch (DuplicateKeyException e) {
                log.warn("切片已存在, 跳过插入, chunkKey: {}", slice.getChunkKey());
            }
        }
    }

    /**
     * 批量更新父块元数据，持久化linkParentChunks的修改
     * @param parentChunks
     */
    private void batchUpdateMetadata(List<SliceRecord> parentChunks) {
        if (parentChunks == null || parentChunks.isEmpty()) {
            return;
        }
        parentChunks.forEach(sliceRecordRepository::updateById);
    }

    private SliceRecord assembleSliceRecord(String content, String docId, String kbId,
                                               String sliceType, String parentId, String version) {
        SliceRecord slice = new SliceRecord();
        slice.setContent(content);
        slice.setDocId(docId);
        slice.setKbId(kbId);
        slice.setSliceType(sliceType);
        slice.setParentId(parentId);
        slice.setVersion(version);
        slice.setIsActive(true);
        return slice;
    }

    /**
     * 估算Token数量（中英文分别估算）
     * @param text
     * @return
     */
    private int estimateTokenCount(String text) {
        if (text == null) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) Math.round(chineseChars * 1.5 + otherChars * 0.25);
    }
}
