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

import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.provider.AgentDefinitionResolver;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册中心定义解析器钩子单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentDefinitionResolver钩子单元测试")
class AgentDefinitionResolverHookTest {

    @Mock
    private AgentManager agentManager;

    @Mock
    private AgentDefinitionResolver agentDefinitionResolver;

    private TestAgentProcessor processor;

    /**
     * 最小化测试处理器（仅暴露enrichFromAgentConfig行为）
     */
    static class TestAgentProcessor extends AbstractAgentProcessor {

        @Override
        public String getAgentCode() {
            return "test-agent";
        }

        @Override
        protected List<AgentMessage> buildInputMessages(AgentRequest request) {
            return List.of();
        }
    }

    /**
     * 构造带配置的Agent
     * @param model
     * @return
     */
    private Agent agentWithModel(String model) {
        Agent agent = new Agent();
        agent.setAgentCode("agent-a");
        agent.setAgentConfig("{\"model\":\"" + model + "\",\"systemPrompt\":\"系统提示\"}");
        return agent;
    }

    @Test
    @DisplayName("resolver命中时优先生效且不查agentManager")
    void resolverTakesPriority() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        processor.agentDefinitionResolver = agentDefinitionResolver;
        when(agentDefinitionResolver.resolve(any(AgentRequest.class))).thenReturn(agentWithModel("gray-model"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(request.getBody().get(AgentRequest.BodyKeys.MODEL_CODE)).isEqualTo("gray-model");
        verify(agentManager, never()).getByCode(any());
    }

    @Test
    @DisplayName("resolver未命中时回退agentManager默认配置")
    void resolverMissFallsBackToManager() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        processor.agentDefinitionResolver = agentDefinitionResolver;
        when(agentDefinitionResolver.resolve(any(AgentRequest.class))).thenReturn(null);
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithModel("default-model"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        processor.enrichFromAgentConfig(request);

        assertThat(request.getBody().get(AgentRequest.BodyKeys.MODEL_CODE)).isEqualTo("default-model");
        verify(agentManager).getByCode("agent-a");
    }

    @Test
    @DisplayName("resolver与agentManager均无时静默返回")
    void bothAbsentSilentReturn() {
        processor = new TestAgentProcessor();
        processor.agentManager = null;
        processor.agentDefinitionResolver = null;

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        assertThatCode(() -> processor.enrichFromAgentConfig(request)).doesNotThrowAnyException();
        assertThat(request.getBody().get(AgentRequest.BodyKeys.MODEL_CODE)).isNull();
    }

    @Test
    @DisplayName("resolver异常时静默降级不阻断请求")
    void resolverExceptionDegrades() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        processor.agentDefinitionResolver = agentDefinitionResolver;
        when(agentDefinitionResolver.resolve(any(AgentRequest.class))).thenThrow(new RuntimeException("注册中心故障"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        assertThatCode(() -> processor.enrichFromAgentConfig(request)).doesNotThrowAnyException();
        assertThat(request.getBody().get(AgentRequest.BodyKeys.MODEL_CODE)).isNull();
    }

    @Test
    @DisplayName("请求级已有model时agentConfig不覆盖（putIfAbsent语义）")
    void requestLevelConfigWins() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        processor.agentDefinitionResolver = agentDefinitionResolver;
        when(agentDefinitionResolver.resolve(any(AgentRequest.class))).thenReturn(agentWithModel("gray-model"));

        AgentRequest request = new AgentRequest().agentCode("agent-a");
        request.getBody().put(AgentRequest.BodyKeys.MODEL_CODE, "request-model");
        processor.enrichFromAgentConfig(request);

        assertThat(request.getBody().get(AgentRequest.BodyKeys.MODEL_CODE)).isEqualTo("request-model");
    }
}
