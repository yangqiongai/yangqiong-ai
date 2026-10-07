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
package com.yangqiongai.ai.agent.registry.gray;

/**
 * 模型路由复杂度信号
 * @author yangqiong
 */
public class ModelRouteSignal {

    /**
     * 历史对话步数
     */
    private int historySteps;

    /**
     * 本次请求可用工具数
     */
    private int toolCount;

    /**
     * 输入文本长度
     */
    private int inputLength;

    /**
     * 预算成本余量比例(0-1，可空=预算能力未启用)
     */
    private Double costRemainingRatio;

    public int getHistorySteps() {
        return historySteps;
    }

    public void setHistorySteps(int historySteps) {
        this.historySteps = historySteps;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = toolCount;
    }

    public int getInputLength() {
        return inputLength;
    }

    public void setInputLength(int inputLength) {
        this.inputLength = inputLength;
    }

    public Double getCostRemainingRatio() {
        return costRemainingRatio;
    }

    public void setCostRemainingRatio(Double costRemainingRatio) {
        this.costRemainingRatio = costRemainingRatio;
    }
}
