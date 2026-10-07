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
package com.yangqiongai.ai.platform.api.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent任务队列配置
 * @author yangqiong
 */
@Component
@ConfigurationProperties(prefix = "ai.agent.queue")
public class QueueProperties {

    /**
     * 是否启用队列调度器（多实例抢占/心跳/回收）
     */
    private boolean enabled = false;

    /**
     * 入队模式(sync=同步直执零破坏/queue=写QUEUED由调度器执行)
     */
    private String mode = "sync";

    /**
     * 调度器轮询间隔(毫秒)
     */
    private long pollIntervalMs = 2000;

    /**
     * 单轮抢占任务数上限
     */
    private int batchSize = 5;

    /**
     * 心跳刷新间隔(毫秒)
     */
    private long heartbeatIntervalMs = 30000;

    /**
     * 心跳超时回收阈值(秒)，超时视为实例失联
     */
    private int reclaimExpireSeconds = 90;

    /**
     * 最大重派次数，耗尽后置FAILED
     */
    private int maxRedeliver = 3;

    /**
     * 背压配置
     */
    private final Backpressure backpressure = new Backpressure();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 是否队列入队模式
     * @return
     */
    public boolean isQueueMode() {
        return "queue".equalsIgnoreCase(mode);
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public int getReclaimExpireSeconds() {
        return reclaimExpireSeconds;
    }

    public void setReclaimExpireSeconds(int reclaimExpireSeconds) {
        this.reclaimExpireSeconds = reclaimExpireSeconds;
    }

    public int getMaxRedeliver() {
        return maxRedeliver;
    }

    public void setMaxRedeliver(int maxRedeliver) {
        this.maxRedeliver = maxRedeliver;
    }

    public Backpressure getBackpressure() {
        return backpressure;
    }

    /**
     * 背压配置项
     */
    public static class Backpressure {

        /**
         * 单作用域最大并发RUNNING数(0=不限制)
         */
        private int maxConcurrentPerScope = 0;

        /**
         * 单Agent最大并发RUNNING数(0=不限制)
         */
        private int maxConcurrentPerAgent = 0;

        /**
         * 单作用域最大队列深度(0=不限制)
         */
        private int maxQueueDepthPerScope = 0;

        /**
         * 单Agent最大队列深度(0=不限制)
         */
        private int maxQueueDepthPerAgent = 0;

        /**
         * 超限策略(REJECT=拒绝/WAIT=阻塞等待超时拒绝/DEGRADE=降优先级入队)
         */
        private String strategy = "REJECT";

        /**
         * WAIT策略最长等待(毫秒)
         */
        private long waitTimeoutMs = 5000;

        public int getMaxConcurrentPerScope() {
            return maxConcurrentPerScope;
        }

        public void setMaxConcurrentPerScope(int maxConcurrentPerScope) {
            this.maxConcurrentPerScope = maxConcurrentPerScope;
        }

        public int getMaxConcurrentPerAgent() {
            return maxConcurrentPerAgent;
        }

        public void setMaxConcurrentPerAgent(int maxConcurrentPerAgent) {
            this.maxConcurrentPerAgent = maxConcurrentPerAgent;
        }

        public int getMaxQueueDepthPerScope() {
            return maxQueueDepthPerScope;
        }

        public void setMaxQueueDepthPerScope(int maxQueueDepthPerScope) {
            this.maxQueueDepthPerScope = maxQueueDepthPerScope;
        }

        public int getMaxQueueDepthPerAgent() {
            return maxQueueDepthPerAgent;
        }

        public void setMaxQueueDepthPerAgent(int maxQueueDepthPerAgent) {
            this.maxQueueDepthPerAgent = maxQueueDepthPerAgent;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public long getWaitTimeoutMs() {
            return waitTimeoutMs;
        }

        public void setWaitTimeoutMs(long waitTimeoutMs) {
            this.waitTimeoutMs = waitTimeoutMs;
        }
    }
}
