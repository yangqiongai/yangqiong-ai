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
package com.yangqiongai.ai.agent.data.trace;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent上下文快照配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.context-snapshot")
public class AgentContextSnapshotProperties {

    /**
     * 总开关（默认开，出问题时可全局关闭）
     */
    private boolean enabled = true;

    /**
     * 单条消息内容最大字符数（超过则截断）
     */
    private int maxItemChars = 65536;

    /**
     * 内存队列容量（满则丢弃并计数）
     */
    private int maxQueueSize = 10000;

    /**
     * 批量落库条数
     */
    private int batchSize = 200;

    /**
     * 刷新间隔（毫秒）
     */
    private long flushIntervalMs = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxItemChars() {
        return maxItemChars;
    }

    public void setMaxItemChars(int maxItemChars) {
        this.maxItemChars = maxItemChars;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }
}
