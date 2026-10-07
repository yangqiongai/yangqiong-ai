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
package com.yangqiongai.ai.agent.runtime.model;

import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;

/**
 * Token消耗指标
 * @author yangqiong
 */
public final class TokenMetrics {

    /**
     * 输入Token数
     */
    private final long inputTokens;

    /**
     * 输出Token数
     */
    private final long outputTokens;

    /**
     * 总Token数
     */
    private final long totalTokens;

    /**
     * 模型执行耗时（秒）
     */
    private final double time;

    public TokenMetrics(long inputTokens, long outputTokens, long totalTokens, double time) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.time = time;
    }

    /**
     * 从AgentChatUsage构建TokenMetrics
     * @param chatUsage
     * @return
     */
    public static TokenMetrics fromChatUsage(AgentChatUsage chatUsage) {
        if (chatUsage == null) {
            return empty();
        }
        return new TokenMetrics(
                chatUsage.getPromptTokens(),
                chatUsage.getCompletionTokens(),
                chatUsage.getTotalTokens(),
                0
        );
    }

    /**
     * 创建空的TokenMetrics
     * @return
     */
    public static TokenMetrics empty() {
        return new TokenMetrics(0, 0, 0, 0);
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public double getTime() {
        return time;
    }

    public static class Builder {

        /**
         * 输入Token数
         */
        private long inputTokens;

        /**
         * 输出Token数
         */
        private long outputTokens;

        /**
         * 总Token数
         */
        private long totalTokens;

        /**
         * 模型执行耗时（秒）
         */
        private double time;

        public Builder inputTokens(long inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(long outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder totalTokens(long totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder time(double time) {
            this.time = time;
            return this;
        }

        public TokenMetrics build() {
            return new TokenMetrics(inputTokens, outputTokens, totalTokens, time);
        }
    }
}
