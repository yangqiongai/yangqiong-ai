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
package com.yangqiongai.ai.open.capability.engine;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力调用器单测
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapabilityInvokerTest {

    @Mock
    private AgentEngine agentEngine;

    private CapabilityRequest request;

    @BeforeEach
    void setUp() {
        request = new CapabilityRequest();
        request.setCapability("cap-demo");
        request.setCaller("caller-a");
        request.setScopeId("scope-1");
    }

    @Test
    @DisplayName("缺省执行体路由到AgentEngine")
    void executeRoutesToAgentByDefault() {
        AgentResult agentResult = AgentResult.success("ok");
        when(agentEngine.run(any(AgentRequest.class))).thenReturn(agentResult);

        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");

        AgentResult result = new CapabilityInvoker(agentEngine).execute(spec, "你好", request, 0);

        assertThat(result).isSameAs(agentResult);
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).run(captor.capture());
        // input以内容块列表承载，文本块内容应为Prompt原文
        assertThat(captor.getValue().getInput().get(0).toString()).contains("你好");
        assertThat(captor.getValue().getAgentCode()).isEqualTo("default");
    }

    @Test
    @DisplayName("WORKFLOW执行体为企业版能力，社区版执行被拒绝")
    void executeRejectsWorkflowExecutor() {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setExecType("WORKFLOW");
        spec.setWorkflowCode("order-audit");

        AgentResult result = new CapabilityInvoker(agentEngine).execute(spec, "你好", request, 0);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("企业版");
        verify(agentEngine, never()).run(any());
    }

    @Test
    @DisplayName("WORKFLOW执行体流式调用被拒绝")
    void streamRejectsWorkflowExecutor() {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setExecType("WORKFLOW");
        spec.setWorkflowCode("order-audit");

        Flux<StreamEvent> flux = new CapabilityInvoker(agentEngine).stream(spec, "你好", request);

        assertThatThrownBy(flux::blockFirst).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("WORKFLOW执行体异步提交被拒绝")
    void submitAsyncRejectsWorkflowExecutor() {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setExecType("WORKFLOW");
        spec.setWorkflowCode("order-audit");

        assertThatThrownBy(() -> new CapabilityInvoker(agentEngine)
                .submitAsync(spec, "你好", request))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
