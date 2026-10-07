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
package com.yangqiongai.ai.platform.ecosystem.mcp.controller;

import com.yangqiongai.ai.agent.tool.AgentToolAdapter;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpExposeService;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpToolMapper;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP暴露白名单管理接口
 * @author yangqiong
 */
@Tag(name = "MCP暴露白名单管理接口")
@RestController
@RequestMapping("/api/ecosystem/mcp/expose")
public class McpExposeController {

    private final McpExposeService exposeService;

    private final List<Tool> toolProviders;

    public McpExposeController(McpExposeService exposeService, List<Tool> toolProviders) {
        this.exposeService = exposeService;
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
    }

    /**
     * 保存暴露配置(类型+编码唯一,存在即覆盖)
     * @param expose
     * @return
     */
    @Operation(summary = "保存暴露配置")
    @PostMapping
    public ApiResult<McpServerExpose> save(@RequestBody McpServerExpose expose) {
        return ApiResult.ok(exposeService.save(expose));
    }

    /**
     * 启用/禁用暴露配置
     * @param id
     * @return
     */
    @Operation(summary = "启用/禁用暴露配置")
    @PostMapping("/{id}/toggle")
    public ApiResult<Void> toggle(@PathVariable Long id) {
        exposeService.toggle(id);
        return ApiResult.ok(null);
    }

    /**
     * 删除暴露配置
     * @param id
     * @return
     */
    @Operation(summary = "删除暴露配置")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        exposeService.delete(id);
        return ApiResult.ok(null);
    }

    /**
     * 查询单个暴露配置
     * @param id
     * @return
     */
    @Operation(summary = "查询单个暴露配置")
    @GetMapping("/{id}")
    public ApiResult<McpServerExpose> get(@PathVariable Long id) {
        return ApiResult.ok(exposeService.get(id));
    }

    /**
     * 查询暴露配置列表
     * @param exposeType
     * @param exposeCode
     * @return
     */
    @Operation(summary = "查询暴露配置列表")
    @GetMapping
    public ApiResult<List<McpServerExpose>> list(@RequestParam(required = false) String exposeType,
                                                 @RequestParam(required = false) String exposeCode) {
        return ApiResult.ok(exposeService.list(exposeType, exposeCode));
    }

    /**
     * 查询可暴露的运行时工具选项(与对外MCP工具索引同一标识)
     * @return
     */
    @Operation(summary = "查询可暴露的运行时工具选项")
    @GetMapping("/tool-options")
    public ApiResult<List<Map<String, Object>>> toolOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        McpToolMapper.indexPlatformTools(toolProviders).values().stream()
                .sorted(java.util.Comparator.comparing(AgentToolAdapter::getName))
                .forEach(adapter -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("name", adapter.getName());
                    option.put("description", adapter.getDescription());
                    options.add(option);
                });
        return ApiResult.ok(options);
    }
}
