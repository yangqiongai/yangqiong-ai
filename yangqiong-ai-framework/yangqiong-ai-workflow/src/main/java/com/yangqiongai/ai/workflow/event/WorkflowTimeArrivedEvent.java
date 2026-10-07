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
package com.yangqiongai.ai.workflow.event;

/**
 * 时间控制节点到达设定时间事件，TIME_CONTROL节点暂停等待后触发工作流恢复
 * @author yangqiong
 */
public class WorkflowTimeArrivedEvent {

    /**
     * 工作流实例ID
     */
    private final String instanceId;

    /**
     * 暂停的时间控制节点ID
     */
    private final String nodeId;

    /**
     * 设定的恢复时间（毫秒时间戳）
     */
    private final Long resumeAtMillis;

    public WorkflowTimeArrivedEvent(String instanceId, String nodeId, Long resumeAtMillis) {
        this.instanceId = instanceId;
        this.nodeId = nodeId;
        this.resumeAtMillis = resumeAtMillis;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Long getResumeAtMillis() {
        return resumeAtMillis;
    }
}
