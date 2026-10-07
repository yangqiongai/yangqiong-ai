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
package com.yangqiongai.ai.common.event;

/**
 * Agent运行用量事件
 * @author yangqiong
 */
public class AgentRunUsageEvent {

    /**
     * 记录ID（幂等键）
     */
    private final String recordId;

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
     * 构造
     * @param recordId 记录ID
     * @param taskId 任务ID
     * @param agentCode Agent编码
     * @param modelCode 主模型编码
     * @param inputTokens 输入Token
     * @param outputTokens 输出Token
     * @param totalTokens 总Token
     * @param durationMs 执行时长（毫秒）
     * @param status 任务结果状态
     * @param scopeId 作用域ID
     * @param userId 用户ID
     */
    public AgentRunUsageEvent(String recordId, String taskId, String agentCode, String modelCode,
                              long inputTokens, long outputTokens, long totalTokens,
                              long durationMs, String status, String scopeId, String userId) {
        this.recordId = recordId;
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

    /**
     * 获取记录ID
     * @return
     */
    public String getRecordId() {
        return recordId;
    }

    /**
     * 获取任务ID
     * @return
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 获取Agent编码
     * @return
     */
    public String getAgentCode() {
        return agentCode;
    }

    /**
     * 获取主模型编码
     * @return
     */
    public String getModelCode() {
        return modelCode;
    }

    /**
     * 获取输入Token
     * @return
     */
    public long getInputTokens() {
        return inputTokens;
    }

    /**
     * 获取输出Token
     * @return
     */
    public long getOutputTokens() {
        return outputTokens;
    }

    /**
     * 获取总Token
     * @return
     */
    public long getTotalTokens() {
        return totalTokens;
    }

    /**
     * 获取执行时长
     * @return
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * 获取任务结果状态
     * @return
     */
    public String getStatus() {
        return status;
    }

    /**
     * 获取作用域ID
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取用户ID
     * @return
     */
    public String getUserId() {
        return userId;
    }
}
