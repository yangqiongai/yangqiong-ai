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

import com.yangqiongai.ai.agent.registry.config.ModelRouteProperties;

/**
 * 模型分级路由策略
 * <p>
 * 复杂度信号(历史步数/工具数/输入长度)任一达到阈值走主力，全部低于阈值走轻量降级池；
 * 降级池未配置或失败率触发熔断时回退主力。边界语义：信号恰好等于阈值视为复杂走主力。
 * </p>
 * @author yangqiong
 */
public class ModelRoutePolicy {

    /**
     * 熔断原因标识
     */
    public static final String REASON_CIRCUIT_BREAK = "HEALTH_CIRCUIT_BREAK";

    /**
     * 复杂度原因标识
     */
    public static final String REASON_COMPLEXITY = "COMPLEXITY_HIGH";

    /**
     * 轻量原因标识
     */
    public static final String REASON_LIGHT = "COMPLEXITY_LOW";

    /**
     * 降级池未配置原因标识
     */
    public static final String REASON_SLM_UNCONFIGURED = "SLM_UNCONFIGURED";

    /**
     * 路由关闭原因标识
     */
    public static final String REASON_ROUTE_DISABLED = "ROUTE_DISABLED";

    /**
     * 路由决策
     * @param signal 复杂度信号
     * @param slmFailurePercent SLM降级池最近失败率百分比(可空=健康数据不足)
     * @param properties 路由配置
     * @return
     */
    public ModelRouteDecision route(ModelRouteSignal signal, Integer slmFailurePercent,
                                    ModelRouteProperties properties) {
        String primaryReason = REASON_ROUTE_DISABLED;
        if (properties.isEnabled()) {
            if (properties.getSlmModelCode() == null || properties.getSlmModelCode().isBlank()) {
                primaryReason = REASON_SLM_UNCONFIGURED;
            } else if (slmFailurePercent != null && slmFailurePercent >= properties.getHealthCircuitBreakerPercent()) {
                primaryReason = REASON_CIRCUIT_BREAK;
            } else if (isComplex(signal, properties)) {
                primaryReason = REASON_COMPLEXITY;
            } else {
                return ModelRouteDecision.slm(properties.getSlmModelCode(),
                        buildLightReason(signal, properties));
            }
        }
        return ModelRouteDecision.primary(null, primaryReason);
    }

    /**
     * 判断是否复杂请求(任一信号达到阈值即复杂)
     * @param signal 复杂度信号
     * @param properties 路由配置
     * @return
     */
    private boolean isComplex(ModelRouteSignal signal, ModelRouteProperties properties) {
        return signal.getHistorySteps() >= properties.getHistoryStepsThreshold()
                || signal.getToolCount() >= properties.getToolCountThreshold()
                || signal.getInputLength() >= properties.getInputLengthThreshold();
    }

    /**
     * 构建轻量路由原因(附明细信号便于审计)
     * @param signal 复杂度信号
     * @param properties 路由配置
     * @return
     */
    private String buildLightReason(ModelRouteSignal signal, ModelRouteProperties properties) {
        return REASON_LIGHT + "(steps=" + signal.getHistorySteps() + "/" + properties.getHistoryStepsThreshold()
                + ",tools=" + signal.getToolCount() + "/" + properties.getToolCountThreshold()
                + ",len=" + signal.getInputLength() + "/" + properties.getInputLengthThreshold()
                + ")";
    }
}
