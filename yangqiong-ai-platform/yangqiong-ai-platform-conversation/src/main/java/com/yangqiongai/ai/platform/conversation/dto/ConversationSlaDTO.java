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
 * 会话 SLA 统计
 * @author yangqiong
 */
public class ConversationSlaDTO {

    /**
     * 总对话次数
     */
    private long totalConversations;

    /**
     * 平均响应时间（毫秒）
     */
    private double avgResponseTimeMs;

    /**
     * P95 响应时间（毫秒）
     */
    private double p95ResponseTimeMs;

    /**
     * P99 响应时间（毫秒）
     */
    private double p99ResponseTimeMs;

    /**
     * 最大响应时间（毫秒）
     */
    private long maxResponseTimeMs;

    /**
     * 平均 Token 消耗
     */
    private double avgTokenCount;

    public long getTotalConversations() {
        return totalConversations;
    }

    public void setTotalConversations(long totalConversations) {
        this.totalConversations = totalConversations;
    }

    public double getAvgResponseTimeMs() {
        return avgResponseTimeMs;
    }

    public void setAvgResponseTimeMs(double avgResponseTimeMs) {
        this.avgResponseTimeMs = avgResponseTimeMs;
    }

    public double getP95ResponseTimeMs() {
        return p95ResponseTimeMs;
    }

    public void setP95ResponseTimeMs(double p95ResponseTimeMs) {
        this.p95ResponseTimeMs = p95ResponseTimeMs;
    }

    public double getP99ResponseTimeMs() {
        return p99ResponseTimeMs;
    }

    public void setP99ResponseTimeMs(double p99ResponseTimeMs) {
        this.p99ResponseTimeMs = p99ResponseTimeMs;
    }

    public long getMaxResponseTimeMs() {
        return maxResponseTimeMs;
    }

    public void setMaxResponseTimeMs(long maxResponseTimeMs) {
        this.maxResponseTimeMs = maxResponseTimeMs;
    }

    public double getAvgTokenCount() {
        return avgTokenCount;
    }

    public void setAvgTokenCount(double avgTokenCount) {
        this.avgTokenCount = avgTokenCount;
    }
}
