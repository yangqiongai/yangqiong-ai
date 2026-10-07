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
package com.yangqiongai.ai.memory.agentmemory.event;

/**
 * Agent记忆隔离事件（可疑来源进入隔离态，企业侧监听审计）
 * @author yangqiong
 */
public class AgentMemoryQuarantinedEvent {

    /**
     * 记忆条目ID
     */
    private final Long entryId;

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 用户锚点
     */
    private final String userAnchor;

    /**
     * 隔离原因
     */
    private final String reason;

    public AgentMemoryQuarantinedEvent(Long entryId, String agentCode, String userAnchor, String reason) {
        this.entryId = entryId;
        this.agentCode = agentCode;
        this.userAnchor = userAnchor;
        this.reason = reason;
    }

    public Long getEntryId() {
        return entryId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getUserAnchor() {
        return userAnchor;
    }

    public String getReason() {
        return reason;
    }
}
