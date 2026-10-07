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
package com.yangqiongai.ai.trust.credential.controller;

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.trust.credential.IssueResult;
import com.yangqiongai.ai.trust.credential.OpenCredentialService;
import com.yangqiongai.ai.trust.credential.entity.OpenCredential;
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
 * 开放凭证管理接口
 * @author yangqiong
 */
@Tag(name = "开放凭证管理接口")
@RestController
@RequestMapping("/api/trust/credential")
public class OpenCredentialController {

    private final OpenCredentialService credentialService;

    public OpenCredentialController(OpenCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /**
     * 签发凭证(密钥明文仅返回一次)
     * @param credential
     * @return
     */
    @Operation(summary = "签发凭证")
    @PostMapping("/issue")
    public ApiResult<IssueResult> issue(@RequestBody OpenCredential credential) {
        return ApiResult.ok(credentialService.issue(credential));
    }

    /**
     * 轮换凭证(重置密钥,旧密钥立即失效)
     * @param id
     * @return
     */
    @Operation(summary = "轮换凭证")
    @PostMapping("/{id}/rotate")
    public ApiResult<IssueResult> rotate(@PathVariable Long id) {
        return ApiResult.ok(credentialService.rotate(id));
    }

    /**
     * 吊销凭证(不可恢复)
     * @param id
     * @return
     */
    @Operation(summary = "吊销凭证")
    @PostMapping("/{id}/revoke")
    public ApiResult<Void> revoke(@PathVariable Long id) {
        credentialService.revoke(id);
        return ApiResult.ok(null);
    }

    /**
     * 启用/禁用凭证
     * @param id
     * @return
     */
    @Operation(summary = "启用/禁用凭证")
    @PostMapping("/{id}/toggle")
    public ApiResult<Void> toggle(@PathVariable Long id) {
        credentialService.toggle(id);
        return ApiResult.ok(null);
    }

    /**
     * 查询单个凭证
     * @param id
     * @return
     */
    @Operation(summary = "查询单个凭证")
    @GetMapping("/{id}")
    public ApiResult<OpenCredential> get(@PathVariable Long id) {
        return ApiResult.ok(credentialService.get(id));
    }

    /**
     * 查询凭证列表
     * @param name
     * @param status
     * @return
     */
    @Operation(summary = "查询凭证列表")
    @GetMapping
    public ApiResult<List<OpenCredential>> list(@RequestParam(required = false) String name,
                                                @RequestParam(required = false) String status) {
        return ApiResult.ok(credentialService.list(name, status));
    }
}
