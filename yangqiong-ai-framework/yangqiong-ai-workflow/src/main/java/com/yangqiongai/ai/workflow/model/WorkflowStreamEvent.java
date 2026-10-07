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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流流式事件
 * @author yangqiong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStreamEvent {

    private String nodeId;

    private String nodeName;

    private String eventType;

    private String payload;

    /**
     * 事件时间戳
     */
    private long timestamp;

    /**
     * 工作流实例ID
     */
    private String instanceId;

    public static WorkflowStreamEvent nodeStart(String nodeId, String nodeName) {
        return new WorkflowStreamEvent(nodeId, nodeName, "NODE_START", null, System.currentTimeMillis(), null);
    }

    public static WorkflowStreamEvent nodeComplete(String nodeId, String nodeName, String payload) {
        return new WorkflowStreamEvent(nodeId, nodeName, "NODE_COMPLETE", payload, System.currentTimeMillis(), null);
    }

    public static WorkflowStreamEvent nodeError(String nodeId, String nodeName, String payload) {
        return new WorkflowStreamEvent(nodeId, nodeName, "NODE_ERROR", payload, System.currentTimeMillis(), null);
    }

    public static WorkflowStreamEvent nodePaused(String nodeId, String nodeName, String payload) {
        return new WorkflowStreamEvent(nodeId, nodeName, "NODE_PAUSED", payload, System.currentTimeMillis(), null);
    }

    public static WorkflowStreamEvent nodeSkip(String nodeId, String nodeName) {
        return new WorkflowStreamEvent(nodeId, nodeName, "NODE_SKIP", null, System.currentTimeMillis(), null);
    }

    public static WorkflowStreamEvent workflowStarted(String definitionName, String instanceId) {
        return new WorkflowStreamEvent(null, definitionName, "WORKFLOW_STARTED", null, System.currentTimeMillis(), instanceId);
    }

    public static WorkflowStreamEvent workflowComplete(String definitionName, String finalResult) {
        return new WorkflowStreamEvent(null, definitionName, "WORKFLOW_COMPLETE", finalResult, System.currentTimeMillis(), null);
    }
}
