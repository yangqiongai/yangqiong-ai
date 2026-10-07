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
package com.yangqiongai.ai.open.capability.trace;

import com.yangqiongai.ai.open.capability.engine.CapabilityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求去重器
 * <p>
 * 基于DedupKey + 请求指纹实现去重。
 * 相同DedupKey的请求在有效期内返回缓存结果。
 * 当前为内存实现，后续可切换为Redis实现。
 * </p>
 * @author yangqiong
 */
public class RequestDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(RequestDeduplicator.class);

    /**
     * 触发摊销过期清理的缓存规模阈值
     */
    private static final int PURGE_THRESHOLD = 1000;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private final long defaultTtlMillis;

    public RequestDeduplicator(long defaultTtlSeconds) {
        this.defaultTtlMillis = defaultTtlSeconds * 1000;
    }

    /**
     * 检查去重
     * @param key 去重键
     * @return 缓存的响应（如存在）
     */
    public CapabilityResponse check(DedupKey key) {
        if (key == null) {
            return null;
        }
        CacheEntry entry = cache.get(key.getCompositeKey());
        if (entry == null) {
            return null;
        }
        // 检查是否过期
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(key.getCompositeKey());
            return null;
        }
        log.debug("命中去重缓存: key={}", key.getKey());
        return entry.response;
    }

    /**
     * 缓存响应
     * @param key 去重键
     * @param response 响应
     * @param ttlSeconds 有效期（秒）
     */
    public void cache(DedupKey key, CapabilityResponse response, long ttlSeconds) {
        if (key == null || response == null) {
            return;
        }
        // 摊销清理：缓存规模超阈值时清除过期条目，防止长期运行内存泄漏
        if (cache.size() >= PURGE_THRESHOLD) {
            purgeExpired();
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds * 1000 : defaultTtlMillis;
        cache.put(key.getCompositeKey(), new CacheEntry(response, System.currentTimeMillis() + ttl));
        log.debug("缓存去重结果: key={}, ttl={}s", key.getKey(), ttl / 1000);
    }

    /**
     * 按去重键清除缓存
     * @param key
     */
    public void evict(DedupKey key) {
        if (key != null) {
            cache.remove(key.getCompositeKey());
        }
    }

    /**
     * 清除所有过期缓存
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now > entry.getValue().expiresAt);
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {

        final CapabilityResponse response;
        final long expiresAt;

        CacheEntry(CapabilityResponse response, long expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }
}