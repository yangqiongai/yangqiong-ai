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
package com.yangqiongai.ai.platform.api.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.agent.tool.ToolCategory;
import com.yangqiongai.ai.agent.tool.model.ToolConfigCategory;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.ToolConfigManager;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具配置管理
 *
 * @author yangqiong
 */
@Tag(name = "工具配置管理接口")
@RestController
@RequestMapping("/api/tool-config")
public class AiToolConfigController {

    @Autowired
    private ToolConfigManager service;

    @Autowired
    private ToolConfigRepository toolConfigRepository;

    /**
     * 工具配置分类管理
     */
    @Autowired
    private ToolConfigCategoryService toolConfigCategoryService;

    /**
     * 套餐能力校验器
     */
    @Autowired
    private FeatureGuard featureGuard;

    /**
     * 用户自建工具的类别标记
     */
    private static final String CATEGORY_USER = "USER";

    /**
     * 查询所有工具配置
     * @return
     */
    @GetMapping
    public List<ToolConfigInfo> list() {
        return service.listAll();
    }

    /**
     * 分页查询工具配置
     * @param page
     * @param size
     * @param keyword
     * @param categoryCode
     * @return
     */
    @Operation(summary = "分页查询工具配置")
    @GetMapping("/list")
    public ApiResult<Page<ToolConfigInfo>> listPaged(
            @Parameter(name = "page", description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(name = "size", description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(name = "keyword", description = "关键字(名称/编码)") @RequestParam(required = false) String keyword,
            @Parameter(name = "categoryCode", description = "分类节点编码(限制返回范围为该节点子树，__ungrouped__表示未分类)")
            @RequestParam(required = false) String categoryCode) {
        Set<String> subtreeCodes = null;
        if (categoryCode != null && !categoryCode.isBlank()) {
            subtreeCodes = toolConfigCategoryService.resolveSubtreeCodes(categoryCode);
        }
        long total = toolConfigRepository.countByKeyword(keyword, categoryCode, subtreeCodes);
        List<ToolConfigInfo> records = toolConfigRepository.searchByKeyword(keyword, categoryCode, subtreeCodes, (page - 1) * size, size);
        Page<ToolConfigInfo> result = new Page<>(page, size, total);
        result.setRecords(records);
        return ApiResult.ok(result);
    }

    /**
     * 按状态查询工具配置
     * @param status
     * @return
     */
    @GetMapping("/status/{status}")
    public List<ToolConfigInfo> listByStatus(@PathVariable Integer status) {
        return service.listByStatus(status);
    }

    /**
     * 按toolCode查询工具配置详情
     * @param toolCode
     * @return
     */
    @Operation(summary = "查询工具配置详情")
    @GetMapping("/{toolCode}")
    public ApiResult<ToolConfigInfo> getByToolCode(
            @Parameter(name = "toolCode", description = "工具编码") @PathVariable String toolCode) {
        ToolConfigInfo config = toolConfigRepository.getByToolCode(toolCode);
        if (config == null) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "工具配置不存在: " + toolCode);
        }
        return ApiResult.ok(config);
    }

    /**
     * 创建工具配置
     * @param config
     * @return
     */
    @Operation(summary = "创建工具配置")
    @PostMapping
    public ApiResult<ToolConfigInfo> create(@RequestBody ToolConfigInfo config) {
        if (config.getToolCode() == null || config.getToolCode().isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "toolCode不能为空");
        }
        if (toolConfigRepository.getByToolCode(config.getToolCode()) != null) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "工具编码已存在: " + config.getToolCode());
        }
        if (config.getToolStatus() == null) {
            config.setToolStatus(1);
        }
        toolConfigCategoryService.validateCategory(config.getCategory());
        // 页面创建的工具均标记为用户自建类别
        config.setToolCategory(CATEGORY_USER);
        toolConfigRepository.save(config);
        return ApiResult.ok(config);
    }

    /**
     * 更新工具配置
     * @param toolCode
     * @param request
     * @return
     */
    @Operation(summary = "更新工具配置")
    @PutMapping("/{toolCode}")
    public ApiResult<ToolConfigInfo> update(
            @Parameter(name = "toolCode", description = "工具编码") @PathVariable String toolCode,
            @RequestBody ToolConfigInfo request) {
        ToolConfigInfo existing = toolConfigRepository.getByToolCode(toolCode);
        if (existing == null) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "工具配置不存在: " + toolCode);
        }
        if (ToolCategory.BUILTIN.name().equals(existing.getToolCategory())) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "内置工具不允许编辑");
        }
        toolConfigCategoryService.validateCategory(request.getCategory());
        // 编码与类别不可修改，仅合并允许编辑的字段
        existing.setToolName(request.getToolName() != null ? request.getToolName() : existing.getToolName());
        existing.setToolDesc(request.getToolDesc() != null ? request.getToolDesc() : existing.getToolDesc());
        existing.setToolType(request.getToolType() != null ? request.getToolType() : existing.getToolType());
        existing.setToolOrder(request.getToolOrder() != null ? request.getToolOrder() : existing.getToolOrder());
        existing.setToolConfig(request.getToolConfig() != null ? request.getToolConfig() : existing.getToolConfig());
        existing.setToolStatus(request.getToolStatus() != null ? request.getToolStatus() : existing.getToolStatus());
        existing.setRemark(request.getRemark() != null ? request.getRemark() : existing.getRemark());
        // 分类允许传空串清空归属
        existing.setCategory(request.getCategory() != null ? request.getCategory() : existing.getCategory());
        toolConfigRepository.updateById(existing);
        return ApiResult.ok(existing);
    }

    /**
     * 删除工具配置
     * @param toolCode
     * @return
     */
    @Operation(summary = "删除工具配置")
    @DeleteMapping("/{toolCode}")
    public ApiResult<Boolean> delete(
            @Parameter(name = "toolCode", description = "工具编码") @PathVariable String toolCode) {
        ToolConfigInfo existing = toolConfigRepository.getByToolCode(toolCode);
        if (existing == null) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "工具配置不存在: " + toolCode);
        }
        // 内置/系统工具由代码注册并随启动同步，删除后会被还原，不允许删除
        if (ToolCategory.BUILTIN.name().equals(existing.getToolCategory())) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "内置工具不允许删除");
        }
        if (ToolCategory.CUSTOM.name().equals(existing.getToolCategory())) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "系统工具不允许删除");
        }
        return ApiResult.ok(toolConfigRepository.deleteByToolCode(toolCode));
    }

    /**
     * 切换工具启用/禁用状态
     * @param toolCode
     * @return
     */
    @PostMapping("/toggle/{toolCode}")
    public boolean toggle(@PathVariable String toolCode) {
        ToolConfigInfo config = service.getByToolCode(toolCode);
        if (config != null && (config.getToolStatus() == null || config.getToolStatus() == 0)) {
            featureGuard.checkToolAllowed(toolCode);
        }
        return service.toggleStatus(toolCode);
    }

    /**
     * 查询工具配置分类树（各节点含挂载工具数）
     * @return
     */
    @Operation(summary = "查询工具配置分类树")
    @GetMapping("/category/tree")
    public ApiResult<Map<String, Object>> categoryTree() {
        return ApiResult.ok(toolConfigCategoryService.tree());
    }

    /**
     * 新增工具配置分类节点
     * @param category
     * @return
     */
    @Operation(summary = "新增工具配置分类节点")
    @PostMapping("/category")
    public ApiResult<ToolConfigCategory> createCategory(@RequestBody ToolConfigCategory category) {
        try {
            return ApiResult.ok(toolConfigCategoryService.create(category));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 更新工具配置分类节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param category
     * @return
     */
    @Operation(summary = "更新工具配置分类节点")
    @PutMapping("/category/{id}")
    public ApiResult<ToolConfigCategory> updateCategory(
            @Parameter(name = "id", description = "分类节点ID") @PathVariable Long id,
            @RequestBody ToolConfigCategory category) {
        try {
            return ApiResult.ok(toolConfigCategoryService.update(id, category));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 删除工具配置分类节点（存在子节点或挂载工具时禁止删除）
     * @param id
     * @return
     */
    @Operation(summary = "删除工具配置分类节点")
    @DeleteMapping("/category/{id}")
    public ApiResult<Void> deleteCategory(
            @Parameter(name = "id", description = "分类节点ID") @PathVariable Long id) {
        try {
            toolConfigCategoryService.delete(id);
            return ApiResult.ok();
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }
}
