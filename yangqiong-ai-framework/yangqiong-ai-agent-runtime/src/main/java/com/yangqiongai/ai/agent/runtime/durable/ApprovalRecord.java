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
package com.yangqiongai.ai.agent.runtime.durable;

/**
 * 审批记录
 * <p>
 * 描述一次工具调用审批的完整信息，供{@link ApprovalStore}持久化与跨节点查询。
 * </p>
 * @author yangqiong
 */
public class ApprovalRecord {

    /**
     * 审批状态
     */
    public enum ApprovalState {

        /**
         * 待审批
         */
        PENDING,

        /**
         * 已批准
         */
        APPROVED,

        /**
         * 已拒绝
         */
        DENIED
    }

    /**
     * 审批ID
     */
    private final String approvalId;

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 工具调用ID
     */
    private final String toolCallId;

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 隔离域ID
     */
    private final String scopeId;

    /**
     * 审批人ID
     */
    private final String approverId;

    /**
     * 当前审批状态
     */
    private ApprovalState state;

    /**
     * 审批意见
     */
    private String reason;

    /**
     * 创建时间戳（毫秒）
     */
    private final long createdAt;

    public ApprovalRecord(String approvalId, String runId, String toolCallId, String toolName,
                          String scopeId, String approverId, ApprovalState state,
                          String reason, long createdAt) {
        this.approvalId = approvalId;
        this.runId = runId;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.scopeId = scopeId;
        this.approverId = approverId;
        this.state = state;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    /**
     * 创建待审批记录
     * @param approvalId
     * @param runId
     * @param toolCallId
     * @param toolName
     * @param scopeId
     * @param approverId
     * @return
     */
    public static ApprovalRecord pending(String approvalId, String runId, String toolCallId,
                                         String toolName, String scopeId, String approverId) {
        return new ApprovalRecord(approvalId, runId, toolCallId, toolName,
                scopeId, approverId, ApprovalState.PENDING, null, System.currentTimeMillis());
    }

    /**
     * 审批落定，返回携带结果的新记录，原记录不变
     * @param approved
     * @param reason
     * @return
     */
    public ApprovalRecord resolve(boolean approved, String reason) {
        ApprovalState target = approved ? ApprovalState.APPROVED : ApprovalState.DENIED;
        return new ApprovalRecord(approvalId, runId, toolCallId, toolName, scopeId,
                approverId, target, reason, createdAt);
    }

    /**
     * 获取审批ID
     * @return
     */
    public String getApprovalId() {
        return approvalId;
    }

    /**
     * 获取运行ID
     * @return
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取隔离域ID
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取审批人ID
     * @return
     */
    public String getApproverId() {
        return approverId;
    }

    /**
     * 获取当前审批状态
     * @return
     */
    public ApprovalState getState() {
        return state;
    }

    /**
     * 设置当前审批状态
     * @param state
     */
    public void setState(ApprovalState state) {
        this.state = state;
    }

    /**
     * 获取审批意见
     * @return
     */
    public String getReason() {
        return reason;
    }

    /**
     * 设置审批意见
     * @param reason
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * 获取创建时间戳（毫秒）
     * @return
     */
    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "ApprovalRecord{approvalId=" + approvalId + ", toolCallId=" + toolCallId
                + ", toolName=" + toolName + ", state=" + state + "}";
    }
}
