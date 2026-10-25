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
import com.yangqiongai.ai.open.capability.interaction.OpenInteractionPolicy;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;

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
    }

    @AfterEach
    void tearDown() {
        ScopeContext.clear();
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

    @Test
    @DisplayName("关闭交互的同步调用发生澄清挂起时返回失败文案")
    void executeClosedInteractionSuspensionReturnsFailure() {
        OpenInteractionPolicy policy = new OpenInteractionPolicy(null, null, null);
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");

        when(agentEngine.run(any(AgentRequest.class))).thenAnswer(invocation -> {
            AgentRequest agentRequest = invocation.getArgument(0);
            // 模拟执行期间模型发起澄清（暂停恢复登记装饰器回调）
            policy.onSuspendRegistered(agentRequest.getSessionId());
            return AgentResult.success("部分结果");
        });

        AgentResult result = new CapabilityInvoker(agentEngine, policy).execute(spec, "你好", request, 0);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(OpenInteractionPolicy.CLOSED_INTERACTION_MESSAGE);
    }

    @Test
    @DisplayName("关闭交互的同步调用正常完成时透传结果并注入Prompt约束")
    void executeClosedInteractionNormalPassesThrough() {
        OpenInteractionPolicy policy = new OpenInteractionPolicy(null, null, null);
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");
        AgentResult agentResult = AgentResult.success("ok");
        when(agentEngine.run(any(AgentRequest.class))).thenReturn(agentResult);

        AgentResult result = new CapabilityInvoker(agentEngine, policy).execute(spec, "你好", request, 0);

        assertThat(result).isSameAs(agentResult);
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).run(captor.capture());
        // Prompt约束段与审批工具禁用均已注入
        assertThat(captor.getValue().getInput().get(0).toString()).contains("禁止向用户发起澄清提问");
        assertThat(captor.getValue().getBody().get("_agentConfig_deniedTools"))
                .isEqualTo(List.of("HumanApprovalTool"));
    }

    @Test
    @DisplayName("关闭交互的流式调用过滤澄清事件")
    void streamClosedInteractionFiltersClarification() {
        OpenInteractionPolicy policy = new OpenInteractionPolicy(null, null, null);
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");
        StreamEvent text = StreamEvent.textDelta("内容");
        when(agentEngine.stream(any(AgentRequest.class)))
                .thenReturn(Flux.just(text, StreamEvent.clarificationRequired("{}")));

        List<StreamEvent> emitted = new CapabilityInvoker(agentEngine, policy).stream(spec, "你好", request)
                .collectList()
                .block();

        assertThat(emitted).containsExactly(text);
    }

    @Test
    @DisplayName("开启交互的流式调用事件照常透传")
    void streamOpenedInteractionPassesThrough() {
        OpenInteractionPolicy policy = new OpenInteractionPolicy(null, null, null);
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");
        spec.setInteractionEnabled(true);
        StreamEvent text = StreamEvent.textDelta("内容");
        StreamEvent clarification = StreamEvent.clarificationRequired("{}");
        when(agentEngine.stream(any(AgentRequest.class)))
                .thenReturn(Flux.just(text, clarification));

        List<StreamEvent> emitted = new CapabilityInvoker(agentEngine, policy).stream(spec, "你好", request)
                .collectList()
                .block();

        assertThat(emitted).containsExactly(text, clarification);
    }

    @Test
    @DisplayName("作用域取凭证绑定上下文，不接受调用方传参")
    void executeUsesCredentialBoundScopeId() {
        AgentResult agentResult = AgentResult.success("ok");
        when(agentEngine.run(any(AgentRequest.class))).thenReturn(agentResult);
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");
        ScopeContext.setScopeId("scope-trusted");

        try {
            new CapabilityInvoker(agentEngine).execute(spec, "你好", request, 0);

            ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
            verify(agentEngine).run(captor.capture());
            assertThat(captor.getValue().getScopeId()).isEqualTo("scope-trusted");
        } finally {
            ScopeContext.clear();
        }
    }

    @Test
    @DisplayName("无凭证上下文时作用域取默认值")
    void executeUsesDefaultScopeIdWithoutCredentialContext() {
        AgentResult agentResult = AgentResult.success("ok");
        when(agentEngine.run(any(AgentRequest.class))).thenReturn(agentResult);
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode("cap-demo");
        spec.setAgentCode("default");

        new CapabilityInvoker(agentEngine).execute(spec, "你好", request, 0);

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).run(captor.capture());
        assertThat(captor.getValue().getScopeId()).isEqualTo(ScopeContext.DEFAULT_SCOPE_ID);
    }
}
