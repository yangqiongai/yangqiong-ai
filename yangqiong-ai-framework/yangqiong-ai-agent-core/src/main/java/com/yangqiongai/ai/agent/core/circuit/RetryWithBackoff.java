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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 退避重试执行器
 * <p>指数退避 + 抖动，避免重试风暴</p>
 */
@Slf4j
@Service
public class RetryWithBackoff {

    private static final RetryConfig DEFAULT_CONFIG = RetryConfig.builder().build();

    /**
     * 使用默认配置执行带重试的操作
     */
    public <T> T executeWithRetry(Supplier<T> action, Predicate<Exception> retryable) {
        return executeWithRetry(action, DEFAULT_CONFIG, retryable);
    }

    /**
     * 执行带退避重试的操作
     *
     * @param action     要执行的操作
     * @param config     重试配置
     * @param retryable  判断异常是否可重试
     * @return 操作结果
     */
    public <T> T executeWithRetry(Supplier<T> action, RetryConfig config, Predicate<Exception> retryable) {
        if (config == null) {
            config = DEFAULT_CONFIG;
        }
        Exception lastException = null;
        for (int attempt = 0; attempt <= config.getMaxRetries(); attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt >= config.getMaxRetries() || !retryable.test(e)) {
                    log.warn("操作失败，不再重试: attempt={}, maxRetries={}, error={}",
                            attempt + 1, config.getMaxRetries(), e.getMessage());
                    break;
                }
                long backoffMs = calculateBackoff(config, attempt);
                log.info("操作失败，退避重试: attempt={}, nextAttempt={}, backoffMs={}, error={}",
                        attempt + 1, attempt + 2, backoffMs, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException("操作重试耗尽", lastException);
    }

    /**
     * 计算退避间隔（含抖动）
     */
    private long calculateBackoff(RetryConfig config, int attempt) {
        long interval = (long) (config.getInitialIntervalMs() * Math.pow(config.getMultiplier(), attempt));
        interval = Math.min(interval, config.getMaxIntervalMs());
        if (config.getJitterFactor() > 0) {
            long jitter = (long) (interval * config.getJitterFactor() * (Math.random() * 2 - 1));
            interval = interval + jitter;
        }
        return Math.max(100, interval);
    }
}
