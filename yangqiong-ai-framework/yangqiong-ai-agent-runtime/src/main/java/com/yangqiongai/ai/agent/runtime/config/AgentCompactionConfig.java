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
 * Agent消息压缩配置
 * @author yangqiong
 */
public final class AgentCompactionConfig {

    /**
     * 触发压缩的消息数
     */
    private final int triggerMessages;

    /**
     * 保留的消息数
     */
    private final int keepMessages;

    private AgentCompactionConfig(int triggerMessages, int keepMessages) {
        this.triggerMessages = triggerMessages;
        this.keepMessages = keepMessages;
    }

    /**
     * 创建默认压缩配置
     * @return
     */
    public static AgentCompactionConfig defaults() {
        return new AgentCompactionConfig(40, 12);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取触发压缩的消息数
     * @return
     */
    public int getTriggerMessages() {
        return triggerMessages;
    }

    /**
     * 获取保留的消息数
     * @return
     */
    public int getKeepMessages() {
        return keepMessages;
    }

    @Override
    public String toString() {
        return "AgentCompactionConfig{triggerMessages=" + triggerMessages + ", keepMessages=" + keepMessages + "}";
    }

    /**
     * 压缩配置构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 触发压缩的消息数
         */
        private int triggerMessages;

        /**
         * 保留的消息数
         */
        private int keepMessages;

        public Builder triggerMessages(int triggerMessages) {
            this.triggerMessages = triggerMessages;
            return this;
        }

        public Builder keepMessages(int keepMessages) {
            this.keepMessages = keepMessages;
            return this;
        }

        public AgentCompactionConfig build() {
            return new AgentCompactionConfig(triggerMessages, keepMessages);
        }
    }
}
