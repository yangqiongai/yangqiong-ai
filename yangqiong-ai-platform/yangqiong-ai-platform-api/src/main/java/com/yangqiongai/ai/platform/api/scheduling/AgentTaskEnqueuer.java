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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Agent任务入队器
 * @author yangqiong
 */
@Component
public class AgentTaskEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskEnqueuer.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 队列模式默认优先级
     */
    private static final int DEFAULT_PRIORITY = 5;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private BackpressureGuard backpressureGuard;

    @Autowired
    private QueueProperties properties;

    /**
     * 任务入队（写QUEUED记录，由队列调度器抢占执行）
     * @param request
     * @return taskId
     */
    public String enqueue(AgentRequest request) {
        String scopeId = request.getScopeId();
        String agentCode = request.getAgentCode();

        // 背压检查通过后返回生效优先级（DEGRADE策略下可能被降低）
        BackpressureGuard.CheckResult check = backpressureGuard.check(scopeId, agentCode, DEFAULT_PRIORITY);

        String taskId = StringUtils.generateCompactId();
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId(taskId);
        info.setAgentCode(agentCode);
        info.setSessionId(request.getSessionId());
        info.setUserId(request.getUserId());
        info.setScopeId(scopeId);
        info.setTaskSource("ASYNC");
        info.setTaskStatus("QUEUED");
        info.setPriority(check.priority());
        info.setQueuedTime(LocalDateTime.now());
        info.setUserInput(request.getInputAsText());
        info.setBody(serializeRequest(request));
        agentTaskRepository.createTask(info);
        log.info("任务入队: taskId={}, agentCode={}, scopeId={}, priority={}", taskId, agentCode, scopeId, check.priority());
        return taskId;
    }

    /**
     * 从任务记录还原请求（调度器抢占后重建执行链路）
     * @param info
     * @return 还原失败时返回null
     */
    public AgentRequest deserializeRequest(AgentTaskInfo info) {
        if (info.getBody() == null || info.getBody().isBlank()) {
            return null;
        }
        try {
            AgentRequest request = OBJECT_MAPPER.readValue(info.getBody(), AgentRequest.class);
            if (request.getAgentCode() == null || request.getAgentCode().isBlank()) {
                request.setAgentCode(info.getAgentCode());
            }
            if (request.getSessionId() == null || request.getSessionId().isBlank()) {
                request.setSessionId(info.getSessionId());
            }
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                request.setUserId(info.getUserId());
            }
            return request;
        } catch (Exception e) {
            log.error("反序列化队列请求失败: taskId={}", info.getTaskId(), e);
            return null;
        }
    }

    /**
     * 序列化完整请求（body列，恢复执行时反序列化重建）
     * @param request
     * @return
     */
    private String serializeRequest(AgentRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("序列化队列请求失败: agentCode=" + request.getAgentCode(), e);
        }
    }
}
