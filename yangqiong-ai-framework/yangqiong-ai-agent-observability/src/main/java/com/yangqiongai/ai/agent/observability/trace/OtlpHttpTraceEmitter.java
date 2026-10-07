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

import com.yangqiongai.ai.agent.runtime.trace.SpanInfo;
import com.yangqiongai.ai.agent.runtime.trace.TraceEmitter;

/**
 * OTLP/HTTP追踪导出器
 * <p>
 * TraceEmitter SPI 的扩展实现：Span 结束回调时入缓冲队列，
 * 由 OtlpBatchBuffer 批量编码为 OTLP JSON 并 POST 到 Collector。
 * 注册为Spring Bean后由 HarnessAutoConfiguration 自动装配到运行时。
 * </p>
 * @author yangqiong
 */
public final class OtlpHttpTraceEmitter implements TraceEmitter, AutoCloseable {

    /**
     * Span批量缓冲导出器
     */
    private final OtlpBatchBuffer buffer;

    /**
     * 构造导出器
     * @param buffer 批量缓冲导出器
     */
    public OtlpHttpTraceEmitter(OtlpBatchBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Span结束时入队缓冲，导出失败由缓冲器内部消化
     * @param spanInfo
     */
    @Override
    public void onSpan(SpanInfo spanInfo) {
        buffer.offer(spanInfo);
    }

    /**
     * 立即触发一次导出
     */
    public void flush() {
        buffer.flush();
    }

    /**
     * 关闭并做最后一次导出
     */
    @Override
    public void close() {
        buffer.close();
    }
}
