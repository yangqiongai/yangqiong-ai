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

/**
 * Agent事件类型
 * @author yangqiong
 */
public enum AgentEventType {

    TEXT_BLOCK_DELTA,

    THINKING_BLOCK_DELTA,

    TOOL_CALL_DELTA,

    AGENT_START,

    AGENT_END,

    MODEL_CALL_START,

    MODEL_CALL_END,

    TOOL_CALL_START,

    TOOL_CALL_END,

    AGENT_RESULT,

    REQUIRE_USER_CONFIRM,

    INTERRUPTED,

    COMPLETED,

    ERROR,

    /**
     * 用户澄清请求（引擎 RequireUserClarificationEvent）
     */
    REQUIRE_USER_CLARIFICATION,

    /**
     * 引擎路由事件（当前引擎无发出方，对齐预留）
     */
    ENGINE_ROUTED,

    /**
     * 范式阶段事件（Reflexion/Self-Refine等反思范式的EVALUATE/REFLECT/CRITIQUE/REVISE阶段，载荷ParadigmStageInfo）
     */
    PARADIGM_STAGE,

    /**
     * Token预算告警
     */
    TOKEN_BUDGET_WARN,

    /**
     * Token预算超限
     */
    TOKEN_BUDGET_EXCEEDED,

    /**
     * 成本预算告警
     */
    COST_BUDGET_WARN,

    /**
     * 成本预算超限
     */
    COST_BUDGET_EXCEEDED,

    /**
     * 单侧特有事件透传，原始类型名见 AgentEvent.getRawTypeName()
     */
    CUSTOM
}
