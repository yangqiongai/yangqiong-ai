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
package com.yangqiongai.ai.agent.runtime.durable;

/**
 * 租户作用域复合键
 * <p>
 * 统一存储键为 (scopeId, sessionId) 复合键，scopeId 为租户标识代名词
 * （ai-platform-tenant 之外一律使用 scopeId）。
 * 任意一段为空时退化为另一段，保证与既有单键语义兼容。
 * </p>
 * @author yangqiong
 */
public final class ScopeKeys {

    private ScopeKeys() {
    }

    /**
     * 构造scopeId与sessionId的复合存储键
     * @param scopeId
     * @param sessionId
     * @return
     */
    public static String key(String scopeId, String sessionId) {
        String scope = scopeId != null ? scopeId : "";
        String session = sessionId != null ? sessionId : "";
        return scope + "::" + session;
    }

    /**
     * 构造scopeId与userId的记忆桶键
     * @param scopeId
     * @param userId
     * @return
     */
    public static String memoryBucket(String scopeId, String userId) {
        String scope = scopeId != null ? scopeId : "";
        String user = userId != null ? userId : "";
        return scope + "::" + user;
    }
}
