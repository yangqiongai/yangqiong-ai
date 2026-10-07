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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

import java.util.List;

/**
 * Agent检查点
 * <p>
 * 某一轮迭代结束时执行现场的完整快照，供中断恢复与续跑。
 * </p>
 * @author yangqiong
 */
public final class AgentCheckpoint {

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 隔离域ID
     */
    private final String scopeId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 快照时的迭代轮次
     */
    private final int iteration;

    /**
     * 快照时的完整消息历史
     */
    private final List<AgentMessage> messages;

    /**
     * 待执行的工具调用列表
     */
    private final List<AgentToolUseBlock> pendingToolCalls;

    /**
     * 已完成副作用工具调用ID集合
     */
    private final List<String> completedToolUseIds;

    /**
     * 快照时间戳（毫秒）
     */
    private final long timestamp;

    /**
     * 乐观锁版本号
     */
    private final long version;

    @JsonCreator
    public AgentCheckpoint(@JsonProperty("runId") String runId,
                           @JsonProperty("scopeId") String scopeId,
                           @JsonProperty("sessionId") String sessionId,
                           @JsonProperty("iteration") int iteration,
                           @JsonProperty("messages") List<AgentMessage> messages,
                           @JsonProperty("pendingToolCalls") List<AgentToolUseBlock> pendingToolCalls,
                           @JsonProperty("completedToolUseIds") List<String> completedToolUseIds,
                           @JsonProperty("timestamp") long timestamp,
                           @JsonProperty("version") long version) {
        this.runId = runId;
        this.scopeId = scopeId;
        this.sessionId = sessionId;
        this.iteration = iteration;
        this.messages = messages;
        this.pendingToolCalls = pendingToolCalls;
        this.completedToolUseIds = completedToolUseIds;
        this.timestamp = timestamp;
        this.version = version;
    }

    /**
     * 获取运行ID
     * @return
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 获取隔离域ID
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取会话ID
     * @return
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取快照时的迭代轮次
     * @return
     */
    public int getIteration() {
        return iteration;
    }

    /**
     * 获取快照时的完整消息历史
     * @return
     */
    public List<AgentMessage> getMessages() {
        return messages;
    }

    /**
     * 获取待执行的工具调用列表
     * @return
     */
    public List<AgentToolUseBlock> getPendingToolCalls() {
        return pendingToolCalls;
    }

    /**
     * 获取已完成副作用工具调用ID集合
     * @return
     */
    public List<String> getCompletedToolUseIds() {
        return completedToolUseIds;
    }

    /**
     * 获取快照时间戳（毫秒）
     * @return
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 获取版本号
     * @return
     */
    public long getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "AgentCheckpoint{runId=" + runId + ", sessionId=" + sessionId
                + ", iteration=" + iteration + ", version=" + version + "}";
    }
}
