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
package com.yangqiongai.ai.trust.identity.controller;

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.trust.identity.AgentIdentityService;
import com.yangqiongai.ai.trust.identity.entity.AgentIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent身份管理接口
 * @author yangqiong
 */
@Tag(name = "Agent身份管理接口")
@RestController
@RequestMapping("/api/trust/identity")
public class AgentIdentityController {

    private final AgentIdentityService identityService;

    public AgentIdentityController(AgentIdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * 查询身份档案列表
     * @return
     */
    @Operation(summary = "查询身份档案列表")
    @GetMapping
    public ApiResult<List<AgentIdentity>> list() {
        return ApiResult.ok(identityService.list());
    }

    /**
     * 查询单个身份档案
     * @param identityUid
     * @return
     */
    @Operation(summary = "查询单个身份档案")
    @GetMapping("/{identityUid}")
    public ApiResult<AgentIdentity> get(@PathVariable String identityUid) {
        return ApiResult.ok(identityService.get(identityUid));
    }

    /**
     * 签发短时身份凭证(仅返回一次明文)
     * @param identityUid
     * @return
     */
    @Operation(summary = "签发短时身份凭证")
    @PostMapping("/{identityUid}/issue")
    public ApiResult<String> issue(@PathVariable String identityUid) {
        return ApiResult.ok(identityService.issueCredential(identityUid));
    }

    /**
     * 轮换凭证(旧凭证在TTL窗口内自然失效)
     * @param identityUid
     * @return
     */
    @Operation(summary = "轮换身份凭证")
    @PostMapping("/{identityUid}/rotate")
    public ApiResult<String> rotate(@PathVariable String identityUid) {
        return ApiResult.ok(identityService.rotate(identityUid));
    }

    /**
     * 吊销身份并联动禁用Agent
     * @param identityUid
     * @param operator
     * @return
     */
    @Operation(summary = "吊销身份")
    @PostMapping("/{identityUid}/revoke")
    public ApiResult<Void> revoke(@PathVariable String identityUid,
                                  @RequestParam(required = false, defaultValue = "system") String operator) {
        identityService.revoke(identityUid, operator);
        return ApiResult.ok(null);
    }
}
