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

import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.bss.scope.auth.RequirePermission;
import com.yangqiongai.ai.platform.system.dto.SystemMenuTreeNode;
import com.yangqiongai.ai.platform.system.entity.SystemMenu;
import com.yangqiongai.ai.platform.system.entity.SystemMenuScope;
import com.yangqiongai.ai.platform.system.service.SystemMenuService;
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

/**
 * 菜单管理
 * @author yangqiong
 */
@Tag(name = "菜单管理接口")
@RestController
@RequestMapping("/api/system/menus")
public class SystemMenuController {

    /**
     * 菜单管理服务
     */
    @Autowired
    private SystemMenuService systemMenuService;

    /**
     * 查询管理树（含隐藏与停用）
     * @param appCode
     * @return
     */
    @Operation(summary = "查询管理树")
    @RequirePermission("system:menu:manage")
    @GetMapping("/tree")
    public ApiResult<List<SystemMenuTreeNode>> tree(
            @Parameter(name = "appCode", description = "端标识：community-admin/enterprise-admin/enterprise-client")
            @RequestParam("appCode") String appCode) {
        return ApiResult.ok(systemMenuService.tree(appCode));
    }

    /**
     * 查询当前用户可见菜单树（各端侧边栏数据源）
     * @param appCode
     * @return
     */
    @Operation(summary = "查询当前用户可见菜单树")
    @GetMapping("/my")
    public ApiResult<List<SystemMenuTreeNode>> my(
            @Parameter(name = "appCode", description = "端标识：community-admin/enterprise-admin/enterprise-client")
            @RequestParam("appCode") String appCode) {
        return ApiResult.ok(systemMenuService.myMenus(appCode));
    }

    /**
     * 创建菜单
     * @param systemMenu
     * @return
     */
    @Operation(summary = "创建菜单")
    @RequirePermission("system:menu:create")
    @PostMapping
    public ApiResult<SystemMenu> create(
            @Parameter(name = "systemMenu", description = "菜单信息，包含appCode、menuKey、menuType、name等") @RequestBody SystemMenu systemMenu) {
        return ApiResult.ok(systemMenuService.create(systemMenu));
    }

    /**
     * 更新菜单
     * @param id
     * @param systemMenu
     * @return
     */
    @Operation(summary = "更新菜单")
    @RequirePermission("system:menu:update")
    @PutMapping("/{id}")
    public ApiResult<SystemMenu> update(
            @Parameter(name = "id", description = "菜单ID") @PathVariable("id") Long id,
            @Parameter(name = "systemMenu", description = "菜单更新信息，不允许调整appCode与scopeId") @RequestBody SystemMenu systemMenu) {
        return ApiResult.ok(systemMenuService.update(id, systemMenu));
    }

    /**
     * 删除菜单
     * @param id
     * @return
     */
    @Operation(summary = "删除菜单")
    @RequirePermission("system:menu:delete")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(name = "id", description = "菜单ID") @PathVariable("id") Long id) {
        systemMenuService.delete(id);
        return ApiResult.ok();
    }

    /**
     * 调整同级排序
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "调整同级排序")
    @RequirePermission("system:menu:update")
    @PutMapping("/{id}/sort")
    public ApiResult<Void> sort(
            @Parameter(name = "id", description = "菜单ID") @PathVariable("id") Long id,
            @Parameter(name = "request", description = "排序请求体，包含direction（UP/DOWN/TOP）") @RequestBody SortRequest request) {
        systemMenuService.sort(id, request.getDirection());
        return ApiResult.ok();
    }

    /**
     * 查询菜单作用域覆盖配置
     * @param id
     * @return
     */
    @Operation(summary = "查询菜单作用域覆盖配置")
    @RequirePermission("system:menu:manage")
    @GetMapping("/{id}/scopes")
    public ApiResult<List<SystemMenuScope>> scopes(
            @Parameter(name = "id", description = "菜单ID") @PathVariable("id") Long id) {
        return ApiResult.ok(systemMenuService.scopes(id));
    }

    /**
     * 全量替换菜单作用域覆盖配置
     * @param id
     * @param scopes
     * @return
     */
    @Operation(summary = "全量替换菜单作用域覆盖配置")
    @RequirePermission("system:menu:update")
    @PutMapping("/{id}/scopes")
    public ApiResult<Void> saveScopes(
            @Parameter(name = "id", description = "菜单ID") @PathVariable("id") Long id,
            @Parameter(name = "scopes", description = "覆盖配置列表，全量替换语义") @RequestBody List<SystemMenuScope> scopes) {
        systemMenuService.saveScopes(id, scopes);
        return ApiResult.ok();
    }

    /**
     * 排序请求体
     * @author yangqiong
     */
    public static class SortRequest {

        /**
         * 排序方向：UP/DOWN/TOP
         */
        private String direction;

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }
    }
}
