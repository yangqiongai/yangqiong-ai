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

import com.yangqiongai.ai.data.workflow.service.WorkflowExecutionHistoryService;
import com.yangqiongai.ai.data.workflow.service.WorkflowNodeExecutionService;
import com.yangqiongai.ai.data.workflow.entity.WorkflowExecutionHistoryEntity;
import com.yangqiongai.ai.data.workflow.entity.WorkflowNodeExecutionEntity;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流执行历史接口
 * @author yangqiong
 */
@Tag(name = "工作流执行历史接口")
@RestController
@RequestMapping("/api/workflow/history")
public class WorkflowHistoryController {

    @Autowired
    private WorkflowExecutionHistoryService workflowExecutionHistoryService;

    @Autowired
    private WorkflowNodeExecutionService workflowNodeExecutionService;

    /**
     * 查询执行历史
     * @param pageNum
     * @param pageSize
     * @param definitionName
     * @param status
     * @return
     */
    @Operation(summary = "查询执行历史")
    @GetMapping
    public ApiResult<Page<WorkflowExecutionHistoryEntity>> listHistory(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "工作流定义名称") @RequestParam(required = false) String definitionName,
            @Parameter(description = "执行状态") @RequestParam(required = false) String status) {
        return ApiResult.ok(workflowExecutionHistoryService.pageHistory(pageNum, pageSize, definitionName, status));
    }

    /**
     * 查询实例的节点执行轨迹（回放用，按执行顺序排序）
     * @param instanceId
     * @return
     */
    @Operation(summary = "查询实例节点执行轨迹")
    @GetMapping("/{instanceId}/nodes")
    public ApiResult<List<WorkflowNodeExecutionEntity>> listNodeTraces(
            @Parameter(description = "工作流实例ID") @PathVariable String instanceId) {
        return ApiResult.ok(workflowNodeExecutionService.listByInstance(instanceId));
    }
}
