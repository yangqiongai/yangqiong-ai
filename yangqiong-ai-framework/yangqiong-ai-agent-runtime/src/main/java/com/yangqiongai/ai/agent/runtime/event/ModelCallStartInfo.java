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
 * 模型调用开始信息
 * <p>
 * MODEL_CALL_START事件的载荷，描述单次LLM调用的模型与轮次，
 * 供指标采集进行调用级耗时配对。
 * </p>
 * @author yangqiong
 */
public final class ModelCallStartInfo {

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
     * 全参构造
     * @param agentName
     * @param modelName
     * @param iteration
     */
    public ModelCallStartInfo(String agentName, String modelName, int iteration) {
        this.agentName = agentName;
        this.modelName = modelName;
        this.iteration = iteration;
    }

    /**
     * 获取Agent名称
     * @return
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * 获取模型名称
     * @return
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 获取迭代轮次
     * @return
     */
    public int getIteration() {
        return iteration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelCallStartInfo that = (ModelCallStartInfo) o;
        return iteration == that.iteration
                && Objects.equals(agentName, that.agentName)
                && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentName, modelName, iteration);
    }

    @Override
    public String toString() {
        return "ModelCallStartInfo{agentName=" + agentName + ", modelName=" + modelName
                + ", iteration=" + iteration + "}";
    }
}
