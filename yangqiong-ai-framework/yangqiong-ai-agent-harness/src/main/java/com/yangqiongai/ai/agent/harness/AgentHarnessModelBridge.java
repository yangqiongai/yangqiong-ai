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

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.model.AgentChatResponse;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Harness模型桥接器
 * <p>
 * 将引擎的AgentModel适配为framework的AgentModel
 * </p>
 * @author yangqiong
 */
public class AgentHarnessModelBridge implements AgentModel {

    /**
     * 独立引擎模型委托
     */
    private final com.yangqiongai.agent.harness.core.model.AgentModel delegate;

    public AgentHarnessModelBridge(com.yangqiongai.agent.harness.core.model.AgentModel delegate) {
        this.delegate = delegate;
    }

    /**
     * 同步生成模型响应
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
        List<com.yangqiongai.agent.harness.core.message.AgentMessage> harnessMessages = SpiConverters.toHarnessMessages(messages);
        com.yangqiongai.agent.harness.core.model.AgentGenerateOptions harnessOptions = RuntimeTypeConverter.toHarness(options);
        com.yangqiongai.agent.harness.core.model.AgentChatResponse harnessResponse = delegate.generate(harnessMessages, tools, harnessOptions);
        return RuntimeTypeConverter.toRuntime(harnessResponse);
    }

    /**
     * 流式生成模型响应
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
        List<com.yangqiongai.agent.harness.core.message.AgentMessage> harnessMessages = SpiConverters.toHarnessMessages(messages);
        com.yangqiongai.agent.harness.core.model.AgentGenerateOptions harnessOptions = RuntimeTypeConverter.toHarness(options);
        return delegate.stream(harnessMessages, tools, harnessOptions)
                .map(RuntimeTypeConverter::toRuntime);
    }

    /**
     * 获取被包装的独立引擎模型
     * @return
     */
    com.yangqiongai.agent.harness.core.model.AgentModel getDelegate() {
        return delegate;
    }
}
