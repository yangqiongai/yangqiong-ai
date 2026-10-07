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
package com.yangqiongai.ai.workflow.spi;

import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

/**
 * 工作流执行审计事件
 * @author yangqiong
 */
@Data
@Builder
public class WorkflowExecutionEvent {

    /**
     * 事件类型
     */
    private WorkflowExecutionEventType eventType;

    /**
     * 执行实例ID
     */
    private String instanceId;

    /**
     * 工作流定义名称
     */
    private String definitionName;

    /**
     * 工作流定义版本
     */
    private Integer definitionVersion;

    /**
     * 作用域ID（异步段从状态变量__scopeId读取）
     */
    private String scopeId;

    /**
     * 事件发生时的执行状态
     */
    private ExecutionStatus status;

    /**
     * 节点ID（节点级事件携带）
     */
    private String nodeId;

    /**
     * 节点名称（节点级事件携带）
     */
    private String nodeName;

    /**
     * 耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 错误信息（失败/暂停事件携带）
     */
    private String errorMessage;

    /**
     * 事件时间戳
     */
    private long timestamp;

    /**
     * 审计事件类型
     */
    public enum WorkflowExecutionEventType {
        /** 工作流开始 */
        WORKFLOW_START,
        /** 工作流执行完成 */
        WORKFLOW_COMPLETE,
        /** 工作流执行失败 */
        WORKFLOW_FAILED,
        /** 工作流暂停等待审批 */
        WORKFLOW_PAUSED,
        /** 节点开始执行 */
        NODE_START,
        /** 节点执行完成 */
        NODE_COMPLETE,
        /** 节点执行失败 */
        NODE_FAILED
    }
}
