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
package com.yangqiongai.ai.agent.core.processor;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.Mockito;

/**
 * Agent思考模式配置测试
 * @author yangqiong
 */
class AgentProcessorReasoningTest {

    /**
     * 构造带agentConfig思考默认值的请求
     * @param enabled
     * @param effort
     * @return
     */
    private AgentRequest requestWithAgentConfig(Object enabled, Object effort) {
        AgentRequest request = new AgentRequest();
        Map<String, Object> reasoning = new HashMap<>();
        if (enabled != null) {
            reasoning.put("enabled", enabled);
        }
        if (effort != null) {
            reasoning.put("effort", effort);
        }
        request.addBody("_agentConfig_reasoning", reasoning);
        return request;
    }

    @Test
    void agentConfig开启思考时回退生效() {
        DefaultAgentProcessor processor = new DefaultAgentProcessor();
        AgentRuntimeBuilder builder = Mockito.mock(AgentRuntimeBuilder.class);
        AgentRequest request = requestWithAgentConfig(true, "high");

        processor.configureReasoning(builder, request);

        ArgumentCaptor<AgentGenerateOptions> captor = ArgumentCaptor.forClass(AgentGenerateOptions.class);
        Mockito.verify(builder).generateOptions(captor.capture());
        assertThat(captor.getValue().getReasoningEffort()).isEqualTo("high");
    }

    @Test
    void agentConfig开启且未配力度时默认medium() {
        DefaultAgentProcessor processor = new DefaultAgentProcessor();
        AgentRuntimeBuilder builder = Mockito.mock(AgentRuntimeBuilder.class);
        AgentRequest request = requestWithAgentConfig(true, null);

        processor.configureReasoning(builder, request);

        ArgumentCaptor<AgentGenerateOptions> captor = ArgumentCaptor.forClass(AgentGenerateOptions.class);
        Mockito.verify(builder).generateOptions(captor.capture());
        assertThat(captor.getValue().getReasoningEffort()).isEqualTo("medium");
    }

    @Test
    void 请求级参数优先于agentConfig() {
        DefaultAgentProcessor processor = new DefaultAgentProcessor();
        AgentRuntimeBuilder builder = Mockito.mock(AgentRuntimeBuilder.class);
        AgentRequest request = requestWithAgentConfig(true, "high");
        request.addBody(AgentRequest.BodyKeys.REASONING_EFFORT, "low");

        processor.configureReasoning(builder, request);

        ArgumentCaptor<AgentGenerateOptions> captor = ArgumentCaptor.forClass(AgentGenerateOptions.class);
        Mockito.verify(builder).generateOptions(captor.capture());
        assertThat(captor.getValue().getReasoningEffort()).isEqualTo("low");
    }

    @Test
    void agentConfig关闭思考时不设置推理参数() {
        DefaultAgentProcessor processor = new DefaultAgentProcessor();
        AgentRuntimeBuilder builder = Mockito.mock(AgentRuntimeBuilder.class);
        AgentRequest request = requestWithAgentConfig(false, null);

        processor.configureReasoning(builder, request);

        ArgumentCaptor<AgentGenerateOptions> captor = ArgumentCaptor.forClass(AgentGenerateOptions.class);
        Mockito.verify(builder).generateOptions(captor.capture());
        assertThat(captor.getValue().getReasoningEffort()).isNull();
    }

    @Test
    void 请求级与agentConfig均无配置时不设置推理参数() {
        DefaultAgentProcessor processor = new DefaultAgentProcessor();
        AgentRuntimeBuilder builder = Mockito.mock(AgentRuntimeBuilder.class);
        AgentRequest request = new AgentRequest();

        processor.configureReasoning(builder, request);

        Mockito.verify(builder, Mockito.never()).generateOptions(Mockito.any());
    }
}
