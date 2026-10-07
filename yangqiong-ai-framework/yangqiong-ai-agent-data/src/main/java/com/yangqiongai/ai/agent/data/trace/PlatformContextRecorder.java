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
import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;
import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.agent.runtime.trace.ContextMessage;
import com.yangqiongai.ai.agent.runtime.trace.ContextSnapshot;
import com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 平台上下文快照批量落库记录器
 * <p>
 * 执行线程仅入有界队列，后台单线程批量落库，落库异常只记日志绝不阻塞主流程。
 * 单条内容超阈值截断、队列满丢弃计数。
 * </p>
 * @author yangqiong
 */
public class PlatformContextRecorder implements ContextSnapshotListener {

    private static final Logger log = LoggerFactory.getLogger(PlatformContextRecorder.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ContextSnapshotRepository contextSnapshotRepository;

    private final AgentContextSnapshotProperties properties;

    private final ArrayBlockingQueue<ContextSnapshot> queue;

    private final LongAdder droppedCount = new LongAdder();

    /**
     * 丢弃告警节流标记（每分钟最多WARN一次）
     */
    private final AtomicLong lastDropWarnMs = new AtomicLong();

    private final Thread flushThread;

    private volatile boolean running = true;

    /**
     * 构造批量落库记录器
     * @param contextSnapshotRepository 快照存储
     * @param properties 快照配置
     */
    public PlatformContextRecorder(ContextSnapshotRepository contextSnapshotRepository,
                                   AgentContextSnapshotProperties properties) {
        this.contextSnapshotRepository = contextSnapshotRepository;
        this.properties = properties;
        this.queue = new ArrayBlockingQueue<>(Math.max(100, properties.getMaxQueueSize()));
        this.flushThread = new Thread(this::flushLoop, "platform-context-recorder");
        this.flushThread.setDaemon(true);
        this.flushThread.start();
    }

    @Override
    public void onSnapshot(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (!queue.offer(snapshot)) {
            droppedCount.increment();
            long now = System.currentTimeMillis();
            long last = lastDropWarnMs.get();
            if (now - last > 60_000 && lastDropWarnMs.compareAndSet(last, now)) {
                log.warn("[PlatformContextRecorder] 队列已满，快照丢弃累计: {}", droppedCount.sum());
            }
        }
    }

    /**
     * 后台刷新循环：批量条数或刷新间隔触发落库
     */
    private void flushLoop() {
        List<ContextSnapshot> buffer = new ArrayList<>(properties.getBatchSize());
        while (running || !queue.isEmpty()) {
            try {
                buffer.clear();
                ContextSnapshot first = queue.poll(properties.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
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
                log.warn("[PlatformContextRecorder] 快照批量落库失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 批量落库一批快照
     * @param batch
     */
    private void saveBatch(List<ContextSnapshot> batch) {
        List<ContextSnapshotEntity> entities = new ArrayList<>(batch.size());
        for (ContextSnapshot snapshot : batch) {
            try {
                entities.add(toEntity(snapshot));
            } catch (Exception e) {
                log.warn("[PlatformContextRecorder] 快照转换失败: taskId={}, callSeq={}",
                        snapshot.getTaskId(), snapshot.getCallSeq());
            }
        }
        if (!entities.isEmpty()) {
            contextSnapshotRepository.batchSave(entities);
        }
    }

    /**
     * 快照转持久化实体（消息序列化+统计冗余）
     * @param snapshot
     * @return
     */
    private ContextSnapshotEntity toEntity(ContextSnapshot snapshot) {
        ContextSnapshotEntity entity = new ContextSnapshotEntity();
        entity.setTaskId(snapshot.getTaskId());
        entity.setTraceId(snapshot.getTraceId());
        entity.setSessionId(snapshot.getSessionId());
        entity.setAgentCode(snapshot.getAgentCode());
        entity.setScopeId(snapshot.getScopeId());
        entity.setModelCode(snapshot.getModelCode());
        entity.setCallSeq(snapshot.getCallSeq());
        entity.setSnapshotJson(serializeMessages(snapshot.getMessages()));
        entity.setMsgCount(snapshot.getMessages() != null ? snapshot.getMessages().size() : 0);
        entity.setTotalChars(totalChars(snapshot.getMessages()));
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 序列化消息列表为JSON数组
     * @param messages
     * @return
     */
    private String serializeMessages(List<ContextMessage> messages) {
        try {
            return OBJECT_MAPPER.writeValueAsString(messages != null ? messages : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 统计快照消息总字符数
     * @param messages
     * @return
     */
    private long totalChars(List<ContextMessage> messages) {
        if (messages == null) {
            return 0;
        }
        long total = 0;
        for (ContextMessage message : messages) {
            if (message.getContent() != null) {
                total += message.getContent().length();
            }
        }
        return total;
    }

    /**
     * 读取队列满丢弃累计数
     * @return
     */
    long droppedCount() {
        return droppedCount.sum();
    }

    /**
     * 停止时刷新剩余快照
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        flushThread.interrupt();
        List<ContextSnapshot> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                saveBatch(remaining);
            } catch (Exception e) {
                log.warn("[PlatformContextRecorder] 停止时刷新失败: {}", e.getMessage());
            }
        }
    }
}
