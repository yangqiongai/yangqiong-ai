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
 * Agent运行Trace配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.trace")
public class AgentTraceProperties {

    /**
     * 总开关（默认关，生产按环境打开）
     */
    private boolean enabled = false;

    /**
     * 采样模式（ratio=按采样率比例采样，all=全量采集）
     */
    private TraceSampleMode sampleMode = TraceSampleMode.RATIO;

    /**
     * 采样率（0~1，根Span掷骰决定整条trace是否采集，ratio模式下生效）
     */
    private double sampleRate = 1.0;

    /**
     * 失败Span必采（覆盖采样决策，ratio模式下实现"全量+失败必采"效果）
     */
    private boolean alwaysKeepErrors = false;

    /**
     * 数据保留天数（0或负数关闭保留期清理）
     */
    private int retentionDays = 30;

    /**
     * 保留期清理单批删除上限（防大事务锁表）
     */
    private int retentionBatchSize = 5000;

    /**
     * 内存队列容量（满则丢弃并计数）
     */
    private int queueCapacity = 10000;

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

    public TraceSampleMode getSampleMode() {
        return sampleMode;
    }

    public void setSampleMode(TraceSampleMode sampleMode) {
        this.sampleMode = sampleMode;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isAlwaysKeepErrors() {
        return alwaysKeepErrors;
    }

    public void setAlwaysKeepErrors(boolean alwaysKeepErrors) {
        this.alwaysKeepErrors = alwaysKeepErrors;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getRetentionBatchSize() {
        return retentionBatchSize;
    }

    public void setRetentionBatchSize(int retentionBatchSize) {
        this.retentionBatchSize = retentionBatchSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
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
