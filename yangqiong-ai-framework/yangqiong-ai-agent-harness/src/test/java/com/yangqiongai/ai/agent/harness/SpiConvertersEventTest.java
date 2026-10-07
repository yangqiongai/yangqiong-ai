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

import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentTextBlockDeltaEvent;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.event.ParadigmStageInfo;
import com.yangqiongai.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPI事件转换容错单元测试
 * @author yangqiong
 */
class SpiConvertersEventTest {

    /**
     * 测试用未知枚举，模拟引擎未来新增、框架未跟进的事件类型
     */
    enum UnknownEngineEvent {
        FUTURE_EVENT
    }

    @Test
    @DisplayName("convertEnum对null源返回null")
    void convertEnum_nullSource_shouldReturnNull() {
        assertThat(SpiConverters.convertEnum(null, AgentEventType.class)).isNull();
        assertThat(SpiConverters.convertEnum(null,
                com.yangqiongai.ai.agent.runtime.event.AgentEventType.class)).isNull();
    }

    @Test
    @DisplayName("convertEnum对已知名返回同名目标值")
    void convertEnum_knownName_shouldConvert() {
        AgentEventType engineType = SpiConverters.convertEnum(
                com.yangqiongai.ai.agent.runtime.event.AgentEventType.TOKEN_BUDGET_WARN, AgentEventType.class);
        assertThat(engineType).isEqualTo(AgentEventType.TOKEN_BUDGET_WARN);
    }

    @Test
    @DisplayName("convertEnum对未知名降级返回null不抛异常")
    void convertEnum_unknownName_shouldReturnNull() {
        assertThat(SpiConverters.convertEnum(UnknownEngineEvent.FUTURE_EVENT,
                com.yangqiongai.ai.agent.runtime.event.AgentEventType.class)).isNull();
    }

    @Test
    @DisplayName("toRuntimeEvent对null事件返回null")
    void toRuntimeEvent_nullEvent_shouldReturnNull() {
        assertThat(SpiConverters.toRuntimeEvent((AgentEvent) null)).isNull();
    }

    @Test
    @DisplayName("toRuntimeEvent转换预算告警事件为同名框架事件")
    void toRuntimeEvent_budgetWarn_shouldConvertBySameName() {
        AgentEvent engineEvent = AgentEvent.of(AgentEventType.TOKEN_BUDGET_WARN, "warn payload");
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent =
                SpiConverters.toRuntimeEvent(engineEvent);
        assertThat(runtimeEvent.getType())
                .isEqualTo(com.yangqiongai.ai.agent.runtime.event.AgentEventType.TOKEN_BUDGET_WARN);
        assertThat(runtimeEvent.getPayload()).isEqualTo("warn payload");
    }

    @Test
    @DisplayName("toRuntimeEvent保留父级代理路径")
    void toRuntimeEvent_parentPath_shouldBeKept() {
        AgentEvent engineEvent = AgentEvent.of(AgentEventType.AGENT_START, "agent", "main/child");
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent =
                SpiConverters.toRuntimeEvent(engineEvent);
        assertThat(runtimeEvent.getParentAgentPath()).isEqualTo("main/child");
    }

    @Test
    @DisplayName("toRuntimeEvent转换文本增量事件")
    void toRuntimeEvent_textDelta_shouldConvert() {
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent =
                SpiConverters.toRuntimeEvent(new AgentTextBlockDeltaEvent("片段"));
        assertThat(runtimeEvent)
                .isEqualTo(new com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent("片段"));
    }

    @Test
    @DisplayName("toRuntimeEvent转换审批事件并保留工具调用列表")
    void toRuntimeEvent_requireUserConfirm_shouldConvertToolUses() {
        AgentEvent engineEvent = new RequireUserConfirmEvent(
                List.of(new AgentToolUseBlock("search", "call-1", Map.of("q", "关键词"))));
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent =
                SpiConverters.toRuntimeEvent(engineEvent);
        assertThat(runtimeEvent)
                .isInstanceOf(com.yangqiongai.ai.agent.runtime.event.RequireUserConfirmEvent.class);
        com.yangqiongai.ai.agent.runtime.event.RequireUserConfirmEvent confirm =
                (com.yangqiongai.ai.agent.runtime.event.RequireUserConfirmEvent) runtimeEvent;
        assertThat(confirm.getPendingToolCalls()).hasSize(1);
        assertThat(confirm.getPendingToolCalls().get(0).getToolName()).isEqualTo("search");
        assertThat(confirm.getPendingToolCalls().get(0).getToolUseId()).isEqualTo("call-1");
        assertThat(confirm.getPendingToolCalls().get(0).getInput()).containsEntry("q", "关键词");
    }

    @Test
    @DisplayName("toHarnessEvent对null事件返回null")
    void toHarnessEvent_nullEvent_shouldReturnNull() {
        assertThat(SpiConverters.toHarnessEvent(null)).isNull();
    }

    @Test
    @DisplayName("toHarnessEvent丢弃custom工厂创建的CUSTOM事件")
    void toHarnessEvent_customFactoryEvent_shouldDrop() {
        com.yangqiongai.ai.agent.runtime.event.AgentEvent customEvent =
                com.yangqiongai.ai.agent.runtime.event.AgentEvent.custom("EXCEED_MAX_ITERS", "payload");
        assertThat(SpiConverters.toHarnessEvent(customEvent)).isNull();
    }

    @Test
    @DisplayName("toHarnessEvent丢弃of构造的CUSTOM事件")
    void toHarnessEvent_customOfEvent_shouldDrop() {
        com.yangqiongai.ai.agent.runtime.event.AgentEvent customEvent =
                com.yangqiongai.ai.agent.runtime.event.AgentEvent.of(
                        com.yangqiongai.ai.agent.runtime.event.AgentEventType.CUSTOM, "payload");
        assertThat(SpiConverters.toHarnessEvent(customEvent)).isNull();
    }

    @Test
    @DisplayName("toHarnessEvent转换预算超限事件为同名引擎事件")
    void toHarnessEvent_budgetExceeded_shouldConvert() {
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent =
                com.yangqiongai.ai.agent.runtime.event.AgentEvent.of(
                        com.yangqiongai.ai.agent.runtime.event.AgentEventType.COST_BUDGET_EXCEEDED, "payload");
        AgentEvent engineEvent = SpiConverters.toHarnessEvent(runtimeEvent);
        assertThat(engineEvent).isNotNull();
        assertThat(engineEvent.getType()).isEqualTo(AgentEventType.COST_BUDGET_EXCEEDED);
    }

    @Test
    @DisplayName("toRuntimeEvent转换范式阶段事件并保留阶段载荷")
    void toRuntimeEvent_paradigmStage_shouldConvertWithPayload() {
        AgentEvent engineEvent = AgentEvent.of(AgentEventType.PARADIGM_STAGE,
                new ParadigmStageInfo(ParadigmStageInfo.EVALUATE, 1));
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent =
                SpiConverters.toRuntimeEvent(engineEvent);
        assertThat(runtimeEvent.getType())
                .isEqualTo(com.yangqiongai.ai.agent.runtime.event.AgentEventType.PARADIGM_STAGE);
        assertThat(runtimeEvent.getPayload()).isInstanceOf(ParadigmStageInfo.class);
        ParadigmStageInfo stage = (ParadigmStageInfo) runtimeEvent.getPayload();
        assertThat(stage.getStage()).isEqualTo(ParadigmStageInfo.EVALUATE);
        assertThat(stage.getAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("toRuntimeConfirmResult透传toolCallId保证精确审批匹配")
    void toRuntimeConfirmResult_shouldKeepToolCallId() {
        ConfirmResult engineResult = ConfirmResult.approveCall("call-1", "search");
        com.yangqiongai.ai.agent.runtime.event.ConfirmResult runtimeResult =
                SpiConverters.toRuntimeConfirmResult(engineResult);
        assertThat(runtimeResult.getToolCallId()).isEqualTo("call-1");
        assertThat(runtimeResult.getToolName()).isEqualTo("search");
        assertThat(runtimeResult.isApproved()).isTrue();

        com.yangqiongai.ai.agent.runtime.event.ConfirmResult nameOnlyResult =
                SpiConverters.toRuntimeConfirmResult(ConfirmResult.approve("search"));
        assertThat(nameOnlyResult.getToolCallId()).isNull();
        assertThat(nameOnlyResult.getToolName()).isEqualTo("search");
    }
}
