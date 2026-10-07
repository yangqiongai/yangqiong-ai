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
package com.yangqiongai.ai.platform.system.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.system.entity.SystemUser;
import com.yangqiongai.ai.platform.system.service.SystemUserService;
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

/**
 * 用户管理
 * @author yangqiong
 */
@Tag(name = "用户管理接口")
@RestController
@RequestMapping("/api/system/user")
public class SystemUserController {

    /**
     * 用户管理服务
     */
    @Autowired
    private SystemUserService systemUserService;

    /**
     * 分页查询用户列表
     * @param page
     * @param size
     * @param username
     * @param status
     * @param orgId
     * @return
     */
    @Operation(summary = "分页查询用户列表")
    @GetMapping("/list")
    public ApiResult<Page<SystemUser>> list(
            @Parameter(name = "page", description = "页码，从1开始") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(name = "size", description = "每页条数") @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(name = "username", description = "用户名，模糊匹配") @RequestParam(value = "username", required = false) String username,
            @Parameter(name = "status", description = "状态（1启用/0停用）") @RequestParam(value = "status", required = false) Integer status,
            @Parameter(name = "orgId", description = "所属组织ID，空为全部") @RequestParam(value = "orgId", required = false) Long orgId) {
        return ApiResult.ok(systemUserService.list(page, size, username, status, orgId));
    }

    /**
     * 查询用户详情
     * @param id
     * @return
     */
    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    public ApiResult<SystemUser> get(
            @Parameter(name = "id", description = "用户ID") @PathVariable("id") Long id) {
        return ApiResult.ok(systemUserService.getById(id));
    }

    /**
     * 创建用户
     * @param systemUser
     * @return
     */
    @Operation(summary = "创建用户")
    @PostMapping
    public ApiResult<SystemUser> create(
            @Parameter(name = "systemUser", description = "用户信息，包含username、password、displayName、email、phone等") @RequestBody SystemUser systemUser) {
        return ApiResult.ok(systemUserService.create(systemUser));
    }

    /**
     * 更新用户信息
     * @param id
     * @param systemUser
     * @return
     */
    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public ApiResult<SystemUser> update(
            @Parameter(name = "id", description = "用户ID") @PathVariable("id") Long id,
            @Parameter(name = "systemUser", description = "用户更新信息，不允许修改username和password") @RequestBody SystemUser systemUser) {
        return ApiResult.ok(systemUserService.update(id, systemUser));
    }

    /**
     * 删除用户
     * @param id
     * @return
     */
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(name = "id", description = "用户ID") @PathVariable("id") Long id) {
        systemUserService.delete(id);
        return ApiResult.ok();
    }

    /**
     * 更新用户状态
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "更新用户状态")
    @PutMapping("/{id}/status")
    public ApiResult<Void> updateStatus(
            @Parameter(name = "id", description = "用户ID") @PathVariable("id") Long id,
            @Parameter(name = "request", description = "状态请求体，包含status（1启用/0停用）") @RequestBody UpdateStatusRequest request) {
        systemUserService.updateStatus(id, request.getStatus());
        return ApiResult.ok();
    }

    /**
     * 重置用户密码
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "重置用户密码")
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(
            @Parameter(name = "id", description = "用户ID") @PathVariable("id") Long id,
            @Parameter(name = "request", description = "密码请求体，包含password") @RequestBody ResetPasswordRequest request) {
        systemUserService.resetPassword(id, request.getPassword());
        return ApiResult.ok();
    }

    /**
     * 更新状态请求体
     * @author yangqiong
     */
    public static class UpdateStatusRequest {

        /**
         * 状态：1启用/0停用
         */
        private Integer status;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 重置密码请求体
     * @author yangqiong
     */
    public static class ResetPasswordRequest {

        /**
         * 新密码
         */
        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
