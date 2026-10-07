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
package com.yangqiongai.ai.common.scope;

/**
 * 作用域上下文
 * @author yangqiong
 */
public final class ScopeContext {

    /**
     * 默认作用域ID
     */
    public static final String DEFAULT_SCOPE_ID = "default";

    private static final ThreadLocal<String> SCOPE_HOLDER = new ThreadLocal<>();

    private ScopeContext() {
    }

    /**
     * 获取当前线程的作用域ID，未设置时返回默认值
     * @return
     */
    public static String getScopeId() {
        String scopeId = SCOPE_HOLDER.get();
        return scopeId == null ? DEFAULT_SCOPE_ID : scopeId;
    }

    /**
     * 设置当前线程的作用域ID
     * @param scopeId
     */
    public static void setScopeId(String scopeId) {
        SCOPE_HOLDER.set(scopeId);
    }

    /**
     * 清除当前线程的作用域ID
     */
    public static void clear() {
        SCOPE_HOLDER.remove();
    }
}
