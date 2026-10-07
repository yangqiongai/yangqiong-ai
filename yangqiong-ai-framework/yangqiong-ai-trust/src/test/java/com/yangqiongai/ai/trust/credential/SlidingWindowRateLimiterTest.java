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
package com.yangqiongai.ai.trust.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 滑动窗口限流测试
 * @author yangqiong
 */
class SlidingWindowRateLimiterTest {

    @Test
    @DisplayName("窗口内计数未达阈值时放行")
    void tryAcquireShouldPassUnderLimit() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("okc0001", 3)).isTrue();
        assertThat(limiter.tryAcquire("okc0001", 3)).isTrue();
    }

    @Test
    @DisplayName("窗口内计数达到阈值后拒绝")
    void tryAcquireShouldRejectWhenLimitReached() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("okc0001", 2)).isTrue();
        assertThat(limiter.tryAcquire("okc0001", 2)).isTrue();
        assertThat(limiter.tryAcquire("okc0001", 2)).isFalse();
    }

    @Test
    @DisplayName("不同key计数互相隔离")
    void tryAcquireShouldIsolateKeys() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("okc0001", 1)).isTrue();
        assertThat(limiter.tryAcquire("okc0001", 1)).isFalse();
        assertThat(limiter.tryAcquire("okc0002", 1)).isTrue();
    }

    @Test
    @DisplayName("阈值为0或负数时直接放行")
    void tryAcquireShouldPassWhenQpsNotPositive() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("okc0001", 0)).isTrue();
        assertThat(limiter.tryAcquire("okc0001", -1)).isTrue();
    }

    @Test
    @DisplayName("滑出窗口的时间戳不再计入窗口")
    void tryAcquireShouldIgnoreExpiredTimestamps() throws Exception {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("okc0001", 1)).isTrue();
        // 回拨窗口内时间戳使其滑出窗口
        backdateWindow(limiter, "okc0001", 2000L);
        assertThat(limiter.tryAcquire("okc0001", 1)).isTrue();
    }

    @Test
    @DisplayName("reset后窗口清空重新放行")
    void resetShouldClearWindow() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        assertThat(limiter.tryAcquire("okc0001", 1)).isTrue();
        assertThat(limiter.tryAcquire("okc0001", 1)).isFalse();
        limiter.reset("okc0001");
        assertThat(limiter.tryAcquire("okc0001", 1)).isTrue();
    }

    /**
     * 将指定key窗口内时间戳回拨指定毫秒(模拟时间流逝)
     * @param limiter
     * @param key
     * @param millis
     */
    @SuppressWarnings("unchecked")
    private void backdateWindow(SlidingWindowRateLimiter limiter, String key, long millis) throws Exception {
        Field windowsField = SlidingWindowRateLimiter.class.getDeclaredField("windows");
        windowsField.setAccessible(true);
        Map<String, Deque<Long>> windows = (Map<String, Deque<Long>>) windowsField.get(limiter);
        Deque<Long> window = windows.get(key);
        assertThat(window).isInstanceOf(ArrayDeque.class);
        Deque<Long> shifted = new ArrayDeque<>();
        for (Long timestamp : window) {
            shifted.add(timestamp - millis);
        }
        window.clear();
        window.addAll(shifted);
    }
}
