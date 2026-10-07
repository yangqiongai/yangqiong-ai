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
package com.yangqiongai.ai.platform.bss.scope;

/**
 * 用户上下文
 * @author yangqiong
 */
public final class UserContext {

    /**
     * 默认用户ID
     */
    private static final String DEFAULT_USER_ID = "system";

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 获取当前线程的用户ID，未设置时返回默认值，永不返回null
     * @return
     */
    public static String getUserId() {
        String userId = USER_ID_HOLDER.get();
        return userId == null ? DEFAULT_USER_ID : userId;
    }

    /**
     * 设置当前线程的用户ID
     * @param userId
     */
    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 清除当前线程的用户ID
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
