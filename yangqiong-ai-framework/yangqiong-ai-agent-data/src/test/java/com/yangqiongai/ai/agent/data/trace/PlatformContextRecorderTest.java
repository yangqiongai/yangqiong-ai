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

import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;
import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.agent.runtime.trace.ContextMessage;
import com.yangqiongai.ai.agent.runtime.trace.ContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * 平台上下文快照落库记录器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class PlatformContextRecorderTest {

    @Mock
    private ContextSnapshotRepository repository;

    private PlatformContextRecorder recorder;

    @AfterEach
    void tearDown() {
        if (recorder != null) {
            recorder.shutdown();
        }
    }

    /**
     * 构造快照
     * @param taskId
     * @param callSeq
     * @return
     */
    private ContextSnapshot snapshot(String taskId, int callSeq) {
        return new ContextSnapshot(taskId, "trace-1", "session-1", "code-assist", "default", "model-a",
                callSeq, List.of(
                new ContextMessage("SYSTEM", "系统提示", "system_prompt", false),
                new ContextMessage("USER", "你好", "history", false)));
    }

    /**
     * 收集全部批量落库实体
     * @return
     */
    private List<ContextSnapshotEntity> collectSaved() {
        List<ContextSnapshotEntity> saved = new ArrayList<>();
        doAnswer(invocation -> {
            saved.addAll(invocation.getArgument(0));
            return null;
        }).when(repository).batchSave(anyList());
        return saved;
    }

    /**
     * 轮询等待条件成立(超时3秒抛出断言异常)
     * @param condition
     */
    private void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                assertThat(condition.getAsBoolean()).as("等待落库超时").isTrue();
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void 快照字段映射与序列化正确() {
        List<ContextSnapshotEntity> saved = collectSaved();
        recorder = new PlatformContextRecorder(repository, props(200, 10000, 50));

        recorder.onSnapshot(snapshot("task-1", 3));

        await(() -> !saved.isEmpty());
        ContextSnapshotEntity entity = saved.get(0);
        assertThat(entity.getTaskId()).isEqualTo("task-1");
        assertThat(entity.getTraceId()).isEqualTo("trace-1");
        assertThat(entity.getSessionId()).isEqualTo("session-1");
        assertThat(entity.getAgentCode()).isEqualTo("code-assist");
        assertThat(entity.getScopeId()).isEqualTo("default");
        assertThat(entity.getModelCode()).isEqualTo("model-a");
        assertThat(entity.getCallSeq()).isEqualTo(3);
        assertThat(entity.getMsgCount()).isEqualTo(2);
        assertThat(entity.getTotalChars()).isEqualTo(6);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getSnapshotJson()).contains("\"source\":\"system_prompt\"");
        assertThat(entity.getSnapshotJson()).contains("\"role\":\"USER\"");
    }

    @Test
    void 落库异常不影响后续批次() {
        List<ContextSnapshotEntity> saved = new ArrayList<>();
        // 第一次批量落库抛异常，后续正常入库
        doThrow(new RuntimeException("db down"))
                .doAnswer(invocation -> {
                    saved.addAll(invocation.getArgument(0));
                    return null;
                })
                .when(repository).batchSave(anyList());
        recorder = new PlatformContextRecorder(repository, props(1, 10000, 20));

        assertThatCode(() -> {
            recorder.onSnapshot(snapshot("task-1", 1));
            recorder.onSnapshot(snapshot("task-1", 2));
        }).doesNotThrowAnyException();

        await(() -> !saved.isEmpty());
        // 失败批次被丢弃，后续批次正常入库
        assertThat(saved).extracting(ContextSnapshotEntity::getCallSeq).containsExactly(2);
    }

    @Test
    void 队列满丢弃并计数() throws Exception {
        List<ContextSnapshotEntity> saved = new ArrayList<>();
        CountDownLatch firstBatchBlocked = new CountDownLatch(1);
        CountDownLatch releaseFirstBatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstBatchBlocked.countDown();
            org.assertj.core.api.Assertions.assertThat(
                    releaseFirstBatch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            saved.addAll(invocation.getArgument(0));
            return null;
        }).when(repository).batchSave(anyList());

        // 队列容量100（容量下限），flushInterval设大防止线程竞争消费
        recorder = new PlatformContextRecorder(repository, props(1, 100, 60000));
        recorder.onSnapshot(snapshot("task-1", 0));
        org.assertj.core.api.Assertions.assertThat(firstBatchBlocked.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("首个批次应进入落库阻塞").isTrue();

        // 阻塞落库期间灌满队列再溢出，溢出的5条应被丢弃
        for (int i = 1; i <= 105; i++) {
            recorder.onSnapshot(snapshot("task-1", i));
        }
        assertThat(recorder.droppedCount()).isEqualTo(5);

        releaseFirstBatch.countDown();
        await(() -> saved.size() == 101);
    }

    /**
     * 构造快照配置
     * @param batchSize
     * @param maxQueueSize
     * @param flushIntervalMs
     * @return
     */
    private AgentContextSnapshotProperties props(int batchSize, int maxQueueSize, long flushIntervalMs) {
        AgentContextSnapshotProperties properties = new AgentContextSnapshotProperties();
        properties.setBatchSize(batchSize);
        properties.setMaxQueueSize(maxQueueSize);
        properties.setFlushIntervalMs(flushIntervalMs);
        return properties;
    }
}
