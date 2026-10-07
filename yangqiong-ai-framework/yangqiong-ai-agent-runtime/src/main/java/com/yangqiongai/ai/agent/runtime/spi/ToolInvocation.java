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

import java.util.Map;

/**
 * 工具调用前置上下文
 * <p>
 * 携带调用身份与入参，供守卫做放行判定与审计留痕。
 * </p>
 * @author yangqiong
 */
public class ToolInvocation {

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
     * 工具入参
     */
    private final Map<String, Object> input;

    public ToolInvocation(String agentCode, String toolName, String toolUseId, String scopeId, String userId,
                          String runId, Map<String, Object> input) {
        this.agentCode = agentCode;
        this.toolName = toolName;
        this.toolUseId = toolUseId;
        this.scopeId = scopeId;
        this.userId = userId;
        this.runId = runId;
        this.input = input;
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

    public Map<String, Object> getInput() {
        return input;
    }
}
