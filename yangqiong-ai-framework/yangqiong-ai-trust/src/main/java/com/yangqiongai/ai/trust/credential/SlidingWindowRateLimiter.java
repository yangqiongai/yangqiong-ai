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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 滑动窗口限流
 * <p>
 * 按凭证编码维度的1秒滑动窗口计数，内存实现（Redis版为企业增强），
 * 窗口内计数达到阈值后拒绝，滑出窗口的时间戳会被惰性清理。
 * </p>
 * @author yangqiong
 */
public class SlidingWindowRateLimiter {

    /**
     * 窗口长度(毫秒)
     */
    private static final long WINDOW_MILLIS = 1000L;

    private final Map<String, Deque<Long>> windows = new HashMap<>();

    /**
     * 尝试获取一次通行额度
     * @param key
     * @param qps
     * @return
     */
    public synchronized boolean tryAcquire(String key, int qps) {
        if (qps <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        // 惰性清理滑出窗口的时间戳
        Iterator<Long> iterator = window.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next() >= WINDOW_MILLIS) {
                iterator.remove();
            } else {
                break;
            }
        }
        if (window.size() >= qps) {
            return false;
        }
        window.addLast(now);
        return true;
    }

    /**
     * 清空指定key的窗口(轮换/吊销时调用)
     * @param key
     */
    public synchronized void reset(String key) {
        windows.remove(key);
    }
}
