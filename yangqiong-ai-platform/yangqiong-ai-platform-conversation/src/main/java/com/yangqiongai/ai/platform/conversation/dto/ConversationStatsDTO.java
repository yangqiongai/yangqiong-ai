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
package com.yangqiongai.ai.platform.conversation.dto;

/**
 * 会话使用统计
 * @author yangqiong
 */
public class ConversationStatsDTO {

    /**
     * 活跃会话数
     */
    private long activeSessions;

    /**
     * 总会话数
     */
    private long totalSessions;

    /**
     * 总消息数
     */
    private long totalMessages;

    /**
     * 总 Token 用量
     */
    private long totalTokensUsed;

    /**
     * 当日 Token 用量
     */
    private long todayTokensUsed;

    /**
     * 每日 Token 上限
     */
    private long dailyTokenLimit;

    /**
     * 长期记忆条数
     */
    private long longTermMemoryCount;

    public long getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(long activeSessions) {
        this.activeSessions = activeSessions;
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public void setTotalTokensUsed(long totalTokensUsed) {
        this.totalTokensUsed = totalTokensUsed;
    }

    public long getTodayTokensUsed() {
        return todayTokensUsed;
    }

    public void setTodayTokensUsed(long todayTokensUsed) {
        this.todayTokensUsed = todayTokensUsed;
    }

    public long getDailyTokenLimit() {
        return dailyTokenLimit;
    }

    public void setDailyTokenLimit(long dailyTokenLimit) {
        this.dailyTokenLimit = dailyTokenLimit;
    }

    public long getLongTermMemoryCount() {
        return longTermMemoryCount;
    }

    public void setLongTermMemoryCount(long longTermMemoryCount) {
        this.longTermMemoryCount = longTermMemoryCount;
    }
}
