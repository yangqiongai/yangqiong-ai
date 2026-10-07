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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent任务入队器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentTaskEnqueuerTest {

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private BackpressureGuard backpressureGuard;

    private QueueProperties properties;

    private AgentTaskEnqueuer enqueuer;

    @BeforeEach
    void setUp() throws Exception {
        properties = new QueueProperties();
        enqueuer = new AgentTaskEnqueuer();
        inject("agentTaskRepository", agentTaskRepository);
        inject("backpressureGuard", backpressureGuard);
        inject("properties", properties);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = AgentTaskEnqueuer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(enqueuer, value);
    }

    private AgentRequest newRequest() {
        return new AgentRequest().agentCode("agent-a").sessionId("sess-1")
                .userId("user-1").scopeId("scope-1").input("你好");
    }

    @Test
    void 入队写QUEUED记录并携带优先级与入队时间() {
        when(backpressureGuard.check(any(), any(), any(Integer.class)))
                .thenReturn(new BackpressureGuard.CheckResult(7));

        String taskId = enqueuer.enqueue(newRequest());

        ArgumentCaptor<AgentTaskInfo> captor = ArgumentCaptor.forClass(AgentTaskInfo.class);
        verify(agentTaskRepository).createTask(captor.capture());
        AgentTaskInfo info = captor.getValue();
        assertThat(taskId).isEqualTo(info.getTaskId());
        assertThat(info.getTaskStatus()).isEqualTo("QUEUED");
        assertThat(info.getPriority()).isEqualTo(7);
        assertThat(info.getQueuedTime()).isNotNull();
        assertThat(info.getTaskSource()).isEqualTo("ASYNC");
        assertThat(info.getAgentCode()).isEqualTo("agent-a");
    }

    @Test
    void 入队序列化完整请求支持往返还原() {
        when(backpressureGuard.check(any(), any(), any(Integer.class)))
                .thenReturn(new BackpressureGuard.CheckResult(5));

        enqueuer.enqueue(newRequest());

        ArgumentCaptor<AgentTaskInfo> captor = ArgumentCaptor.forClass(AgentTaskInfo.class);
        verify(agentTaskRepository).createTask(captor.capture());
        AgentRequest restored = enqueuer.deserializeRequest(captor.getValue());
        assertThat(restored).isNotNull();
        assertThat(restored.getAgentCode()).isEqualTo("agent-a");
        assertThat(restored.getSessionId()).isEqualTo("sess-1");
        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getScopeId()).isEqualTo("scope-1");
        assertThat(restored.getInputAsText()).isEqualTo("你好");
    }

    @Test
    void 还原请求补全任务记录中的缺失字段() {
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId("task-1");
        info.setAgentCode("agent-from-task");
        info.setSessionId("sess-from-task");
        info.setUserId("user-from-task");
        info.setBody(new ObjectMapper().valueToTree(java.util.Map.of("agentCode", "")).toString());

        AgentRequest restored = enqueuer.deserializeRequest(info);

        assertThat(restored).isNotNull();
        assertThat(restored.getAgentCode()).isEqualTo("agent-from-task");
        assertThat(restored.getSessionId()).isEqualTo("sess-from-task");
        assertThat(restored.getUserId()).isEqualTo("user-from-task");
    }

    @Test
    void 请求体缺失时还原返回null() {
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId("task-1");

        assertThat(enqueuer.deserializeRequest(info)).isNull();
    }

    @Test
    void 请求体非法JSON时还原返回null() {
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId("task-1");
        info.setBody("not-json");

        assertThat(enqueuer.deserializeRequest(info)).isNull();
    }

    @Test
    void 背压拒绝时异常向上传播不入队() {
        when(backpressureGuard.check(any(), any(), any(Integer.class)))
                .thenThrow(new AiException(com.yangqiongai.ai.common.exception.AiErrorCode.AGENT_QUOTA_EXCEEDED, "队列已满"));

        assertThatThrownBy(() -> enqueuer.enqueue(newRequest()))
                .isInstanceOf(AiException.class);
        verify(agentTaskRepository, org.mockito.Mockito.never()).createTask(any());
    }
}
