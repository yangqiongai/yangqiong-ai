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
package com.yangqiongai.ai.agent.core.executor;

import java.time.Instant;

/**
 * 暂停恢复登记
 * <p>
 * 引擎确认与澄清暂停时的持久登记载体，同节点恢复直接携带内存句柄续跑，
 * 跨节点恢复（句柄为空）凭requestData与pendingData重建现场。
 * </p>
 * @author yangqiong
 */
public class PendingResumeEntry {

    /**
     * 恢复请求标识（confirm:会话ID 或 clarification:会话ID:工具调用ID）
     */
    private String requestId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 关联工具调用ID（澄清场景）
     */
    private String toolCallId;

    /**
     * 租户范围ID
     */
    private String scopeId;

    /**
     * 引擎运行ID
     */
    private String runId;

    /**
     * 智能体编码
     */
    private String agentCode;

    /**
     * 恢复类型：CONFIRM/CLARIFICATION
     */
    private String resumeType;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 注册节点标识
     */
    private String nodeId;

    /**
     * 待恢复数据JSON（待确认工具清单或澄清请求）
     */
    private String pendingData;

    /**
     * 暂停时请求JSON（跨节点重建上下文用）
     */
    private String requestData;

    /**
     * 状态：PENDING待恢复/RESOLVED已恢复/EXPIRED已过期
     */
    private String status;

    /**
     * 过期时间
     */
    private Instant expireTime;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 引擎确认内存句柄（仅内存实现携带，跨节点JDBC实现为空）
     */
    private transient ConfirmResumeHandle confirmHandle;

    /**
     * 澄清内存句柄（仅内存实现携带，跨节点JDBC实现为空）
     */
    private transient ClarificationResumeHandle clarificationHandle;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getResumeType() {
        return resumeType;
    }

    public void setResumeType(String resumeType) {
        this.resumeType = resumeType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getPendingData() {
        return pendingData;
    }

    public void setPendingData(String pendingData) {
        this.pendingData = pendingData;
    }

    public String getRequestData() {
        return requestData;
    }

    public void setRequestData(String requestData) {
        this.requestData = requestData;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Instant expireTime) {
        this.expireTime = expireTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ConfirmResumeHandle getConfirmHandle() {
        return confirmHandle;
    }

    public void setConfirmHandle(ConfirmResumeHandle confirmHandle) {
        this.confirmHandle = confirmHandle;
    }

    public ClarificationResumeHandle getClarificationHandle() {
        return clarificationHandle;
    }

    public void setClarificationHandle(ClarificationResumeHandle clarificationHandle) {
        this.clarificationHandle = clarificationHandle;
    }
}
