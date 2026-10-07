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
package com.yangqiongai.ai.agent.runtime.durable.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint;
import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

/**
 * 持久化序列化单元测试
 * @author yangqiong
 */
class DurableSerializationTest {

    @Nested
    @DisplayName("消息序列化")
    class MessageSerializationTest {

        @Test
        @DisplayName("含嵌套工具结果块的消息JSON往返一致")
        void messageJsonRoundTrip_withNestedToolResult() {
            AgentMessage message = AgentMessage.builder()
                    .name("assistant")
                    .role(AgentMessageRole.ASSISTANT)
                    .content(List.of(
                            AgentTextBlock.builder().text("查询结果如下").build(),
                            new AgentToolUseBlock("http_call", "tu-1",
                                    java.util.Map.of("url", "https://example.com")),
                            AgentToolResultBlock.of("tu-1", List.of(
                                    AgentTextBlock.builder().text("工具输出").build()))))
                    .chatUsage(new AgentChatUsage(100, 20, 120))
                    .latency(1500L)
                    .build();
            String json = AgentMessageSerializer.toJson(message);
            AgentMessage restored = AgentMessageSerializer.fromJson(json);
            assertThat(restored).isEqualTo(message);
            assertThat(restored.getTextContent()).isEqualTo("查询结果如下");
            assertThat(restored.getContent()).hasSize(3);
            assertThat(restored.getContent().get(2)).isInstanceOf(AgentToolResultBlock.class);
            AgentToolResultBlock toolResult = (AgentToolResultBlock) restored.getContent().get(2);
            assertThat(toolResult.getToolUseId()).isEqualTo("tu-1");
            assertThat(toolResult.getContent().get(0)).isInstanceOf(AgentTextBlock.class);
        }

        @Test
        @DisplayName("消息列表JSON往返一致")
        void messageListJsonRoundTrip() {
            List<AgentMessage> messages = List.of(
                    AgentMessage.builder().role(AgentMessageRole.USER)
                            .content(List.of(AgentTextBlock.builder().text("你好").build())).build(),
                    AgentMessage.builder().role(AgentMessageRole.ASSISTANT)
                            .content(List.of(AgentTextBlock.builder().text("你好，有什么可以帮你").build())).build());
            String json = AgentMessageSerializer.listToJson(messages);
            List<AgentMessage> restored = AgentMessageSerializer.listFromJson(json);
            assertThat(restored).isEqualTo(messages);
        }

        @Test
        @DisplayName("空内容块消息往返不丢角色")
        void messageJsonRoundTrip_emptyContent() {
            AgentMessage message = AgentMessage.builder().role(AgentMessageRole.TOOL).build();
            AgentMessage restored = AgentMessageSerializer.fromJson(AgentMessageSerializer.toJson(message));
            assertThat(restored.getRole()).isEqualTo(AgentMessageRole.TOOL);
            assertThat(restored.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("检查点序列化")
    class CheckpointSerializationTest {

        @Test
        @DisplayName("检查点JSON往返一致")
        void checkpointJsonRoundTrip() {
            AgentCheckpoint checkpoint = new AgentCheckpoint(
                    "run-1", "scope-1", "session-1", 3,
                    List.of(AgentMessage.builder().role(AgentMessageRole.USER)
                            .content(List.of(AgentTextBlock.builder().text("问题").build())).build()),
                    List.of(new AgentToolUseBlock("http_call", "tu-9",
                            java.util.Map.of("q", "v"))),
                    List.of("tu-8"), 1234567890L, 7);
            String json = AgentCheckpointSerializer.toJson(checkpoint);
            AgentCheckpoint restored = AgentCheckpointSerializer.fromJson(json);
            assertThat(restored.getRunId()).isEqualTo("run-1");
            assertThat(restored.getScopeId()).isEqualTo("scope-1");
            assertThat(restored.getSessionId()).isEqualTo("session-1");
            assertThat(restored.getIteration()).isEqualTo(3);
            assertThat(restored.getMessages()).hasSize(1);
            assertThat(restored.getMessages().get(0).getTextContent()).isEqualTo("问题");
            assertThat(restored.getPendingToolCalls()).hasSize(1);
            assertThat(restored.getPendingToolCalls().get(0).getToolName()).isEqualTo("http_call");
            assertThat(restored.getCompletedToolUseIds()).containsExactly("tu-8");
            assertThat(restored.getTimestamp()).isEqualTo(1234567890L);
            assertThat(restored.getVersion()).isEqualTo(7);
        }

        @Test
        @DisplayName("非法JSON反序列化抛出IllegalStateException")
        void fromJson_invalidJson_shouldThrowIllegalState() {
            assertThatThrownBy(() -> AgentCheckpointSerializer.fromJson("{not-json"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("反序列化");
        }
    }
}
