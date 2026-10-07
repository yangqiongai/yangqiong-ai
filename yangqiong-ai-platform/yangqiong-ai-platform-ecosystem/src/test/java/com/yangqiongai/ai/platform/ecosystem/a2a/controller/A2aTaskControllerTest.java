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
package com.yangqiongai.ai.platform.ecosystem.a2a.controller;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aPushConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A2A任务端点
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class A2aTaskControllerTest {

    @Mock
    private AgentTaskEnqueuer taskEnqueuer;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private A2aPushConfigService pushConfigService;

    @Mock
    private AgentEngine agentEngine;

    private QueueProperties queueProperties;

    private A2aTaskController controller;

    @BeforeEach
    void setUp() {
        queueProperties = new QueueProperties();
        controller = new A2aTaskController(taskEnqueuer, agentTaskRepository,
                pushConfigService, queueProperties, agentEngine);
    }

    private Map<String, Object> messageBody(String text) {
        return Map.of(
                "message", Map.of("role", "user",
                        "parts", List.of(Map.of("kind", "text", "text", text))),
                "metadata", Map.of("agentCode", "support-agent", "userId", "tester"));
    }

    private AgentTaskInfo taskInfo(String taskId, String status) {
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId(taskId);
        info.setAgentCode("support-agent");
        info.setTaskStatus(status);
        return info;
    }

    @Test
    @DisplayName("队列模式开启时走入队器")
    void sendMessageQueueModeEnqueues() {
        queueProperties.setEnabled(true);
        queueProperties.setMode("queue");
        when(taskEnqueuer.enqueue(any())).thenReturn("task-queue-1");
        when(agentTaskRepository.queryTask("task-queue-1")).thenReturn(taskInfo("task-queue-1", "QUEUED"));

        Map<String, Object> payload = controller.sendMessage(messageBody("你好"));

        assertEquals("task-queue-1", payload.get("id"));
        assertEquals("submitted", ((Map<?, ?>) payload.get("status")).get("state"));
        ArgumentCaptor<com.yangqiongai.ai.agent.core.model.request.AgentRequest> captor =
                ArgumentCaptor.forClass(com.yangqiongai.ai.agent.core.model.request.AgentRequest.class);
        verify(taskEnqueuer).enqueue(captor.capture());
        assertEquals("support-agent", captor.getValue().getAgentCode());
        verify(agentEngine, never()).submitTask(any());
    }

    @Test
    @DisplayName("非队列模式默认走引擎异步直执")
    void sendMessageNonQueueModeSubmitsToEngine() {
        when(agentEngine.submitTask(any())).thenReturn("task-engine-1");
        when(agentTaskRepository.queryTask("task-engine-1")).thenReturn(taskInfo("task-engine-1", "PENDING"));

        Map<String, Object> payload = controller.sendMessage(messageBody("你好"));

        assertEquals("task-engine-1", payload.get("id"));
        assertEquals("working", ((Map<?, ?>) payload.get("status")).get("state"));
        verify(agentEngine).submitTask(any());
        verify(taskEnqueuer, never()).enqueue(any());
    }

    @Test
    @DisplayName("队列关闭但启用开关单独打开仍走引擎直执")
    void sendMessageEnabledWithoutQueueModeSubmitsToEngine() {
        queueProperties.setEnabled(true);
        when(agentEngine.submitTask(any())).thenReturn("task-engine-2");
        when(agentTaskRepository.queryTask("task-engine-2")).thenReturn(taskInfo("task-engine-2", "PENDING"));

        controller.sendMessage(messageBody("你好"));

        verify(agentEngine).submitTask(any());
        verify(taskEnqueuer, never()).enqueue(any());
    }

    @Test
    @DisplayName("缺失agentCode报参数错误")
    void sendMessageMissingAgentCodeRejected() {
        Map<String, Object> body = Map.of(
                "message", Map.of("role", "user",
                        "parts", List.of(Map.of("kind", "text", "text", "你好"))));

        AiException ex = assertThrows(AiException.class, () -> controller.sendMessage(body));
        assertEquals("metadata.agentCode不能为空", ex.getMessage());
        verify(taskEnqueuer, never()).enqueue(any());
        verify(agentEngine, never()).submitTask(any());
    }

    @Test
    @DisplayName("空文本报参数错误")
    void sendMessageBlankTextRejected() {
        Map<String, Object> body = Map.of(
                "message", Map.of("role", "user", "parts", List.of(Map.of("kind", "text", "text", "  "))),
                "metadata", Map.of("agentCode", "support-agent"));

        AiException ex = assertThrows(AiException.class, () -> controller.sendMessage(body));
        assertEquals("消息文本不能为空", ex.getMessage());
        verify(taskEnqueuer, never()).enqueue(any());
        verify(agentEngine, never()).submitTask(any());
    }

    @Test
    @DisplayName("任务查询不存在报NOT_FOUND")
    void getTaskNotFoundRejected() {
        when(agentTaskRepository.queryTask("no-such-task")).thenReturn(null);

        AiException ex = assertThrows(AiException.class, () -> controller.getTask("no-such-task"));
        assertEquals("任务不存在: no-such-task", ex.getMessage());
    }

    @Test
    @DisplayName("完成任务返回artifacts输出")
    void getTaskCompletedReturnsArtifacts() {
        AgentTaskInfo info = taskInfo("task-done-1", "SUCCEEDED");
        info.setOutputText("我是客服助手");
        when(agentTaskRepository.queryTask("task-done-1")).thenReturn(info);

        Map<String, Object> payload = controller.getTask("task-done-1");

        assertEquals("completed", ((Map<?, ?>) payload.get("status")).get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) payload.get("artifacts");
        assertEquals(1, artifacts.size());
        assertEquals("task-done-1-output", artifacts.get(0).get("artifactId"));
    }

    @Test
    @DisplayName("推送配置注册成功")
    void registerPushConfigSuccess() {
        Map<String, Object> result = controller.registerPushConfig("task-1",
                Map.of("url", "https://callback.example.com/hook", "token", "tk"));

        assertEquals("task-1", result.get("taskId"));
        assertEquals("registered", result.get("pushConfig"));
        verify(pushConfigService).register(any());
    }

    @Test
    @DisplayName("推送配置缺URL报参数错误")
    void registerPushConfigMissingUrlRejected() {
        AiException ex = assertThrows(AiException.class,
                () -> controller.registerPushConfig("task-1", Map.of()));

        assertEquals("推送URL不能为空", ex.getMessage());
        verify(pushConfigService, never()).register(any());
    }

    @Test
    @DisplayName("失败任务返回status.message错误信息")
    void getTaskFailedReturnsErrorMessage() {
        AgentTaskInfo info = taskInfo("task-fail-1", "FAILED");
        info.setErrorMessage("模型调用超时");
        when(agentTaskRepository.queryTask("task-fail-1")).thenReturn(info);

        Map<String, Object> payload = controller.getTask("task-fail-1");

        assertEquals("failed", ((Map<?, ?>) payload.get("status")).get("state"));
        @SuppressWarnings("unchecked")
        Map<String, Object> lastMessage = (Map<String, Object>) ((Map<?, ?>) payload.get("status")).get("message");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) lastMessage.get("parts");
        assertEquals("模型调用超时", parts.get(0).get("text"));
    }

    @Test
    @DisplayName("队列模式消息透传sessionId与userId")
    void sendMessageMetadataPropagation() {
        queueProperties.setEnabled(true);
        queueProperties.setMode("queue");
        when(taskEnqueuer.enqueue(any())).thenReturn("task-meta-1");
        when(agentTaskRepository.queryTask("task-meta-1")).thenReturn(taskInfo("task-meta-1", "QUEUED"));

        Map<String, Object> body = Map.of(
                "message", Map.of("role", "user",
                        "parts", List.of(Map.of("kind", "text", "text", "查询订单"))),
                "metadata", Map.of("agentCode", "support-agent",
                        "userId", "u-1001", "sessionId", "s-2002", "scopeId", "t-3003"));

        controller.sendMessage(body);

        ArgumentCaptor<com.yangqiongai.ai.agent.core.model.request.AgentRequest> captor =
                ArgumentCaptor.forClass(com.yangqiongai.ai.agent.core.model.request.AgentRequest.class);
        verify(taskEnqueuer).enqueue(captor.capture());
        assertEquals("u-1001", captor.getValue().getUserId());
        assertEquals("s-2002", captor.getValue().getSessionId());
        assertEquals("t-3003", captor.getValue().getScopeId());
        verify(agentTaskRepository).queryTask(eq("task-meta-1"));
    }
}
