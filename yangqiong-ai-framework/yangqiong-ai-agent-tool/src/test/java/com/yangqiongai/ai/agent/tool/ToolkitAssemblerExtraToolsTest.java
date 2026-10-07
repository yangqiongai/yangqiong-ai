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
package com.yangqiongai.ai.agent.tool;

import com.yangqiongai.ai.agent.mcp.McpConnectionPool;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ToolkitAssembler 外部工具集装配测试")
class ToolkitAssemblerExtraToolsTest {

    /**
     * 构建指定名称的模拟AgentTool
     * @param name 工具名
     * @return
     */
    private AgentTool mockAgentTool(String name) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "工具" + name;
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of();
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(
                        List.of(AgentTextBlock.builder().text("ok").build())));
            }
        };
    }

    /**
     * 构建BUILTIN分类的模拟项目工具
     * @return
     */
    private Tool mockBuiltinTool() {
        Tool tool = mock(Tool.class);
        when(tool.getToolCategory()).thenReturn(ToolCategory.BUILTIN);
        return tool;
    }

    @Nested
    @DisplayName("extraTools 并入测试")
    class ExtraToolsTest {

        @Test
        @DisplayName("extraTools为null时按原逻辑装配")
        void shouldAssembleWithoutExtraToolsWhenNull() {
            Tool tool = mockBuiltinTool();
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(tool), null);

            Toolkit toolkit = assembler.assemble("agent-1", null, null, null);

            assertThat(toolkit.getTools()).containsExactly(tool);
        }

        @Test
        @DisplayName("extraTools为空列表时按原逻辑装配")
        void shouldAssembleWithoutExtraToolsWhenEmpty() {
            Tool tool = mockBuiltinTool();
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(tool), null);

            Toolkit toolkit = assembler.assemble("agent-1", null, null, List.of());

            assertThat(toolkit.getTools()).containsExactly(tool);
        }

        @Test
        @DisplayName("外部工具正常并入工具箱")
        void shouldAppendExtraTools() {
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(), null);
            AgentTool extra1 = mockAgentTool("connector_dt_send_notice");
            AgentTool extra2 = mockAgentTool("connector_dt_query_user");

            Toolkit toolkit = assembler.assemble("agent-1", null, null, List.of(extra1, extra2));

            assertThat(toolkit.getTools()).containsExactly(extra1, extra2);
        }

        @Test
        @DisplayName("外部工具与项目工具重名时跳过外部工具")
        void shouldSkipExtraToolConflictingWithProjectTool() {
            Tool provider = mockBuiltinTool();
            when(provider.getToolCode()).thenReturn("builtin_tool");
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(provider), null);
            AgentTool extra = mockAgentTool("builtin_tool");
            AgentTool normal = mockAgentTool("connector_db_query");

            Toolkit toolkit = assembler.assemble("agent-1", null, null, List.of(extra, normal));

            // BUILTIN内置工具自动装配，冲突外部工具跳过，无冲突外部工具正常并入
            assertThat(toolkit.getTools()).containsExactly(provider, normal);
        }

        @Test
        @DisplayName("外部工具之间重名时后者跳过")
        void shouldSkipDuplicatedExtraTools() {
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(), null);
            AgentTool first = mockAgentTool("connector_db_query");
            AgentTool duplicate = mockAgentTool("connector_db_query");

            Toolkit toolkit = assembler.assemble("agent-1", null, null, List.of(first, duplicate));

            assertThat(toolkit.getTools()).containsExactly(first);
        }

        @Test
        @DisplayName("外部工具列表含null元素时跳过")
        void shouldSkipNullExtraTool() {
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(), null);
            AgentTool extra = mockAgentTool("connector_db_query");

            Toolkit toolkit = assembler.assemble("agent-1", null, null, java.util.Arrays.asList(null, extra));

            assertThat(toolkit.getTools()).containsExactly(extra);
        }

        @Test
        @DisplayName("三参重载委托四参重载行为一致")
        void shouldDelegateFromThreeArgOverload() {
            Tool tool = mockBuiltinTool();
            ToolkitAssembler assembler = new ToolkitAssembler(mock(McpConnectionPool.class), List.of(tool), null);

            Toolkit toolkit = assembler.assemble("agent-1", null, null);

            assertThat(toolkit.getTools()).containsExactly(tool);
        }
    }
}
