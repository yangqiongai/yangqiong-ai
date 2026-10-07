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

import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.WorkflowEngine;
import com.yangqiongai.ai.workflow.api.dto.WorkflowControlRequest;
import com.yangqiongai.ai.workflow.executor.WorkflowAgentExecutor;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowPauseHistory;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.store.WorkflowPauseHistoryStore;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.bss.security.annotation.IgnoreSecurityCheckEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流实例控制接口（状态查询、恢复、暂停、取消、校验）
 * @author yangqiong
 */
@Tag(name = "工作流实例控制接口")
@RestController
@RequestMapping("/api/workflow")
@IgnoreSecurityCheckEntity
public class WorkflowInstanceController {

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WorkflowAgentExecutor workflowExecutor;

    @Autowired
    private WorkflowPauseHistoryStore pauseHistoryStore;

    /**
     * 查询工作流状态
     * @param instanceId
     * @return
     */
    @Operation(summary = "查询工作流状态")
    @GetMapping("/status/{instanceId}")
    public ApiResult<WorkflowState> status(
            @Parameter(description = "工作流实例ID") @PathVariable String instanceId) {
        return ApiResult.ok(workflowEngine.queryStatus(instanceId));
    }

    /**
     * 恢复工作流执行
     * @param instanceId
     * @param request
     * @return
     */
    @Operation(summary = "恢复工作流执行")
    @PostMapping("/resume/{instanceId}")
    public ApiResult<AgentResult> resume(
            @Parameter(description = "工作流实例ID") @PathVariable String instanceId,
            @RequestBody(required = false) WorkflowControlRequest request) {
        String operator = request != null ? request.getOperator() : null;
        String reason = request != null ? request.getReason() : null;
        return ApiResult.ok(workflowEngine.resume(instanceId, operator, reason));
    }

    /**
     * 暂停工作流
     * @param instanceId
     * @param request
     * @return
     */
    @Operation(summary = "暂停工作流")
    @PostMapping("/pause/{instanceId}")
    public ApiResult<Boolean> pause(
            @Parameter(description = "工作流实例ID") @PathVariable String instanceId,
            @RequestBody(required = false) WorkflowControlRequest request) {
        String operator = request != null ? request.getOperator() : null;
        String reason = request != null ? request.getReason() : null;
        return ApiResult.ok(workflowEngine.pause(instanceId, operator, reason));
    }

    /**
     * 查询工作流暂停恢复流水(独立pause-history前缀,避免落入企业版/api/workflow/instances功能拦截范围)
     * @param instanceId
     * @return
     */
    @Operation(summary = "查询工作流暂停恢复流水")
    @GetMapping("/pause-history/{instanceId}")
    public ApiResult<List<WorkflowPauseHistory>> pauseHistory(
            @Parameter(description = "工作流实例ID") @PathVariable String instanceId) {
        return ApiResult.ok(pauseHistoryStore.listByInstanceId(instanceId));
    }

    /**
     * 取消工作流
     * @param instanceId
     * @return
     */
    @Operation(summary = "取消工作流")
    @PostMapping("/cancel/{instanceId}")
    public ApiResult<Boolean> cancel(
            @Parameter(description = "工作流实例ID") @PathVariable String instanceId) {
        return ApiResult.ok(workflowEngine.cancel(instanceId));
    }

    /**
     * 校验工作流定义
     * @param definition
     * @return
     */
    @Operation(summary = "校验工作流定义")
    @PostMapping("/validate")
    public ApiResult<Map<String, Object>> validate(
            @Parameter(description = "工作流定义JSON") @RequestBody WorkflowDefinition definition) {
        Map<String, Object> result = new HashMap<>();
        try {
            workflowExecutor.topologicalSort(definition);
            result.put("valid", true);
            result.put("message", "工作流定义校验通过");
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", e.getMessage());
        }
        return ApiResult.ok(result);
    }
}
