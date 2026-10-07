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
package com.yangqiongai.ai.data.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * 对话会话
 * @author yangqiong
 */
@TableName("ai_conversation_session")
public class ConversationSession extends ScopeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 会话标题
     */
    private String sessionTitle;

    /**
     * 会话摘要
     */
    private String summaryText;

    /**
     * 会话类型
     */
    private String sessionType;

    /**
     * 请求数据体
     */
    private String body;

    /**
     * 摘要轮次
     */
    private Integer summaryRound;

    /**
     * 最新摘要ID
     */
    private String latestSummaryId;

    /**
     * 最后摘要时间
     */
    private LocalDateTime lastSummarizedAt;

    /**
     * 会话状态
     */
    private Integer sessionStatus;

    /**
     * 归档时间
     */
    private LocalDateTime archivedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    /**
     * @deprecated 使用 {@link #setBody(String)} 代替，此方法因命名错误已弃用
     */
    @Deprecated
    public void setMetadata(String body) {
        this.body = body;
    }

    public Integer getSummaryRound() {
        return summaryRound;
    }

    public void setSummaryRound(Integer summaryRound) {
        this.summaryRound = summaryRound;
    }

    public String getLatestSummaryId() {
        return latestSummaryId;
    }

    public void setLatestSummaryId(String latestSummaryId) {
        this.latestSummaryId = latestSummaryId;
    }

    public LocalDateTime getLastSummarizedAt() {
        return lastSummarizedAt;
    }

    public void setLastSummarizedAt(LocalDateTime lastSummarizedAt) {
        this.lastSummarizedAt = lastSummarizedAt;
    }

    public Integer getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(Integer sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(LocalDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
