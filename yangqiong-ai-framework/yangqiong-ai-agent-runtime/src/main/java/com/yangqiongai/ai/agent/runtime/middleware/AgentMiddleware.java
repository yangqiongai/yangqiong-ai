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
package com.yangqiongai.ai.agent.runtime.middleware;

import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;

import java.util.Map;

/**
 * Agent中间件
 * <p>
 * 继承{@link AgentReactiveMiddleware}的洋葱模型响应式钩子，并补充同步钩子。
 * 所有方法均有默认透传实现，实现类按需覆写即可。
 * </p>
 * @author yangqiong
 */
public interface AgentMiddleware extends AgentReactiveMiddleware {

    /**
     * 是否尾部注入型中间件
     * <p>
     * 标记为true的调用方中间件将追加到引擎内置注入器（技能摘要、工具目录）之后执行，
     * 使其注入的业务上下文（如运行记忆）位于系统提示词末尾，贴近用户消息获得更强注意力。
     * </p>
     * @return
     */
    default boolean isTailInjection() {
        return false;
    }

    /**
     * 系统提示词处理钩子，返回可能修改后的提示词
     * @param systemPrompt
     * @param context
     * @return
     */
    default String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        return systemPrompt;
    }

    /**
     * 工具调用前拦截钩子，返回可能修改后的输入
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    default Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        return input;
    }

    /**
     * 工具调用后拦截钩子，返回可能修改后的结果
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    default AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
        return result;
    }

    /**
     * 消息处理钩子，返回可能修改后的消息
     * @param message
     * @param context
     * @return
     */
    default AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
        return message;
    }

    /**
     * 错误处理钩子，由provider在reactive流程的onErrorResume中调用
     * <p>
     * phase取值：agent / reasoning / acting / modelCall，用于区分错误发生阶段。
     * 实现方可根据phase决定日志级别或特殊处理（如TOOL_FAILURE仅warn）。
     * </p>
     * @param throwable
     * @param context
     * @param phase
     */
    default void onError(Throwable throwable, AgentRuntimeContext context, String phase) {
    }
}
