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
package com.yangqiongai.ai.agent.runtime.permission;

import com.yangqiongai.ai.agent.runtime.config.AgentPermissionDecision;

import java.util.Map;

/**
 * 统一权限策略门
 * <p>
 * 注入后覆盖运行时按白/黑名单、requireApproval自动装配的默认策略门，
 * 由实现方自行裁决工具调用的放行、拒绝或人工确认。
 * </p>
 * @author yangqiong
 */
public interface ToolPolicyGate {

    /**
     * 裁决工具调用是否放行
     * @param toolName 工具名称
     * @param toolCallId 工具调用ID
     * @param scopeId 隔离域ID，可为null
     * @param runId 运行ID，可为null
     * @param toolInput 工具调用入参，可为null
     * @return 权限决策
     */
    AgentPermissionDecision evaluate(String toolName, String toolCallId,
                                     String scopeId, String runId, Map<String, Object> toolInput);
}
