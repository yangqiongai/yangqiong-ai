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

import com.yangqiongai.ai.agent.mcp.model.McpConnectionTestResult;
import com.yangqiongai.ai.agent.mcp.model.McpServerCategory;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfigInfo;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import com.yangqiongai.ai.agent.mcp.repository.McpServerCategoryRepository;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigService;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP服务配置管理
 * @author yangqiong
 */
@Tag(name = "MCP服务配置管理接口")
@RestController
@RequestMapping("/api/mcp/server")
public class McpServerConfigController {

    /**
     * MCP能力标识
     */
    private static final String CAPABILITY_MCP = "MCP";

    @Autowired
    private McpServerConfigService mcpServerConfigService;

    /**
     * 套餐能力校验器
     */
    @Autowired
    private FeatureGuard featureGuard;

    /**
     * MCP服务分类管理
     */
    @Autowired
    private McpServerCategoryService mcpServerCategoryService;

    /**
     * 查询所有MCP服务配置
     * @param categoryCode 分类节点编码（限制返回范围为该节点子树，__ungrouped__表示未分类）
     * @return
     */
    @Operation(summary = "查询所有MCP服务配置")
    @GetMapping
    public ApiResult<List<McpServerConfigInfo>> list(
            @Parameter(name = "categoryCode", description = "分类节点编码(限制返回范围为该节点子树，__ungrouped__表示未分类)")
            @RequestParam(required = false) String categoryCode) {
        List<McpServerConfigInfo> configs = mcpServerConfigService.list();
        if (categoryCode != null && !categoryCode.isBlank()) {
            Set<String> subtreeCodes = mcpServerCategoryService.resolveSubtreeCodes(categoryCode);
            configs = configs.stream()
                    .filter(info -> {
                        String category = info.getCategory();
                        boolean ungrouped = category == null || category.isBlank();
                        if (McpServerCategoryRepository.UNGROUPED_CODE.equals(categoryCode)) {
                            return ungrouped;
                        }
                        return !ungrouped && subtreeCodes.contains(category);
                    })
                    .toList();
        }
        return ApiResult.ok(configs);
    }

    /**
     * 查询启用的MCP服务配置
     * @return
     */
    @Operation(summary = "查询启用的MCP服务配置")
    @GetMapping("/enabled")
    public ApiResult<List<McpServerConfigInfo>> listEnabled() {
        return ApiResult.ok(mcpServerConfigService.listEnabled());
    }

    /**
     * 根据serverCode查询配置
     * @param serverCode
     * @return
     */
    @Operation(summary = "根据serverCode查询配置")
    @GetMapping("/{serverCode}")
    public ApiResult<McpServerConfigInfo> getByServerCode(
            @Parameter(name = "serverCode", description = "服务编码") @PathVariable String serverCode) {
        McpServerConfigInfo entity = mcpServerConfigService.getByServerCode(serverCode);
        if (entity == null) {
            return ApiResult.fail(AiErrorCode.MCP_CLIENT_INIT_FAILED.getCode(), "MCP服务配置不存在: " + serverCode);
        }
        return ApiResult.ok(entity);
    }

    /**
     * 注册新MCP服务
     * @param config
     * @return
     */
    @Operation(summary = "注册新MCP服务")
    @PostMapping
    public ApiResult<McpServerConfigInfo> register(@RequestBody McpServerConfig config) {
        featureGuard.checkFeature(CAPABILITY_MCP);
        mcpServerCategoryService.validateCategory(config.getCategory());
        return ApiResult.ok(mcpServerConfigService.register(config));
    }

    /**
     * 更新MCP服务配置
     * @param serverCode
     * @param config
     * @return
     */
    @Operation(summary = "更新MCP服务配置")
    @PutMapping("/{serverCode}")
    public ApiResult<Boolean> update(
            @Parameter(name = "serverCode", description = "服务编码") @PathVariable String serverCode,
            @RequestBody McpServerConfig config) {
        featureGuard.checkFeature(CAPABILITY_MCP);
        mcpServerCategoryService.validateCategory(config.getCategory());
        return ApiResult.ok(mcpServerConfigService.updateConfig(serverCode, config));
    }

    /**
     * 切换服务状态
     * @param serverCode
     * @param body
     */
    @Operation(summary = "切换服务状态")
    @PatchMapping("/{serverCode}/status")
    public ApiResult<Boolean> toggleStatus(
            @Parameter(name = "serverCode", description = "服务编码") @PathVariable String serverCode,
            @RequestBody java.util.Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "status必须为0或1");
        }
        return ApiResult.ok(mcpServerConfigService.toggleStatus(serverCode, status));
    }

    /**
     * 测试已注册服务的连接
     * @param serverCode
     * @return
     */
    @Operation(summary = "测试已注册服务的连接")
    @PostMapping("/{serverCode}/test")
    public ApiResult<McpConnectionTestResult> testConnection(
            @Parameter(name = "serverCode", description = "服务编码") @PathVariable String serverCode) {
        return ApiResult.ok(mcpServerConfigService.testConnection(serverCode));
    }

    /**
     * 测试新配置连接（不保存）
     * @param config
     * @return
     */
    @Operation(summary = "测试新配置连接（不保存）")
    @PostMapping("/test")
    public ApiResult<McpConnectionTestResult> testNewConnection(@RequestBody McpServerConfig config) {
        return ApiResult.ok(mcpServerConfigService.testNewConnection(config));
    }

    /**
     * 注销MCP服务（从数据库删除并从连接池移除）
     * @param serverCode
     */
    @Operation(summary = "注销MCP服务")
    @DeleteMapping("/{serverCode}")
    public ApiResult<Boolean> unregister(
            @Parameter(name = "serverCode", description = "服务编码") @PathVariable String serverCode) {
        return ApiResult.ok(mcpServerConfigService.unregister(serverCode));
    }

    /**
     * 获取所有已注册的服务编码
     * @return
     */
    @Operation(summary = "获取所有已注册的服务编码")
    @GetMapping("/codes")
    public ApiResult<Set<String>> listServerCodes() {
        return ApiResult.ok(mcpServerConfigService.getRegisteredServerCodes());
    }

    /**
     * 列出指定服务的可用工具
     * @param serverCode
     * @return
     */
    @Operation(summary = "列出指定服务的可用工具")
    @GetMapping("/{serverCode}/tools")
    public ApiResult<List<McpToolInfo>> listServerTools(
            @Parameter(name = "serverCode", description = "服务编码") @PathVariable String serverCode) {
        return ApiResult.ok(mcpServerConfigService.listServerTools(serverCode));
    }

    /**
     * 列出所有可用工具
     * @return
     */
    @Operation(summary = "列出所有可用工具")
    @GetMapping("/tools")
    public ApiResult<List<McpToolInfo>> listAllTools() {
        return ApiResult.ok(mcpServerConfigService.listAllTools());
    }

    /**
     * 查询MCP服务分类树（各节点含挂载服务数）
     * @return
     */
    @Operation(summary = "查询MCP服务分类树")
    @GetMapping("/category/tree")
    public ApiResult<Map<String, Object>> categoryTree() {
        return ApiResult.ok(mcpServerCategoryService.tree());
    }

    /**
     * 新增MCP服务分类节点
     * @param category
     * @return
     */
    @Operation(summary = "新增MCP服务分类节点")
    @PostMapping("/category")
    public ApiResult<McpServerCategory> createCategory(@RequestBody McpServerCategory category) {
        try {
            return ApiResult.ok(mcpServerCategoryService.create(category));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 更新MCP服务分类节点（编码不可修改，全量提交名称/父节点/排序）
     * @param id
     * @param category
     * @return
     */
    @Operation(summary = "更新MCP服务分类节点")
    @PutMapping("/category/{id}")
    public ApiResult<McpServerCategory> updateCategory(
            @Parameter(name = "id", description = "分类节点ID") @PathVariable Long id,
            @RequestBody McpServerCategory category) {
        try {
            return ApiResult.ok(mcpServerCategoryService.update(id, category));
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 删除MCP服务分类节点（存在子节点或挂载服务时禁止删除）
     * @param id
     * @return
     */
    @Operation(summary = "删除MCP服务分类节点")
    @DeleteMapping("/category/{id}")
    public ApiResult<Void> deleteCategory(
            @Parameter(name = "id", description = "分类节点ID") @PathVariable Long id) {
        try {
            mcpServerCategoryService.delete(id);
            return ApiResult.ok();
        } catch (AiException e) {
            return ApiResult.fail(e.getCode(), e.getMessage());
        }
    }
}
