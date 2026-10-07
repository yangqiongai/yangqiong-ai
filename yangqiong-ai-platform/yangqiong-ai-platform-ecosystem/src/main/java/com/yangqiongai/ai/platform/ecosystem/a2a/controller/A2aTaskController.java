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
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aPushConfigService;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aStateMapper;
import com.yangqiongai.ai.platform.ecosystem.a2a.entity.A2aPushConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A2A任务端点
 * <p>
 * message/send入队平台队列、tasks/get轮询状态（含PAUSED→input-required映射）、pushConfig注册回调。
 * </p>
 * @author yangqiong
 */
@Tag(name = "A2A任务端点")
@RestController
@RequestMapping("/a2a/v1")
public class A2aTaskController {

    private final AgentTaskEnqueuer taskEnqueuer;

    private final AgentTaskRepository agentTaskRepository;

    private final A2aPushConfigService pushConfigService;

    private final QueueProperties queueProperties;

    private final AgentEngine agentEngine;

    public A2aTaskController(AgentTaskEnqueuer taskEnqueuer, AgentTaskRepository agentTaskRepository,
                             A2aPushConfigService pushConfigService, QueueProperties queueProperties,
                             AgentEngine agentEngine) {
        this.taskEnqueuer = taskEnqueuer;
        this.agentTaskRepository = agentTaskRepository;
        this.pushConfigService = pushConfigService;
        this.queueProperties = queueProperties;
        this.agentEngine = agentEngine;
    }

    /**
     * 发送消息并创建任务(message/send)
     * @param body
     * @return
     */
    @Operation(summary = "A2A消息发送入队")
    @PostMapping("/message:send")
    public Map<String, Object> sendMessage(@RequestBody Map<String, Object> body) {
        String agentCode = metadataValue(body, "agentCode");
        if (agentCode == null || agentCode.isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "metadata.agentCode不能为空");
        }
        String text = extractText(body);
        if (text == null || text.isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "消息文本不能为空");
        }

        AgentRequest request = new AgentRequest();
        request.setAgentCode(agentCode);
        request.setInput(text);
        request.setUserId(metadataValue(body, "userId"));
        request.setSessionId(metadataValue(body, "sessionId"));
        request.setScopeId(metadataValue(body, "scopeId"));
        // 队列模式：写QUEUED由调度器抢占执行；否则走引擎异步直执（同一任务表，轮询端点同源可查）
        String taskId;
        if (queueProperties.isEnabled() && queueProperties.isQueueMode()) {
            taskId = taskEnqueuer.enqueue(request);
        } else {
            taskId = agentEngine.submitTask(request);
        }

        return taskPayload(agentTaskRepository.queryTask(taskId));
    }

    /**
     * 查询任务状态(tasks/get)
     * @param taskId
     * @return
     */
    @Operation(summary = "A2A任务状态查询")
    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getTask(@PathVariable String taskId) {
        AgentTaskInfo task = agentTaskRepository.queryTask(taskId);
        if (task == null) {
            throw new AiException(AiErrorCode.NOT_FOUND.getCode(), "任务不存在: " + taskId);
        }
        return taskPayload(task);
    }

    /**
     * 注册任务推送回调(pushConfig)
     * @param taskId
     * @param body
     * @return
     */
    @Operation(summary = "A2A推送配置注册")
    @PostMapping("/tasks/{taskId}/pushConfig")
    public Map<String, Object> registerPushConfig(@PathVariable String taskId,
                                                  @RequestBody Map<String, Object> body) {
        String url = stringValue(body.get("url"));
        if (url == null || url.isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "推送URL不能为空");
        }
        A2aPushConfig config = new A2aPushConfig();
        config.setTaskId(taskId);
        config.setUrl(url);
        config.setTokenHeader(stringValue(body.get("tokenHeader")));
        config.setToken(stringValue(body.get("token")));
        pushConfigService.register(config);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("pushConfig", "registered");
        return result;
    }

    /**
     * 平台任务转A2A任务载荷
     * @param task
     * @return
     */
    private Map<String, Object> taskPayload(AgentTaskInfo task) {
        A2aStateMapper.A2aTaskState state = A2aStateMapper.map(task.getTaskStatus());
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", state.wire());
        if (task.getQueuedTime() != null) {
            status.put("timestamp", OffsetDateTime.ofInstant(
                    task.getQueuedTime().atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
                    .toString());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", task.getTaskId());
        payload.put("contextId", task.getSessionId() != null ? task.getSessionId() : task.getTaskId());
        payload.put("status", status);
        if (state == A2aStateMapper.A2aTaskState.COMPLETED && task.getOutputText() != null) {
            payload.put("artifacts", List.of(Map.of(
                    "artifactId", task.getTaskId() + "-output",
                    "parts", List.of(Map.of("kind", "text", "text", task.getOutputText())))));
        }
        if (state == A2aStateMapper.A2aTaskState.FAILED && task.getErrorMessage() != null) {
            Map<String, Object> lastMessage = new LinkedHashMap<>();
            lastMessage.put("role", "agent");
            lastMessage.put("parts", List.of(Map.of("kind", "text", "text", task.getErrorMessage())));
            status.put("message", lastMessage);
        }
        return payload;
    }

    /**
     * 提取消息文本(兼容message.parts与parts两种载体)
     * @param body
     * @return
     */
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        Object message = body.get("message");
        Map<String, Object> messageMap = message instanceof Map ? (Map<String, Object>) message : body;
        Object parts = messageMap.get("parts");
        if (!(parts instanceof List<?> partList)) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Object part : partList) {
            if (part instanceof Map<?, ?> partMap && "text".equals(stringValue(partMap.get("kind")))) {
                String piece = stringValue(partMap.get("text"));
                if (piece != null) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(piece);
                }
            }
        }
        return text.toString();
    }

    private String metadataValue(Map<String, Object> body, String key) {
        Object metadata = body.get("metadata");
        if (metadata instanceof Map<?, ?> metadataMap) {
            return stringValue(metadataMap.get(key));
        }
        return stringValue(body.get(key));
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }
}
