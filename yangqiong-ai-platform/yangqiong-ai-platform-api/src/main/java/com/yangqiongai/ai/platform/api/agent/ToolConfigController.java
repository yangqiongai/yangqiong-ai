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

import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigService;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具配置管理
 * @author yangqiong
 */
@Tag(name = "工具配置管理接口")
@RestController
@RequestMapping("/api/agent/tool")
public class ToolConfigController {

    @Autowired
    private McpServerConfigService mcpServerConfigService;

    /**
     * 查询可用工具列表
     */
    @Operation(summary = "查询可用工具列表")
    @GetMapping
    public ApiResult<List<McpToolInfo>> list() {
        return ApiResult.ok(mcpServerConfigService.listAllTools());
    }

    /**
     * 查询工具详情
     */
    @Operation(summary = "查询工具详情")
    @GetMapping("/{toolName}")
    public ApiResult<McpToolInfo> getByName(
            @Parameter(name = "toolName", description = "工具名称") @PathVariable String toolName) {
        return mcpServerConfigService.listAllTools().stream()
                .filter(t -> toolName.equals(t.getName()))
                .findFirst()
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.MCP_TOOL_NOT_FOUND.getCode(), "工具不存在: " + toolName));
    }
}
