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
package com.yangqiongai.ai.security.guardrails;

import java.util.Map;

/**
 * 护栏检查上下文
 *
 * @author yangqiong
 */
public record GuardrailContext(

        /**
         * Agent标识
         */
        String agentId,

        /**
         * Agent编码
         */
        String agentCode,

        /**
         * 会话ID
         */
        String sessionId,

        /**
         * 用户ID
         */
        String userId,

        /**
         * 作用域ID
         */
        String scopeId,

        /**
         * 扩展元数据
         */
        Map<String, Object> metadata
) {

    /**
     * 创建空上下文
     * @return
     */
    public static GuardrailContext empty() {
        return new GuardrailContext(null, null, null, null, null, Map.of());
    }

    /**
     * 创建指定agentId的上下文
     * @param agentId
     * @return
     */
    public static GuardrailContext ofAgent(String agentId) {
        return new GuardrailContext(agentId, null, null, null, null, Map.of());
    }

    /**
     * 创建指定agentId和sessionId的上下文
     * @param agentId
     * @param sessionId
     * @return
     */
    public static GuardrailContext of(String agentId, String sessionId) {
        return new GuardrailContext(agentId, null, sessionId, null, null, Map.of());
    }

    /**
     * 创建完整上下文
     * @param agentId
     * @param agentCode
     * @param sessionId
     * @param userId
     * @param scopeId
     * @param metadata
     * @return
     */
    public static GuardrailContext of(String agentId, String agentCode, String sessionId,
                                     String userId, String scopeId, Map<String, Object> metadata) {
        return new GuardrailContext(agentId, agentCode, sessionId, userId, scopeId,
                metadata == null ? Map.of() : metadata);
    }
}
