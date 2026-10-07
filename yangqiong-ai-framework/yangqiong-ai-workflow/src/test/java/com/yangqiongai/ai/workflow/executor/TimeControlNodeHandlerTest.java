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
package com.yangqiongai.ai.workflow.executor;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.event.WorkflowTimeArrivedEvent;
import com.yangqiongai.ai.workflow.model.NodeTimeControlConfig;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.spi.WorkflowTimeModeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TimeControlNodeHandler 单元测试
 *
 * @author yangqiong
 */
class TimeControlNodeHandlerTest {

    private ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<WorkflowTimeModeResolver> resolverProvider = mock(ObjectProvider.class);

    private TimeControlNodeHandler handler;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        // 社区版默认未注入企业版时间模式解析器
        when(resolverProvider.getIfAvailable()).thenReturn(null);
        handler = new TimeControlNodeHandler(eventPublisher, resolverProvider);
    }

    // ==================== 辅助方法 ====================

    private WorkflowNode buildNode(String nodeId, NodeTimeControlConfig config) {
        WorkflowNode node = new WorkflowNode();
        node.setId(nodeId);
        node.setName("时间控制");
        node.setType(com.yangqiongai.ai.workflow.model.NodeType.TIME_CONTROL);
        node.setTimeControlConfig(config);
        return node;
    }

    private WorkflowState buildState(String instanceId) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(instanceId);
        return state;
    }

    private AgentContext buildContext() {
        AgentRequest request = new AgentRequest()
                .agentCode("workflow")
                .input("test-input")
                .sessionId("test-session");
        return new AgentContext(request);
    }

    // ==================== 延迟执行模式（社区内置） ====================

    @Nested
    @DisplayName("延迟执行模式")
    class DelayMode {

        @Test
        @DisplayName("DELAY模式暂停等待")
        void executeDelay() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.DELAY);
            config.setDelaySeconds(60);
            WorkflowState state = buildState("inst-002");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("delay-node", config));

            assertThat(result.isPaused()).isTrue();
            assertThat((Long) state.getVariable("timeResumeAt:delay-node")).isGreaterThan(System.currentTimeMillis());
        }

        @Test
        @DisplayName("DELAY模式秒数为0直接通过")
        void executeDelayZeroPasses() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.DELAY);
            config.setDelaySeconds(0);
            WorkflowState state = buildState("inst-003");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("delay-node", config));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("未配置timeControlConfig直接通过")
        void executeNullConfigPasses() {
            WorkflowState state = buildState("inst-004");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("none-node", null));

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("未配置timeType按DELAY处理")
        void executeNullTimeTypeTreatedAsDelay() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setDelaySeconds(1);
            WorkflowState state = buildState("inst-005");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("delay-node", config));

            assertThat(result.isPaused()).isTrue();
        }
    }

    // ==================== 企业版专属模式门禁 ====================

    @Nested
    @DisplayName("企业版专属模式门禁")
    class EnterpriseModeGate {

        @Test
        @DisplayName("CRON模式无解析器时预览报企业版专属")
        void previewCronWithoutResolverRejected() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.CRON);
            config.setCronExpression("0 0/15 * * * *");

            AgentResult result = handler.previewTimeControlNode(buildNode("cron-node", config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("企业版专属");
        }

        @Test
        @DisplayName("COUNTDOWN模式无解析器时内置计算恢复时间")
        void executeCountdownWithoutResolverUsesBuiltinCalc() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.COUNTDOWN);
            config.setCountdownMinutes(1);
            config.setCountdownSeconds(30);
            WorkflowState state = buildState("inst-006");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("count-node", config));

            assertThat(result.isPaused()).isTrue();
            assertThat((Long) state.getVariable("timeResumeAt:count-node"))
                    .isGreaterThan(System.currentTimeMillis() + 80_000L);
        }

        @Test
        @DisplayName("COUNTDOWN总秒数为0时视为已过期直接继续")
        void executeCountdownZeroPassesThrough() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.COUNTDOWN);
            config.setCountdownMinutes(0);
            config.setCountdownSeconds(0);
            WorkflowState state = buildState("inst-008");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("count-node", config));

            assertThat(result.isSuccess()).isTrue();
            assertThat(state.getVariable("timeResumeAt:count-node")).isNull();
        }

        @Test
        @DisplayName("SPECIFIC模式无解析器时预览报企业版专属")
        void previewSpecificWithoutResolverRejected() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.SPECIFIC);
            config.setSpecificTime("2030-01-01 10:00:00");

            AgentResult result = handler.previewTimeControlNode(buildNode("spec-node", config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("企业版专属");
        }

        @Test
        @DisplayName("PERIODIC模式无解析器时预览报企业版专属")
        void previewPeriodicWithoutResolverRejected() {
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.PERIODIC);
            config.setPeriodType(NodeTimeControlConfig.PeriodType.DAILY);
            config.setPeriodTime("23:59");

            AgentResult result = handler.previewTimeControlNode(buildNode("period-node", config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("企业版专属");
        }

        @Test
        @DisplayName("注入解析器后高级模式恢复时间由解析器计算")
        void executeWithResolverDelegates() {
            when(resolverProvider.getIfAvailable()).thenReturn((config, nowMillis) -> nowMillis + 60_000L);
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.SPECIFIC);
            config.setSpecificTime("2030-01-01 10:00:00");
            WorkflowState state = buildState("inst-007");

            AgentResult result = handler.executeTimeControlNode(buildContext(), state, buildNode("spec-node", config));

            assertThat(result.isPaused()).isTrue();
            assertThat((Long) state.getVariable("timeResumeAt:spec-node"))
                    .isGreaterThan(System.currentTimeMillis() + 55_000L);
        }

        @Test
        @DisplayName("解析器返回-1时按配置无效处理")
        void executeWithResolverInvalidReturnsFailure() {
            when(resolverProvider.getIfAvailable()).thenReturn((config, nowMillis) -> -1L);
            NodeTimeControlConfig config = new NodeTimeControlConfig();
            config.setTimeType(NodeTimeControlConfig.TimeType.CRON);
            config.setCronExpression("not a cron");

            AgentResult result = handler.previewTimeControlNode(buildNode("cron-node", config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("配置无效");
        }
    }

    // ==================== 恢复事件调度 ====================

    @Nested
    @DisplayName("恢复事件调度")
    class ScheduleResume {

        @Test
        @DisplayName("到达设定时间发布恢复事件")
        void schedulePublishesEventOnTime() {
            long resumeAt = System.currentTimeMillis() - 1000;

            handler.scheduleResumeEvent("inst-100", "node-a", resumeAt);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, timeout(3000)).publishEvent(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(WorkflowTimeArrivedEvent.class);
            WorkflowTimeArrivedEvent event = (WorkflowTimeArrivedEvent) captor.getValue();
            assertThat(event.getInstanceId()).isEqualTo("inst-100");
            assertThat(event.getNodeId()).isEqualTo("node-a");
            assertThat(event.getResumeAtMillis()).isEqualTo(resumeAt);
        }

        @Test
        @DisplayName("未来时间未到不发布事件")
        void scheduleFutureDoesNotPublishImmediately() throws Exception {
            handler.scheduleResumeEvent("inst-101", "node-b", System.currentTimeMillis() + 60_000);

            Thread.sleep(300);
            verifyNoInteractions(eventPublisher);
        }
    }
}
