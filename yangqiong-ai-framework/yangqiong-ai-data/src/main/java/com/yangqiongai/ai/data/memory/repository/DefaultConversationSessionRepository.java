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
package com.yangqiongai.ai.data.memory.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.data.memory.entity.ConversationSession;
import com.yangqiongai.ai.data.memory.mapper.ConversationSessionMapper;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 对话会话
 * @author yangqiong
 */
public class DefaultConversationSessionRepository implements ConversationSessionRepository {

    @Autowired
    private ConversationSessionMapper conversationSessionMapper;

    @Override
    public ConversationSessionInfo create(ConversationSessionInfo session) {
        ConversationSession entity = toEntity(session);
        conversationSessionMapper.insert(entity);
        session.setId(entity.getId());
        return session;
    }

    @Override
    public void insertOnDuplicateKeyUpdate(ConversationSessionInfo session) {
        ConversationSession entity = toEntity(session);
        conversationSessionMapper.insertOnDuplicateKeyUpdate(entity);
    }

    @Override
    public Optional<ConversationSessionInfo> findBySessionId(String sessionId) {
        LambdaQueryWrapper<ConversationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationSession::getSessionId, sessionId);
        ConversationSession session = conversationSessionMapper.selectOne(wrapper);
        return Optional.ofNullable(session).map(this::toModel);
    }

    @Override
    public List<ConversationSessionInfo> findByUserId(String userId) {
        LambdaQueryWrapper<ConversationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationSession::getUserId, userId)
                .orderByDesc(ConversationSession::getCreateTime);
        return conversationSessionMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<ConversationSessionInfo> search(String userId, String keyword, Integer status,
                                                   String agentCode, LocalDateTime startTime,
                                                   LocalDateTime endTime, int page, int size) {
        LambdaQueryWrapper<ConversationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationSession::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ConversationSession::getSessionTitle, keyword)
                    .or().like(ConversationSession::getSummaryText, keyword));
        }
        if (status != null) {
            wrapper.eq(ConversationSession::getSessionStatus, status);
        }
        if (agentCode != null && !agentCode.isBlank()) {
            wrapper.eq(ConversationSession::getAgentCode, agentCode);
        }
        if (startTime != null) {
            wrapper.ge(ConversationSession::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(ConversationSession::getCreateTime, endTime);
        }
        wrapper.orderByDesc(ConversationSession::getCreateTime);
        Page<ConversationSession> entityPage = conversationSessionMapper.selectPage(new Page<>(page, size), wrapper);
        return entityPage.getRecords().stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void update(ConversationSessionInfo session) {
        ConversationSession entity = toEntity(session);
        if (entity.getId() == null) {
            // 无主键时按sessionId定位更新，避免updateById空转（引擎侧会话状态刷新不带id）
            LambdaUpdateWrapper<ConversationSession> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(ConversationSession::getSessionId, session.getSessionId())
                    .set(ConversationSession::getAgentCode, entity.getAgentCode())
                    .set(ConversationSession::getSessionTitle, entity.getSessionTitle())
                    .set(ConversationSession::getSessionType, entity.getSessionType())
                    .set(ConversationSession::getSessionStatus, entity.getSessionStatus())
                    .set(ConversationSession::getSummaryRound, entity.getSummaryRound())
                    .set(ConversationSession::getUpdateTime, LocalDateTime.now());
            conversationSessionMapper.update(wrapper);
            return;
        }
        conversationSessionMapper.updateById(entity);
    }

    @Override
    public void updateSummaryText(String sessionId, String summaryText) {
        LambdaUpdateWrapper<ConversationSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ConversationSession::getSessionId, sessionId)
                .set(ConversationSession::getSummaryText, summaryText);
        conversationSessionMapper.update(wrapper);
    }

    @Override
    public void updateSummaryTracking(String sessionId, Integer summaryRound, String latestSummaryId) {
        LambdaUpdateWrapper<ConversationSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ConversationSession::getSessionId, sessionId)
                .set(ConversationSession::getSummaryRound, summaryRound)
                .set(ConversationSession::getLatestSummaryId, latestSummaryId)
                .set(ConversationSession::getLastSummarizedAt, LocalDateTime.now());
        conversationSessionMapper.update(wrapper);
    }

    @Override
    public void deleteById(Long id) {
        conversationSessionMapper.deleteById(id);
    }

    @Override
    public void closeSession(String sessionId) {
        LambdaUpdateWrapper<ConversationSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ConversationSession::getSessionId, sessionId)
                .set(ConversationSession::getSessionStatus, 0)
                .set(ConversationSession::getUpdateTime, LocalDateTime.now());
        conversationSessionMapper.update(wrapper);
    }

    @Override
    public long countByUserId(String userId) {
        LambdaQueryWrapper<ConversationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationSession::getUserId, userId);
        return conversationSessionMapper.selectCount(wrapper);
    }

    @Override
    public long countByStatus(String userId, int status) {
        LambdaQueryWrapper<ConversationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationSession::getUserId, userId)
                .eq(ConversationSession::getSessionStatus, status);
        return conversationSessionMapper.selectCount(wrapper);
    }

    @Override
    public List<ConversationSessionInfo> findInactiveSessions(int status, LocalDateTime threshold) {
        LambdaQueryWrapper<ConversationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationSession::getSessionStatus, status)
                .lt(ConversationSession::getUpdateTime, threshold);
        return conversationSessionMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void archiveSession(String sessionId) {
        LambdaUpdateWrapper<ConversationSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ConversationSession::getSessionId, sessionId)
                .set(ConversationSession::getSessionStatus, 2)
                .set(ConversationSession::getArchivedAt, LocalDateTime.now())
                .set(ConversationSession::getUpdateTime, LocalDateTime.now());
        conversationSessionMapper.update(wrapper);
    }

    private ConversationSession toEntity(ConversationSessionInfo model) {
        ConversationSession entity = new ConversationSession();
        entity.setId(model.getId());
        entity.setSessionId(model.getSessionId());
        entity.setUserId(model.getUserId());
        entity.setScopeId(model.getScopeId());
        entity.setAgentCode(model.getAgentCode());
        entity.setSessionTitle(model.getSessionTitle());
        entity.setSummaryText(model.getSummaryText());
        entity.setSessionType(model.getSessionType());
        entity.setBody(model.getBody());
        entity.setSummaryRound(model.getSummaryRound());
        entity.setLatestSummaryId(model.getLatestSummaryId());
        entity.setLastSummarizedAt(model.getLastSummarizedAt());
        entity.setSessionStatus(model.getSessionStatus());
        entity.setArchivedAt(model.getArchivedAt());
        return entity;
    }

    private ConversationSessionInfo toModel(ConversationSession entity) {
        ConversationSessionInfo model = new ConversationSessionInfo();
        model.setId(entity.getId());
        model.setSessionId(entity.getSessionId());
        model.setUserId(entity.getUserId());
        model.setScopeId(entity.getScopeId());
        model.setAgentCode(entity.getAgentCode());
        model.setSessionTitle(entity.getSessionTitle());
        model.setSummaryText(entity.getSummaryText());
        model.setSessionType(entity.getSessionType());
        model.setBody(entity.getBody());
        model.setSummaryRound(entity.getSummaryRound());
        model.setLatestSummaryId(entity.getLatestSummaryId());
        model.setLastSummarizedAt(entity.getLastSummarizedAt());
        model.setSessionStatus(entity.getSessionStatus());
        model.setArchivedAt(entity.getArchivedAt());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }
}
