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
package com.yangqiongai.ai.agent.core.task;

import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * AgentTaskTracker单元测试
 */
class AgentTaskTrackerTest {

    private AgentTaskTracker tracker;

    private TaskStore taskStore;

    private final CountDownLatch runnerLatch = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        taskStore = Mockito.mock(TaskStore.class);
        tracker = new AgentTaskTracker();
        ReflectionTestUtils.setField(tracker, "persistenceService", taskStore);
    }

    @AfterEach
    void tearDown() {
        // 释放阻塞的异步任务，避免线程泄漏
        runnerLatch.countDown();
    }

    private AgentRequest buildRequest(String agentCode) {
        return new AgentRequest()
                .agentCode(agentCode)
                .sessionId("session-1")
                .input("hello")
                .userId("user-1");
    }

    /**
     * 返回阻塞的runner，使异步任务停在执行点，便于测试显式操作任务状态
     */
    private Callable<AgentResult> blockingRunner() {
        return () -> {
            runnerLatch.await(5, TimeUnit.SECONDS);
            return AgentResult.success("ok");
        };
    }

    /**
     * 等待异步任务进入RUNNING（异步线程先执行markRunning("submitted")再阻塞在runner上）
     */
    private void awaitRunning(String taskId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            AgentTaskRecord record = tracker.query(taskId);
            if (record != null && record.getStatus() == AgentTaskRecord.TaskStatus.RUNNING) {
                return;
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("任务未进入RUNNING状态: " + taskId);
    }

    @Test
    @DisplayName("submit()创建PENDING状态的任务")
    void submit_createsPendingTask() {
        AgentRequest request = buildRequest("chat");

        AgentTaskRecord record = tracker.submit("task-1", request, blockingRunner());

        assertThat(record).isNotNull();
        assertThat(record.getTaskId()).isEqualTo("task-1");
        assertThat(record.getAgentCode()).isEqualTo("chat");
        assertThat(record.getSessionId()).isEqualTo("session-1");
        assertThat(record.getStartedAt()).isNotNull();
        // 任务创建时以PENDING写入DB
        ArgumentCaptor<AgentTaskInfo> captor = ArgumentCaptor.forClass(AgentTaskInfo.class);
        verify(taskStore).createTask(captor.capture());
        assertThat(captor.getValue().getTaskStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("query()返回已提交的任务")
    void query_returnsSubmittedTask() {
        AgentRequest request = buildRequest("chat");
        tracker.submit("task-2", request, blockingRunner());

        AgentTaskRecord record = tracker.query("task-2");

        assertThat(record).isNotNull();
        assertThat(record.getTaskId()).isEqualTo("task-2");
    }

    @Test
    @DisplayName("query()未知taskId返回null")
    void query_unknownTaskId_returnsNull() {
        AgentTaskRecord record = tracker.query("non-existent");

        assertThat(record).isNull();
    }

    @Test
    @DisplayName("markRunning()更新状态为RUNNING")
    void markRunning_updatesStatus() throws InterruptedException {
        AgentRequest request = buildRequest("chat");
        tracker.submit("task-3", request, blockingRunner());
        awaitRunning("task-3");

        tracker.markRunning("task-3", "executing");

        AgentTaskRecord record = tracker.query("task-3");
        assertThat(record.getStatus()).isEqualTo(AgentTaskRecord.TaskStatus.RUNNING);
        assertThat(record.getCurrentStep()).isEqualTo("executing");
    }

    @Test
    @DisplayName("markCompleted()更新状态为SUCCEEDED")
    void markCompleted_updatesStatus() throws InterruptedException {
        AgentRequest request = buildRequest("chat");
        tracker.submit("task-4", request, blockingRunner());
        awaitRunning("task-4");

        AgentResult result = AgentResult.success("done");
        tracker.markCompleted("task-4", result);

        AgentTaskRecord record = tracker.query("task-4");
        assertThat(record.getStatus()).isEqualTo(AgentTaskRecord.TaskStatus.SUCCEEDED);
        assertThat(record.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed()更新状态为FAILED并设置错误信息")
    void markFailed_updatesStatusWithErrorMessage() throws InterruptedException {
        AgentRequest request = buildRequest("chat");
        tracker.submit("task-5", request, blockingRunner());
        awaitRunning("task-5");

        tracker.markFailed("task-5", "execution", "something went wrong");

        AgentTaskRecord record = tracker.query("task-5");
        assertThat(record.getStatus()).isEqualTo(AgentTaskRecord.TaskStatus.FAILED);
        assertThat(record.getErrorMessage()).isEqualTo("something went wrong");
        assertThat(record.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markRunning()对不存在的taskId无影响")
    void markRunning_nonExistentTask_noEffect() {
        // 不应抛出异常
        tracker.markRunning("non-existent", "step");
    }
}
