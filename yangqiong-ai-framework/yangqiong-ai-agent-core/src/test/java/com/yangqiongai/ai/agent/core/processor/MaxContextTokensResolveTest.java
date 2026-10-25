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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 上下文窗口Token上限配置解析单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("上下文窗口Token上限配置解析单元测试")
class MaxContextTokensResolveTest {

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
     * 构造带配置的Agent
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
    @DisplayName("agentConfig正数解析为对应Token上限")
    void positiveConfig_resolvesValue() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a"))
                .thenReturn(agentWithConfig("{\"maxContextTokens\":32000}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(processor.resolveMaxContextTokens(request)).isEqualTo(32000);
    }

    @Test
    @DisplayName("未配置时返回0表示不启用截断")
    void absentConfig_returnsZero() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig("{\"model\":\"deepseek\"}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(processor.resolveMaxContextTokens(request)).isZero();
    }

    @Test
    @DisplayName("agentConfig为零或负数时不注入返回0")
    void nonPositiveConfig_returnsZero() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a")).thenReturn(
                agentWithConfig("{\"maxContextTokens\":0,\"maxIterations\":5}"),
                agentWithConfig("{\"maxContextTokens\":-100}"));

        AgentRequest zeroConfigRequest = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(zeroConfigRequest);
        assertThat(processor.resolveMaxContextTokens(zeroConfigRequest)).isZero();
        assertThat(processor.resolveMaxIterations(zeroConfigRequest)).isEqualTo(5);

        AgentRequest negativeConfigRequest = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(negativeConfigRequest);
        assertThat(processor.resolveMaxContextTokens(negativeConfigRequest)).isZero();
    }

    @Test
    @DisplayName("非数值配置不注入返回0")
    void invalidTypeConfig_returnsZero() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        when(agentManager.getByCode("agent-a"))
                .thenReturn(agentWithConfig("{\"maxContextTokens\":\"32000\"}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(processor.resolveMaxContextTokens(request)).isZero();
    }

    @Test
    @DisplayName("resolveMaxContextTokens请求为null时静默返回0")
    void nullRequest_returnsZero() {
        processor = new TestAgentProcessor();
        assertThat(processor.resolveMaxContextTokens(null)).isZero();
    }
}
