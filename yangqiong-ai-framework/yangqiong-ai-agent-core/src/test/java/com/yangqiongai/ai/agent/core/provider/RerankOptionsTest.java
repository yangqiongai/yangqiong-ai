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
package com.yangqiongai.ai.agent.core.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索重排序选项单元测试
 * @author yangqiong
 */
@DisplayName("检索重排序选项单元测试")
class RerankOptionsTest {

    @Test
    @DisplayName("两项均未指定时返回null表示无需覆盖")
    void of_bothBlank_returnsNull() {
        assertThat(RerankOptions.of(null, null)).isNull();
        assertThat(RerankOptions.of(null, "  ")).isNull();
    }

    @Test
    @DisplayName("开关或模型任一指定即返回选项实例")
    void of_anySpecified_returnsInstance() {
        RerankOptions byEnabled = RerankOptions.of(Boolean.FALSE, null);
        assertThat(byEnabled.getEnabled()).isFalse();
        assertThat(byEnabled.getModelCode()).isNull();

        RerankOptions byModel = RerankOptions.of(null, "deepseek");
        assertThat(byModel.getEnabled()).isNull();
        assertThat(byModel.getModelCode()).isEqualTo("deepseek");
    }

    @Test
    @DisplayName("开关解析以全局为总开关，全局关闭时覆盖值不生效")
    void resolveEnabled_globalIsMasterSwitch() {
        assertThat(RerankOptions.of(Boolean.FALSE, null).resolveEnabled(true)).isFalse();
        assertThat(RerankOptions.of(Boolean.TRUE, null).resolveEnabled(true)).isTrue();
        assertThat(RerankOptions.of(null, "m").resolveEnabled(true)).isTrue();
        assertThat(RerankOptions.of(Boolean.TRUE, null).resolveEnabled(false)).isFalse();
        assertThat(RerankOptions.of(null, "m").resolveEnabled(false)).isFalse();
    }

    @Test
    @DisplayName("模型解析覆盖值优先且空白覆盖回退全局值")
    void resolveModelCode_overrideWins() {
        assertThat(RerankOptions.of(null, "deepseek").resolveModelCode("global-model")).isEqualTo("deepseek");
        assertThat(RerankOptions.of(Boolean.TRUE, "  ").resolveModelCode("global-model")).isEqualTo("global-model");
        assertThat(RerankOptions.of(Boolean.TRUE, null).resolveModelCode("global-model")).isEqualTo("global-model");
    }

    @Test
    @DisplayName("bind后current可读且clear后移除，未绑定时current为null")
    void bindCurrentClear_threadLocalLifecycle() {
        assertThat(RerankOptions.current()).isNull();

        RerankOptions options = RerankOptions.of(Boolean.FALSE, "deepseek");
        try {
            RerankOptions.bind(options);
            assertThat(RerankOptions.current()).isSameAs(options);

            RerankOptions.bind(null);
            assertThat(RerankOptions.current()).isSameAs(options);
        } finally {
            RerankOptions.clear();
        }
        assertThat(RerankOptions.current()).isNull();
    }
}
