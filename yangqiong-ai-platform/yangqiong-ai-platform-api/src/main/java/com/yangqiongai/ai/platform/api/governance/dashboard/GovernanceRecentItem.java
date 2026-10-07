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
package com.yangqiongai.ai.platform.api.governance.dashboard;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 治理信号事件条目
 * @author yangqiong
 */
public class GovernanceRecentItem {

    /**
     * 时间格式(与前端口径一致)
     */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 信号类型(与signals键一致: driftDetected/slaDegraded/costOverrun/triggerFailures/...)
     */
    private String type;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * 一行摘要
     */
    private String summary;

    /**
     * 发生时间(yyyy-MM-dd HH:mm:ss)
     */
    private String occurredAt;

    /**
     * 深链路径(可空，如/agent-runs?traceId=xxx，前端渲染"查看"跳转)
     */
    private String link;

    /**
     * 失败模式键(可空，处置回写定位用)
     */
    private String patternKey;

    public GovernanceRecentItem() {
    }

    /**
     * 全参构造
     * @param type
     * @param agentCode
     * @param agentName
     * @param summary
     * @param occurredAt
     */
    public GovernanceRecentItem(String type, String agentCode, String agentName, String summary,
                                LocalDateTime occurredAt) {
        this(type, agentCode, agentName, summary, occurredAt, null);
    }

    /**
     * 全参构造(带深链)
     * @param type
     * @param agentCode
     * @param agentName
     * @param summary
     * @param occurredAt
     * @param link
     */
    public GovernanceRecentItem(String type, String agentCode, String agentName, String summary,
                                LocalDateTime occurredAt, String link) {
        this.type = type;
        this.agentCode = agentCode;
        this.agentName = agentName;
        this.summary = summary;
        this.occurredAt = occurredAt != null ? TIME_FORMATTER.format(occurredAt) : null;
        this.link = link;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getPatternKey() {
        return patternKey;
    }

    public void setPatternKey(String patternKey) {
        this.patternKey = patternKey;
    }
}
