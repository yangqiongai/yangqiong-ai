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
package com.yangqiongai.ai.agent.runtime.trace;

import java.util.List;

/**
 * 上下文快照
 * <p>
 * 描述一次模型调用前注入模型的完整上下文，供{@link ContextSnapshotListener}落库取证。
 * </p>
 * @author yangqiong
 */
public final class ContextSnapshot {

    /**
     * 任务ID
     */
    private final String taskId;

    /**
     * 追踪ID
     */
    private final String traceId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 代理编码
     */
    private final String agentCode;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * 模型编码
     */
    private final String modelCode;

    /**
     * 当次任务内的模型调用序号（从1开始）
     */
    private final int callSeq;

    /**
     * 本次调用注入模型的上下文消息列表
     */
    private final List<ContextMessage> messages;

    /**
     * 全参构造
     * @param taskId
     * @param traceId
     * @param sessionId
     * @param agentCode
     * @param scopeId
     * @param modelCode
     * @param callSeq
     * @param messages
     */
    public ContextSnapshot(String taskId, String traceId, String sessionId, String agentCode,
                           String scopeId, String modelCode, int callSeq, List<ContextMessage> messages) {
        this.taskId = taskId;
        this.traceId = traceId;
        this.sessionId = sessionId;
        this.agentCode = agentCode;
        this.scopeId = scopeId;
        this.modelCode = modelCode;
        this.callSeq = callSeq;
        this.messages = messages;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public int getCallSeq() {
        return callSeq;
    }

    public List<ContextMessage> getMessages() {
        return messages;
    }
}
