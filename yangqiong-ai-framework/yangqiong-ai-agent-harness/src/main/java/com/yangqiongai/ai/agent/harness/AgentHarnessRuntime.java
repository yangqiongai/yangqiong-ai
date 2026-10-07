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

import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer;
import com.yangqiongai.ai.agent.runtime.event.ConfirmResult;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent运行时
 * @author yangqiong
 */
public class AgentHarnessRuntime implements AgentRuntime {

    /**
     * 独立引擎运行时委托
     */
    private final com.yangqiongai.agent.harness.core.AgentRuntime delegate;

    public AgentHarnessRuntime(com.yangqiongai.agent.harness.core.AgentRuntime delegate) {
        this.delegate = delegate;
    }

    /**
     * 同步调用Agent
     * @param inputs
     * @param context
     * @return
     */
    @Override
    public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
        List<com.yangqiongai.agent.harness.core.message.AgentMessage> harnessInputs = SpiConverters.toHarnessMessages(inputs);
        com.yangqiongai.agent.harness.engine.AgentRuntimeContext harnessContext = RuntimeTypeConverter.toHarness(context);
        return delegate.call(harnessInputs, harnessContext).map(SpiConverters::toRuntimeMessage);
    }

    /**
     * 流式输出Agent事件
     * @param inputs
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> streamEvents(List<AgentMessage> inputs, AgentRuntimeContext context) {
        List<com.yangqiongai.agent.harness.core.message.AgentMessage> harnessInputs = SpiConverters.toHarnessMessages(inputs);
        com.yangqiongai.agent.harness.engine.AgentRuntimeContext harnessContext = RuntimeTypeConverter.toHarness(context);
        return delegate.stream(harnessInputs, harnessContext).map(SpiConverters::toRuntimeEvent);
    }

    /**
     * 获取Agent名称
     * @return
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    /**
     * 恢复因人工确认暂停的Agent执行
     * @param confirmResults
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resume(List<ConfirmResult> confirmResults, AgentRuntimeContext context) {
        List<com.yangqiongai.agent.harness.core.event.ConfirmResult> harnessResults = new ArrayList<>();
        if (confirmResults != null) {
            for (ConfirmResult result : confirmResults) {
                harnessResults.add(RuntimeTypeConverter.toHarness(result));
            }
        }
        com.yangqiongai.agent.harness.engine.AgentRuntimeContext harnessContext = RuntimeTypeConverter.toHarness(context);
        return delegate.resume(harnessResults, harnessContext).map(SpiConverters::toRuntimeEvent);
    }

    /**
     * 恢复因澄清请求暂停的Agent执行
     * @param answers
     * @param context
     * @return
     */
    @Override
    public Flux<AgentEvent> resumeWithClarification(List<ClarificationAnswer> answers, AgentRuntimeContext context) {
        List<com.yangqiongai.agent.harness.core.event.ClarificationAnswer> harnessAnswers = new ArrayList<>();
        if (answers != null) {
            for (ClarificationAnswer answer : answers) {
                harnessAnswers.add(new com.yangqiongai.agent.harness.core.event.ClarificationAnswer(
                        answer.getToolCallId(), answer.getAnswer()));
            }
        }
        com.yangqiongai.agent.harness.engine.AgentRuntimeContext harnessContext = RuntimeTypeConverter.toHarness(context);
        return delegate.resumeWithClarification(harnessAnswers, harnessContext).map(SpiConverters::toRuntimeEvent);
    }

    /**
     * 获取被包装的独立引擎运行时
     * @return
     */
    com.yangqiongai.agent.harness.core.AgentRuntime getDelegate() {
        return delegate;
    }
}
