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
package com.yangqiongai.ai.agent.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl;

/**
 * Agent运行时上下文
 * <p>
 * 可变上下文：sessionId/userId 不可变，attributes 为可变映射，
 * 供中间件在执行过程中写入 SkillBox、TaskStepRecorder 等运行时对象。
 * </p>
 * @author yangqiong
 */
public class AgentRuntimeContext {

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * 中断控制器
     */
    private final AgentInterruptControl interruptControl;

    /**
     * 扩展属性
     */
    private final Map<String, Object> attributes;

    private AgentRuntimeContext(String sessionId, String userId, AgentInterruptControl interruptControl,
                                Map<String, Object> attributes) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.interruptControl = interruptControl;
        this.attributes = attributes != null ? new ConcurrentHashMap<>(attributes) : new ConcurrentHashMap<>();
    }

    /**
     * 创建空上下文
     * @return
     */
    public static AgentRuntimeContext empty() {
        return new AgentRuntimeContext(null, null, null, new HashMap<>());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取会话ID
     * @return
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取用户ID
     * @return
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取中断控制器
     * @return
     */
    public AgentInterruptControl getInterruptControl() {
        return interruptControl;
    }

    /**
     * 获取扩展属性
     * @return
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 写入扩展属性
     * @param key
     * @param value
     */
    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 读取扩展属性
     * @param key
     * @return
     */
    public Object get(String key) {
        return attributes.get(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentRuntimeContext that = (AgentRuntimeContext) o;
        return Objects.equals(sessionId, that.sessionId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(interruptControl, that.interruptControl)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, userId, interruptControl, attributes);
    }

    @Override
    public String toString() {
        return "AgentRuntimeContext{sessionId='" + sessionId + "', userId='" + userId + "', attributeSize=" + attributes.size() + "}";
    }

    /**
     * 运行时上下文构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 会话ID
         */
        private String sessionId;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 中断控制器
         */
        private AgentInterruptControl interruptControl;

        /**
         * 扩展属性
         */
        private Map<String, Object> attributes = new HashMap<>();

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder interruptControl(AgentInterruptControl interruptControl) {
            this.interruptControl = interruptControl;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
            return this;
        }

        public AgentRuntimeContext build() {
            return new AgentRuntimeContext(sessionId, userId, interruptControl, attributes);
        }
    }
}
