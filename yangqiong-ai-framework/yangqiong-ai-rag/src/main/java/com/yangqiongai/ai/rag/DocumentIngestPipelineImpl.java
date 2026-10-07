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
package com.yangqiongai.ai.rag;

import com.yangqiongai.ai.common.rag.DocumentMetadataHandler;
import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.rag.chunk.DocumentChunker;
import com.yangqiongai.ai.rag.cleaner.UniversalDocumentCleaner;
import com.yangqiongai.ai.rag.embed.VectorEmbedder;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.rag.model.ChunkConfig;
import com.yangqiongai.ai.rag.parser.DocumentParser;
import com.yangqiongai.ai.rag.parser.ParsedDocument;
import com.yangqiongai.ai.rag.parser.ParserFactory;

import com.yangqiongai.ai.storage.DocumentStorageService;
import com.yangqiongai.ai.storage.VectorStorageService;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 文档入库流水线
 * @author yangqiong
 */
@Service
public class DocumentIngestPipelineImpl implements DocumentIngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestPipelineImpl.class);

    /**
     * 文本类来源：直接提供content，无需从对象存储下载文件
     */
    private static final String SOURCE_TYPE_TEXT = "TEXT";

    @Autowired
    private DocumentMetadataHandler documentMetadataHandler;

    @Autowired
    private DocumentStorageService documentStorageService;

    @Autowired
    private ParserFactory parserFactory;

    @Autowired
    private UniversalDocumentCleaner cleaner;

    @Autowired
    private DocumentChunker documentChunker;

    @Autowired
    private VectorEmbedder vectorEmbedder;

    @Autowired(required = false)
    private SliceRecordRepository sliceRecordRepository;

    @Autowired
    private VectorStorageService qdrantVectorService;

    /**
     * 集合名称解析器
     */
    @Autowired
    private CollectionNameResolver collectionNameResolver;

    @Override
    public void process(String docId) {
        IngestDocument doc = documentMetadataHandler.loadDocument(docId);
        if (doc == null) {
            log.warn("文档不存在, 跳过入库流水线, docId: {}", docId);
            return;
        }
        process(doc);
    }

    @Override
    public void process(IngestDocument doc) {
        String docId = doc.getDocId();
        log.info("开始执行入库流水线, docId: {}, kbId: {}, sourceType: {}",
                docId, doc.getKbId(), doc.getSourceType());
        try {
            updateStatusSafely(docId, DocumentStatus.EMBEDDING,null, null);

            ParsedDocument parsed = parsePhase(doc);
            cleanPhase(parsed);
            List<SliceRecord> slices = chunkPhase(doc, parsed);
            embedPhase(doc, slices);

            updateStatusSafely(docId, DocumentStatus.COMPLETED, slices.size(), null);
            log.info("入库流水线完成, docId: {}, 切片数: {}", docId, slices.size());
        } catch (Exception e) {
            log.error("入库流水线失败, docId: {}", docId, e);
            // 回滚切片数据并清理 Qdrant 残留向量, 保证切片与向量同时成功或同时失败
            rollbackSlicesAndVectors(docId, doc.getKbId());
            updateStatusSafely(docId, DocumentStatus.FAILED, null, truncateMessage(e.getMessage()));
        }
    }

    /**
     * 向量化失败时回滚已落库的切片并清理 Qdrant 残留向量
     * @param docId
     * @param kbId
     */
    private void rollbackSlicesAndVectors(String docId, String kbId) {
        // 删除已落库的切片
        try {
            int count = sliceRecordRepository.deleteByDocId(docId);
            log.info("向量化失败, 已回滚切片数据, docId: {}, 删除切片数: {}", docId, count);
        } catch (Exception e) {
            log.warn("回滚切片数据异常, docId: {}", docId, e);
        }

        // 清理 Qdrant 中可能已写入的残留向量
        try {
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("docId")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(docId)
                                            .build())
                                    .build())
                            .build())
                    .build();
            qdrantVectorService.deleteByFilter(collectionNameResolver.resolve(kbId), filter);
            log.info("向量化失败, 已清理 Qdrant 残留向量, docId: {}, kbId: {}", docId, kbId);
        } catch (Exception e) {
            log.warn("清理 Qdrant 残留向量异常, docId: {}", docId, e);
        }
    }

    /**
     * 解析阶段（支持文本直传和文件下载双模式）
     * @param doc
     * @return
     */
    private ParsedDocument parsePhase(IngestDocument doc) {
        updateStatusSafely(doc.getDocId(), DocumentStatus.PARSING, null, null);
        try {
            ParsedDocument parsed = isTextSource(doc)
                    ? parseFromContent(doc)
                    : parseFromFilePath(doc);
            log.info("文档解析完成, docId: {}, 内容长度: {}", doc.getDocId(),
                    parsed.getContent() == null ? 0 : parsed.getContent().length());
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 直接文本解析（TEXT/API/WEBPAGE等来源）
     * @param doc
     * @return
     */
    private ParsedDocument parseFromContent(IngestDocument doc) {
        byte[] bytes = doc.getContent() == null
                ? new byte[0]
                : doc.getContent().getBytes(StandardCharsets.UTF_8);
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            return doParse(input, doc);
        } catch (Exception e) {
            throw new IllegalStateException("文本内容解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文件下载解析（FILE来源，从对象存储下载）
     * @param doc
     * @return
     */
    private ParsedDocument parseFromFilePath(IngestDocument doc) {
        try (InputStream input = documentStorageService.downloadFile(doc.getFileBucket(), doc.getFilePath())) {
            return doParse(input, doc);
        } catch (Exception e) {
            throw new IllegalStateException("文件下载解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行解析器解析
     * @param input
     * @param doc
     * @return
     */
    private ParsedDocument doParse(InputStream input, IngestDocument doc) throws Exception {
        Optional<DocumentParser> parserOpt = parserFactory.getParser(null, doc.getFileType(), doc.getDocName());
        DocumentParser parser = parserOpt.orElseThrow(() ->
                new IllegalStateException("未找到匹配的文档解析器, fileType: " + doc.getFileType()
                        + ", fileName: " + doc.getDocName()));
        return parser.parse(input, doc.getDocName(), doc.getFilePath());
    }

    /**
     * 判断是否为文本类来源（直接提供content，无需文件下载）
     * @param doc
     * @return
     */
    private boolean isTextSource(IngestDocument doc) {
        return SOURCE_TYPE_TEXT.equalsIgnoreCase(doc.getSourceType())
                || (doc.getContent() != null && !doc.getContent().isBlank());
    }

    /**
     * 清洗阶段
     * @param parsed
     */
    private void cleanPhase(ParsedDocument parsed) {
        String content = parsed.getContent();
        if (content == null || content.isBlank()) {
            return;
        }
        String cleaned = cleaner.clean(content);
        parsed.setContent(cleaned);
        log.info("文档清洗完成, 清洗前: {}, 清洗后: {}", content.length(), cleaned.length());
    }

    /**
     * 切片阶段
     * @param doc
     * @param parsed
     * @return
     */
    private List<SliceRecord> chunkPhase(IngestDocument doc, ParsedDocument parsed) {
        log.info("开始文档切片, docId: {}", doc.getDocId());
        ChunkConfig config = new ChunkConfig();
        config.validate();
        List<SliceRecord> slices = documentChunker.chunkAndSave(parsed, doc, config);
        log.info("文档切片完成, docId: {}, 切片数: {}", doc.getDocId(), slices.size());
        return slices;
    }

    /**
     * 向量化入库阶段
     * @param doc
     * @param slices
     */
    private void embedPhase(IngestDocument doc, List<SliceRecord> slices) {
        if (slices == null || slices.isEmpty()) {
            log.warn("切片为空, 跳过向量化, docId: {}", doc.getDocId());
            return;
        }
        log.info("开始向量化入库, docId: {}", doc.getDocId());
        vectorEmbedder.embedAndIndex(slices, doc.getKbId());
        log.info("向量化入库完成, docId: {}", doc.getDocId());
    }

    /**
     * 安全更新文档状态（端口回写失败不影响主流程异常传播）
     * @param docId
     * @param status
     * @param chunkCount
     * @param errorMessage
     */
    @Override
    public void updateStatusSafely(String docId, DocumentStatus status, Integer chunkCount, String errorMessage) {
        try {
            documentMetadataHandler.updateDocumentStatus(docId, status, chunkCount, errorMessage);
        } catch (Exception e) {
            log.warn("文档状态回写失败, docId: {}, status: {}", docId, status, e);
        }
    }

    private String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
