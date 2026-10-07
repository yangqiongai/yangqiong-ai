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
package com.yangqiongai.ai.workflow.model;

/**
 * 工作流暂停恢复流水
 */
public class WorkflowPauseHistory {

    /**
     * 动作：暂停
     */
    public static final String ACTION_PAUSE = "PAUSE";

    /**
     * 动作：恢复
     */
    public static final String ACTION_RESUME = "RESUME";

    /**
     * ID
     */
    private String id;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 工作流名称
     */
    private String definitionName;

    /**
     * 暂停节点ID
     */
    private String pausedNodeId;

    /**
     * 动作（PAUSE/RESUME）
     */
    private String action;

    /**
     * 原因（暂停为用户输入，恢复为恢复方式说明）
     */
    private String reason;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 操作时间（毫秒）
     */
    private Long operatorTime;

    /**
     * 关联审批请求ID
     */
    private String pendingRequestId;

    /**
     * 本次暂停等待时长（毫秒，恢复时计算）
     */
    private Long waitDurationMs;

    /**
     * 创建时间（毫秒）
     */
    private Long createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public void setDefinitionName(String definitionName) {
        this.definitionName = definitionName;
    }

    public String getPausedNodeId() {
        return pausedNodeId;
    }

    public void setPausedNodeId(String pausedNodeId) {
        this.pausedNodeId = pausedNodeId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Long getOperatorTime() {
        return operatorTime;
    }

    public void setOperatorTime(Long operatorTime) {
        this.operatorTime = operatorTime;
    }

    public String getPendingRequestId() {
        return pendingRequestId;
    }

    public void setPendingRequestId(String pendingRequestId) {
        this.pendingRequestId = pendingRequestId;
    }

    public Long getWaitDurationMs() {
        return waitDurationMs;
    }

    public void setWaitDurationMs(Long waitDurationMs) {
        this.waitDurationMs = waitDurationMs;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
