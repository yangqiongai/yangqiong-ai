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
package com.yangqiongai.ai.platform.knowledge.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.knowledge.entity.KbDocument;
import com.yangqiongai.ai.common.event.KbDocumentDeleteEvent;
import com.yangqiongai.ai.common.event.ScopeStorageUsageEvent;
import com.yangqiongai.ai.common.scope.PlanLimitGuard;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.platform.knowledge.mapper.KbDocumentMapper;
import com.yangqiongai.ai.platform.knowledge.service.DocumentIngestService;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识库文档管理
 * @author yangqiong
 */
@Tag(name = "知识库文档管理接口")
@RestController
@RequestMapping("/api/knowledge-base/{kbId}/documents")
public class KbDocumentController {

    private static final Logger log = LoggerFactory.getLogger(KbDocumentController.class);

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    @Autowired
    private DocumentIngestService documentIngestService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlanLimitGuard planLimitGuard;

    /**
     * 上传文档入库
     * @param kbId
     * @param file
     * @return
     */
    @Operation(summary = "上传文档入库")
    @PostMapping("/upload")
    public ApiResult<KbDocument> uploadDocument(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "file", description = "文档文件") @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResult.fail("文件不能为空");
        }
        planLimitGuard.checkStorageLimit(file.getSize());
        try {
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            KbDocument doc = documentIngestService.ingestDocument(
                    kbId, fileName, file.getBytes(), contentType);
            eventPublisher.publishEvent(new ScopeStorageUsageEvent(
                    ScopeContext.getScopeId(), file.getSize()));
            log.info("文档上传入库请求已提交, kbId: {}, fileName: {}, docId: {}",
                    kbId, fileName, doc.getDocId());
            return ApiResult.ok(doc);
        } catch (IOException e) {
            log.error("文件读取失败, kbId: {}", kbId, e);
            return ApiResult.fail("文件读取失败: " + e.getMessage());
        }
    }

    /**
     * 重新处理文档
     * @param kbId
     * @param docId
     * @return
     */
    @Operation(summary = "重新处理文档")
    @PostMapping("/{docId}/reprocess")
    public ApiResult<Void> reprocessDocument(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "docId", description = "文档ID") @PathVariable String docId) {
        documentIngestService.reprocessDocument(docId);
        log.info("文档重处理请求已提交, kbId: {}, docId: {}", kbId, docId);
        return ApiResult.ok();
    }

    /**
     * 查询知识库下的文档列表
     * @param kbId
     * @param userId
     * @return
     */
    @Operation(summary = "查询知识库下的文档列表")
    @GetMapping
    public ApiResult<List<KbDocument>> listDocuments(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "userId", description = "用户ID, 不传则查询该知识库全部文档") @RequestParam(required = false) String userId) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getKbId, kbId);
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(KbDocument::getUserId, userId);
        }
        wrapper.orderByDesc(KbDocument::getCreateTime);
        return ApiResult.ok(kbDocumentMapper.selectList(wrapper));
    }

    /**
     * 查询文档详情
     * @param kbId
     * @param docId
     * @return
     */
    @Operation(summary = "查询文档详情")
    @GetMapping("/{docId}")
    public ApiResult<KbDocument> getDocument(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "docId", description = "文档ID") @PathVariable String docId) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getDocId, docId);
        KbDocument doc = kbDocumentMapper.selectOne(wrapper);
        if (doc == null) {
            return ApiResult.fail(AiErrorCode.RAG_KB_DOCUMENT_NOT_FOUND.getCode(), "文档不存在: " + docId);
        }
        return ApiResult.ok(doc);
    }

    /**
     * 删除文档
     * @param kbId
     * @param docId
     * @return
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{docId}")
    public ApiResult<Void> deleteDocument(
            @Parameter(name = "kbId", description = "知识库ID") @PathVariable String kbId,
            @Parameter(name = "docId", description = "文档ID") @PathVariable String docId) {
        LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getDocId, docId);
        KbDocument doc = kbDocumentMapper.selectOne(docWrapper);
        if (doc == null) {
            throw new AiException(AiErrorCode.RAG_KB_DOCUMENT_NOT_FOUND, docId);
        }

        kbDocumentMapper.deleteById(doc.getId());

        // 发布事件通知ai-rag清理切片和父块数据
        eventPublisher.publishEvent(new KbDocumentDeleteEvent(docId, kbId));
        log.info("文档已删除, docId: {}, 已通知切片清理", docId);
        return ApiResult.ok();
    }
}
