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
package com.yangqiongai.ai.agent.data.trace;

import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import com.yangqiongai.ai.agent.runtime.trace.SpanInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 平台Span批量落库导出器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class PlatformTraceEmitterTest {

    @Mock
    private TraceSpanRepository traceSpanRepository;

    private PlatformTraceEmitter emitter;

    private AgentTraceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AgentTraceProperties();
        properties.setEnabled(true);
    }

    @AfterEach
    void tearDown() {
        if (emitter != null) {
            emitter.shutdown();
        }
    }

    private SpanInfo span(String traceId, String spanId, String parentSpanId, Map<String, Object> attributes) {
        return new SpanInfo(traceId, spanId, parentSpanId, "agent_run", 100, attributes, "OK", null,
                System.currentTimeMillis() - 100);
    }

    private SpanInfo errorSpan(String traceId, String spanId, String parentSpanId) {
        return new SpanInfo(traceId, spanId, parentSpanId, "tool_call", 100, null, "ERROR", "boom",
                System.currentTimeMillis() - 100);
    }

    @Test
    void 采样率1时全部Span落库且字段映射正确() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("taskId", "task-1");
        attributes.put("agentCode", "default");
        attributes.put("sessionId", "session-1");
        attributes.put("scopeId", "scope-1");

        emitter.onSpan(span("t1", "s1", null, attributes));

        ArgumentCaptor<List<TraceSpanEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(traceSpanRepository, timeout(3000)).batchSave(captor.capture());
        List<TraceSpanEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        TraceSpanEntity entity = saved.get(0);
        assertThat(entity.getTraceId()).isEqualTo("t1");
        assertThat(entity.getSpanId()).isEqualTo("s1");
        assertThat(entity.getParentSpanId()).isNull();
        assertThat(entity.getOperation()).isEqualTo("agent_run");
        assertThat(entity.getStatus()).isEqualTo("OK");
        assertThat(entity.getTaskId()).isEqualTo("task-1");
        assertThat(entity.getAgentCode()).isEqualTo("default");
        assertThat(entity.getSessionId()).isEqualTo("session-1");
        assertThat(entity.getScopeId()).isEqualTo("scope-1");
        assertThat(entity.getDurationMs()).isEqualTo(100L);
        assertThat(entity.getStartTime()).isNotNull();
        assertThat(entity.getAttributes()).contains("taskId");
    }

    @Test
    void 采样率0时不落库() {
        properties.setSampleRate(0.0);
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        for (int i = 0; i < 10; i++) {
            emitter.onSpan(span("t1", "s" + i, null, null));
        }

        verify(traceSpanRepository, after(500).never()).batchSave(anyList());
    }

    @Test
    void 全量模式忽略采样率全部落库() throws Exception {
        properties.setSampleMode(TraceSampleMode.ALL);
        properties.setSampleRate(0.0);
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            List<TraceSpanEntity> batch = invocation.getArgument(0);
            batchSizes.add(batch.size());
            return null;
        }).when(traceSpanRepository).batchSave(anyList());
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        for (int i = 0; i < 10; i++) {
            emitter.onSpan(span("t" + i, "s" + i, null, null));
        }

        long deadline = System.currentTimeMillis() + 3000;
        while (batchSizes.stream().mapToInt(Integer::intValue).sum() < 10
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        int total = batchSizes.stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(10);
    }

    @Test
    void 失败必采覆盖未采样决策() {
        properties.setSampleRate(0.0);
        properties.setAlwaysKeepErrors(true);
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        emitter.onSpan(errorSpan("t1", "s1", null));
        emitter.onSpan(span("t2", "s2", null, null));

        ArgumentCaptor<List<TraceSpanEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(traceSpanRepository, timeout(3000)).batchSave(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getStatus()).isEqualTo("ERROR");
    }

    @Test
    void 失败必采覆盖同trace多条错误Span() throws Exception {
        properties.setSampleRate(0.0);
        properties.setAlwaysKeepErrors(true);
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            List<TraceSpanEntity> batch = invocation.getArgument(0);
            batchSizes.add(batch.size());
            return null;
        }).when(traceSpanRepository).batchSave(anyList());
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        for (int i = 0; i < 3; i++) {
            emitter.onSpan(errorSpan("t1", "s" + i, "root"));
        }

        long deadline = System.currentTimeMillis() + 3000;
        while (batchSizes.stream().mapToInt(Integer::intValue).sum() < 3
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        int total = batchSizes.stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(3);
    }

    @Test
    void 失败必采关闭时未采样错误Span不落库() {
        properties.setSampleRate(0.0);
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        emitter.onSpan(errorSpan("t1", "s1", null));

        verify(traceSpanRepository, after(500).never()).batchSave(anyList());
    }

    @Test
    void 同一trace共享采样决策() throws Exception {
        properties.setSampleRate(0.5);
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        lenient().doAnswer(invocation -> {
            List<TraceSpanEntity> batch = invocation.getArgument(0);
            batchSizes.add(batch.size());
            return null;
        }).when(traceSpanRepository).batchSave(anyList());
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        for (int i = 0; i < 200; i++) {
            emitter.onSpan(span("t-same", "s" + i, "root", null));
        }

        // 采样命中时200条分一至两批全部落库，未命中时保持0，轮询到终态后断言全有或全无
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            int total = batchSizes.stream().mapToInt(Integer::intValue).sum();
            if (total == 200) {
                break;
            }
            Thread.sleep(50);
        }
        int total = batchSizes.stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isIn(0, 200);
    }

    @Test
    void 超长错误信息被截断到1024() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);
        String longError = "e".repeat(2000);

        emitter.onSpan(new SpanInfo("t1", "s1", null, "agent_run", 100,
                null, "ERROR", longError, System.currentTimeMillis() - 100));

        ArgumentCaptor<List<TraceSpanEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(traceSpanRepository, timeout(3000)).batchSave(captor.capture());
        assertThat(captor.getValue().get(0).getErrorMessage()).hasSize(1024);
    }

    @Test
    void 超大属性被截断并打标() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("big", "x".repeat(20000));

        emitter.onSpan(span("t1", "s1", null, attributes));

        ArgumentCaptor<List<TraceSpanEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(traceSpanRepository, timeout(3000)).batchSave(captor.capture());
        String savedAttributes = captor.getValue().get(0).getAttributes();
        assertThat(savedAttributes.length()).isLessThanOrEqualTo(8 * 1024);
    }

    @Test
    void 空属性时实体属性字段为空() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        emitter.onSpan(span("t1", "s1", null, null));

        ArgumentCaptor<List<TraceSpanEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(traceSpanRepository, timeout(3000)).batchSave(captor.capture());
        TraceSpanEntity entity = captor.getValue().get(0);
        assertThat(entity.getAttributes()).isNull();
        assertThat(entity.getTaskId()).isNull();
        assertThat(entity.getAgentCode()).isNull();
    }

    @Test
    void 落库异常不阻塞调用方() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);
        doThrow(new RuntimeException("db down")).when(traceSpanRepository).batchSave(anyList());

        assertThatCode(() -> emitter.onSpan(span("t1", "s1", null, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> emitter.onSpan(span("t2", "s2", null, null)))
                .doesNotThrowAnyException();
        // 落库在后台线程异步执行，等待flush消费stub后主线程再做strictness校验
        verify(traceSpanRepository, timeout(3000).atLeastOnce()).batchSave(anyList());
    }

    @Test
    void 队列满时丢弃且不阻塞() {
        properties.setQueueCapacity(1);
        properties.setFlushIntervalMs(60_000);
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                emitter.onSpan(span("t1", "s" + i, null, null));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void 停止时刷新队列中剩余Span() {
        properties.setFlushIntervalMs(60_000);
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        emitter.onSpan(span("t1", "s1", null, null));
        emitter.shutdown();

        ArgumentCaptor<List<TraceSpanEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(traceSpanRepository, timeout(3000)).batchSave(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        emitter = null;
    }

    @Test
    void nullSpan安全忽略() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);

        assertThatCode(() -> emitter.onSpan(null)).doesNotThrowAnyException();
        verify(traceSpanRepository, after(300).never()).batchSave(anyList());
    }

    @Test
    void 关闭后再次关闭安全() {
        emitter = new PlatformTraceEmitter(traceSpanRepository, properties);
        emitter.shutdown();
        emitter.shutdown();
        emitter = null;
    }
}
