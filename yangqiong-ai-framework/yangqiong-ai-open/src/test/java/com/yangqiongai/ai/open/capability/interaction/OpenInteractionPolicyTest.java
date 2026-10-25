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
package com.yangqiongai.ai.open.capability.interaction;

import com.yangqiongai.ai.agent.core.event.ApprovalRequiredEvent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.task.AgentTaskTracker;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.approval.PendingRequestStore;
import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 开放能力交互策略单测（关闭语义）
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenInteractionPolicyTest {

    @Mock
    private AgentTaskTracker taskTracker;

    @Mock
    private PendingRequestStore pendingRequestStore;

    @Mock
    private ApprovalGate approvalGate;

    @Mock
    private ObjectProvider<PendingRequestStore> pendingRequestStoreProvider;

    @Mock
    private ObjectProvider<ApprovalGate> approvalGateProvider;

    private OpenInteractionPolicy policy;

    @BeforeEach
    void setUp() {
        lenient().when(pendingRequestStoreProvider.getIfAvailable()).thenReturn(pendingRequestStore);
        lenient().when(approvalGateProvider.getIfAvailable()).thenReturn(approvalGate);
        policy = new OpenInteractionPolicy(taskTracker, pendingRequestStoreProvider, approvalGateProvider);
    }

    @Test
    @DisplayName("interactionEnabled缺省与false均为关闭，true为开启")
    void interactionEnabledDefaultClosed() {
        assertThat(policy.isInteractionEnabled(null)).isFalse();

        CapabilitySpec closed = new CapabilitySpec();
        assertThat(policy.isInteractionEnabled(closed)).isFalse();

        closed.setInteractionEnabled(false);
        assertThat(policy.isInteractionEnabled(closed)).isFalse();

        CapabilitySpec opened = new CapabilitySpec();
        opened.setInteractionEnabled(true);
        assertThat(policy.isInteractionEnabled(opened)).isTrue();
    }

    @Test
    @DisplayName("关闭语义源头排除审批工具")
    void applyClosedModeDeniesApprovalTool() {
        AgentRequest agentRequest = new AgentRequest();
        policy.applyClosedMode(agentRequest);

        assertThat(agentRequest.getBody().get("_agentConfig_deniedTools"))
                .isEqualTo(List.of(OpenInteractionPolicy.APPROVAL_TOOL_CODE));
    }

    @Test
    @DisplayName("关闭语义Prompt约束段包含禁止澄清与审批表述")
    void buildClosedConstraintContainsProhibition() {
        String constraint = policy.buildClosedConstraint();

        assertThat(constraint).contains("禁止向用户发起澄清提问");
        assertThat(constraint).contains("禁止请求人工审批");
    }

    @Test
    @DisplayName("异步调用挂起时任务即时标记失败且登记移除")
    void asyncSuspensionMarksTaskFailed() {
        policy.registerInvocation("session-1", "task-1");

        policy.onSuspendRegistered("session-1");

        verify(taskTracker).markFailed(eq("task-1"), eq("interaction"),
                eq(OpenInteractionPolicy.CLOSED_INTERACTION_MESSAGE));
        // 一次性处理后登记移除，重复回调不再重复标记
        policy.onSuspendRegistered("session-1");
        verify(taskTracker).markFailed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("同步调用挂起时标记中止，解除登记后标记清除")
    void syncSuspensionSetsSuspendedFlag() {
        policy.registerInvocation("session-1", null);
        assertThat(policy.isSuspended("session-1")).isFalse();

        policy.onSuspendRegistered("session-1");
        assertThat(policy.isSuspended("session-1")).isTrue();

        policy.unregister("session-1");
        assertThat(policy.isSuspended("session-1")).isFalse();
    }

    @Test
    @DisplayName("未登记会话的挂起回调不触发任务标记")
    void unregisteredSessionIgnored() {
        policy.onSuspendRegistered("session-unknown");

        verify(taskTracker, never()).markFailed(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("关闭交互的流过滤澄清与确认事件，正常事件透传")
    void filterSuspendEventsDropsInteractionOnly() {
        StreamEvent text = StreamEvent.textDelta("内容");
        StreamEvent clarification = StreamEvent.clarificationRequired("{}");
        StreamEvent confirm = StreamEvent.confirmRequired("{}");

        List<StreamEvent> emitted = policy.filterSuspendEvents(Flux.just(text, clarification, confirm))
                .collectList()
                .block();

        assertThat(emitted).containsExactly(text);
    }

    @Test
    @DisplayName("关闭交互的调用触发审批事件时自动拒绝")
    void approvalEventAutoRejectedForClosedSession() {
        policy.registerInvocation("session-1", null);

        PendingRequestInfo pending = new PendingRequestInfo();
        pending.setRequestId("req-1");
        pending.setApprovalToken("token-1");
        when(pendingRequestStore.findByRequestId("req-1")).thenReturn(Optional.of(pending));

        ApprovalRequiredEvent event = new ApprovalRequiredEvent(this, "req-1", "session-1",
                "caller-a", "LLM_DYNAMIC", "humanApproval", "风险操作", 300, null);
        policy.onApprovalRequired(event);

        verify(approvalGate).reject(eq("token-1"), eq("open-interaction-policy"),
                eq(OpenInteractionPolicy.CLOSED_INTERACTION_MESSAGE));
    }

    @Test
    @DisplayName("非登记会话的审批事件不处理")
    void approvalEventIgnoredForUnregisteredSession() {
        ApprovalRequiredEvent event = new ApprovalRequiredEvent(this, "req-1", "session-other",
                "caller-a", "LLM_DYNAMIC", "humanApproval", "风险操作", 300, null);
        policy.onApprovalRequired(event);

        verify(pendingRequestStore, never()).findByRequestId(anyString());
        verify(approvalGate, never()).reject(anyString(), anyString(), anyString());
    }
}
