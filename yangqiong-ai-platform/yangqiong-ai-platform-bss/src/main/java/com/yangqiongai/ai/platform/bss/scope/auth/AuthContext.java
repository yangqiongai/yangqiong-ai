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
package com.yangqiongai.ai.platform.bss.scope.auth;

import java.util.Collections;
import java.util.Set;

/**
 * 鉴权上下文
 * <p>
 * 承载当前请求的角色、权限点与数据范围，由企业版 AuthContextFilter 装载，
 * 业务代码通过 AuthContextHolder 读取，社区版不被装配时恒为 EMPTY。
 * </p>
 * @author yangqiong
 */
public class AuthContext {

    /**
     * 空上下文（未启用鉴权时的兜底）
     */
    public static final AuthContext EMPTY = new AuthContext(null, null,
            Collections.emptySet(), Collections.emptySet(), null, null, false);

    /**
     * 全权限通配符（内置租户管理员角色装载时写入）
     */
    public static final String ALL_PERMISSIONS = "*";

    /**
     * 用户ID（与 UserContext.getUserId 一致）
     */
    private final String userId;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 所属组织ID（数据范围注入用，未归属组织时为null）
     */
    private final Long orgId;

    /**
     * 角色编码集（含内置角色）
     */
    private final Set<String> roles;

    /**
     * 权限点编码集
     */
    private final Set<String> permissions;

    /**
     * 当前数据范围（用户最高角色对应范围）：ALL/ORG/ORG_AND_CHILD/SELF/CUSTOM
     */
    private final String dataScope;

    /**
     * 是否平台管理员（全权限短路）
     */
    private final boolean platformAdmin;

    public AuthContext(String userId, String scopeId, Set<String> roles,
                       Set<String> permissions, String dataScope, Long orgId, boolean platformAdmin) {
        this.userId = userId;
        this.scopeId = scopeId;
        this.orgId = orgId;
        this.roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
        this.permissions = permissions == null ? Collections.emptySet() : Set.copyOf(permissions);
        this.dataScope = dataScope;
        this.platformAdmin = platformAdmin;
    }

    /**
     * 判断是否拥有指定权限点（platformAdmin 或装载通配符时恒真）
     * @param permCode
     * @return
     */
    public boolean hasPermission(String permCode) {
        return platformAdmin || permissions.contains(ALL_PERMISSIONS) || permissions.contains(permCode);
    }

    public String getUserId() {
        return userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public String getDataScope() {
        return dataScope;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }
}
