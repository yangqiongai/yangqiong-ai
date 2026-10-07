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

import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.spi.ParadigmSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 执行范式配置解析单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("执行范式配置解析单元测试")
class ExecutionParadigmResolveTest {

    @Mock
    private AgentManager agentManager;

    private TestAgentProcessor processor;

    /**
     * 最小化测试处理器（仅暴露配置解析行为）
     */
    static class TestAgentProcessor extends AbstractAgentProcessor {

        @Override
        public String getAgentCode() {
            return "test-agent";
        }

        @Override
        protected List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> buildInputMessages(AgentRequest request) {
            return List.of();
        }
    }

    /**
     * 构造带执行范式配置的Agent
     * @param configJson
     * @return
     */
    private Agent agentWithConfig(String configJson) {
        Agent agent = new Agent();
        agent.setAgentCode("agent-a");
        agent.setAgentConfig(configJson);
        return agent;
    }

    @Test
    @DisplayName("agentConfig字符串简写解析为对应范式类型")
    void stringShorthand_resolvesToType() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a"))
                .thenReturn(agentWithConfig("{\"executionParadigm\":\"reflexion\"}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        ParadigmSpec spec = processor.resolveExecutionParadigm(request);
        assertThat(spec).isNotNull();
        assertThat(spec.getType()).isEqualTo("reflexion");
        assertThat(spec.getMaxSteps()).isNull();
        assertThat(spec.getMaxReflections()).isNull();
    }

    @Test
    @DisplayName("agentConfig对象格式解析类型与次数配置")
    void objectFormat_resolvesTypeAndLimits() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"executionParadigm\":{\"type\":\"self-refine\",\"maxSteps\":8,\"maxRefinements\":3}}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        ParadigmSpec spec = processor.resolveExecutionParadigm(request);
        assertThat(spec).isNotNull();
        assertThat(spec.getType()).isEqualTo("self-refine");
        assertThat(spec.getMaxSteps()).isEqualTo(8);
        assertThat(spec.getMaxRefinements()).isEqualTo(3);
        assertThat(spec.getMaxReflections()).isNull();
    }

    @Test
    @DisplayName("请求级executionParadigm优先于agentConfig")
    void requestLevelOverridesAgentConfig() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a"))
                .thenReturn(agentWithConfig("{\"executionParadigm\":\"reflexion\"}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        request.getBody().put("executionParadigm", "self-ask");
        processor.enrichFromAgentConfig(request);

        ParadigmSpec spec = processor.resolveExecutionParadigm(request);
        assertThat(spec).isNotNull();
        assertThat(spec.getType()).isEqualTo("self-ask");
    }

    @Test
    @DisplayName("未配置执行范式时返回null走默认ReAct")
    void absentConfig_returnsNull() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig("{\"model\":\"deepseek\"}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(processor.resolveExecutionParadigm(request)).isNull();
    }

    @Test
    @DisplayName("非法配置返回null：无type对象与非字符串非对象值")
    void invalidConfig_returnsNull() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"executionParadigm\":{\"maxSteps\":5},\"remark\":123}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(processor.resolveExecutionParadigm(request)).isNull();
    }

    @Test
    @DisplayName("resolveExecutionParadigm请求为null时静默返回null")
    void nullRequest_returnsNull() {
        processor = new TestAgentProcessor();
        assertThat(processor.resolveExecutionParadigm(null)).isNull();
    }
}
