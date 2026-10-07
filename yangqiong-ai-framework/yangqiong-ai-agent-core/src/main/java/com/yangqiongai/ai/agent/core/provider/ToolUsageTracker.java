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
package com.yangqiongai.ai.agent.core.provider;

/**
 * 工具使用量追踪接口
 * <p>
 * 定义在ai-agent-core中，由数据层模块实现。
 * AgentMiddlewareAdapter在onToolCall/onToolResult中记录调用事件。
 * </p>
 * @author yangqiong
 */
public interface ToolUsageTracker {

    /**
     * 工具被调用
     * @param toolCode
     * @param toolName
     * @param toolDesc
     * @param scopeId 作用域ID，可为null（全局统计）
     */
    void bumpCall(String toolCode, String toolName, String toolDesc, String scopeId);

    /**
     * 工具调用结果回填（成功/失败计数）
     * @param toolCode
     * @param success
     * @param scopeId 作用域ID，可为null（全局统计）
     */
    void bumpResult(String toolCode, boolean success, String scopeId);

    /**
     * 是否启用使用量追踪
     * @return
     */
    default boolean isEnabled() {
        return true;
    }
}
