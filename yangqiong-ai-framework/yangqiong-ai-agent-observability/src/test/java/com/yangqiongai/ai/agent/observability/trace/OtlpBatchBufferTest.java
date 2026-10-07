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
package com.yangqiongai.ai.agent.observability.trace;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.trace.SpanInfo;

/**
 * OTLP Span批量缓冲导出器测试
 * @author yangqiong
 */
class OtlpBatchBufferTest {

    /**
     * 构造Span
     * @param traceId
     * @return
     */
    private SpanInfo span(String traceId) {
        return new SpanInfo(traceId, "span-1", null, "agent_run", 100L, Map.of());
    }

    @Test
    @DisplayName("flush触发批量导出且端点自动补全/v1/traces")
    void flushExportsBatchToTracesPath() {
        List<String> urls = new ArrayList<>();
        List<byte[]> bodies = new ArrayList<>();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 10,
                0L, 1000, 100,
                (url, contentType, body, timeoutMs) -> {
                    urls.add(url);
                    bodies.add(body);
                    return true;
                });
        buffer.offer(span("t1"));
        buffer.flush();

        assertThat(urls).containsExactly("http://collector:4318/v1/traces");
        assertThat(buffer.pendingCount()).isZero();
        String payload = new String(bodies.get(0), StandardCharsets.UTF_8);
        assertThat(payload).contains("t1").contains("service.name");
    }

    @Test
    @DisplayName("缓冲满时淘汰最旧并累计丢弃计数")
    void offerEvictsOldestWhenFull() {
        List<byte[]> bodies = new ArrayList<>();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 100,
                0L, 1000, 2,
                (url, contentType, body, timeoutMs) -> {
                    bodies.add(body);
                    return true;
                });
        buffer.offer(span("t1"));
        buffer.offer(span("t2"));
        buffer.offer(span("t3"));
        buffer.flush();

        assertThat(buffer.droppedCount()).isEqualTo(1);
        String payload = new String(bodies.get(0), StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("t1").contains("t2").contains("t3");
    }

    @Test
    @DisplayName("导出异常内部消化不外抛且丢弃该批")
    void flushSwallowsExportFailure() {
        AtomicInteger calls = new AtomicInteger();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 10,
                0L, 1000, 100,
                (url, contentType, body, timeoutMs) -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("boom");
                });
        buffer.offer(span("t1"));

        assertThatCode(buffer::flush).doesNotThrowAnyException();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    @DisplayName("非2xx响应视为失败仅告警")
    void flushTreatsNon2xxAsFailure() {
        AtomicInteger calls = new AtomicInteger();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 10,
                0L, 1000, 100,
                (url, contentType, body, timeoutMs) -> {
                    calls.incrementAndGet();
                    return false;
                });
        buffer.offer(span("t1"));
        buffer.flush();

        assertThat(calls.get()).isEqualTo(1);
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    @DisplayName("close幂等并做最后一次导出")
    void closeExportsRemainingAndIsIdempotent() {
        AtomicInteger calls = new AtomicInteger();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 10,
                0L, 1000, 100,
                (url, contentType, body, timeoutMs) -> {
                    calls.incrementAndGet();
                    return true;
                });
        buffer.offer(span("t1"));
        buffer.close();
        buffer.close();
        buffer.offer(span("t2"));

        assertThat(calls.get()).isEqualTo(1);
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    @DisplayName("flushSize取整批上限分批导出")
    void flushBatchesByBatchSize() {
        List<byte[]> bodies = new ArrayList<>();
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 2,
                0L, 1000, 100,
                (url, contentType, body, timeoutMs) -> {
                    bodies.add(body);
                    return true;
                });
        buffer.offer(span("t1"));
        buffer.offer(span("t2"));
        buffer.offer(span("t3"));
        buffer.flush();

        assertThat(bodies).hasSize(2);
    }

    @Test
    @DisplayName("null Span与关闭后offer静默忽略")
    void offerIgnoresInvalidInput() {
        OtlpBatchBuffer buffer = new OtlpBatchBuffer("http://collector:4318", "svc", 10,
                0L, 1000, 100, (url, contentType, body, timeoutMs) -> true);
        buffer.offer(null);
        assertThat(buffer.pendingCount()).isZero();
        buffer.close();
        buffer.offer(span("t1"));
        assertThat(buffer.pendingCount()).isZero();
    }
}
