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

import java.util.Map;
import java.util.Objects;

/**
 * Agent工具调用增量事件
 * @author yangqiong
 */
public final class AgentToolCallDeltaEvent extends AgentEvent {

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 工具调用ID
     */
    private final String toolUseId;

    /**
     * 工具调用输入参数
     */
    private final Map<String, Object> input;

    public AgentToolCallDeltaEvent() {
        super(AgentEventType.TOOL_CALL_DELTA, null);
        this.toolName = null;
        this.toolUseId = null;
        this.input = null;
    }

    /**
     * 构造携带工具调用数据的增量事件
     * @param toolName
     * @param toolUseId
     * @param input
     */
    public AgentToolCallDeltaEvent(String toolName, String toolUseId, Map<String, Object> input) {
        super(AgentEventType.TOOL_CALL_DELTA, toolName);
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.input = input;
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolUseId() {
        return toolUseId;
    }

    /**
     * 获取工具调用输入参数
     * @return
     */
    public Map<String, Object> getInput() {
        return input;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AgentToolCallDeltaEvent that = (AgentToolCallDeltaEvent) o;
        return Objects.equals(toolName, that.toolName)
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), toolName, toolUseId, input);
    }

    @Override
    public String toString() {
        return "AgentToolCallDeltaEvent{toolName='" + toolName + "', toolUseId='" + toolUseId + "'}";
    }
}
