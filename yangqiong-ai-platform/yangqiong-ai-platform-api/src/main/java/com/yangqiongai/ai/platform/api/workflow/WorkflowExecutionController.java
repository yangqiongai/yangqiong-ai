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

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.workflow.WorkflowEngine;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteRequest;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowStreamEvent;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.platform.bss.sse.SseStreamHelper;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.bss.security.annotation.IgnoreSecurityCheckEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 工作流执行接口
 * @author yangqiong
 */
@Tag(name = "工作流执行接口")
@RestController
@RequestMapping("/api/workflow")
@IgnoreSecurityCheckEntity
public class WorkflowExecutionController {

    /**
     * 能力标识：工作流
     */
    private static final String CAPABILITY_WORKFLOW = "WORKFLOW";

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WorkflowControllerSupport support;

    /**
     * 功能集守卫
     */
    @Autowired
    private FeatureGuard featureGuard;

    /**
     * 执行工作流
     * @param request
     * @return
     */
    @Operation(summary = "执行工作流", description = "支持传入完整定义或按名称执行，返回结构化结果")
    @PostMapping("/execute")
    public ApiResult<WorkflowExecuteResult> execute(
            @Parameter(description = "执行请求") @Valid @RequestBody WorkflowExecuteRequest request) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        WorkflowDefinition definition = support.resolveDefinition(request);
        AgentContext context = support.createAgentContext(definition.getName(), request);
        return ApiResult.ok(workflowEngine.executeWithResult(definition, context));
    }

    /**
     * 按名称执行工作流
     * @param name
     * @param version
     * @param executionRequest
     * @return
     */
    @Operation(summary = "按名称执行工作流", description = "从数据库加载工作流定义并执行，默认最新启用版本，返回结构化结果")
    @PostMapping("/execute/{name}")
    public ApiResult<WorkflowExecuteResult> executeByName(
            @Parameter(description = "工作流定义名称") @PathVariable String name,
            @Parameter(description = "版本号，不传则使用最新启用版本") @RequestParam(required = false) Integer version,
            @Parameter(description = "执行参数") @RequestBody(required = false) WorkflowExecuteRequest executionRequest) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        WorkflowDefinition definition = support.loadDefinition(name, version);
        AgentContext context = support.createAgentContext(name, executionRequest);
        return ApiResult.ok(workflowEngine.executeWithResult(definition, context));
    }

    /**
     * 流式执行工作流
     * @param request
     * @return
     */
    @Operation(summary = "流式执行工作流", description = "支持传入完整定义或按名称执行")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(description = "执行请求") @Valid @RequestBody WorkflowExecuteRequest request) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        WorkflowDefinition definition = support.resolveDefinition(request);
        SseEmitter emitter = new SseEmitter(300000L);
        AgentContext context = support.createAgentContext(definition.getName(), request);
        Flux<WorkflowStreamEvent> eventFlux = workflowEngine.stream(definition, context);
        SseStreamHelper.streamEventsToResponse(emitter, support.mapStreamEvents(eventFlux));
        return emitter;
    }

    /**
     * 按名称流式执行工作流
     * @param name
     * @param version
     * @param executionRequest
     * @return
     */
    @Operation(summary = "按名称流式执行工作流", description = "从数据库加载工作流定义并流式执行")
    @PostMapping(value = "/stream/{name}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamByName(
            @Parameter(description = "工作流定义名称") @PathVariable String name,
            @Parameter(description = "版本号") @RequestParam(required = false) Integer version,
            @Parameter(description = "执行参数") @RequestBody(required = false) WorkflowExecuteRequest executionRequest) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        WorkflowDefinition definition = support.loadDefinition(name, version);
        SseEmitter emitter = new SseEmitter(300000L);
        AgentContext context = support.createAgentContext(name, executionRequest);
        Flux<WorkflowStreamEvent> eventFlux = workflowEngine.stream(definition, context);
        SseStreamHelper.streamEventsToResponse(emitter, support.mapStreamEvents(eventFlux));
        return emitter;
    }
}
