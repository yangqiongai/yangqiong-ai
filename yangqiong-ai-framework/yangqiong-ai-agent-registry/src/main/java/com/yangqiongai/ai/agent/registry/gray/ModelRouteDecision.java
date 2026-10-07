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
 * 模型分级路由决策
 * @author yangqiong
 */
public class ModelRouteDecision {

    /**
     * 路由层级标识：主力模型
     */
    public static final String TIER_PRIMARY = "PRIMARY";

    /**
     * 路由层级标识：轻量降级池
     */
    public static final String TIER_SLM = "SLM";

    /**
     * 路由层级(PRIMARY/SLM)
     */
    private final String tier;

    /**
     * 选定的模型编码
     */
    private final String modelCode;

    /**
     * 路由原因(复杂度/熔断/配置缺失等，落决策记录)
     */
    private final String reason;

    private ModelRouteDecision(String tier, String modelCode, String reason) {
        this.tier = tier;
        this.modelCode = modelCode;
        this.reason = reason;
    }

    /**
     * 主力模型决策
     * @param modelCode 模型编码
     * @param reason 路由原因
     * @return
     */
    public static ModelRouteDecision primary(String modelCode, String reason) {
        return new ModelRouteDecision(TIER_PRIMARY, modelCode, reason);
    }

    /**
     * 轻量降级池决策
     * @param modelCode 模型编码
     * @param reason 路由原因
     * @return
     */
    public static ModelRouteDecision slm(String modelCode, String reason) {
        return new ModelRouteDecision(TIER_SLM, modelCode, reason);
    }

    public String getTier() {
        return tier;
    }

    public String getModelCode() {
        return modelCode;
    }

    public String getReason() {
        return reason;
    }
}
