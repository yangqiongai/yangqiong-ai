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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.middleware.AgentMiddleware;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Agent中间件适配器
 * @author yangqiong
 */
public class AgentMiddlewareAdapter implements AgentMiddleware {

    /**
     * 框架中间件委托
     */
    private final com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware delegate;

    public AgentMiddlewareAdapter(com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware delegate) {
        this.delegate = delegate;
    }

    /**
     * 是否尾部注入型中间件，透传委托方标记
     * @return
     */
    @Override
    public boolean isTailInjection() {
        return delegate.isTailInjection();
    }

    /**
     * 系统提示词处理钩子
     * @param systemPrompt
     * @param context
     * @return
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        return delegate.onSystemPrompt(systemPrompt, SpiConverters.toRuntimeContext(context));
    }

    /**
     * 工具调用前拦截钩子
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    @Override
    public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        return delegate.onToolCall(toolName, input, SpiConverters.toRuntimeContext(context));
    }

    /**
     * 工具调用后拦截钩子
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    @Override
    public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
        com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock runtimeResult = SpiConverters.toRuntimeToolResult(result);
        com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock converted = delegate.onToolResult(toolName, runtimeResult, SpiConverters.toRuntimeContext(context));
        if (converted == runtimeResult) {
            // 中间件未修改结果时保留原始块，避免类型转换重建丢失clarificationRequest澄清标志导致引擎无法暂停
            return result;
        }
        return SpiConverters.toHarnessToolResult(converted);
    }

    /**
     * 消息处理钩子
     * @param message
     * @param context
     * @return
     */
    @Override
    public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
        com.yangqiongai.ai.agent.runtime.message.AgentMessage runtimeMessage = SpiConverters.toRuntimeMessage(message);
        com.yangqiongai.ai.agent.runtime.message.AgentMessage converted = delegate.onMessage(runtimeMessage, SpiConverters.toRuntimeContext(context));
        return SpiConverters.toHarnessMessage(converted);
    }

    /**
     * 错误处理钩子
     * @param throwable
     * @param context
     * @param phase
     */
    @Override
    public void onError(Throwable throwable, AgentRuntimeContext context, String phase) {
        delegate.onError(throwable, SpiConverters.toRuntimeContext(context), phase);
    }

    /**
     * Agent整体调用拦截
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                    Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        com.yangqiongai.ai.agent.runtime.AgentRuntimeContext runtimeContext = SpiConverters.toRuntimeContext(context);
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> runtimeInput = SpiConverters.toRuntimeMessages(input);
        Function<List<com.yangqiongai.ai.agent.runtime.message.AgentMessage>, Flux<com.yangqiongai.ai.agent.runtime.event.AgentEvent>> runtimeNext =
                messages -> next.apply(SpiConverters.toHarnessMessages(messages)).map(SpiConverters::toRuntimeEvent);
        return delegate.onAgent(runtimeContext, runtimeInput, runtimeNext).mapNotNull(SpiConverters::toHarnessEvent);
    }

    /**
     * 推理阶段拦截
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                        Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        com.yangqiongai.ai.agent.runtime.AgentRuntimeContext runtimeContext = SpiConverters.toRuntimeContext(context);
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> runtimeInput = SpiConverters.toRuntimeMessages(input);
        Function<List<com.yangqiongai.ai.agent.runtime.message.AgentMessage>, Flux<com.yangqiongai.ai.agent.runtime.event.AgentEvent>> runtimeNext =
                messages -> next.apply(SpiConverters.toHarnessMessages(messages)).map(SpiConverters::toRuntimeEvent);
        return delegate.onReasoning(runtimeContext, runtimeInput, runtimeNext).map(SpiConverters::toHarnessEvent);
    }

    /**
     * 执行阶段拦截
     * @param context
     * @param toolCalls
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onActing(AgentRuntimeContext context, List<AgentToolUseBlock> toolCalls,
                                     Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
        com.yangqiongai.ai.agent.runtime.AgentRuntimeContext runtimeContext = SpiConverters.toRuntimeContext(context);
        List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> runtimeCalls = SpiConverters.toRuntimeToolUses(toolCalls);
        Function<List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock>, Flux<com.yangqiongai.ai.agent.runtime.event.AgentEvent>> runtimeNext =
                calls -> next.apply(SpiConverters.toHarnessToolUses(calls)).map(SpiConverters::toRuntimeEvent);
        return delegate.onActing(runtimeContext, runtimeCalls, runtimeNext).mapNotNull(SpiConverters::toHarnessEvent);
    }

    /**
     * 原始LLM调用拦截
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Mono<AgentMessage> onModelCall(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Mono<AgentMessage>> next) {
        com.yangqiongai.ai.agent.runtime.AgentRuntimeContext runtimeContext = SpiConverters.toRuntimeContext(context);
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> runtimeInput = SpiConverters.toRuntimeMessages(input);
        Function<List<com.yangqiongai.ai.agent.runtime.message.AgentMessage>, Mono<com.yangqiongai.ai.agent.runtime.message.AgentMessage>> runtimeNext =
                messages -> next.apply(SpiConverters.toHarnessMessages(messages)).map(SpiConverters::toRuntimeMessage);
        return delegate.onModelCall(runtimeContext, runtimeInput, runtimeNext).map(SpiConverters::toHarnessMessage);
    }

    /**
     * 获取被包装的框架中间件
     * @return
     */
    com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware getDelegate() {
        return delegate;
    }
}
