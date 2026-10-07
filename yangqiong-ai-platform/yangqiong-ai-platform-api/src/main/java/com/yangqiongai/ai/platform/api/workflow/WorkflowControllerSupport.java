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
package com.yangqiongai.ai.platform.api.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteRequest;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowStreamEvent;
import com.yangqiongai.ai.workflow.repository.WorkflowDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 工作流Controller共享逻辑
 * @author yangqiong
 */
@Component
public class WorkflowControllerSupport {

    private static final Logger log = LoggerFactory.getLogger(WorkflowControllerSupport.class);

    private final ObjectMapper objectMapper;
    private final WorkflowDefinitionRepository definitionRepository;

    public WorkflowControllerSupport(ObjectMapper objectMapper, WorkflowDefinitionRepository definitionRepository) {
        this.objectMapper = objectMapper;
        this.definitionRepository = definitionRepository;
    }

    /**
     * 将工作流SSE事件映射为通用StreamEvent
     * @param eventFlux
     * @return
     */
    public Flux<StreamEvent> mapStreamEvents(Flux<WorkflowStreamEvent> eventFlux) {
        return eventFlux.map(event -> {
            try {
                String payload = objectMapper.writeValueAsString(event);
                return StreamEvent.textDelta(event.getEventType() + ":" + payload);
            } catch (Exception e) {
                log.warn("工作流SSE事件序列化失败: {}", e.getMessage());
                return StreamEvent.textDelta("error:" + e.getMessage());
            }
        });
    }

    /**
     * 解析工作流定义：优先使用请求中的definition，否则按名称从数据库加载
     * @param request
     * @return
     */
    public WorkflowDefinition resolveDefinition(WorkflowExecuteRequest request) {
        if (request.getDefinition() != null) {
            return request.getDefinition();
        }
        if (request.getDefinitionName() != null && !request.getDefinitionName().isBlank()) {
            return loadDefinition(request.getDefinitionName(), request.getVersion());
        }
        throw new AiException(AiErrorCode.BAD_REQUEST, "必须提供definition或definitionName");
    }

    /**
     * 从数据库加载工作流定义
     * @param name
     * @param version
     * @return
     */
    public WorkflowDefinition loadDefinition(String name, Integer version) {
        WorkflowDefinition definition;
        if (version != null) {
            definition = definitionRepository.loadByNameAndVersion(name, version);
        } else {
            definition = definitionRepository.loadByName(name);
        }
        if (definition == null) {
            throw new AiException(AiErrorCode.NOT_FOUND,
                    "工作流定义不存在: " + name + (version != null ? " v" + version : ""));
        }
        return definition;
    }

    /**
     * 构建工作流执行上下文
     * @param definitionName
     * @param request
     * @return
     */
    public AgentContext createAgentContext(String definitionName, WorkflowExecuteRequest request) {
        AgentRequest agentRequest = new AgentRequest();
        agentRequest.setInput(definitionName);
        if (request != null) {
            if (request.getUserId() != null) {
                agentRequest.setUserId(request.getUserId());
            }
            if (request.getSessionId() != null && !request.getSessionId().isBlank()) {
                agentRequest.setSessionId(request.getSessionId());
            } else {
                agentRequest.setSessionId("wf-" + System.currentTimeMillis());
            }
            if (request.getInput() != null) {
                agentRequest.setInput(request.getInput());
            }
            if (request.getParams() != null) {
                agentRequest.setBody(request.getParams());
            }
        } else {
            agentRequest.setSessionId("wf-" + System.currentTimeMillis());
        }
        AgentContext context = new AgentContext(agentRequest);
        if (request != null && request.getInitialVariables() != null) {
            context.setAttribute("initialVariables", request.getInitialVariables());
        }
        if (request != null && request.getPauseReason() != null && !request.getPauseReason().isBlank()) {
            context.setAttribute("pauseReason", request.getPauseReason());
        }
        return context;
    }
}
