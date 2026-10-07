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
package com.yangqiongai.ai.agent.core.middleware;

import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer;
import com.yangqiongai.ai.agent.core.trace.TraceCollector;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent追踪中间件，补充AgentMiddleware的追踪能力
 * <p>
 * 监听Agent执行全生命周期事件，记录耗时、错误分类等追踪数据。
 * 原Hook模式的事件通知迁移为AgentMiddleware的同步钩子方法。
 * </p>
 *
 * @author yangqiong
 */
public class AgentTraceHook implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceHook.class);

    private final TraceCollector traceCollector;
    private final ErrorCategorizer errorCategorizer;

    /**
     * 各阶段开始时间戳缓存
     */
    private final Map<String, Long> phaseStartTimes = new ConcurrentHashMap<>();

    public AgentTraceHook(TraceCollector traceCollector, ErrorCategorizer errorCategorizer) {
        this.traceCollector = traceCollector;
        this.errorCategorizer = errorCategorizer;
    }

    /**
     * 系统提示词处理，记录追踪日志
     * @param systemPrompt
     * @param context
     * @return
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        log.debug("TraceHook onSystemPrompt");
        phaseStartTimes.put("systemPrompt", System.currentTimeMillis());
        return systemPrompt;
    }

    /**
     * 工具调用前拦截，记录开始时间并恢复SessionContext
     * <p>
     * 在工具执行线程中恢复SessionContext，解决工具回调在boundedElastic线程
     * 导致ThreadLocal丢失userId/sessionId的问题（影响DbLongTermMemory等）
     * </p>
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    @Override
    public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        log.debug("TraceHook onToolCall: tool={}", toolName);
        phaseStartTimes.put("acting_" + toolName, System.currentTimeMillis());
        // 在工具执行线程中恢复SessionContext
        propagateSessionContext(context);
        return input;
    }

    /**
     * 工具调用后拦截，记录耗时
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    @Override
    public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
        Long startTime = phaseStartTimes.remove("acting_" + toolName);
        long latencyMs = startTime != null ? System.currentTimeMillis() - startTime : -1;
        int resultSize = result != null ? result.getTextContent().length() : 0;
        log.debug("TraceHook onToolResult: tool={}, latencyMs={}, resultSize={}", toolName, latencyMs, resultSize);
        // TODO 原Hook ERROR事件中的错误分类逻辑依赖异常事件通知，迁移后需在调用方补充
        return result;
    }

    /**
     * 消息处理拦截，记录追踪日志
     * @param message
     * @param context
     * @return
     */
    @Override
    public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
        log.debug("TraceHook onMessage");
        return message;
    }

    /**
     * 从AgentRuntimeContext中恢复SessionContext ThreadLocal
     * <p>
     * 工具回调可能在boundedElastic线程池中执行，与原始请求线程不同，
     * 导致SessionContext的ThreadLocal（userId、sessionId、scopeId）丢失。
     * 通过AgentRuntimeContext恢复这些值到当前线程的ThreadLocal。
     * </p>
     * @param context
     */
    private void propagateSessionContext(AgentRuntimeContext context) {
        if (context == null) {
            return;
        }
        if (SessionContext.getUserId() == null && context.getUserId() != null) {
            SessionContext.setUserId(context.getUserId());
        }
        if (SessionContext.getSessionId() == null && context.getSessionId() != null) {
            SessionContext.setSessionId(context.getSessionId());
        }
        if (SessionContext.getScopeId() == null) {
            SessionContext.setScopeId(ScopeContext.getScopeId());
        }
    }
}
