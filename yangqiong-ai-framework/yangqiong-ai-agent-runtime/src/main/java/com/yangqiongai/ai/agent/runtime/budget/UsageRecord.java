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
package com.yangqiongai.ai.agent.runtime.budget;

/**
 * 运行级用量
 * <p>
 * 描述一次Agent运行结束后的Token用量汇总，供{@link UsageListener}发布。
 * </p>
 * @author yangqiong
 */
public class UsageRecord {

    /**
     * 记录ID（幂等键，UUID）
     */
    private final String recordId;

    /**
     * 追踪ID
     */
    private final String traceId;

    /**
     * 任务ID
     */
    private final String taskId;

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 主模型编码
     */
    private final String modelCode;

    /**
     * 输入Token
     */
    private final long inputTokens;

    /**
     * 输出Token
     */
    private final long outputTokens;

    /**
     * 总Token
     */
    private final long totalTokens;

    /**
     * 执行时长（毫秒）
     */
    private final long durationMs;

    /**
     * 任务结果状态(SUCCEEDED/FAILED)
     */
    private final String status;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * 全参构造
     * @param recordId
     * @param traceId
     * @param taskId
     * @param agentCode
     * @param modelCode
     * @param inputTokens
     * @param outputTokens
     * @param totalTokens
     * @param durationMs
     * @param status
     * @param scopeId
     * @param userId
     */
    public UsageRecord(String recordId, String traceId, String taskId, String agentCode,
                       String modelCode, long inputTokens, long outputTokens, long totalTokens,
                       long durationMs, String status, String scopeId, String userId) {
        this.recordId = recordId;
        this.traceId = traceId;
        this.taskId = taskId;
        this.agentCode = agentCode;
        this.modelCode = modelCode;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.durationMs = durationMs;
        this.status = status;
        this.scopeId = scopeId;
        this.userId = userId;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getModelCode() {
        return modelCode;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getStatus() {
        return status;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return "UsageRecord{taskId=" + taskId + ", agentCode=" + agentCode
                + ", totalTokens=" + totalTokens + ", status=" + status + "}";
    }
}
