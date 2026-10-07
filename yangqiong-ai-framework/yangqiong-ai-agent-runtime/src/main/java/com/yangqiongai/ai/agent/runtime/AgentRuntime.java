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
package com.yangqiongai.ai.agent.runtime;

import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer;
import com.yangqiongai.ai.agent.runtime.event.ConfirmResult;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent运行时
 * @author yangqiong
 */
public interface AgentRuntime {

    /**
     * 同步调用Agent
     * @param inputs
     * @param context
     * @return
     */
    Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context);

    /**
     * 流式输出Agent事件
     * @param inputs
     * @param context
     * @return
     */
    Flux<AgentEvent> streamEvents(List<AgentMessage> inputs, AgentRuntimeContext context);

    /**
     * 获取Agent名称
     * @return
     */
    String getName();

    /**
     * 恢复因人工确认暂停的Agent执行
     * @param confirmResults
     * @param context
     * @return
     */
    default Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, AgentRuntimeContext context) {
        return Flux.error(new UnsupportedOperationException("当前运行时不支持resume操作"));
    }

    /**
     * 恢复因澄清请求暂停的Agent执行
     * @param answers
     * @param context
     * @return
     */
    default Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, AgentRuntimeContext context) {
        return Flux.error(new UnsupportedOperationException("当前运行时不支持resumeWithClarification操作"));
    }
}
