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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求去重器
 * @author yangqiong
 */
class RequestDeduplicatorTest {

    private RequestDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new RequestDeduplicator(60);
    }

    @Test
    void testCheckReturnsNullForNonExistentKey() {
        DedupKey key = new DedupKey("test-key", "test-fingerprint");
        CapabilityResponse result = deduplicator.check(key);
        assertThat(result).isNull();
    }

    @Test
    void testCacheAndCheck() {
        DedupKey key = new DedupKey("test-key", "test-fingerprint");
        CapabilityResponse response = CapabilityResponse.success("输出内容", null);

        deduplicator.cache(key, response, 60);

        CapabilityResponse result = deduplicator.check(key);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("输出内容");
    }

    @Test
    void testEvict() {
        DedupKey key = new DedupKey("test-key", "test-fingerprint");
        CapabilityResponse response = CapabilityResponse.success("输出内容", null);

        deduplicator.cache(key, response, 60);
        assertThat(deduplicator.check(key)).isNotNull();

        deduplicator.evict(key);
        assertThat(deduplicator.check(key)).isNull();
    }

    @Test
    void testCacheExpiration() {
        DedupKey key = new DedupKey("test-key", "test-fingerprint");
        CapabilityResponse response = CapabilityResponse.success("输出内容", null);

        deduplicator.cache(key, response, 1);

        assertThat(deduplicator.check(key)).isNotNull();

        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(deduplicator.check(key)).isNull();
    }

    @Test
    void testCheckWithNullKeyReturnsNull() {
        CapabilityResponse result = deduplicator.check(null);
        assertThat(result).isNull();
    }

    @Test
    void testCacheWithNullKey() {
        CapabilityResponse response = CapabilityResponse.success("输出内容", null);
        // 不应抛出异常
        deduplicator.cache(null, response, 60);
    }

    @Test
    void testCacheWithNullResponse() {
        DedupKey key = new DedupKey("test-key", "test-fingerprint");
        // 不应抛出异常
        deduplicator.cache(key, null, 60);
    }

    @Test
    void testEvictWithNullKey() {
        // 不应抛出异常
        deduplicator.evict(null);
    }

    @Test
    void testPurgeExpired() {
        DedupKey key1 = new DedupKey("key-1", "fp-1");
        DedupKey key2 = new DedupKey("key-2", "fp-2");

        deduplicator.cache(key1, CapabilityResponse.success("结果1", null), 1);
        deduplicator.cache(key2, CapabilityResponse.success("结果2", null), 60);

        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        deduplicator.purgeExpired();

        assertThat(deduplicator.check(key1)).isNull();
        assertThat(deduplicator.check(key2)).isNotNull();
    }

    @Test
    void testCacheWithZeroTtlUsesDefault() {
        DedupKey key = new DedupKey("test-key", "test-fingerprint");
        deduplicator.cache(key, CapabilityResponse.success("输出", null), 0);

        // 默认TTL是60秒，所以应该能命中
        assertThat(deduplicator.check(key)).isNotNull();
    }

    @Test
    void testDifferentKeysDoNotConflict() {
        DedupKey key1 = new DedupKey("key-1", "fp-1");
        DedupKey key2 = new DedupKey("key-2", "fp-2");

        deduplicator.cache(key1, CapabilityResponse.success("结果1", null), 60);

        assertThat(deduplicator.check(key1)).isNotNull();
        assertThat(deduplicator.check(key2)).isNull();
    }

    @Test
    void testSameKeyDifferentFingerprintDoNotConflict() {
        DedupKey key1 = new DedupKey("same-key", "fp-1");
        DedupKey key2 = new DedupKey("same-key", "fp-2");

        deduplicator.cache(key1, CapabilityResponse.success("结果1", null), 60);

        assertThat(deduplicator.check(key1)).isNotNull();
        assertThat(deduplicator.check(key2)).isNull();
    }
}