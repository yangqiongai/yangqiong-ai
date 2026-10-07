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
package com.yangqiongai.ai.agent.core.executor;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

import java.time.Instant;
import java.util.List;

/**
 * 引擎确认恢复句柄
 * <p>
 * 引擎审批暂停时保存的恢复要素与待确认工具清单，暂停快照写入运行时上下文的attributes，
 * 恢复时必须原样复用暂停时的运行时与上下文引用透传引擎续跑。
 * </p>
 * @author yangqiong
 */
public class ConfirmResumeHandle {

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 待确认工具调用清单
     */
    private final List<AgentToolUseBlock> pendingToolCalls;

    /**
     * 暂停时的执行器（恢复续跑宿主）
     */
    private final ReActAgentExecutor executor;

    /**
     * 暂停时的运行时
     */
    private final AgentRuntime runtime;

    /**
     * 暂停时的运行时上下文（含引擎暂停快照）
     */
    private final AgentRuntimeContext runtimeContext;

    /**
     * 暂停时的执行上下文
     */
    private final AgentContext agentContext;

    /**
     * 注册时间
     */
    private final Instant createdAt;

    public ConfirmResumeHandle(String sessionId, List<AgentToolUseBlock> pendingToolCalls,
                               ReActAgentExecutor executor, AgentRuntime runtime,
                               AgentRuntimeContext runtimeContext, AgentContext agentContext) {
        this.sessionId = sessionId;
        this.pendingToolCalls = pendingToolCalls == null ? List.of() : List.copyOf(pendingToolCalls);
        this.executor = executor;
        this.runtime = runtime;
        this.runtimeContext = runtimeContext;
        this.agentContext = agentContext;
        this.createdAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<AgentToolUseBlock> getPendingToolCalls() {
        return pendingToolCalls;
    }

    public ReActAgentExecutor getExecutor() {
        return executor;
    }

    public AgentRuntime getRuntime() {
        return runtime;
    }

    public AgentRuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public AgentContext getAgentContext() {
        return agentContext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
