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

import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

import java.util.List;

/**
 * 需要人工确认事件
 * @author yangqiong
 */
public class RequireUserConfirmEvent extends AgentEvent {

    /**
     * 待确认的工具调用列表
     */
    private final List<AgentToolUseBlock> pendingToolCalls;

    public RequireUserConfirmEvent(List<AgentToolUseBlock> pendingToolCalls) {
        super(AgentEventType.REQUIRE_USER_CONFIRM, pendingToolCalls);
        this.pendingToolCalls = pendingToolCalls;
    }

    /**
     * 获取待确认的工具调用列表
     * @return
     */
    public List<AgentToolUseBlock> getPendingToolCalls() {
        return pendingToolCalls;
    }
}
