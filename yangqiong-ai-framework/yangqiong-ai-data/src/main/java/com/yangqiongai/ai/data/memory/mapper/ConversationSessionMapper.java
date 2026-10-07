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
package com.yangqiongai.ai.data.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.data.memory.entity.ConversationSession;
import org.apache.ibatis.annotations.Insert;

/**
 * 对话会话
 * @author yangqiong
 */
public interface ConversationSessionMapper extends BaseMapper<ConversationSession> {

    /**
     * 插入或更新
     * @param session
     */
    @Insert("INSERT INTO bss_ai_conversation_session " +
            "(session_id, user_id, agent_code, session_title, summary_text, session_type, body, " +
            "summary_round, latest_summary_id, last_summarized_at, " +
            "session_status, create_user, create_time, update_user, update_time) " +
            "VALUES " +
            "(#{sessionId}, #{userId}, #{agentCode}, #{sessionTitle}, #{summaryText}, #{sessionType}, #{body}, " +
            "#{summaryRound}, #{latestSummaryId}, #{lastSummarizedAt}, " +
            "#{sessionStatus}, #{createUser}, NOW(), #{updateUser}, NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "user_id = VALUES(user_id), " +
            "agent_code = VALUES(agent_code), " +
            "session_title = IF(VALUES(session_title) IS NOT NULL AND VALUES(session_title) != '', VALUES(session_title), session_title), " +
            "session_type = VALUES(session_type), " +
            "body = VALUES(body), " +
            "session_status = VALUES(session_status), " +
            "update_user = VALUES(update_user), " +
            "update_time = NOW()")
    void insertOnDuplicateKeyUpdate(ConversationSession session);
}
