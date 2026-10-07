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

import org.springframework.context.ApplicationEvent;

/**
 * 权限缓存失效事件
 * <p>
 * 角色、权限点、用户授权变更后发布，监听方失效对应权限缓存。
 * </p>
 * @author yangqiong
 */
public class PermissionChangedEvent extends ApplicationEvent {

    /**
     * 作用域ID，null 表示全局失效（如权限点目录变更）
     */
    private final String scopeId;

    /**
     * 用户ID，null 表示失效整个作用域
     */
    private final String userId;

    public PermissionChangedEvent(Object source, String scopeId, String userId) {
        super(source);
        this.scopeId = scopeId;
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getUserId() {
        return userId;
    }
}
