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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPI澄清事件双向转换单元测试
 * @author yangqiong
 */
class SpiConvertersClarificationTest {

    @Test
    @DisplayName("toRuntimeEvent转换引擎澄清事件为框架澄清事件")
    void toRuntimeEvent_clarification_shouldConvertFields() {
        com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent engineEvent =
                new com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent("请确认查询范围", "tc-1");
        com.yangqiongai.ai.agent.runtime.event.AgentEvent runtimeEvent = SpiConverters.toRuntimeEvent(engineEvent);
        assertThat(runtimeEvent)
                .isInstanceOf(com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent.class);
        com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent clarification =
                (com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent) runtimeEvent;
        assertThat(clarification.getQuestion()).isEqualTo("请确认查询范围");
        assertThat(clarification.getToolCallId()).isEqualTo("tc-1");
        assertThat(clarification.getType())
                .isEqualTo(com.yangqiongai.ai.agent.runtime.event.AgentEventType.REQUIRE_USER_CLARIFICATION);
    }

    @Test
    @DisplayName("toHarnessEvent转换框架澄清事件为引擎澄清事件")
    void toHarnessEvent_clarification_shouldConvertFields() {
        com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent runtimeEvent =
                new com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent("要使用哪个引擎？", "tc-2");
        com.yangqiongai.agent.harness.core.event.AgentEvent engineEvent = SpiConverters.toHarnessEvent(runtimeEvent);
        assertThat(engineEvent)
                .isInstanceOf(com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent.class);
        com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent clarification =
                (com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent) engineEvent;
        assertThat(clarification.getQuestion()).isEqualTo("要使用哪个引擎？");
        assertThat(clarification.getToolCallId()).isEqualTo("tc-2");
    }

    @Test
    @DisplayName("toRuntimeEvent转换引擎澄清应答载体无需转换")
    void runtimeTypeConverter_clarificationAnswer_shouldMirrorEngineRecord() {
        com.yangqiongai.agent.harness.core.event.ClarificationAnswer engineAnswer =
                new com.yangqiongai.agent.harness.core.event.ClarificationAnswer("tc-3", "按月份统计");
        com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer runtimeAnswer =
                new com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer(
                        engineAnswer.toolCallId(), engineAnswer.answer());
        assertThat(runtimeAnswer.getToolCallId()).isEqualTo("tc-3");
        assertThat(runtimeAnswer.getAnswer()).isEqualTo("按月份统计");
    }

    @Test
    @DisplayName("toHarness转换审批结果应透传工具调用ID")
    void runtimeTypeConverter_confirmResult_shouldKeepToolCallId() {
        com.yangqiongai.ai.agent.runtime.event.ConfirmResult runtimeResult =
                com.yangqiongai.ai.agent.runtime.event.ConfirmResult.approveCall("tc-4", "search");
        com.yangqiongai.agent.harness.core.event.ConfirmResult harnessResult =
                RuntimeTypeConverter.toHarness(runtimeResult);
        assertThat(harnessResult.getToolCallId()).isEqualTo("tc-4");
        assertThat(harnessResult.getToolName()).isEqualTo("search");
        assertThat(harnessResult.isApproved()).isTrue();
    }

    @Test
    @DisplayName("toRuntime转换引擎审批结果应透传工具调用ID")
    void runtimeTypeConverter_confirmResult_shouldKeepToolCallIdBack() {
        com.yangqiongai.agent.harness.core.event.ConfirmResult harnessResult =
                com.yangqiongai.agent.harness.core.event.ConfirmResult.denyCall("tc-5", "delete", "不允许删除");
        com.yangqiongai.ai.agent.runtime.event.ConfirmResult runtimeResult =
                RuntimeTypeConverter.toRuntime(harnessResult);
        assertThat(runtimeResult.getToolCallId()).isEqualTo("tc-5");
        assertThat(runtimeResult.getToolName()).isEqualTo("delete");
        assertThat(runtimeResult.isApproved()).isFalse();
        assertThat(runtimeResult.getReason()).isEqualTo("不允许删除");
    }

    @Test
    @DisplayName("toHarness转换名称匹配的审批结果ID保持为空")
    void runtimeTypeConverter_confirmResultByName_shouldKeepNullToolCallId() {
        com.yangqiongai.ai.agent.runtime.event.ConfirmResult runtimeResult =
                com.yangqiongai.ai.agent.runtime.event.ConfirmResult.deny("search", "不批准");
        com.yangqiongai.agent.harness.core.event.ConfirmResult harnessResult =
                RuntimeTypeConverter.toHarness(runtimeResult);
        assertThat(harnessResult.getToolCallId()).isNull();
        assertThat(harnessResult.getToolName()).isEqualTo("search");
        assertThat(harnessResult.getReason()).isEqualTo("不批准");
    }

    @Test
    @DisplayName("转换器对null审批结果返回null")
    void runtimeTypeConverter_nullConfirmResult_shouldReturnNull() {
        assertThat(RuntimeTypeConverter.toHarness(
                (com.yangqiongai.ai.agent.runtime.event.ConfirmResult) null)).isNull();
        assertThat(RuntimeTypeConverter.toRuntime(
                (com.yangqiongai.agent.harness.core.event.ConfirmResult) null)).isNull();
    }
}
