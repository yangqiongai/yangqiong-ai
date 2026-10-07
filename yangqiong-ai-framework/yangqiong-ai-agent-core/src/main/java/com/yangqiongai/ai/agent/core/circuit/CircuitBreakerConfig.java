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
package com.yangqiongai.ai.agent.core.circuit;

import lombok.Builder;
import lombok.Data;

/**
 * 熔断器配置
 */
@Data
@Builder
public class CircuitBreakerConfig {

    /**
     * 滑动窗口内失败次数阈值，达到后熔断
     */
    @Builder.Default
    private int failureThreshold = 5;

    /**
     * 滑动窗口大小（秒）
     */
    @Builder.Default
    private int windowSizeSeconds = 60;

    /**
     * OPEN状态持续时间（秒），过后进入HALF_OPEN
     */
    @Builder.Default
    private int openDurationSeconds = 30;

    /**
     * HALF_OPEN状态允许的探测请求数
     */
    @Builder.Default
    private int halfOpenMaxRequests = 1;
}
