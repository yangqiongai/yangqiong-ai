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
package com.yangqiongai.ai.agent.runtime.trace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 组合追踪导出器测试
 * @author yangqiong
 */
class CompositeTraceEmitterTest {

    /**
     * 记录型假导出器
     */
    private static class RecordingEmitter implements TraceEmitter {
        final List<SpanInfo> spans = new ArrayList<>();
        final boolean fail;

        RecordingEmitter() {
            this.fail = false;
        }

        RecordingEmitter(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void onSpan(SpanInfo spanInfo) {
            if (fail) {
                throw new IllegalStateException("导出失败");
            }
            spans.add(spanInfo);
        }
    }

    private SpanInfo span(String traceId, String spanId, String parentSpanId) {
        return new SpanInfo(traceId, spanId, parentSpanId, "agent_run", 10, null);
    }

    @Test
    void 按注册顺序向全部导出器广播() {
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        CompositeTraceEmitter composite = new CompositeTraceEmitter(List.of(first, second));

        composite.onSpan(span("t1", "s1", null));

        assertThat(first.spans).hasSize(1);
        assertThat(second.spans).hasSize(1);
        assertThat(second.spans.get(0).getSpanId()).isEqualTo("s1");
    }

    @Test
    void 单个导出器异常不影响其他导出器与调用方() {
        RecordingEmitter failing = new RecordingEmitter(true);
        RecordingEmitter normal = new RecordingEmitter();
        CompositeTraceEmitter composite = new CompositeTraceEmitter(List.of(failing, normal));

        assertThatCode(() -> composite.onSpan(span("t1", "s1", null)))
                .doesNotThrowAnyException();
        assertThat(normal.spans).hasSize(1);
    }

    @Test
    void 空导出器列表安全广播() {
        CompositeTraceEmitter composite = new CompositeTraceEmitter(List.of());

        assertThatCode(() -> composite.onSpan(span("t1", "s1", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void 广播后导出器列表不可变() {
        List<TraceEmitter> delegates = new ArrayList<>();
        delegates.add(new RecordingEmitter());
        CompositeTraceEmitter composite = new CompositeTraceEmitter(delegates);
        delegates.add(new RecordingEmitter());

        assertThatCode(() -> composite.onSpan(span("t1", "s1", null)))
                .doesNotThrowAnyException();
    }
}
