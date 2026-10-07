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
 * Agent记忆配置
 * @author yangqiong
 */
public final class AgentMemoryConfig {

    /**
     * 消息压缩配置
     */
    private final AgentCompactionConfig compactionConfig;

    /**
     * 工具结果驱逐配置
     */
    private final AgentToolResultEvictionConfig toolResultEvictionConfig;

    private AgentMemoryConfig(AgentCompactionConfig compactionConfig,
                              AgentToolResultEvictionConfig toolResultEvictionConfig) {
        this.compactionConfig = compactionConfig;
        this.toolResultEvictionConfig = toolResultEvictionConfig;
    }

    /**
     * 创建默认记忆配置
     * @return
     */
    public static AgentMemoryConfig defaults() {
        return new AgentMemoryConfig(AgentCompactionConfig.defaults(), null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取消息压缩配置
     * @return
     */
    public AgentCompactionConfig getCompactionConfig() {
        return compactionConfig;
    }

    /**
     * 获取工具结果驱逐配置
     * @return
     */
    public AgentToolResultEvictionConfig getToolResultEvictionConfig() {
        return toolResultEvictionConfig;
    }

    @Override
    public String toString() {
        return "AgentMemoryConfig{compactionConfig=" + compactionConfig
                + ", toolResultEvictionConfig=" + toolResultEvictionConfig + "}";
    }

    /**
     * 记忆配置构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 消息压缩配置
         */
        private AgentCompactionConfig compactionConfig;

        /**
         * 工具结果驱逐配置
         */
        private AgentToolResultEvictionConfig toolResultEvictionConfig;

        public Builder compactionConfig(AgentCompactionConfig compactionConfig) {
            this.compactionConfig = compactionConfig;
            return this;
        }

        public Builder toolResultEvictionConfig(AgentToolResultEvictionConfig toolResultEvictionConfig) {
            this.toolResultEvictionConfig = toolResultEvictionConfig;
            return this;
        }

        public AgentMemoryConfig build() {
            return new AgentMemoryConfig(compactionConfig, toolResultEvictionConfig);
        }
    }
}
