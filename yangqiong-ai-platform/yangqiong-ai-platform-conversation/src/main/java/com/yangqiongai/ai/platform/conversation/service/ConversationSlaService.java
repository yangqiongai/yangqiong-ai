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
package com.yangqiongai.ai.platform.conversation.service;

import com.yangqiongai.ai.platform.conversation.dto.ConversationSlaDTO;

/**
 * 会话 SLA 统计服务
 * @author yangqiong
 */
public interface ConversationSlaService {

    /**
     * 记录单次对话响应时间
     * @param userId
     * @param sessionId
     * @param responseTimeMs
     * @param tokenCount
     */
    void recordResponse(String userId, String sessionId, long responseTimeMs, int tokenCount);

    /**
     * 获取用户指定时间范围（天数）内的 SLA 统计
     * @param userId
     * @param days
     * @return
     */
    ConversationSlaDTO getSlaStats(String userId, int days);
}
