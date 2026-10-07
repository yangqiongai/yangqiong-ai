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
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.yangqiongai.ai.platform.bss.util.DigestUtils;
import com.yangqiongai.ai.platform.knowledge.entity.KbDocument;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.common.event.DocumentIngestEvent;
import com.yangqiongai.ai.common.event.KbDocumentDeleteEvent;
import com.yangqiongai.ai.platform.knowledge.mapper.KbDocumentMapper;
import com.yangqiongai.ai.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.yangqiongai.ai.platform.knowledge.service.DocumentIngestService;
import com.yangqiongai.ai.storage.DocumentStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文档入库服务
 * @author yangqiong
 */
@Service
public class DocumentIngestServiceImpl implements DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestServiceImpl.class);

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 知识库元数据端口实现（版本继承）
     */
    @Autowired
    private KnowledgeBaseMetadataServiceImpl knowledgeBaseMetadataService;

    @Autowired
    private DocumentStorageService documentStorageService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${ai.strage.minio.default-bucket:yangqiong-ai}")
    private String bucketName;

    @Override
    public KbDocument ingestDocument(String kbId, String fileName, byte[] fileContent, String contentType) {
        if (fileContent == null || fileContent.length == 0) {
            throw new AiException(AiErrorCode.RAG_DOCUMENT_NOT_FOUND, "文件内容不能为空");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new AiException(AiErrorCode.RAG_DOCUMENT_NOT_FOUND, "知识库ID不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new AiException(AiErrorCode.RAG_DOCUMENT_NOT_FOUND, "文件名不能为空");
        }

        // 计算文件内容MD5，用于去重
        String contentHash = DigestUtils.md5Hex(fileContent);

        // 查询同一知识库下是否存在相同内容的文档
        KbDocument existingDoc = findExistingDocument(kbId, contentHash);
        if (existingDoc != null) {
            return handleDuplicateDocument(existingDoc, kbId, fileName, fileContent, contentType, contentHash);
        }

        // 新文档：正常上传
        KbDocument doc = createNewDocument(kbId, fileName, fileContent, contentType, contentHash);
        return doc;
    }

    /**
     * 查询同一知识库下是否存在相同内容哈希的文档
     */
    private KbDocument findExistingDocument(String kbId, String contentHash) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getContentHash, contentHash)
                .last("LIMIT 1");
        return kbDocumentMapper.selectOne(wrapper);
    }

    /**
     * 处理重复文档
     * <ul>
     *   <li>COMPLETED → 返回已有文档（幂等）</li>
     *   <li>FAILED → 删除旧记录及关联数据，重新上传</li>
     *   <li>处理中（PENDING/PARSING等）→ 返回已有文档，提示正在处理</li>
     * </ul>
     */
    private KbDocument handleDuplicateDocument(KbDocument existingDoc, String kbId,
                                                String fileName, byte[] fileContent,
                                                String contentType, String contentHash) {
        DocumentStatus status = existingDoc.getDocStatus();
        log.info("检测到重复文档, docId: {}, kbId: {}, status: {}, contentHash: {}",
                existingDoc.getDocId(), kbId, status, contentHash);

        if (DocumentStatus.COMPLETED == status) {
            log.info("文档已存在且处理完成, 跳过重复上传, docId: {}", existingDoc.getDocId());
            return existingDoc;
        }

        if (DocumentStatus.FAILED == status) {
            log.info("文档已存在但处理失败, 删除旧记录后重新上传, oldDocId: {}", existingDoc.getDocId());
            // 发布删除事件，清理切片和向量
            eventPublisher.publishEvent(new KbDocumentDeleteEvent(existingDoc.getDocId(), kbId));
            // 删除旧记录
            kbDocumentMapper.deleteById(existingDoc.getId());
            // 重新上传
            return createNewDocument(kbId, fileName, fileContent, contentType, contentHash);
        }

        // 正在处理中（PENDING/PARSING/CHUNKED/EMBEDDING等）
        log.info("文档正在处理中, 返回已有记录, docId: {}, status: {}", existingDoc.getDocId(), status);
        return existingDoc;
    }

    /**
     * 创建新文档并上传到MinIO
     */
    private KbDocument createNewDocument(String kbId, String fileName, byte[] fileContent,
                                          String contentType, String contentHash) {
        KbDocument doc = new KbDocument();
        doc.setDocId(UUID.randomUUID().toString());
        doc.setKbId(kbId);
        doc.setDocName(fileName);
        doc.setFileSize((long) fileContent.length);
        doc.setContentHash(contentHash);
        doc.setDocStatus(DocumentStatus.PENDING);
        doc.setSourceType("FILE");
        doc.setCreateTime(LocalDateTime.now());
        doc.setFileBucket(bucketName);
        doc.setVersionTag(knowledgeBaseMetadataService.ensureActiveVersion(kbId));

        // 从知识库继承userId
        LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
        kbWrapper.eq(KnowledgeBase::getKbId, kbId);
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(kbWrapper);
        if (kb != null && kb.getUserId() != null) {
            doc.setUserId(kb.getUserId());
        }

        kbDocumentMapper.insert(doc);

        String objectKey = kbId + "/" + doc.getDocId() + "/" + fileName;
        try {
            documentStorageService.uploadFile(bucketName, objectKey,
                    new ByteArrayInputStream(fileContent), contentType);
            doc.setFilePath(objectKey);
            doc.setUpdateTime(LocalDateTime.now());
            kbDocumentMapper.updateById(doc);
            log.info("文档已上传并记录, docId: {}, kbId: {}, contentHash: {}", doc.getDocId(), kbId, contentHash);
            eventPublisher.publishEvent(new DocumentIngestEvent(doc.getDocId(), kbId));
        } catch (Exception e) {
            log.error("MinIO上传失败, 更新文档状态为FAILED, docId: {}", doc.getDocId(), e);
            doc.setDocStatus(DocumentStatus.FAILED);
            doc.setErrorMessage("文件上传失败: " + e.getMessage());
            doc.setUpdateTime(LocalDateTime.now());
            kbDocumentMapper.updateById(doc);
            throw new AiException(AiErrorCode.RAG_INGEST_FAILED, "文件上传失败: " + e.getMessage());
        }
        return doc;
    }

    @Override
    public void reprocessDocument(String docId) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getDocId, docId);
        KbDocument doc = kbDocumentMapper.selectOne(wrapper);
        if (doc == null) {
            throw new AiException(AiErrorCode.RAG_KB_DOCUMENT_NOT_FOUND, docId);
        }
        doc.setDocStatus(DocumentStatus.PENDING);
        doc.setErrorMessage(null);
        doc.setUpdateTime(LocalDateTime.now());
        kbDocumentMapper.updateById(doc);
        log.info("文档已重置为待处理状态, docId: {}", docId);
        eventPublisher.publishEvent(new DocumentIngestEvent(docId, doc.getKbId()));
    }
}
