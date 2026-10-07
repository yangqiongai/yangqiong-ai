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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent熔断器，基于滑动窗口实现
 * <p>按modelCode粒度熔断，同一模型连续失败则熔断该模型，不影响其他模型</p>
 * <p>状态机：CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN</p>
 */
@Slf4j
@Service
public class AgentCircuitBreaker {

    private final CircuitBreakerConfig defaultConfig;
    private final ConcurrentHashMap<String, CircuitStateHolder> circuits = new ConcurrentHashMap<>();

    public AgentCircuitBreaker() {
        this.defaultConfig = CircuitBreakerConfig.builder().build();
    }

    public AgentCircuitBreaker(CircuitBreakerConfig config) {
        this.defaultConfig = config != null ? config : CircuitBreakerConfig.builder().build();
    }

    /**
     * 判断是否允许请求通过
     */
    public boolean allowRequest(String key) {
        CircuitStateHolder holder = circuits.computeIfAbsent(key, k -> new CircuitStateHolder(defaultConfig));
        return holder.allowRequest();
    }

    /**
     * 记录成功
     */
    public void recordSuccess(String key) {
        CircuitStateHolder holder = circuits.get(key);
        if (holder != null) {
            holder.recordSuccess();
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure(String key) {
        CircuitStateHolder holder = circuits.computeIfAbsent(key, k -> new CircuitStateHolder(defaultConfig));
        holder.recordFailure();
    }

    /**
     * 获取当前状态
     */
    public CircuitState getState(String key) {
        CircuitStateHolder holder = circuits.get(key);
        return holder != null ? holder.getState() : CircuitState.CLOSED;
    }

    /**
     * 重置指定key的熔断器
     */
    public void reset(String key) {
        circuits.remove(key);
        log.info("熔断器已重置: key={}", key);
    }

    /**
     * 单个熔断器的状态持有者
     */
    private static class CircuitStateHolder {
        private final CircuitBreakerConfig config;
        private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger halfOpenCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicLong openSince = new AtomicLong(0);

        CircuitStateHolder(CircuitBreakerConfig config) {
            this.config = config;
        }

        boolean allowRequest() {
            CircuitState current = state.get();
            switch (current) {
                case CLOSED:
                    return true;
                case OPEN:
                    // 检查是否超过OPEN持续时间
                    if (System.currentTimeMillis() - openSince.get() >= config.getOpenDurationSeconds() * 1000L) {
                        // 尝试转换为HALF_OPEN
                        if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                            halfOpenCount.set(0);
                            log.info("熔断器状态变更: OPEN → HALF_OPEN");
                            return true;
                        }
                        return state.get() != CircuitState.OPEN;
                    }
                    return false;
                case HALF_OPEN:
                    // 允许有限探测请求
                    return halfOpenCount.incrementAndGet() <= config.getHalfOpenMaxRequests();
                default:
                    return true;
            }
        }

        void recordSuccess() {
            CircuitState current = state.get();
            if (current == CircuitState.HALF_OPEN) {
                // 探测成功，恢复CLOSED
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                    failureCount.set(0);
                    log.info("熔断器状态变更: HALF_OPEN → CLOSED (探测成功)");
                }
            } else if (current == CircuitState.CLOSED) {
                // 正常成功，重置失败计数
                failureCount.set(0);
            }
        }

        void recordFailure() {
            lastFailureTime.set(System.currentTimeMillis());
            CircuitState current = state.get();
            if (current == CircuitState.HALF_OPEN) {
                // 探测失败，回到OPEN
                if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.OPEN)) {
                    openSince.set(System.currentTimeMillis());
                    log.warn("熔断器状态变更: HALF_OPEN → OPEN (探测失败)");
                }
            } else if (current == CircuitState.CLOSED) {
                int count = failureCount.incrementAndGet();
                if (count >= config.getFailureThreshold()) {
                    if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                        openSince.set(System.currentTimeMillis());
                        log.warn("熔断器状态变更: CLOSED → OPEN (连续失败{}次)", count);
                    }
                }
            }
        }

        CircuitState getState() {
            CircuitState current = state.get();
            // 懒检查OPEN→HALF_OPEN转换
            if (current == CircuitState.OPEN) {
                if (System.currentTimeMillis() - openSince.get() >= config.getOpenDurationSeconds() * 1000L) {
                    return CircuitState.HALF_OPEN;
                }
            }
            return current;
        }
    }
}
