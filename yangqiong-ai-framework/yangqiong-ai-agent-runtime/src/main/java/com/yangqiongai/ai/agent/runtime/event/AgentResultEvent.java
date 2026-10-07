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

import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

/**
 * Agent结果事件
 * @author yangqiong
 */
public final class AgentResultEvent extends AgentEvent {

    /**
     * Agent消息结果
     */
    private final AgentMessage result;

    /**
     * 累计Token用量
     */
    private final AgentChatUsage usage;

    public AgentResultEvent(AgentMessage result) {
        this(result, null);
    }

    public AgentResultEvent(AgentMessage result, AgentChatUsage usage) {
        super(AgentEventType.AGENT_RESULT, result);
        this.result = result;
        this.usage = usage;
    }

    /**
     * 获取Agent消息结果
     * @return
     */
    public AgentMessage getResult() {
        return result;
    }

    /**
     * 获取累计Token用量
     * @return
     */
    public AgentChatUsage getUsage() {
        return usage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AgentResultEvent that = (AgentResultEvent) o;
        return Objects.equals(result, that.result) && Objects.equals(usage, that.usage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), result, usage);
    }

    @Override
    public String toString() {
        return "AgentResultEvent{result=" + result + ", usage=" + usage + "}";
    }
}
