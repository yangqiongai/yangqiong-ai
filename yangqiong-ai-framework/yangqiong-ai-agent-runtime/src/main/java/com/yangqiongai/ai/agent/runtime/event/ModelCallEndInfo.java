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
package com.yangqiongai.ai.agent.runtime.event;

import java.util.Objects;

/**
 * 模型调用结束信息
 * <p>
 * MODEL_CALL_END事件的载荷，描述单次LLM调用的模型与Token用量，
 * 供调用级计量（按模型拆分计价）消费。
 * </p>
 * @author yangqiong
 */
public final class ModelCallEndInfo {

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 模型名称
     */
    private final String modelName;

    /**
     * 迭代轮次
     */
    private final int iteration;

    /**
     * 输入Token
     */
    private final int inputTokens;

    /**
     * 输出Token
     */
    private final int outputTokens;

    /**
     * 总Token
     */
    private final int totalTokens;

    /**
     * 全参构造
     * @param agentName
     * @param modelName
     * @param iteration
     * @param inputTokens
     * @param outputTokens
     * @param totalTokens
     */
    public ModelCallEndInfo(String agentName, String modelName, int iteration,
                            int inputTokens, int outputTokens, int totalTokens) {
        this.agentName = agentName;
        this.modelName = modelName;
        this.iteration = iteration;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getModelName() {
        return modelName;
    }

    public int getIteration() {
        return iteration;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelCallEndInfo that = (ModelCallEndInfo) o;
        return iteration == that.iteration
                && inputTokens == that.inputTokens
                && outputTokens == that.outputTokens
                && totalTokens == that.totalTokens
                && Objects.equals(agentName, that.agentName)
                && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentName, modelName, iteration, inputTokens, outputTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "ModelCallEndInfo{agentName=" + agentName + ", modelName=" + modelName
                + ", iteration=" + iteration + ", inputTokens=" + inputTokens
                + ", outputTokens=" + outputTokens + ", totalTokens=" + totalTokens + "}";
    }
}
