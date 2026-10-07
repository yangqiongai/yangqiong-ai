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
package com.yangqiongai.ai.agent.core.trace;

/**
 * 追踪上下文
 * @author yangqiong
 */
public class TraceContext {

    /**
     * 追踪ID
     */
    private String traceId;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 会话ID
     */
    private String sessionId;

    public TraceContext() {
    }

    public TraceContext(String traceId, String runId, String agentCode, String sessionId) {
        this.traceId = traceId;
        this.runId = runId;
        this.agentCode = agentCode;
        this.sessionId = sessionId;
    }

    /**
     * 从MDC构建追踪上下文
     * @param runId
     * @param agentCode
     * @param sessionId
     * @return
     */
    public static TraceContext fromMdc(String runId, String agentCode, String sessionId) {
        String traceId = org.slf4j.MDC.get("traceId");
        return new TraceContext(traceId, runId, agentCode, sessionId);
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
