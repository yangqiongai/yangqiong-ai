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
package com.yangqiongai.ai.trust.profile.controller;

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.trust.profile.PermissionProfileService;
import com.yangqiongai.ai.trust.profile.entity.AgentPermissionProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent权限画像管理接口
 * @author yangqiong
 */
@Tag(name = "Agent权限画像管理接口")
@RestController
@RequestMapping("/api/trust/profile")
public class PermissionProfileController {

    private final PermissionProfileService profileService;

    public PermissionProfileController(PermissionProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * 按Agent编码查询画像
     * @param agentCode
     * @return
     */
    @Operation(summary = "按Agent编码查询画像")
    @GetMapping("/agent/{agentCode}")
    public ApiResult<AgentPermissionProfile> getByAgentCode(@PathVariable String agentCode) {
        return ApiResult.ok(profileService.getByAgentCode(agentCode));
    }

    /**
     * 保存画像(按Agent编码upsert)
     * @param profile
     * @return
     */
    @Operation(summary = "保存画像")
    @PostMapping
    public ApiResult<AgentPermissionProfile> save(@RequestBody AgentPermissionProfile profile) {
        return ApiResult.ok(profileService.save(profile));
    }

    /**
     * 查询画像列表
     * @param agentCode
     * @param status
     * @return
     */
    @Operation(summary = "查询画像列表")
    @GetMapping
    public ApiResult<List<AgentPermissionProfile>> list(@RequestParam(required = false) String agentCode,
                                                        @RequestParam(required = false) String status) {
        return ApiResult.ok(profileService.list(agentCode, status));
    }

    /**
     * 启用/禁用画像
     * @param id
     * @return
     */
    @Operation(summary = "启用/禁用画像")
    @PostMapping("/{id}/toggle")
    public ApiResult<Void> toggle(@PathVariable Long id) {
        profileService.toggle(id);
        return ApiResult.ok(null);
    }
}
