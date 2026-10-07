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

/**
 * 鉴权权限缓存接口
 * <p>
 * 按用户维度缓存权限集与角色集，企业版提供查库实现；
 * 角色授权变更时通过 PermissionChangedEvent 失效。
 * </p>
 * @author yangqiong
 */
public interface AuthPermissionCache {

    /**
     * 查询用户权限点编码集（缓存未命中时回源加载）
     * @param scopeId
     * @param userId
     * @return
     */
    java.util.Set<String> getPermissions(String scopeId, String userId);

    /**
     * 查询用户角色编码集（缓存未命中时回源加载）
     * @param scopeId
     * @param userId
     * @return
     */
    java.util.Set<String> getRoles(String scopeId, String userId);

    /**
     * 查询用户数据范围（用户绑定角色中最大范围，无绑定角色时返回null由装载方兜底）
     * @param scopeId
     * @param userId
     * @return
     */
    String getDataScope(String scopeId, String userId);

    /**
     * 失效指定作用域的全部权限缓存
     * @param scopeId
     * @return
     */
    void evictScope(String scopeId);

    /**
     * 失效指定用户权限缓存
     * @param scopeId
     * @param userId
     * @return
     */
    void evictUser(String scopeId, String userId);
}
