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
package com.yangqiongai.ai.memory;

import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 对话会话管理
 * @author yangqiong
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    @Autowired(required = false)
    private ConversationSessionRepository conversationSessionRepository;

    /**
     * 创建会话
     * @param session
     * @return
     */
    public ConversationSessionInfo create(ConversationSessionInfo session) {
        conversationSessionRepository.create(session);
        return session;
    }

    /**
     * 确保会话存在，不存在则创建
     * @param sessionId
     * @param userId
     * @return
     */
    public ConversationSessionInfo ensureSession(String sessionId, String userId) {
        ConversationSessionInfo defaultSession = buildDefaultSession(sessionId, userId);
        conversationSessionRepository.insertOnDuplicateKeyUpdate(defaultSession);
        return findBySessionId(sessionId).orElse(null);
    }

    /**
     * 根据会话ID查找
     * @param sessionId
     * @return
     */
    public Optional<ConversationSessionInfo> findBySessionId(String sessionId) {
        return conversationSessionRepository.findBySessionId(sessionId);
    }

    /**
     * 根据用户ID查找会话列表
     * @param userId
     * @return
     */
    public List<ConversationSessionInfo> findByUserId(String userId) {
        return conversationSessionRepository.findByUserId(userId);
    }

    /**
     * 多条件分页搜索会话
     * @param userId
     * @param keyword
     * @param status
     * @param agentCode
     * @param startTime
     * @param endTime
     * @param page
     * @param size
     * @return
     */
    public List<ConversationSessionInfo> search(String userId, String keyword, Integer status,
                                            String agentCode, LocalDateTime startTime,
                                            LocalDateTime endTime, int page, int size) {
        return conversationSessionRepository.search(userId, keyword, status, agentCode, startTime, endTime, page, size);
    }

    /**
     * 更新会话
     * @param session
     */
    public void update(ConversationSessionInfo session) {
        conversationSessionRepository.update(session);
    }

    /**
     * 更新会话摘要
     * @param sessionId
     * @param summaryText
     */
    public void updateSummaryText(String sessionId, String summaryText) {
        conversationSessionRepository.updateSummaryText(sessionId, summaryText);
    }

    /**
     * 更新摘要追踪信息
     * @param sessionId
     * @param summaryRound
     * @param latestSummaryId
     */
    public void updateSummaryTracking(String sessionId, Integer summaryRound, String latestSummaryId) {
        conversationSessionRepository.updateSummaryTracking(sessionId, summaryRound, latestSummaryId);
    }

    /**
     * 根据会话ID删除
     * @param sessionId
     */
    public void deleteBySessionId(String sessionId) {
        Optional<ConversationSessionInfo> optional = findBySessionId(sessionId);
        if (optional.isPresent()) {
            conversationSessionRepository.deleteById(optional.get().getId());
        }
    }

    /**
     * 关闭会话（将状态设为已结束）
     * @param sessionId
     */
    public void closeSession(String sessionId) {
        conversationSessionRepository.closeSession(sessionId);
        log.info("会话已关闭: sessionId={}", sessionId);
    }

    /**
     * 构建默认会话
     * @param sessionId
     * @param userId
     * @return
     */
    private ConversationSessionInfo buildDefaultSession(String sessionId, String userId) {
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setSessionStatus(1);
        return session;
    }
}
