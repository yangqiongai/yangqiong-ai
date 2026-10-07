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
package com.yangqiongai.ai.agent.registry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型分级路由配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.registry.model-route")
public class ModelRouteProperties {

    /**
     * 路由总开关(灰度开关，关闭时全部走主力)
     */
    private boolean enabled = false;

    /**
     * 轻量降级池模型编码(空=降级池未配置，全部走主力)
     */
    private String slmModelCode;

    /**
     * 历史步数复杂度阈值(信号>=阈值走主力)
     */
    private int historyStepsThreshold = 8;

    /**
     * 工具数复杂度阈值(信号>=阈值走主力)
     */
    private int toolCountThreshold = 3;

    /**
     * 输入长度复杂度阈值(信号>=阈值走主力)
     */
    private int inputLengthThreshold = 4000;

    /**
     * SLM失败率熔断百分比(0-100，达到即回退主力)
     */
    private int healthCircuitBreakerPercent = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSlmModelCode() {
        return slmModelCode;
    }

    public void setSlmModelCode(String slmModelCode) {
        this.slmModelCode = slmModelCode;
    }

    public int getHistoryStepsThreshold() {
        return historyStepsThreshold;
    }

    public void setHistoryStepsThreshold(int historyStepsThreshold) {
        this.historyStepsThreshold = historyStepsThreshold;
    }

    public int getToolCountThreshold() {
        return toolCountThreshold;
    }

    public void setToolCountThreshold(int toolCountThreshold) {
        this.toolCountThreshold = toolCountThreshold;
    }

    public int getInputLengthThreshold() {
        return inputLengthThreshold;
    }

    public void setInputLengthThreshold(int inputLengthThreshold) {
        this.inputLengthThreshold = inputLengthThreshold;
    }

    public int getHealthCircuitBreakerPercent() {
        return healthCircuitBreakerPercent;
    }

    public void setHealthCircuitBreakerPercent(int healthCircuitBreakerPercent) {
        this.healthCircuitBreakerPercent = healthCircuitBreakerPercent;
    }
}
