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
package com.yangqiongai.ai.agent.registry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.registry.service.AgentRegistryService;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/**
 * Agent注册中心接口
 * @author yangqiong
 */
@Tag(name = "Agent注册中心接口")
@RestController
@RequestMapping("/api/agent/registry")
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class AgentRegistryController {

    @Autowired
    private AgentRegistryService registryService;

    @Operation(summary = "创建Agent定义")
    @PostMapping("/definitions")
    public ApiResult<AgentDefinition> createDefinition(@RequestBody AgentDefinition definition) {
        return ApiResult.ok(registryService.createDefinition(definition));
    }

    @Operation(summary = "更新Agent定义")
    @PutMapping("/definitions/{agentCode}")
    public ApiResult<AgentDefinition> updateDefinition(@PathVariable String agentCode,
                                                       @RequestBody AgentDefinition definition) {
        if (registryService.getDefinition(agentCode) == null) {
            return notFound("Agent定义不存在: " + agentCode);
        }
        return ApiResult.ok(registryService.updateDefinition(agentCode, definition));
    }

    @Operation(summary = "查询单个Agent定义")
    @GetMapping("/definitions/{agentCode}")
    public ApiResult<AgentDefinition> getDefinition(@PathVariable String agentCode) {
        AgentDefinition definition = registryService.getDefinition(agentCode);
        if (definition == null) {
            return notFound("Agent定义不存在: " + agentCode);
        }
        return ApiResult.ok(definition);
    }

    @Operation(summary = "分页查询Agent定义")
    @GetMapping("/definitions")
    public ApiResult<Page<AgentDefinition>> listDefinitions(@RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "10") int pageSize,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(registryService.listDefinitions(pageNum, pageSize, status, keyword));
    }

    @Operation(summary = "启用/禁用Agent定义")
    @PutMapping("/definitions/{agentCode}/status")
    public ApiResult<AgentDefinition> updateStatus(@PathVariable String agentCode,
                                                   @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
        return ApiResult.ok(registryService.updateStatus(agentCode, enabled));
    }

    @Operation(summary = "启用/禁用A2A卡片对外发布")
    @PutMapping("/definitions/{agentCode}/card-status")
    public ApiResult<AgentDefinition> updateCardStatus(@PathVariable String agentCode,
                                                       @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
        return ApiResult.ok(registryService.updateCardEnabled(agentCode, enabled));
    }

    @Operation(summary = "删除Agent定义")
    @DeleteMapping("/definitions/{agentCode}")
    public ApiResult<Void> deleteDefinition(@PathVariable String agentCode) {
        if (registryService.getDefinition(agentCode) == null) {
            return notFound("Agent定义不存在: " + agentCode);
        }
        registryService.deleteDefinition(agentCode);
        return ApiResult.ok(null);
    }

    @Operation(summary = "创建版本草稿")
    @PostMapping("/definitions/{agentCode}/versions")
    public ApiResult<AgentVersion> createVersion(@PathVariable String agentCode,
                                                 @RequestBody AgentVersion version) {
        return ApiResult.ok(registryService.createVersion(agentCode, version));
    }

    @Operation(summary = "更新版本草稿")
    @PutMapping("/versions/{versionId}")
    public ApiResult<AgentVersion> updateDraft(@PathVariable Long versionId,
                                               @RequestBody AgentVersion version) {
        if (registryService.getVersion(versionId) == null) {
            return notFound("Agent版本不存在: " + versionId);
        }
        return ApiResult.ok(registryService.updateDraft(versionId, version));
    }

    @Operation(summary = "查询Agent版本列表")
    @GetMapping("/definitions/{agentCode}/versions")
    public ApiResult<List<AgentVersion>> listVersions(@PathVariable String agentCode) {
        return ApiResult.ok(registryService.listVersions(agentCode));
    }

    @Operation(summary = "查询单个版本")
    @GetMapping("/versions/{versionId}")
    public ApiResult<AgentVersion> getVersion(@PathVariable Long versionId) {
        AgentVersion version = registryService.getVersion(versionId);
        if (version == null) {
            return notFound("Agent版本不存在: " + versionId);
        }
        return ApiResult.ok(version);
    }

    @Operation(summary = "禁用Agent")
    @PostMapping("/definitions/{agentCode}/disable")
    public ApiResult<Void> disable(@PathVariable String agentCode,
                                   @RequestBody(required = false) Map<String, Object> body) {
        registryService.updateStatus(agentCode, false);
        return ApiResult.ok(null);
    }

    /**
     * 不存在语义（HTTP 200 + success=false）
     * @param message
     * @return
     */
    private <T> ApiResult<T> notFound(String message) {
        return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(), message);
    }
}
