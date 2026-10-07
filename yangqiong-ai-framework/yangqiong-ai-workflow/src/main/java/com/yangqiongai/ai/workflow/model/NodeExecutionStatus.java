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

import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import lombok.Data;

import java.util.Map;

/**
 * 节点执行状态
 * @author yangqiong
 */
@Data
public class NodeExecutionStatus {

    private String nodeId;

    /**
     * 节点名称
     */
    private String nodeName;

    private ExecutionStatus status;

    /**
     * 节点输入（结构化，记录节点接收的参数）
     */
    private Map<String, Object> input;

    /**
     * 节点输出（结构化，记录节点产出的数据）
     */
    private Map<String, Object> outputData;

    private AgentResult output;

    private Long startTime;

    private Long endTime;

    private String errorMessage;

    private int iterationCount;

    private Integer retryCount;

    public boolean isCompleted() {
        return status == ExecutionStatus.COMPLETED;
    }

    public boolean isSkipped() {
        return status == ExecutionStatus.SKIPPED;
    }

    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    public boolean isRunning() {
        return status == ExecutionStatus.RUNNING;
    }

    public Long getDuration() {
        if (startTime != null && endTime != null) {
            return endTime - startTime;
        }
        return null;
    }
}
