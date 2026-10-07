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
package com.yangqiongai.ai.agent.runtime.event;

import java.util.Objects;

/**
 * Agent事件基类
 * @author yangqiong
 */
public class AgentEvent {

    /**
     * 事件类型
     */
    private final AgentEventType type;

    /**
     * 事件载荷
     */
    private final Object payload;

    /**
     * 父级代理路径，用于子代理事件冒泡时标记来源，可空
     */
    private final String parentAgentPath;

    /**
     * CUSTOM事件携带的原始类型名（如SDK的EXCEED_MAX_ITERS），规范事件为null
     */
    private final String rawTypeName;

    protected AgentEvent(AgentEventType type, Object payload) {
        this(type, payload, null, null);
    }

    protected AgentEvent(AgentEventType type, Object payload, String parentAgentPath) {
        this(type, payload, parentAgentPath, null);
    }

    private AgentEvent(AgentEventType type, Object payload, String parentAgentPath, String rawTypeName) {
        this.type = type;
        this.payload = payload;
        this.parentAgentPath = parentAgentPath;
        this.rawTypeName = rawTypeName;
    }

    /**
     * 创建完成事件
     * @return
     */
    public static AgentEvent completed() {
        return new AgentEvent(AgentEventType.COMPLETED, null);
    }

    /**
     * 创建通用事件
     * @param type
     * @param payload
     * @return
     */
    public static AgentEvent of(AgentEventType type, Object payload) {
        return new AgentEvent(type, payload);
    }

    /**
     * 创建带父级代理路径的事件，用于子代理事件冒泡
     * @param type
     * @param payload
     * @param parentAgentPath
     * @return
     */
    public static AgentEvent of(AgentEventType type, Object payload, String parentAgentPath) {
        return new AgentEvent(type, payload, parentAgentPath);
    }

    /**
     * 创建单侧特有事件透传
     * @param rawTypeName 原始事件类型名
     * @param payload 原始载荷（原样透传，不做字段级转换）
     * @return
     */
    public static AgentEvent custom(String rawTypeName, Object payload) {
        return new AgentEvent(AgentEventType.CUSTOM, payload, null, rawTypeName);
    }

    /**
     * 获取事件类型
     * @return
     */
    public AgentEventType getType() {
        return type;
    }

    /**
     * 获取事件载荷
     * @return
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * 获取父级代理路径
     * @return
     */
    public String getParentAgentPath() {
        return parentAgentPath;
    }

    /**
     * 获取CUSTOM事件携带的原始类型名，规范事件返回null
     * @return
     */
    public String getRawTypeName() {
        return rawTypeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentEvent that = (AgentEvent) o;
        return type == that.type && Objects.equals(payload, that.payload)
                && Objects.equals(parentAgentPath, that.parentAgentPath)
                && Objects.equals(rawTypeName, that.rawTypeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, payload, parentAgentPath, rawTypeName);
    }

    @Override
    public String toString() {
        return "AgentEvent{type=" + type + ", payload=" + payload
                + (parentAgentPath != null ? ", parentAgentPath=" + parentAgentPath : "")
                + (rawTypeName != null ? ", rawTypeName=" + rawTypeName : "") + "}";
    }
}
