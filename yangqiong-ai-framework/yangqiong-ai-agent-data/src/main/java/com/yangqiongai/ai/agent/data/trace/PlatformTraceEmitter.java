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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import com.yangqiongai.ai.agent.runtime.trace.SpanInfo;
import com.yangqiongai.ai.agent.runtime.trace.TraceEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 平台Span批量落库导出器
 * <p>
 * 执行线程仅入有界队列，后台单线程批量落库，落库异常只记日志绝不阻塞主流程。
 * 支持采样（根Span决定整条trace）、属性截断、队列满丢弃计数。
 * </p>
 * @author yangqiong
 */
public class PlatformTraceEmitter implements TraceEmitter {

    private static final Logger log = LoggerFactory.getLogger(PlatformTraceEmitter.class);

    /**
     * attributes序列化上限（字节），超出截断
     */
    private static final int ATTRIBUTES_MAX_BYTES = 8 * 1024;

    /**
     * 错误信息上限（字符）
     */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1024;

    /**
     * 截断标记属性键
     */
    private static final String TRUNCATED_FLAG = "trace.truncated";

    /**
     * 失败Span状态标识
     */
    private static final String STATUS_ERROR = "ERROR";

    /**
     * 采样决策缓存上限，超出清空重建
     */
    private static final int SAMPLE_CACHE_MAX = 50000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TraceSpanRepository traceSpanRepository;

    private final AgentTraceProperties properties;

    private final ArrayBlockingQueue<SpanInfo> queue;

    /**
     * 采样决策缓存（traceId → 是否采集），避免同trace的子Span重复掷骰
     */
    private final ConcurrentHashMap<String, Boolean> sampleDecisions = new ConcurrentHashMap<>();

    private final LongAdder droppedCount = new LongAdder();

    /**
     * 丢弃告警节流标记（每分钟最多WARN一次）
     */
    private final AtomicLong lastDropWarnMs = new AtomicLong();

    private final Thread flushThread;

    private volatile boolean running = true;

    /**
     * 构造批量落库导出器
     * @param traceSpanRepository Span存储
     * @param properties Trace配置
     */
    public PlatformTraceEmitter(TraceSpanRepository traceSpanRepository, AgentTraceProperties properties) {
        this.traceSpanRepository = traceSpanRepository;
        this.properties = properties;
        this.queue = new ArrayBlockingQueue<>(Math.max(100, properties.getQueueCapacity()));
        this.flushThread = new Thread(this::flushLoop, "platform-trace-emitter");
        this.flushThread.setDaemon(true);
        this.flushThread.start();
    }

    @Override
    public void onSpan(SpanInfo spanInfo) {
        if (spanInfo == null) {
            return;
        }
        if (!shouldSample(spanInfo) && !isErrorSpanForced(spanInfo)) {
            return;
        }
        if (!queue.offer(spanInfo)) {
            droppedCount.increment();
            long now = System.currentTimeMillis();
            long last = lastDropWarnMs.get();
            if (now - last > 60_000 && lastDropWarnMs.compareAndSet(last, now)) {
                log.warn("[PlatformTraceEmitter] 队列已满，Span丢弃累计: {}", droppedCount.sum());
            }
        }
    }

    /**
     * 采样决策：全量模式直接采集；比例模式下根Span掷骰缓存结果，子Span沿用同trace决策
     * @param spanInfo
     * @return
     */
    private boolean shouldSample(SpanInfo spanInfo) {
        if (properties.getSampleMode() == TraceSampleMode.ALL) {
            return true;
        }
        double rate = properties.getSampleRate();
        if (rate >= 1.0) {
            return true;
        }
        if (rate <= 0) {
            return false;
        }
        String traceId = spanInfo.getTraceId();
        if (traceId == null) {
            return ThreadLocalRandom.current().nextDouble() < rate;
        }
        if (sampleDecisions.size() > SAMPLE_CACHE_MAX) {
            sampleDecisions.clear();
        }
        return sampleDecisions.computeIfAbsent(traceId, id -> ThreadLocalRandom.current().nextDouble() < rate);
    }

    /**
     * 失败Span必采判定（覆盖采样决策，比例采样下保障故障可复盘）
     * @param spanInfo
     * @return
     */
    private boolean isErrorSpanForced(SpanInfo spanInfo) {
        return properties.isAlwaysKeepErrors() && STATUS_ERROR.equals(spanInfo.getStatus());
    }

    /**
     * 后台刷新循环：批量条数或刷新间隔触发落库
     */
    private void flushLoop() {
        List<SpanInfo> buffer = new ArrayList<>(properties.getBatchSize());
        while (running || !queue.isEmpty()) {
            try {
                buffer.clear();
                SpanInfo first = queue.poll(properties.getFlushIntervalMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                buffer.add(first);
                queue.drainTo(buffer, properties.getBatchSize() - 1);
                saveBatch(buffer);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 刷新失败仅记WARN丢弃本批，绝不阻塞
                log.warn("[PlatformTraceEmitter] Span批量落库失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 批量落库一批Span
     * @param batch
     */
    private void saveBatch(List<SpanInfo> batch) {
        List<TraceSpanEntity> entities = new ArrayList<>(batch.size());
        for (SpanInfo span : batch) {
            try {
                entities.add(toEntity(span));
            } catch (Exception e) {
                log.warn("[PlatformTraceEmitter] Span转换失败: traceId={}, spanId={}",
                        span.getTraceId(), span.getSpanId());
            }
        }
        if (!entities.isEmpty()) {
            traceSpanRepository.batchSave(entities);
        }
    }

    /**
     * SpanInfo转持久化实体（冗余字段提取+截断）
     * @param spanInfo
     * @return
     */
    private TraceSpanEntity toEntity(SpanInfo spanInfo) {
        TraceSpanEntity entity = new TraceSpanEntity();
        entity.setTraceId(spanInfo.getTraceId());
        entity.setSpanId(spanInfo.getSpanId());
        entity.setParentSpanId(spanInfo.getParentSpanId());
        entity.setOperation(spanInfo.getOperation());
        entity.setStatus(spanInfo.getStatus());
        entity.setErrorMessage(truncate(spanInfo.getErrorMessage(), ERROR_MESSAGE_MAX_LENGTH));
        entity.setDurationMs(spanInfo.getDurationMs());
        entity.setStartTime(toLocalDateTime(spanInfo.getStartTimeMs(), spanInfo.getDurationMs()));

        Map<String, Object> attributes = spanInfo.getAttributes();
        if (attributes != null && !attributes.isEmpty()) {
            entity.setTaskId(asString(attributes.get("taskId")));
            entity.setAgentCode(asString(attributes.get("agentCode")));
            entity.setSessionId(asString(attributes.get("sessionId")));
            entity.setScopeId(asString(attributes.get("scopeId")));
            entity.setAttributes(serializeAttributes(attributes));
        }
        return entity;
    }

    /**
     * 序列化属性，超限截断并打标
     * @param attributes
     * @return
     */
    private String serializeAttributes(Map<String, Object> attributes) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(attributes);
            if (json.length() <= ATTRIBUTES_MAX_BYTES) {
                return json;
            }
            Map<String, Object> marked = new java.util.LinkedHashMap<>(attributes);
            marked.put(TRUNCATED_FLAG, true);
            String markedJson = OBJECT_MAPPER.writeValueAsString(marked);
            return markedJson.length() <= ATTRIBUTES_MAX_BYTES
                    ? markedJson : markedJson.substring(0, ATTRIBUTES_MAX_BYTES);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * epoch毫秒转LocalDateTime，未知时按结束时间回推
     * @param startTimeMs
     * @param durationMs
     * @return
     */
    private LocalDateTime toLocalDateTime(long startTimeMs, long durationMs) {
        long millis = startTimeMs > 0 ? startTimeMs : System.currentTimeMillis() - durationMs;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /**
     * 属性值转字符串
     * @param value
     * @return
     */
    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 字符串截断
     * @param value
     * @param maxLength
     * @return
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 停止时刷新剩余Span
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        flushThread.interrupt();
        List<SpanInfo> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                saveBatch(remaining);
            } catch (Exception e) {
                log.warn("[PlatformTraceEmitter] 停止时刷新失败: {}", e.getMessage());
            }
        }
        sampleDecisions.clear();
    }
}
