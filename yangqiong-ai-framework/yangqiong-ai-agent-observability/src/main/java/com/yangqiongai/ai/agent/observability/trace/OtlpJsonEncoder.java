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

import java.util.Collection;
import java.util.Map;

import com.yangqiongai.ai.agent.runtime.trace.SpanInfo;

/**
 * OTLP JSON编码器
 * <p>
 * 将已结束的SpanInfo集合编码为OTLP/HTTP协议（application/json）的Traces导出报文。
 * 零外部依赖手写序列化，int64字段（纳秒时间戳）按proto3 JSON映射输出为字符串；
 * Span状态映射为OTLP status（ERROR时附错误信息），开始时间优先取startTimeMs，
 * 缺失时按入队时刻回推补齐。
 * </p>
 * @author yangqiong
 */
final class OtlpJsonEncoder {

    /**
     * Span归属的InstrumentationScope名称
     */
    private static final String SCOPE_NAME = "yangqiong-ai-agent-observability";

    /**
     * W3C traceId标准长度（16字节32个十六进制字符）
     */
    private static final int TRACE_ID_LEN = 32;

    /**
     * W3C spanId标准长度（8字节16个十六进制字符）
     */
    private static final int SPAN_ID_LEN = 16;

    /**
     * 错误状态标记
     */
    private static final String STATUS_ERROR = "ERROR";

    /**
     * 私有构造，工具类禁止实例化
     */
    private OtlpJsonEncoder() {
    }

    /**
     * 编码一批Span为OTLP Traces JSON报文
     * @param spans
     * @param serviceName
     * @return
     */
    static String encode(Collection<OtlpBatchBuffer.TimedSpan> spans, String serviceName) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"resourceSpans\":[{\"resource\":{\"attributes\":[")
                .append(attributeJson("service.name", serviceName))
                .append("]},\"scopeSpans\":[{\"scope\":{\"name\":\"").append(escape(SCOPE_NAME))
                .append("\"},\"spans\":[");
        boolean first = true;
        for (OtlpBatchBuffer.TimedSpan timed : spans) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            appendSpan(sb, timed);
        }
        sb.append("]}]}]}");
        return sb.toString();
    }

    /**
     * 追加单个Span的JSON片段
     * @param sb
     * @param timed
     */
    private static void appendSpan(StringBuilder sb, OtlpBatchBuffer.TimedSpan timed) {
        SpanInfo span = timed.span();
        long endNanos = timed.endEpochMs() * 1_000_000L;
        long startNanos = resolveStartNanos(span, endNanos);
        sb.append("{");
        sb.append("\"traceId\":\"").append(normalizeId(span.getTraceId(), TRACE_ID_LEN)).append("\",");
        sb.append("\"spanId\":\"").append(normalizeId(span.getSpanId(), SPAN_ID_LEN)).append("\",");
        String parent = span.getParentSpanId();
        if (parent != null && !parent.isBlank()) {
            sb.append("\"parentSpanId\":\"").append(normalizeId(parent, SPAN_ID_LEN)).append("\",");
        }
        sb.append("\"name\":\"").append(escape(span.getOperation())).append("\",");
        sb.append("\"kind\":\"SPAN_KIND_INTERNAL\",");
        sb.append("\"startTimeUnixNano\":\"").append(startNanos).append("\",");
        sb.append("\"endTimeUnixNano\":\"").append(endNanos).append("\"");
        Map<String, Object> attributes = span.getAttributes();
        if (!attributes.isEmpty()) {
            sb.append(",\"attributes\":[");
            boolean attrFirst = true;
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                if (!attrFirst) {
                    sb.append(",");
                }
                attrFirst = false;
                sb.append(attributeJson(entry.getKey(), entry.getValue()));
            }
            sb.append("]");
        }
        sb.append(",\"status\":").append(statusJson(span));
        sb.append("}");
    }

    /**
     * 解析开始纳秒：startTimeMs有效时直接换算，否则按结束时刻回推
     * @param span
     * @param endNanos
     * @return
     */
    private static long resolveStartNanos(SpanInfo span, long endNanos) {
        if (span.getStartTimeMs() > 0) {
            return span.getStartTimeMs() * 1_000_000L;
        }
        long fallbackMs = Math.max(0L, endNanos / 1_000_000L - span.getDurationMs());
        return fallbackMs * 1_000_000L;
    }

    /**
     * 构造Span状态JSON：ERROR时附错误信息，其余为OK
     * @param span
     * @return
     */
    private static String statusJson(SpanInfo span) {
        boolean error = STATUS_ERROR.equalsIgnoreCase(span.getStatus())
                || (span.getErrorMessage() != null && !span.getErrorMessage().isBlank());
        if (error) {
            String message = span.getErrorMessage() != null ? span.getErrorMessage() : span.getStatus();
            return "{\"code\":\"STATUS_CODE_ERROR\",\"message\":\"" + escape(message) + "\"}";
        }
        return "{\"code\":\"STATUS_CODE_OK\"}";
    }

    /**
     * 构造单个OTLP属性键值对JSON
     * @param key
     * @param value
     * @return
     */
    private static String attributeJson(String key, Object value) {
        return "{\"key\":\"" + escape(key) + "\",\"value\":" + anyValueJson(value) + "}";
    }

    /**
     * 按OTLP anyValue类型映射构造值JSON
     * @param value
     * @return
     */
    private static String anyValueJson(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return "{\"intValue\":\"" + value + "\"}";
        }
        if (value instanceof Boolean) {
            return "{\"boolValue\":" + value + "}";
        }
        if (value instanceof Number) {
            return "{\"doubleValue\":" + value + "}";
        }
        return "{\"stringValue\":\"" + escape(String.valueOf(value)) + "\"}";
    }

    /**
     * 规整ID为W3C标准长度：超长截断、不足右补零、空白回退全零
     * @param id
     * @param targetLen
     * @return
     */
    private static String normalizeId(String id, int targetLen) {
        if (id == null || id.isBlank()) {
            return "0".repeat(targetLen);
        }
        String hex = id.length() > targetLen ? id.substring(0, targetLen) : id;
        if (hex.length() < targetLen) {
            hex = hex + "0".repeat(targetLen - hex.length());
        }
        return hex;
    }

    /**
     * JSON字符串转义
     * @param text
     * @return
     */
    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
