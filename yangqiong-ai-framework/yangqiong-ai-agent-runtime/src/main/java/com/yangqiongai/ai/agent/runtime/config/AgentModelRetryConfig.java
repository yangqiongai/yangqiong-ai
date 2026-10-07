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
package com.yangqiongai.ai.agent.runtime.config;

import java.time.Duration;

/**
 * 模型调用重试配置
 * <p>
 * 模型调用失败时的指数退避重试策略：初始退避时长起按倍数递增，直至最大退避时长。
 * </p>
 * @author yangqiong
 */
public final class AgentModelRetryConfig {

    /**
     * 是否启用重试
     */
    private final boolean enabled;

    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 初始退避时长
     */
    private final Duration initialBackoff;

    /**
     * 退避倍数
     */
    private final double backoffMultiplier;

    /**
     * 最大退避时长
     */
    private final Duration maxBackoff;

    private AgentModelRetryConfig(boolean enabled, int maxRetries, Duration initialBackoff,
                                  double backoffMultiplier, Duration maxBackoff) {
        this.enabled = enabled;
        this.maxRetries = maxRetries;
        this.initialBackoff = initialBackoff;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoff = maxBackoff;
    }

    /**
     * 创建启用重试的默认配置（3次、1s起、2倍、上限30s）
     * @return
     */
    public static AgentModelRetryConfig defaultEnabled() {
        return builder().build();
    }

    /**
     * 创建禁用重试的配置
     * @return
     */
    public static AgentModelRetryConfig disabled() {
        return builder().enabled(false).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 是否启用重试
     * @return
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取最大重试次数
     * @return
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * 获取初始退避时长
     * @return
     */
    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    /**
     * 获取退避倍数
     * @return
     */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * 获取最大退避时长
     * @return
     */
    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    @Override
    public String toString() {
        return "AgentModelRetryConfig{enabled=" + enabled + ", maxRetries=" + maxRetries
                + ", initialBackoff=" + initialBackoff + ", backoffMultiplier=" + backoffMultiplier
                + ", maxBackoff=" + maxBackoff + "}";
    }

    /**
     * 重试配置构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 是否启用重试
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 初始退避时长
         */
        private Duration initialBackoff = Duration.ofSeconds(1);

        /**
         * 退避倍数
         */
        private double backoffMultiplier = 2.0;

        /**
         * 最大退避时长
         */
        private Duration maxBackoff = Duration.ofSeconds(30);

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public AgentModelRetryConfig build() {
            return new AgentModelRetryConfig(enabled, maxRetries, initialBackoff,
                    backoffMultiplier, maxBackoff);
        }
    }
}
