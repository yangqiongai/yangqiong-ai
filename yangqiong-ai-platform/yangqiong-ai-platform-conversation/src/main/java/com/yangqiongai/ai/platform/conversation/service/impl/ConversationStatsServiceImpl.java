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
package com.yangqiongai.ai.platform.conversation.service.impl;

import com.yangqiongai.ai.platform.conversation.dto.ConversationStatsDTO;
import com.yangqiongai.ai.platform.conversation.governance.TokenBudgetTracker;
import com.yangqiongai.ai.platform.conversation.service.ConversationStatsService;
import com.yangqiongai.ai.memory.enums.SessionStatus;
import com.yangqiongai.ai.memory.repository.ChatMemoryRepository;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 会话统计服务
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class ConversationStatsServiceImpl implements ConversationStatsService {

    private static final Logger log = LoggerFactory.getLogger(ConversationStatsServiceImpl.class);

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Autowired
    private UserLongTermMemoryRepository userLongTermMemoryRepository;

    @Autowired
    private TokenBudgetTracker tokenBudgetTracker;

    @Override
    public ConversationStatsDTO getSessionStats(String userId) {
        ConversationStatsDTO stats = new ConversationStatsDTO();

        // 活跃会话数
        stats.setActiveSessions(conversationSessionRepository.countByStatus(userId, SessionStatus.ACTIVE.getCode()));

        // 总会话数
        stats.setTotalSessions(conversationSessionRepository.countByUserId(userId));

        // 总消息数
        stats.setTotalMessages(chatMemoryRepository.countByUserId(userId));

        // 长期记忆条数
        stats.setLongTermMemoryCount(userLongTermMemoryRepository.countByUserId(userId));

        // 当日 Token 用量（按当日消息求和，与总量同口径；不依赖Redis追踪，Redis未装配时也能准确统计）
        stats.setTodayTokensUsed(chatMemoryRepository.sumTokensByUserIdAndCreateTimeAfter(userId, LocalDate.now().atStartOfDay()));

        // 每日 Token 上限
        stats.setDailyTokenLimit(tokenBudgetTracker.getDailyTokenLimit());

        // 总 Token 用量（优先使用真实Token，无真实值时降级为估算值）
        stats.setTotalTokensUsed(chatMemoryRepository.sumTokensByUserId(userId));

        log.debug("用户会话统计: userId={}, stats={}", userId, stats);
        return stats;
    }
}
