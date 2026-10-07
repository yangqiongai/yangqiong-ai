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
package com.yangqiongai.ai.agent.runtime.budget;

/**
 * 模型调用限流器
 * <p>
 * 令牌桶限流SPI，用于模型调用RPM级限流，调用方在每次模型调用前等待配额。
 * </p>
 * @author yangqiong
 */
public interface RateLimiter {

    /**
     * 获取一个调用配额需要等待的纳秒数
     * @return 等待纳秒数，0表示立即可用
     */
    long waitNanos();

    /**
     * 获取当前生效速率
     * @return 当前速率（次/秒）
     */
    double getCurrentRate();
}
