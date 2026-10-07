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
import com.yangqiongai.ai.agent.mcp.client.McpClientWrapper;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("ToolkitAssembler 单元测试")
class ToolkitAssemblerTest {

    @Nested
    @DisplayName("assemble 测试")
    class AssembleTest {

        @Test
        @DisplayName("BUILTIN工具自动装配到工具箱")
        void shouldAddBuiltinToolProviders() {
            Tool tool1 = new BuiltinStubTool();
            Tool tool2 = new BuiltinStubTool2();
            McpConnectionPool mcpPool = mock(McpConnectionPool.class);

            ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, List.of(tool1, tool2), null);
            Toolkit toolkit = assembler.assemble("task1", null, Set.of());

            assertThat(toolkit.getTools()).containsExactly(tool1, tool2);
        }

        @Test
        @DisplayName("按serverStatus过滤MCP配置")
        void shouldFilterByServerStatus() {
            McpConnectionPool mcpPool = mock(McpConnectionPool.class);

            McpServerConfig activeConfig = new McpServerConfig();
            activeConfig.setServerCode("server-1");
            activeConfig.setServerStatus(1);

            McpServerConfig inactiveConfig = new McpServerConfig();
            inactiveConfig.setServerCode("server-2");
            inactiveConfig.setServerStatus(0);

            McpClientWrapper client = mock(McpClientWrapper.class);
            when(client.listTools()).thenReturn(List.of());
            when(mcpPool.getClient("server-1")).thenReturn(Optional.of(client));
            when(mcpPool.getClientWithPolicy("server-1", null, null)).thenReturn(Optional.of(client));

            ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, List.of(), null);
            Toolkit toolkit = assembler.assemble("task1", List.of(activeConfig, inactiveConfig), Set.of());

            verify(mcpPool).getClientWithPolicy("server-1", null, null);
            verify(mcpPool, never()).getClientWithPolicy(eq("server-2"), any(), any());
            assertThat(toolkit.getMcpTools()).isEmpty();
        }

        @Test
        @DisplayName("null mcpConfigs仅添加项目工具")
        void shouldOnlyAddProjectToolsWhenNullMcpConfigs() {
            Tool tool = new BuiltinStubTool();
            McpConnectionPool mcpPool = mock(McpConnectionPool.class);

            ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, List.of(tool), null);
            Toolkit toolkit = assembler.assemble("task1", null, Set.of());

            assertThat(toolkit.getTools()).containsExactly(tool);
            assertThat(toolkit.getMcpTools()).isEmpty();
            verify(mcpPool, never()).getClient(anyString());
            verify(mcpPool, never()).getClientWithPolicy(anyString(), any(), any());
        }

        @Test
        @DisplayName("null toolProviders创建空工具箱")
        void shouldCreateEmptyToolkitWithNullToolProviders() {
            McpConnectionPool mcpPool = mock(McpConnectionPool.class);

            ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, null, null);
            Toolkit toolkit = assembler.assemble("task1", null, Set.of());

            assertThat(toolkit.getTools()).isEmpty();
            assertThat(toolkit.getMcpTools()).isEmpty();
        }
    }

    static class BuiltinStubTool implements Tool {
        @Override
        public ToolCategory getToolCategory() {
            return ToolCategory.BUILTIN;
        }
    }

    static class BuiltinStubTool2 implements Tool {
        @Override
        public ToolCategory getToolCategory() {
            return ToolCategory.BUILTIN;
        }
    }
}
