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

import com.yangqiongai.ai.agent.tool.model.ToolUsageInfo;
import com.yangqiongai.ai.agent.tool.repository.ToolUsageRepository;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具使用量统计
 * @author yangqiong
 */
@Tag(name = "工具使用量统计接口")
@RestController
@RequestMapping("/api/agent/tool/usage")
public class ToolUsageController {

    @Autowired
    private ToolUsageRepository toolUsageRepository;

    /**
     * 获取工具使用量详情
     * @param toolCode
     * @return
     */
    @Operation(summary = "获取工具使用量详情")
    @GetMapping("/{toolCode}")
    public ApiResult<ToolUsageInfo> getByToolCode(
            @Parameter(name = "toolCode", description = "工具编码") @PathVariable String toolCode) {
        return ApiResult.ok(toolUsageRepository.getByToolCode(toolCode));
    }

    /**
     * 获取使用量排行
     * @param limit
     * @return
     */
    @Operation(summary = "获取使用量排行")
    @GetMapping("/top")
    public ApiResult<List<ToolUsageInfo>> listTop(
            @Parameter(name = "limit", description = "返回数量") @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(toolUsageRepository.listTopByCallCount(limit));
    }
}
