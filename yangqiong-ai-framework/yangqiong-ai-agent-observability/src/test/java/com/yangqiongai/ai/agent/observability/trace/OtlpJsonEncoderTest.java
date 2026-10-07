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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.trace.SpanInfo;

/**
 * OTLP JSON编码器测试
 * @author yangqiong
 */
class OtlpJsonEncoderTest {

    /**
     * 构造基础Span
     * @return
     */
    private SpanInfo span() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("agent", "writer");
        attrs.put("iteration", 3);
        attrs.put("success", true);
        return new SpanInfo("trace-abc", "span-123", "parent-9", "agent_run",
                1500L, attrs, "OK", null, 1700000000000L);
    }

    @Test
    @DisplayName("编码报文包含资源、作用域与Span骨架字段")
    void encodeProducesOtlpSkeleton() {
        String json = OtlpJsonEncoder.encode(
                List.of(new OtlpBatchBuffer.TimedSpan(span(), 1700000002000L)), "svc");

        assertThat(json).startsWith("{\"resourceSpans\":[{\"resource\":{\"attributes\":[")
                .contains("\"service.name\"")
                .contains("yangqiong-ai-agent-observability")
                .contains("\"traceId\":\"" + "trace-abc" + "0".repeat(23) + "\"")
                .contains("\"spanId\":\"" + "span-123" + "0".repeat(8) + "\"")
                .contains("\"parentSpanId\":\"" + "parent-9" + "0".repeat(8) + "\"")
                .contains("\"name\":\"agent_run\"")
                .contains("\"kind\":\"SPAN_KIND_INTERNAL\"")
                .contains("\"startTimeUnixNano\":\"1700000000000000000\"")
                .contains("\"endTimeUnixNano\":\"1700000002000000000\"")
                .endsWith("]}]}]}");
    }

    @Test
    @DisplayName("属性按anyValue类型映射：整数字符串化、布尔原样、其余字符串")
    void encodeMapsAttributeTypes() {
        String json = OtlpJsonEncoder.encode(
                List.of(new OtlpBatchBuffer.TimedSpan(span(), 1700000002000L)), "svc");

        assertThat(json)
                .contains("{\"key\":\"agent\",\"value\":{\"stringValue\":\"writer\"}}")
                .contains("{\"key\":\"iteration\",\"value\":{\"intValue\":\"3\"}}")
                .contains("{\"key\":\"success\",\"value\":{\"boolValue\":true}}");
    }

    @Test
    @DisplayName("错误Span输出STATUS_CODE_ERROR并附错误信息")
    void encodeMapsErrorStatus() {
        SpanInfo error = new SpanInfo("t", "s", null, "op", 10L, Map.of(),
                "ERROR", "boom", 1700000000000L);
        String json = OtlpJsonEncoder.encode(
                List.of(new OtlpBatchBuffer.TimedSpan(error, 1700000000010L)), "svc");

        assertThat(json).contains("\"status\":{\"code\":\"STATUS_CODE_ERROR\",\"message\":\"boom\"}");
    }

    @Test
    @DisplayName("正常Span输出STATUS_CODE_OK")
    void encodeMapsOkStatus() {
        String json = OtlpJsonEncoder.encode(
                List.of(new OtlpBatchBuffer.TimedSpan(span(), 1700000002000L)), "svc");

        assertThat(json).contains("\"status\":{\"code\":\"STATUS_CODE_OK\"}");
    }

    @Test
    @DisplayName("startTimeMs未知（0）时按结束时刻回推开始时间")
    void encodeFallsBackStartTime() {
        SpanInfo withoutStart = new SpanInfo("t", "s", null, "op", 1000L, Map.of(),
                "OK", null, 0L);
        String json = OtlpJsonEncoder.encode(
                List.of(new OtlpBatchBuffer.TimedSpan(withoutStart, 1700000002000L)), "svc");

        assertThat(json).contains("\"startTimeUnixNano\":\"1700000001000000000\"");
    }

    @Test
    @DisplayName("ID空白回退全零且名称转义")
    void encodeNormalizesIdsAndEscapes() {
        SpanInfo weird = new SpanInfo(" ", "", null, "op\"x\n", 5L, Map.of());
        String json = OtlpJsonEncoder.encode(
                List.of(new OtlpBatchBuffer.TimedSpan(weird, 1700000000005L)), "svc");

        assertThat(json)
                .contains("\"traceId\":\"00000000000000000000000000000000\"")
                .contains("\"spanId\":\"0000000000000000\"")
                .contains("\"name\":\"op\\\"x\\n\"");
    }

    @Test
    @DisplayName("多个Span以逗号分隔输出")
    void encodeMultipleSpans() {
        SpanInfo first = span();
        SpanInfo second = new SpanInfo("t2", "s2", null, "acting", 1L, Map.of());
        String json = OtlpJsonEncoder.encode(List.of(
                new OtlpBatchBuffer.TimedSpan(first, 1700000002000L),
                new OtlpBatchBuffer.TimedSpan(second, 1700000002001L)), "svc");

        assertThat(json).contains("},{");
        assertThat(json.indexOf("agent_run")).isLessThan(json.indexOf("acting"));
    }
}
