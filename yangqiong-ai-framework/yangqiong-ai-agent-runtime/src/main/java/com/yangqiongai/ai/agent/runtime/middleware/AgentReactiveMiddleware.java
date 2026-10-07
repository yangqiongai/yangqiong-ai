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
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

/**
 * Agent响应式中间件
 * <p>
 * 提供LLM调用前后的响应式拦截钩子，所有方法默认透传到下一层。
 * 仅需响应式拦截的组件可实现此接口，无需实现同步钩子。
 * </p>
 * @author yangqiong
 */
public interface AgentReactiveMiddleware {

    /**
     * Agent整体调用拦截（洋葱模型最外层）
     * <p>
     * 可用于全局追踪、限流、缓存等。默认透传到下一层。
     * </p>
     * @param context
     * @param input
     * @param next
     * @return
     */
    default Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                      Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /**
     * 推理阶段拦截（LLM调用前后）
     * <p>
     * 可用于Token计量、推理缓存、prompt改写等。默认透传到下一层。
     * </p>
     * @param context
     * @param input
     * @param next
     * @return
     */
    default Flux<AgentEvent> onReasoning(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        return next.apply(input);
    }

    /**
     * 执行阶段拦截（批量工具调用）
     * <p>
     * 可用于并行控制、超时管理、权限校验等。默认透传到下一层。
     * </p>
     * @param context
     * @param toolCalls
     * @param next
     * @return
     */
    default Flux<AgentEvent> onActing(AgentRuntimeContext context, List<AgentToolUseBlock> toolCalls,
                                       Function<List<AgentToolUseBlock>, Flux<AgentEvent>> next) {
        return next.apply(toolCalls);
    }

    /**
     * 原始LLM调用拦截
     * <p>
     * 可用于模型路由、fallback、结果缓存等。默认透传到下一层。
     * </p>
     * @param context
     * @param input
     * @param next
     * @return
     */
    default Mono<AgentMessage> onModelCall(AgentRuntimeContext context, List<AgentMessage> input,
                                            Function<List<AgentMessage>, Mono<AgentMessage>> next) {
        return next.apply(input);
    }
}
