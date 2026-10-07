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
package com.yangqiongai.ai.platform.api.evaluation;

/**
 * 评测运行完成事件
 * @author yangqiong
 */
public class EvalRunFinishedEvent {

    /**
     * 评测运行ID
     */
    private final Long runId;

    /**
     * 被测Agent编码
     */
    private final String agentCode;

    /**
     * 数据集ID
     */
    private final Long datasetId;

    /**
     * 终态(PASSED/FAILED/ERROR/CANCELLED)
     */
    private final String status;

    /**
     * 构造评测运行完成事件
     * @param runId 评测运行ID
     * @param agentCode 被测Agent编码
     * @param datasetId 数据集ID
     * @param status 终态
     */
    public EvalRunFinishedEvent(Long runId, String agentCode, Long datasetId, String status) {
        this.runId = runId;
        this.agentCode = agentCode;
        this.datasetId = datasetId;
        this.status = status;
    }

    public Long getRunId() {
        return runId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public String getStatus() {
        return status;
    }
}
