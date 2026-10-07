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
package com.yangqiongai.ai.agent.core.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 活跃Agent流注册表
 * @author yangqiong
 */
@Component
public class ActiveAgentStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActiveAgentStreamRegistry.class);

    private static final long STREAM_TIMEOUT_MINUTES = 30;

    private static final long CLEANUP_INTERVAL_MINUTES = 5;

    private final Map<String, StreamEntry> activeStreams = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleanupScheduler;

    public ActiveAgentStreamRegistry() {
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agent-stream-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(
                this::cleanupTimedOutStreams,
                CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * 注册流
     * @param taskId
     * @return
     */
    public CancellationToken register(String taskId) {
        CancellationToken token = new CancellationToken();
        activeStreams.put(taskId, new StreamEntry(token, System.currentTimeMillis()));
        return token;
    }

    /**
     * 取消流
     * @param taskId
     */
    public void cancel(String taskId) {
        StreamEntry entry = activeStreams.get(taskId);
        if (entry != null) {
            entry.getToken().cancel();
        }
    }

    /**
     * 移除流
     * @param taskId
     */
    public void remove(String taskId) {
        activeStreams.remove(taskId);
    }

    /**
     * 获取取消令牌
     * @param taskId
     * @return
     */
    public CancellationToken getToken(String taskId) {
        StreamEntry entry = activeStreams.get(taskId);
        return entry != null ? entry.getToken() : null;
    }

    /**
     * 清理超时的流
     */
    private void cleanupTimedOutStreams() {
        long now = System.currentTimeMillis();
        long timeoutMillis = TimeUnit.MINUTES.toMillis(STREAM_TIMEOUT_MINUTES);
        activeStreams.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getRegisterTime() > timeoutMillis) {
                log.warn("清理超时Agent流: taskId={}, 超时时间={}分钟", entry.getKey(), STREAM_TIMEOUT_MINUTES);
                entry.getValue().getToken().cancel();
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void destroy() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 流条目，包含取消令牌和注册时间
     */
    private static class StreamEntry {
        private final CancellationToken token;
        private final long registerTime;

        StreamEntry(CancellationToken token, long registerTime) {
            this.token = token;
            this.registerTime = registerTime;
        }

        CancellationToken getToken() {
            return token;
        }

        long getRegisterTime() {
            return registerTime;
        }
    }
}
