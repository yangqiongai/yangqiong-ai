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
 * Agent思考块增量事件
 * @author yangqiong
 */
public final class AgentThinkingBlockDeltaEvent extends AgentEvent {

    /**
     * 思考增量内容
     */
    private final String delta;

    public AgentThinkingBlockDeltaEvent(String delta) {
        super(AgentEventType.THINKING_BLOCK_DELTA, delta);
        this.delta = delta;
    }

    /**
     * 获取思考增量内容
     * @return
     */
    public String getDelta() {
        return delta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AgentThinkingBlockDeltaEvent that = (AgentThinkingBlockDeltaEvent) o;
        return Objects.equals(delta, that.delta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delta);
    }

    @Override
    public String toString() {
        return "AgentThinkingBlockDeltaEvent{delta='" + delta + "'}";
    }
}
