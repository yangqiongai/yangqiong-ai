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
package com.yangqiongai.ai.platform.api.scheduling;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent任务队列调度器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTaskQueueSchedulerTest {

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private AgentTaskEnqueuer agentTaskEnqueuer;

    @Mock
    private AgentEngine agentEngine;

    private QueueProperties properties;

    private AgentTaskQueueScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        properties = new QueueProperties();
        properties.setEnabled(true);
        properties.setBatchSize(5);
        properties.setReclaimExpireSeconds(90);
        properties.setMaxRedeliver(3);

        scheduler = new AgentTaskQueueScheduler();
        inject("agentTaskRepository", agentTaskRepository);
        inject("agentTaskEnqueuer", agentTaskEnqueuer);
        inject("agentEngine", agentEngine);
        inject("properties", properties);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = AgentTaskQueueScheduler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(scheduler, value);
    }

    private AgentTaskInfo claimedTask(String taskId) {
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId(taskId);
        info.setAgentCode("agent-a");
        info.setSessionId("sess-1");
        info.setUserId("user-1");
        info.setTaskStatus("RUNNING");
        try {
            info.setBody(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(new AgentRequest().agentCode("agent-a").sessionId("sess-1")
                            .userId("user-1").scopeId("scope-1").input("你好")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return info;
    }

    @Test
    void 轮询先回收再抢占并分发执行() {
        when(agentTaskRepository.reclaimExpired(any(), anyInt(), anyInt())).thenReturn(1);
        when(agentTaskRepository.claimQueued(anyString(), anyInt()))
                .thenReturn(List.of(claimedTask("task-1")));
        when(agentTaskEnqueuer.deserializeRequest(any(AgentTaskInfo.class)))
                .thenReturn(new AgentRequest().agentCode("agent-a").scopeId("scope-1").input("你好"));
        when(agentEngine.executeClaimed(anyString(), any(AgentRequest.class))).thenReturn("task-1");

        scheduler.poll();

        verify(agentTaskRepository).reclaimExpired(any(), eq(3), eq(5));
        verify(agentTaskRepository).claimQueued(anyString(), eq(5));
        verify(agentEngine).executeClaimed(eq("task-1"), any(AgentRequest.class));
    }

    @Test
    void 请求还原失败时任务标记FAILED() {
        when(agentTaskRepository.claimQueued(anyString(), anyInt()))
                .thenReturn(List.of(claimedTask("task-bad")));
        when(agentTaskEnqueuer.deserializeRequest(any(AgentTaskInfo.class))).thenReturn(null);

        scheduler.poll();

        verify(agentEngine, never()).executeClaimed(anyString(), any(AgentRequest.class));
        verify(agentTaskRepository).markFailed(eq("task-bad"), anyString(), eq(0L));
    }

    @Test
    void 分发异常时任务标记FAILED不中断轮询() {
        when(agentTaskRepository.claimQueued(anyString(), anyInt()))
                .thenReturn(List.of(claimedTask("task-err")));
        when(agentTaskEnqueuer.deserializeRequest(any(AgentTaskInfo.class)))
                .thenReturn(new AgentRequest().agentCode("agent-a"));
        when(agentEngine.executeClaimed(anyString(), any(AgentRequest.class)))
                .thenThrow(new RuntimeException("engine down"));

        scheduler.poll();

        verify(agentTaskRepository).markFailed(eq("task-err"), anyString(), eq(0L));
    }

    @Test
    void 心跳续约按实例标识刷新() {
        scheduler.heartbeat();

        verify(agentTaskRepository).heartbeat(scheduler.getRunnerId());
    }

    @Test
    void 回收异常不阻断抢占调度() {
        when(agentTaskRepository.reclaimExpired(any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        when(agentTaskRepository.claimQueued(anyString(), anyInt())).thenReturn(List.of());

        scheduler.poll();

        verify(agentTaskRepository).claimQueued(anyString(), eq(5));
    }

    @Test
    void 实例标识非空且含主机信息() {
        assertThat(scheduler.getRunnerId()).isNotBlank();
        assertThat(scheduler.getRunnerId()).contains(":");
    }
}
