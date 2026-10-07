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
import com.yangqiongai.ai.common.event.DocumentIngestEvent;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.rag.DocumentMetadataHandler;
import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.yangqiongai.ai.common.rag.IngestDocument;
import com.yangqiongai.ai.platform.knowledge.entity.DataSourceIngestLog;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.platform.knowledge.mapper.DataSourceIngestLogMapper;
import com.yangqiongai.ai.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.yangqiongai.ai.platform.knowledge.service.DataSourceIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源接入
 * @author yangqiong
 */
@Service
public class DataSourceIngestServiceImpl implements DataSourceIngestService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceIngestServiceImpl.class);

    @Autowired
    private DocumentMetadataHandler documentMetadataHandler;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private DataSourceIngestLogMapper dataSourceIngestLogMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public IngestDocument ingestFromDatabase(String kbId, String title, String content) {
        return ingestContent(kbId, title, content, "DATABASE", null);
    }

    @Override
    public IngestDocument ingestFromApi(String kbId, String title, String content, String sourceUrl) {
        return ingestContent(kbId, title, content, "API", sourceUrl);
    }

    @Override
    public IngestDocument ingestFromWebpage(String kbId, String url, String title, String content) {
        return ingestContent(kbId, title, content, "WEBPAGE", url);
    }

    @Override
    public IngestDocument ingestFromText(String kbId, String title, String content) {
        return ingestContent(kbId, title, content, "TEXT", null);
    }

    /**
     * 核心接入逻辑：记录接入历史→执行接入，成功失败均留痕
     * @param kbId
     * @param title
     * @param content
     * @param sourceType
     * @param sourceUrl
     * @return
     */
    private IngestDocument ingestContent(String kbId, String title, String content,
                                         String sourceType, String sourceUrl) {
        long startMs = System.currentTimeMillis();
        try {
            IngestDocument created = doIngest(kbId, title, content, sourceType, sourceUrl);
            // 记录接入成功日志
            saveIngestLog(kbId, title, sourceType, sourceUrl, created.getDocId(),
                    content == null ? null : (long) content.length(), resolveUserIdQuietly(kbId),
                    true, null, System.currentTimeMillis() - startMs);
            return created;
        } catch (RuntimeException e) {
            // 记录接入失败日志后原样抛出，保持错误语义不变
            saveIngestLog(kbId, title, sourceType, sourceUrl, null,
                    content == null ? null : (long) content.length(), resolveUserIdQuietly(kbId),
                    false, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    System.currentTimeMillis() - startMs);
            throw e;
        }
    }

    /**
     * 执行接入：校验→构造文档→持久化→发布事件
     * @param kbId
     * @param title
     * @param content
     * @param sourceType
     * @param sourceUrl
     * @return
     */
    private IngestDocument doIngest(String kbId, String title, String content,
                                    String sourceType, String sourceUrl) {
        if (content == null || content.isBlank()) {
            throw new AiException(AiErrorCode.RAG_INGEST_FAILED, "文本内容不能为空");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new AiException(AiErrorCode.RAG_INGEST_FAILED, "知识库ID不能为空");
        }

        String userId = resolveUserId(kbId);

        IngestDocument doc = new IngestDocument();
        doc.setDocName(title != null && !title.isBlank() ? title : "未命名文档");
        doc.setSourceType(sourceType);
        doc.setContent(content);
        doc.setFileSize((long) content.length());
        doc.setFileType("txt");
        doc.setDocStatus(DocumentStatus.PENDING);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("kbId", kbId);
        if (userId != null) {
            metadata.put("userId", userId);
        }
        if (sourceUrl != null && !sourceUrl.isBlank()) {
            metadata.put("sourceUrl", sourceUrl);
        }
        doc.setMetadata(metadata);

        IngestDocument created = documentMetadataHandler.createDocument(doc);
        log.info("数据源文档已创建, docId: {}, kbId: {}, sourceType: {}", created.getDocId(), kbId, sourceType);
        eventPublisher.publishEvent(new DocumentIngestEvent(created.getDocId(), kbId));
        return created;
    }

    /**
     * 保存接入记录，记录失败仅告警不影响接入主流程
     * @param kbId
     * @param title
     * @param sourceType
     * @param sourceUrl
     * @param docId
     * @param contentSize
     * @param userId
     * @param success
     * @param errorMessage
     * @param durationMs
     */
    private void saveIngestLog(String kbId, String title, String sourceType, String sourceUrl,
                               String docId, Long contentSize, String userId,
                               boolean success, String errorMessage, long durationMs) {
        try {
            DataSourceIngestLog record = new DataSourceIngestLog();
            record.setIngestType(sourceType);
            record.setTitle(title);
            record.setKbId(kbId);
            record.setSourceUrl(sourceUrl);
            record.setDocId(docId);
            record.setContentSize(contentSize);
            record.setUserId(userId);
            record.setSuccess(success);
            record.setErrorMessage(errorMessage);
            record.setDurationMs(durationMs);
            record.setIngestTime(LocalDateTime.now());
            record.setCreateTime(LocalDateTime.now());
            dataSourceIngestLogMapper.insert(record);
        } catch (Exception logError) {
            log.warn("保存数据源接入记录失败, kbId: {}, sourceType: {}", kbId, sourceType, logError);
        }
    }

    /**
     * 从知识库继承userId，查询失败返回null
     * @param kbId
     * @return
     */
    private String resolveUserIdQuietly(String kbId) {
        try {
            return resolveUserId(kbId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从知识库继承userId
     * @param kbId
     * @return
     */
    private String resolveUserId(String kbId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getKbId, kbId);
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        return kb != null ? kb.getUserId() : null;
    }
}
