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
import com.yangqiongai.ai.platform.knowledge.service.KnowledgeBaseService;
import com.yangqiongai.ai.platform.knowledge.entity.KbDocument;
import com.yangqiongai.ai.platform.knowledge.entity.KnowledgeBase;
import com.yangqiongai.ai.common.event.KnowledgeBasePurgeEvent;
import com.yangqiongai.ai.platform.knowledge.mapper.KbDocumentMapper;
import com.yangqiongai.ai.platform.knowledge.mapper.KnowledgeBaseMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 知识库管理
 * @author yangqiong
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    /**
     * 知识库已删除状态（0-禁用 1-启用 2-已删除）
     */
    private static final int KB_STATUS_DELETED = 2;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public KnowledgeBase create(KnowledgeBase kb) {
        // 强制由系统生成 id 和 kbId，忽略前端传入
        kb.setId(null);
        kb.setKbId(generateKbId());
        if (kb.getCreateTime() == null) {
            kb.setCreateTime(LocalDateTime.now());
        }
        if (kb.getCreateUser() != null) {
            kb.setUpdateUser(kb.getCreateUser());
            kb.setUpdateTime(LocalDateTime.now());
        }
        knowledgeBaseMapper.insert(kb);
        return kb;
    }

    /**
     * 生成唯一的知识库业务ID
     * @return
     */
    private String generateKbId() {
        return "kb_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public Optional<KnowledgeBase> findByKbId(String kbId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getKbId, kbId);
        KnowledgeBase kb = knowledgeBaseMapper.selectOne(wrapper);
        return Optional.ofNullable(kb);
    }

    @Override
    public List<KnowledgeBase> findAll() {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(KnowledgeBase::getKbStatus, KB_STATUS_DELETED);
        return fillDocumentCounts(knowledgeBaseMapper.selectList(wrapper));
    }

    @Override
    public List<KnowledgeBase> findByUserId(String userId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, userId)
                .ne(KnowledgeBase::getKbStatus, KB_STATUS_DELETED)
                .orderByDesc(KnowledgeBase::getCreateTime);
        return fillDocumentCounts(knowledgeBaseMapper.selectList(wrapper));
    }

    /**
     * 批量填充知识库文档数统计
     * @param kbList
     * @return
     */
    private List<KnowledgeBase> fillDocumentCounts(List<KnowledgeBase> kbList) {
        if (kbList == null || kbList.isEmpty()) {
            return kbList;
        }
        // 按kbId分组统计文档数，一条SQL完成
        java.util.Map<String, Long> countMap = kbDocumentMapper.selectList(
                        new LambdaQueryWrapper<KbDocument>().in(KbDocument::getKbId,
                                kbList.stream().map(KnowledgeBase::getKbId).toList()))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(KbDocument::getKbId, java.util.stream.Collectors.counting()));
        kbList.forEach(kb -> kb.setDocumentCount(countMap.getOrDefault(kb.getKbId(), 0L)));
        return kbList;
    }

    @Override
    public KnowledgeBase update(KnowledgeBase kb) {
        KnowledgeBase existing = findByKbId(kb.getKbId())
                .orElseThrow(() -> new AiException(AiErrorCode.RAG_KB_NOT_FOUND, kb.getKbId()));
        kb.setId(existing.getId());
        kb.setKbId(existing.getKbId());
        kb.setCreateTime(existing.getCreateTime());
        kb.setCreateUser(existing.getCreateUser());
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
        return kb;
    }

    @Override
    public void deleteByKbId(String kbId) {
        Optional<KnowledgeBase> optional = findByKbId(kbId);
        if (!optional.isPresent()) {
            throw new AiException(AiErrorCode.RAG_KB_NOT_FOUND, kbId);
        }
        KnowledgeBase kb = optional.get();
        kb.setKbStatus(KB_STATUS_DELETED);
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.updateById(kb);
        log.info("知识库已逻辑删除, kbId: {}", kbId);
    }

    /**
     * 物理删除知识库及其所有关联数据
     * @param kbId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purgeKnowledgeBase(String kbId) {
        Optional<KnowledgeBase> optional = findByKbId(kbId);
        if (!optional.isPresent()) {
            throw new AiException(AiErrorCode.RAG_KB_NOT_FOUND, kbId);
        }

        // 删除文档记录
        LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KbDocument::getKbId, kbId);
        int docCount = kbDocumentMapper.delete(docWrapper);
        log.info("已删除文档记录, kbId: {}, 数量: {}", kbId, docCount);

        // 删除知识库记录
        knowledgeBaseMapper.deleteById(optional.get().getId());
        log.info("知识库已物理删除, kbId: {}", kbId);

        // 发布事件通知ai-rag清理切片和父块数据
        eventPublisher.publishEvent(new KnowledgeBasePurgeEvent(kbId));
        log.info("已发布知识库物理删除事件, kbId: {}", kbId);
    }
}
