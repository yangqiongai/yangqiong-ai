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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型分级路由策略测试
 * @author yangqiong
 */
class ModelRoutePolicyTest {

    private ModelRoutePolicy policy;

    private ModelRouteProperties properties;

    @BeforeEach
    void setUp() {
        policy = new ModelRoutePolicy();
        properties = new ModelRouteProperties();
        properties.setEnabled(true);
        properties.setSlmModelCode("slm-lite");
        properties.setHistoryStepsThreshold(8);
        properties.setToolCountThreshold(3);
        properties.setInputLengthThreshold(4000);
        properties.setHealthCircuitBreakerPercent(30);
    }

    /**
     * 构造信号
     * @param historySteps 历史步数
     * @param toolCount 工具数
     * @param inputLength 输入长度
     * @return
     */
    private ModelRouteSignal signal(int historySteps, int toolCount, int inputLength) {
        ModelRouteSignal signal = new ModelRouteSignal();
        signal.setHistorySteps(historySteps);
        signal.setToolCount(toolCount);
        signal.setInputLength(inputLength);
        return signal;
    }

    @Test
    void routesToSlmWhenAllSignalsBelowThreshold() {
        ModelRouteDecision decision = policy.route(signal(2, 1, 100), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_SLM);
        assertThat(decision.getModelCode()).isEqualTo("slm-lite");
        assertThat(decision.getReason()).startsWith(ModelRoutePolicy.REASON_LIGHT);
    }

    @Test
    void historyStepsEqualToThresholdRoutesToPrimary() {
        // 边界：恰好等于阈值视为复杂走主力
        ModelRouteDecision decision = policy.route(signal(8, 0, 0), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_PRIMARY);
        assertThat(decision.getReason()).isEqualTo(ModelRoutePolicy.REASON_COMPLEXITY);
    }

    @Test
    void toolCountEqualToThresholdRoutesToPrimary() {
        ModelRouteDecision decision = policy.route(signal(0, 3, 0), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_PRIMARY);
    }

    @Test
    void inputLengthEqualToThresholdRoutesToPrimary() {
        ModelRouteDecision decision = policy.route(signal(0, 0, 4000), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_PRIMARY);
    }

    @Test
    void circuitBreakFallsBackToPrimary() {
        // SLM失败率达到熔断阈值回退主力
        ModelRouteDecision decision = policy.route(signal(1, 0, 10), 30, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_PRIMARY);
        assertThat(decision.getReason()).isEqualTo(ModelRoutePolicy.REASON_CIRCUIT_BREAK);
    }

    @Test
    void slmUnconfiguredFallsBackToPrimary() {
        properties.setSlmModelCode("");

        ModelRouteDecision decision = policy.route(signal(1, 0, 10), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_PRIMARY);
        assertThat(decision.getReason()).isEqualTo(ModelRoutePolicy.REASON_SLM_UNCONFIGURED);
    }

    @Test
    void disabledRouteAlwaysPrimary() {
        properties.setEnabled(false);

        ModelRouteDecision decision = policy.route(signal(1, 0, 10), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_PRIMARY);
        assertThat(decision.getReason()).isEqualTo(ModelRoutePolicy.REASON_ROUTE_DISABLED);
    }

    @Test
    void failureDataMissingStillRoutesToSlm() {
        // 健康数据不足(失败率未知)不熔断
        ModelRouteDecision decision = policy.route(signal(1, 0, 10), null, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_SLM);
    }

    @Test
    void failureRateBelowBreakerRoutesToSlm() {
        ModelRouteDecision decision = policy.route(signal(1, 0, 10), 29, properties);

        assertThat(decision.getTier()).isEqualTo(ModelRouteDecision.TIER_SLM);
    }
}
