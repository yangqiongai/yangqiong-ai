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
package com.yangqiongai.ai.platform.conversation.governance;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.memory.enums.SessionStatus;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 会话配额治理，控制用户活跃会话数与总会话数
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class SessionQuotaService {

    private static final Logger log = LoggerFactory.getLogger(SessionQuotaService.class);

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    /**
     * 每用户最大活跃会话数，0 表示不限
     */
    @Value("${ai.conversation.quota.max-active-sessions:10}")
    private int maxActiveSessions;

    /**
     * 每用户最大总会话数（含归档），0 表示不限
     */
    @Value("${ai.conversation.quota.max-total-sessions:100}")
    private int maxTotalSessions;

    /**
     * 校验用户配额，超限抛 ConversationQuotaExceededException
     * @param userId
     */
    public void checkQuota(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (maxActiveSessions > 0) {
            long activeCount = countByStatus(userId, SessionStatus.ACTIVE.getCode());
            if (activeCount >= maxActiveSessions) {
                log.warn("用户活跃会话数超限: userId={}, activeCount={}, limit={}",
                        userId, activeCount, maxActiveSessions);
                throw new ConversationQuotaExceededException(
                        AiErrorCode.CONVERSATION_QUOTA_EXCEEDED,
                        "活跃会话数超限，当前=" + activeCount + "，上限=" + maxActiveSessions);
            }
        }
        if (maxTotalSessions > 0) {
            long totalCount = countByUserId(userId);
            if (totalCount >= maxTotalSessions) {
                log.warn("用户总会话数超限: userId={}, totalCount={}, limit={}",
                        userId, totalCount, maxTotalSessions);
                throw new ConversationQuotaExceededException(
                        AiErrorCode.CONVERSATION_QUOTA_EXCEEDED,
                        "总会话数超限，当前=" + totalCount + "，上限=" + maxTotalSessions);
            }
        }
    }

    /**
     * 统计用户指定状态的会话数
     * @param userId
     * @param status
     * @return
     */
    private long countByStatus(String userId, int status) {
        return conversationSessionRepository.countByStatus(userId, status);
    }

    /**
     * 统计用户总会话数
     * @param userId
     * @return
     */
    private long countByUserId(String userId) {
        return conversationSessionRepository.countByUserId(userId);
    }

    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }

    public int getMaxTotalSessions() {
        return maxTotalSessions;
    }
}
