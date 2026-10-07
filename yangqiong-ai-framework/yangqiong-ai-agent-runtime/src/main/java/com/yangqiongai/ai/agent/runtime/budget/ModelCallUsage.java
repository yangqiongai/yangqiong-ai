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
 * 调用级用量
 * <p>
 * 描述一次运行内单次LLM调用的Token用量明细（按模型拆分计价的基础数据），
 * 供{@link UsageListener#onModelCall(ModelCallUsage)}发布。
 * </p>
 * @author yangqiong
 */
public class ModelCallUsage {

    /**
     * 任务ID
     */
    private final String taskId;

    /**
     * 模型调用回复ID
     */
    private final String replyId;

    /**
     * 调用序号（同一运行内从1递增）
     */
    private final int callSeq;

    /**
     * 模型编码（该次调用实际使用）
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
     * 缓存Token
     */
    private final long cachedTokens;

    /**
     * 总Token
     */
    private final long totalTokens;

    /**
     * 该次调用耗时（毫秒）
     */
    private final long durationMs;

    /**
     * 调用开始时间（epoch毫秒，0表示未知）
     */
    private final long startTimeMs;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 全参构造
     * @param taskId
     * @param replyId
     * @param callSeq
     * @param modelCode
     * @param inputTokens
     * @param outputTokens
     * @param cachedTokens
     * @param totalTokens
     * @param durationMs
     * @param startTimeMs
     * @param scopeId
     */
    public ModelCallUsage(String taskId, String replyId, int callSeq, String modelCode,
                          long inputTokens, long outputTokens, long cachedTokens, long totalTokens,
                          long durationMs, long startTimeMs, String scopeId) {
        this.taskId = taskId;
        this.replyId = replyId;
        this.callSeq = callSeq;
        this.modelCode = modelCode;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cachedTokens = cachedTokens;
        this.totalTokens = totalTokens;
        this.durationMs = durationMs;
        this.startTimeMs = startTimeMs;
        this.scopeId = scopeId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getReplyId() {
        return replyId;
    }

    public int getCallSeq() {
        return callSeq;
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

    public long getCachedTokens() {
        return cachedTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public String getScopeId() {
        return scopeId;
    }

    @Override
    public String toString() {
        return "ModelCallUsage{taskId=" + taskId + ", callSeq=" + callSeq
                + ", modelCode=" + modelCode + ", totalTokens=" + totalTokens + "}";
    }
}
