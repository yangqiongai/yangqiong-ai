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
package com.yangqiongai.ai.platform.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.rag.DocumentMetadataHandler;
import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.platform.knowledge.entity.KbDocument;
import com.yangqiongai.ai.platform.knowledge.mapper.KbDocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档元数据端口实现
 * @author yangqiong
 */
@Service
public class DocumentMetadataServiceImpl implements DocumentMetadataHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataServiceImpl.class);

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    /**
     * 知识库元数据端口实现（版本继承）
     */
    @Autowired
    private KnowledgeBaseMetadataServiceImpl knowledgeBaseMetadataService;

    @Override
    public IngestDocument loadDocument(String docId) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getDocId, docId);
        KbDocument doc = kbDocumentMapper.selectOne(wrapper);
        return doc != null ? toIngestDocument(doc) : null;
    }

    @Override
    public Map<String, IngestDocument> batchLoadDocuments(Set<String> docIds) {
        Map<String, IngestDocument> result = new LinkedHashMap<>();
        if (docIds == null || docIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(KbDocument::getDocId, docIds);
        List<KbDocument> documents = kbDocumentMapper.selectList(wrapper);
        for (KbDocument doc : documents) {
            result.put(doc.getDocId(), toIngestDocument(doc));
        }
        return result;
    }

    @Override
    public IngestDocument createDocument(IngestDocument document) {
        KbDocument doc = toKbDocument(document);
        if (doc.getDocId() == null || doc.getDocId().isBlank()) {
            doc.setDocId(java.util.UUID.randomUUID().toString());
        }
        if (doc.getDocStatus() == null) {
            doc.setDocStatus(DocumentStatus.PENDING);
        }
        if (doc.getSourceType() == null) {
            doc.setSourceType("FILE");
        }
        doc.setCreateTime(LocalDateTime.now());
        doc.setVersionTag(knowledgeBaseMetadataService.ensureActiveVersion(document.getKbId()));
        kbDocumentMapper.insert(doc);
        log.info("文档记录已创建, docId: {}, sourceType: {}", doc.getDocId(), doc.getSourceType());
        return toIngestDocument(doc);
    }

    @Override
    public void updateDocumentStatus(String docId, DocumentStatus docStatus, Integer chunkCount, String errorMessage) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getDocId, docId);
        KbDocument doc = kbDocumentMapper.selectOne(wrapper);
        if (doc == null) {
            log.warn("文档不存在, 无法更新状态, docId: {}", docId);
            return;
        }
        doc.setDocStatus(docStatus);
        if (chunkCount != null) {
            doc.setChunkCount(chunkCount);
        }
        if (errorMessage != null) {
            doc.setErrorMessage(errorMessage);
        }
        doc.setUpdateTime(LocalDateTime.now());
        kbDocumentMapper.updateById(doc);
    }

    @Override
    public void updateDocumentSummary(String docId, String summary) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getDocId, docId);
        KbDocument doc = kbDocumentMapper.selectOne(wrapper);
        if (doc == null) {
            log.warn("文档不存在, 无法更新摘要, docId: {}", docId);
            return;
        }
        doc.setSummary(summary);
        doc.setUpdateTime(LocalDateTime.now());
        kbDocumentMapper.updateById(doc);
    }

    /**
     * KbDocument转IngestDocument
     * @param doc
     * @return
     */
    private IngestDocument toIngestDocument(KbDocument doc) {
        IngestDocument result = new IngestDocument();
        result.setDocId(doc.getDocId());
        result.setDocName(doc.getDocName());
        result.setFileBucket(doc.getFileBucket());
        result.setFilePath(doc.getFilePath());
        result.setSourceType(doc.getSourceType() != null ? doc.getSourceType() : "FILE");
        result.setFileType(doc.getFileType());
        result.setFileSize(doc.getFileSize());
        result.setVersionTag(doc.getVersionTag());
        result.setDocStatus(doc.getDocStatus());
        result.setErrorMessage(doc.getErrorMessage());
        result.setChunkCount(doc.getChunkCount());
        result.setSummary(doc.getSummary());

        Map<String, String> metadata = new HashMap<>();
        if (doc.getKbId() != null) {
            metadata.put("kbId", doc.getKbId());
        }
        if (doc.getUserId() != null) {
            metadata.put("userId", doc.getUserId());
        }
        result.setMetadata(metadata);
        return result;
    }

    /**
     * IngestDocument转KbDocument
     * @param document
     * @return
     */
    private KbDocument toKbDocument(IngestDocument document) {
        KbDocument doc = new KbDocument();
        doc.setDocId(document.getDocId());
        doc.setDocName(document.getDocName());
        doc.setFilePath(document.getFilePath());
        doc.setSourceType(document.getSourceType());
        doc.setContent(document.getContent());
        doc.setFileType(document.getFileType());
        doc.setFileSize(document.getFileSize());
        doc.setVersionTag(document.getVersionTag());
        doc.setDocStatus(document.getDocStatus());
        doc.setErrorMessage(document.getErrorMessage());
        doc.setChunkCount(document.getChunkCount());
        doc.setSummary(document.getSummary());

        if (document.getKbId() != null) {
            doc.setKbId(document.getKbId());
        }
        if (document.getUserId() != null) {
            doc.setUserId(document.getUserId());
        }
        return doc;
    }
}
