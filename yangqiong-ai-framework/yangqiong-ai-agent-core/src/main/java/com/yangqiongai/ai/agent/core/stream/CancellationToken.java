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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式取消令牌，支持回调和超时自动取消
 * @author yangqiong
 */
public class CancellationToken {

    private static final ScheduledExecutorService TIMEOUT_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cancel-token-timeout");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Runnable> cancelCallback = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> timeoutFuture = new AtomicReference<>();
    private volatile String reason;

    /**
     * 取消
     */
    public void cancel() {
        cancel("操作已取消");
    }

    /**
     * 取消并指定原因
     * @param reason
     */
    public void cancel(String reason) {
        this.reason = reason;
        if (cancelled.compareAndSet(false, true)) {
            ScheduledFuture<?> future = timeoutFuture.get();
            if (future != null) {
                future.cancel(false);
            }
            Runnable callback = cancelCallback.getAndSet(null);
            if (callback != null) {
                callback.run();
            }
        }
    }

    /**
     * 是否已取消
     * @return
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 获取取消原因
     * @return
     */
    public String reason() {
        return reason;
    }

    /**
     * 注册取消回调，使用原子操作避免竞态条件
     * @param callback
     * @return 清理函数，可取消回调注册
     */
    public Runnable onCancel(Runnable callback) {
        // 如果已经取消，直接执行回调
        if (cancelled.get()) {
            callback.run();
            return () -> {};
        }
        // 原子注册回调：仅当未注册过时才设置
        if (cancelCallback.compareAndSet(null, callback)) {
            // 注册成功后再次检查是否已取消（防止注册和取消之间的竞态）
            if (cancelled.get()) {
                // 尝试取走回调并执行，如果已被cancel()取走则说明cancel()已执行过
                Runnable existing = cancelCallback.getAndSet(null);
                if (existing != null) {
                    existing.run();
                }
            }
            return () -> cancelCallback.compareAndSet(callback, null);
        }
        // 已有回调注册，直接执行（不太可能发生）
        callback.run();
        return () -> {};
    }

    /**
     * 超时自动取消
     * @param timeout
     * @return
     */
    public CancellationToken cancelAfter(java.time.Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return this;
        }
        ScheduledFuture<?> future = TIMEOUT_SCHEDULER.schedule(
                () -> cancel("执行超时: " + timeout.toSeconds() + "s"),
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        timeoutFuture.set(future);
        if (cancelled.get()) {
            future.cancel(false);
        }
        return this;
    }
}
