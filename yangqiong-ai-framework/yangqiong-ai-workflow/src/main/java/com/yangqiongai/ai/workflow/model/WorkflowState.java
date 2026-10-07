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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流状态
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowState {

    private String instanceId;

    private String definitionName;

    private ExecutionStatus status;

    private Map<String, NodeExecutionStatus> nodeStates;

    private Map<String, Object> variables;

    /**
     * 工作流定义快照（JSON，用于恢复）
     */
    private String definitionSnapshot;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 更新时间
     */
    private Long updateTime;

    /**
     * 最近心跳时间（僵尸实例回收判定依据）
     */
    private Long lastHeartbeatTime;

    /**
     * 执行时的定义版本号
     */
    private Integer definitionVersion;

    /**
     * 是否请求取消
     */
    private boolean cancelRequested;

    /**
     * 暂停的节点ID（审批等待时记录）
     */
    private String pausedNodeId;

    /**
     * 暂停时关联的审批请求ID
     */
    private String pendingRequestId;

    /**
     * 暂停原因（用户输入）
     */
    private String pausedReason;

    /**
     * 暂停操作人
     */
    private String pausedBy;

    /**
     * 暂停操作时间
     */
    private Long pausedTime;

    public NodeExecutionStatus getNodeState(String nodeId) {
        return nodeStates != null ? nodeStates.get(nodeId) : null;
    }

    public void setNodeState(String nodeId, NodeExecutionStatus nodeState) {
        if (nodeStates == null) {
            nodeStates = new ConcurrentHashMap<>();
        }
        nodeStates.put(nodeId, nodeState);
    }

    public Object getVariable(String key) {
        return variables != null ? variables.get(key) : null;
    }

    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new ConcurrentHashMap<>();
        }
        variables.put(key, value);
    }

    @JsonIgnore
    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING;
    }

    @JsonIgnore
    public boolean isCompleted() {
        return status == ExecutionStatus.COMPLETED;
    }

    @JsonIgnore
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    @JsonIgnore
    public boolean isPaused() {
        return status == ExecutionStatus.PAUSED;
    }

    @JsonIgnore
    public boolean isCancelled() {
        return status == ExecutionStatus.CANCELLED;
    }
}
