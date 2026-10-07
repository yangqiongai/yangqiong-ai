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
package com.yangqiongai.ai.agent.runtime.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需要用户澄清事件单元测试
 * @author yangqiong
 */
class RequireUserClarificationEventTest {

    @Test
    @DisplayName("构建澄清事件应携带问题与工具调用ID")
    void build_shouldCarryQuestionAndToolCallId() {
        RequireUserClarificationEvent event = new RequireUserClarificationEvent("请确认查询时间范围", "tc-1");
        assertThat(event.getType()).isEqualTo(AgentEventType.REQUIRE_USER_CLARIFICATION);
        assertThat(event.getQuestion()).isEqualTo("请确认查询时间范围");
        assertThat(event.getToolCallId()).isEqualTo("tc-1");
    }

    @Test
    @DisplayName("澄清事件载荷为问题内容")
    void build_payload_shouldBeQuestion() {
        RequireUserClarificationEvent event = new RequireUserClarificationEvent("要使用哪个数据源？", "tc-2");
        assertThat(event.getPayload()).isEqualTo("要使用哪个数据源？");
    }

    @Test
    @DisplayName("允许空问题与空工具调用ID的边界值")
    void build_nullValues_shouldBeAllowed() {
        RequireUserClarificationEvent event = new RequireUserClarificationEvent(null, null);
        assertThat(event.getQuestion()).isNull();
        assertThat(event.getToolCallId()).isNull();
        assertThat(event.getType()).isEqualTo(AgentEventType.REQUIRE_USER_CLARIFICATION);
    }
}
