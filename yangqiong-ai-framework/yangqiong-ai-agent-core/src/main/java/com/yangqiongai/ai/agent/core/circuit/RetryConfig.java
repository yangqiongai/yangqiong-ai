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
 * 退避重试配置
 */
@Data
@Builder
public class RetryConfig {

    /**
     * 最大重试次数（框架整轮重试仅兜底1次，细粒度重试由引擎层承担，避免两层重试叠加放大调用）
     */
    @Builder.Default
    private int maxRetries = 1;

    /**
     * 初始退避间隔（毫秒）
     */
    @Builder.Default
    private long initialIntervalMs = 1000;

    /**
     * 退避倍数
     */
    @Builder.Default
    private double multiplier = 2.0;

    /**
     * 最大退避间隔（毫秒）
     */
    @Builder.Default
    private long maxIntervalMs = 30000;

    /**
     * 抖动因子（0~1），0表示无抖动
     */
    @Builder.Default
    private double jitterFactor = 0.5;
}
