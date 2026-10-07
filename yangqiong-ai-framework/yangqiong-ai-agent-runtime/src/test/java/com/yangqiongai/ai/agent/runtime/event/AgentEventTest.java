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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Agent事件单元测试
 * @author yangqiong
 */
class AgentEventTest {

    @Test
    @DisplayName("枚举总数为22且新增类型存在")
    void enumValues_shouldContainAllCanonicalTypes() {
        assertThat(AgentEventType.values()).hasSize(22);
        assertThat(AgentEventType.valueOf("REQUIRE_USER_CLARIFICATION")).isNotNull();
        assertThat(AgentEventType.valueOf("ENGINE_ROUTED")).isNotNull();
        assertThat(AgentEventType.valueOf("PARADIGM_STAGE")).isNotNull();
        assertThat(AgentEventType.valueOf("TOKEN_BUDGET_WARN")).isNotNull();
        assertThat(AgentEventType.valueOf("TOKEN_BUDGET_EXCEEDED")).isNotNull();
        assertThat(AgentEventType.valueOf("COST_BUDGET_WARN")).isNotNull();
        assertThat(AgentEventType.valueOf("COST_BUDGET_EXCEEDED")).isNotNull();
        assertThat(AgentEventType.valueOf("CUSTOM")).isNotNull();
    }

    @Test
    @DisplayName("custom工厂创建CUSTOM事件并携带原始类型名")
    void custom_shouldCreateEventWithRawTypeName() {
        Object payload = java.util.Map.of("k", "v");
        AgentEvent event = AgentEvent.custom("EXCEED_MAX_ITERS", payload);
        assertThat(event.getType()).isEqualTo(AgentEventType.CUSTOM);
        assertThat(event.getRawTypeName()).isEqualTo("EXCEED_MAX_ITERS");
        assertThat(event.getPayload()).isSameAs(payload);
        assertThat(event.getParentAgentPath()).isNull();
    }

    @Test
    @DisplayName("规范事件rawTypeName为null")
    void of_shouldCreateEventWithoutRawTypeName() {
        AgentEvent event = AgentEvent.of(AgentEventType.AGENT_START, "agent");
        assertThat(event.getRawTypeName()).isNull();
        assertThat(AgentEvent.completed().getRawTypeName()).isNull();
        assertThat(AgentEvent.of(AgentEventType.COMPLETED, null, "parent").getRawTypeName()).isNull();
    }

    @Test
    @DisplayName("equals与hashCode纳入rawTypeName")
    void equalsHashCode_shouldConsiderRawTypeName() {
        AgentEvent first = AgentEvent.custom("HINT_BLOCK", "payload");
        AgentEvent second = AgentEvent.custom("HINT_BLOCK", "payload");
        AgentEvent third = AgentEvent.custom("EXCEED_MAX_ITERS", "payload");
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isNotEqualTo(third);
        assertThat(first).isNotEqualTo(AgentEvent.of(AgentEventType.CUSTOM, "payload"));
    }

    @Test
    @DisplayName("toString包含rawTypeName")
    void toString_shouldContainRawTypeName() {
        AgentEvent event = AgentEvent.custom("HINT_BLOCK", "payload");
        assertThat(event.toString()).contains("type=CUSTOM").contains("rawTypeName=HINT_BLOCK");
        assertThat(AgentEvent.completed().toString()).doesNotContain("rawTypeName");
    }
}
