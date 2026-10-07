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

import com.yangqiongai.ai.workflow.api.dto.WorkflowDefinitionSaveRequest;
import com.yangqiongai.ai.workflow.api.dto.WorkflowDefinitionUpdateRequest;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.spi.WorkflowScheduleCleaner;
import com.yangqiongai.ai.data.workflow.service.WorkflowDefinitionService;
import com.yangqiongai.ai.data.workflow.entity.WorkflowDefinitionEntity;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.platform.bss.security.annotation.IgnoreSecurityCheckEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 工作流定义管理接口
 * @author yangqiong
 */
@Tag(name = "工作流定义管理接口")
@RestController
@RequestMapping("/api/workflow/definitions")
@IgnoreSecurityCheckEntity
public class WorkflowDefinitionController {

    private static final String CAPABILITY_WORKFLOW = "WORKFLOW";

    @Autowired
    private WorkflowDefinitionService definitionDbService;

    @Autowired
    private WorkflowControllerSupport support;

    @Autowired
    private FeatureGuard featureGuard;

    @Autowired
    private ObjectProvider<WorkflowScheduleCleaner> scheduleCleanerProvider;

    /**
     * 保存工作流定义
     * @param request
     * @return
     */
    @Operation(summary = "保存工作流定义")
    @PostMapping
    public ApiResult<Map<String, Object>> saveDefinition(
            @Parameter(description = "工作流定义保存请求") @Valid @RequestBody WorkflowDefinitionSaveRequest request) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        Long id = definitionDbService.saveDefinition(request);
        return ApiResult.ok(Map.of("id", id, "name", request.getDefinitionName()));
    }

    /**
     * 分页查询工作流定义列表
     * @param pageNum
     * @param pageSize
     * @param category
     * @param status
     * @return
     */
    @Operation(summary = "分页查询工作流定义列表")
    @GetMapping
    public ApiResult<Page<WorkflowDefinitionEntity>> listDefinitions(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数，默认20") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "分类筛选") @RequestParam(required = false) String category,
            @Parameter(description = "状态筛选(0-禁用 1-启用)") @RequestParam(required = false) Integer status) {
        return ApiResult.ok(definitionDbService.pageDefinitions(pageNum, pageSize, category, status));
    }

    /**
     * 根据名称加载工作流定义
     * @param name
     * @param version
     * @return
     */
    @Operation(summary = "根据名称加载工作流定义")
    @GetMapping("/{name}")
    public ApiResult<WorkflowDefinition> loadDefinition(
            @Parameter(description = "工作流定义名称") @PathVariable String name,
            @Parameter(description = "版本号，不传则使用最新启用版本") @RequestParam(required = false) Integer version) {
        WorkflowDefinition definition = support.loadDefinition(name, version);
        return ApiResult.ok(definition);
    }

    /**
     * 更新工作流定义
     * @param name
     * @param request
     * @return
     */
    @Operation(summary = "更新工作流定义")
    @PutMapping("/{name}")
    public ApiResult<Boolean> updateDefinition(
            @Parameter(description = "工作流定义名称") @PathVariable String name,
            @Parameter(description = "更新请求") @Valid @RequestBody WorkflowDefinitionUpdateRequest request) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        return ApiResult.ok(definitionDbService.updateDefinition(name, request));
    }

    /**
     * 删除工作流定义
     * @param name
     * @return
     */
    @Operation(summary = "删除工作流定义")
    @DeleteMapping("/{name}")
    public ApiResult<Boolean> deleteDefinition(
            @Parameter(description = "工作流定义名称") @PathVariable String name) {
        Boolean deleted = definitionDbService.deleteByName(name);
        if (Boolean.TRUE.equals(deleted)) {
            // 级联清理该工作流的定时调度任务(企业版注入实现)，防止悬空调度
            WorkflowScheduleCleaner cleaner = scheduleCleanerProvider.getIfAvailable();
            if (cleaner != null) {
                cleaner.deleteByWorkflowName(name);
            }
        }
        return ApiResult.ok(deleted);
    }

    /**
     * 切换工作流定义状态
     * @param name
     * @return
     */
    @Operation(summary = "切换工作流定义状态")
    @PostMapping("/{name}/toggle")
    public ApiResult<Boolean> toggleDefinition(
            @Parameter(description = "工作流定义名称") @PathVariable String name) {
        return ApiResult.ok(definitionDbService.toggleStatus(name));
    }
}
