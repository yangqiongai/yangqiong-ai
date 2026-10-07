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
package com.yangqiongai.ai.memory.repository;

import com.yangqiongai.ai.memory.model.ConversationSessionInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 对话会话
 * @author yangqiong
 */
public interface ConversationSessionRepository {

    /**
     * 创建会话
     * @param session
     * @return
     */
    ConversationSessionInfo create(ConversationSessionInfo session);

    /**
     * 插入或更新
     * @param session
     */
    void insertOnDuplicateKeyUpdate(ConversationSessionInfo session);

    /**
     * 根据会话ID查找
     * @param sessionId
     * @return
     */
    Optional<ConversationSessionInfo> findBySessionId(String sessionId);

    /**
     * 根据用户ID查找会话列表
     * @param userId
     * @return
     */
    List<ConversationSessionInfo> findByUserId(String userId);

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
    List<ConversationSessionInfo> search(String userId, String keyword, Integer status,
                                           String agentCode, LocalDateTime startTime,
                                           LocalDateTime endTime, int page, int size);

    /**
     * 更新会话
     * @param session
     */
    void update(ConversationSessionInfo session);

    /**
     * 更新会话摘要
     * @param sessionId
     * @param summaryText
     */
    void updateSummaryText(String sessionId, String summaryText);

    /**
     * 更新摘要追踪信息
     * @param sessionId
     * @param summaryRound
     * @param latestSummaryId
     */
    void updateSummaryTracking(String sessionId, Integer summaryRound, String latestSummaryId);

    /**
     * 根据主键删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 关闭会话
     * @param sessionId
     */
    void closeSession(String sessionId);

    /**
     * 统计用户总会话数
     * @param userId
     * @return
     */
    long countByUserId(String userId);

    /**
     * 统计用户指定状态的会话数
     * @param userId
     * @param status
     * @return
     */
    long countByStatus(String userId, int status);

    /**
     * 查询不活跃会话（指定状态且更新时间早于阈值）
     * @param status
     * @param threshold
     * @return
     */
    List<ConversationSessionInfo> findInactiveSessions(int status, LocalDateTime threshold);

    /**
     * 归档会话（将状态设为ARCHIVED并记录归档时间）
     * @param sessionId
     */
    void archiveSession(String sessionId);
}
