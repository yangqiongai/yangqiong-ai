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

/**
 * Agent工具结果驱逐配置
 * @author yangqiong
 */
public final class AgentToolResultEvictionConfig {

    /**
     * 结果最大字符数
     */
    private final int maxResultChars;

    /**
     * 预览字符数
     */
    private final int previewChars;

    private AgentToolResultEvictionConfig(int maxResultChars, int previewChars) {
        this.maxResultChars = maxResultChars;
        this.previewChars = previewChars;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取结果最大字符数
     * @return
     */
    public int getMaxResultChars() {
        return maxResultChars;
    }

    /**
     * 获取预览字符数
     * @return
     */
    public int getPreviewChars() {
        return previewChars;
    }

    @Override
    public String toString() {
        return "AgentToolResultEvictionConfig{maxResultChars=" + maxResultChars
                + ", previewChars=" + previewChars + "}";
    }

    /**
     * 工具结果驱逐配置构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 结果最大字符数
         */
        private int maxResultChars;

        /**
         * 预览字符数
         */
        private int previewChars;

        public Builder maxResultChars(int maxResultChars) {
            this.maxResultChars = maxResultChars;
            return this;
        }

        public Builder previewChars(int previewChars) {
            this.previewChars = previewChars;
            return this;
        }

        public AgentToolResultEvictionConfig build() {
            return new AgentToolResultEvictionConfig(maxResultChars, previewChars);
        }
    }
}
