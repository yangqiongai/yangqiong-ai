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
package com.yangqiongai.ai.agent.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文
 * @author yangqiong
 */
public class ToolExecutionContext {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 上下文属性
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 设置上下文属性
     * @param key
     * @param value
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取上下文属性
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 获取会话ID
     * @return
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置会话ID
     * @param sessionId
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取Agent编码
     * @return
     */
    public String getAgentCode() {
        return agentCode;
    }

    /**
     * 设置Agent编码
     * @param agentCode
     */
    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }
}
