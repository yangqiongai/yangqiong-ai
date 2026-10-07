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
package com.yangqiongai.ai.agent.runtime.spi;

/**
 * 工具调用结果上下文
 * <p>
 * 携带调用结果或失败信息，供守卫做事后审计与异常检测。
 * </p>
 * @author yangqiong
 */
public class ToolInvocationOutcome {

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 工具调用ID
     */
    private final String toolUseId;

    /**
     * 租户标识
     */
    private final String scopeId;

    /**
     * 用户标识
     */
    private final String userId;

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 是否被守卫拒绝（未进入实际执行）
     */
    private final boolean denied;

    /**
     * 是否执行成功
     */
    private final boolean success;

    /**
     * 摘要信息（已脱敏，失败时为错误摘要）
     */
    private final String summary;

    /**
     * 执行耗时毫秒
     */
    private final long durationMillis;

    public ToolInvocationOutcome(String agentCode, String toolName, String toolUseId, String scopeId, String userId,
                                 String runId, boolean denied, boolean success,
                                 String summary, long durationMillis) {
        this.agentCode = agentCode;
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.scopeId = scopeId;
        this.userId = userId;
        this.runId = runId;
        this.denied = denied;
        this.success = success;
        this.summary = summary;
        this.durationMillis = durationMillis;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRunId() {
        return runId;
    }

    public boolean isDenied() {
        return denied;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public long getDurationMillis() {
        return durationMillis;
    }
}
