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
package com.yangqiongai.ai.agent.mcp;

import com.yangqiongai.ai.agent.mcp.client.McpClientFactory;
import com.yangqiongai.ai.agent.mcp.client.McpClientWrapper;
import com.yangqiongai.ai.agent.mcp.client.McpHealthChecker;
import com.yangqiongai.ai.agent.mcp.client.ToolPolicyMcpClientWrapper;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpConnectionPool 单元测试")
class McpConnectionPoolTest {

    @Mock
    private McpClientFactory clientFactory;

    @Mock
    private McpHealthChecker healthChecker;

    @Mock
    private McpClientWrapper mockClient;

    private McpConnectionPool connectionPool;

    private McpServerConfig sampleConfig;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        connectionPool = new McpConnectionPool(clientFactory, healthChecker, properties);

        sampleConfig = new McpServerConfig();
        sampleConfig.setServerCode("mcp-001");
        sampleConfig.setServerName("测试服务");
        sampleConfig.setTransportType("streamable-http");
    }

    @Nested
    @DisplayName("registerClient")
    class RegisterClientTest {

        @Test
        @DisplayName("注册成功")
        void registersSuccessfully() {
            when(clientFactory.buildClient(sampleConfig)).thenReturn(mockClient);

            connectionPool.registerClient("mcp-001", sampleConfig);

            Optional<McpClientWrapper> result = connectionPool.getClient("mcp-001");
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(mockClient);
        }

        @Test
        @DisplayName("注册失败 - 静默处理（仅日志）")
        void failure_silent() {
            when(clientFactory.buildClient(sampleConfig)).thenThrow(new RuntimeException("连接失败"));

            connectionPool.registerClient("mcp-001", sampleConfig);

            Optional<McpClientWrapper> result = connectionPool.getClient("mcp-001");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null mcpId - 忽略")
        void nullMcpId_ignored() {
            connectionPool.registerClient(null, sampleConfig);

            verify(clientFactory, never()).buildClient(any());
        }

        @Test
        @DisplayName("null config - 忽略")
        void nullConfig_ignored() {
            connectionPool.registerClient("mcp-001", null);

            verify(clientFactory, never()).buildClient(any());
        }
    }

    @Nested
    @DisplayName("getClient")
    class GetClientTest {

        @Test
        @DisplayName("找到客户端")
        void found() {
            when(clientFactory.buildClient(sampleConfig)).thenReturn(mockClient);
            connectionPool.registerClient("mcp-001", sampleConfig);

            Optional<McpClientWrapper> result = connectionPool.getClient("mcp-001");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("未找到客户端")
        void notFound() {
            Optional<McpClientWrapper> result = connectionPool.getClient("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null mcpId - 返回空")
        void nullMcpId_returnsEmpty() {
            Optional<McpClientWrapper> result = connectionPool.getClient(null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getClientWithPolicy")
    class GetClientWithPolicyTest {

        @BeforeEach
        void registerClient() {
            when(clientFactory.buildClient(sampleConfig)).thenReturn(mockClient);
            connectionPool.registerClient("mcp-001", sampleConfig);
        }

        @Test
        @DisplayName("有enabled/disabled工具 - 返回ToolPolicyMcpClientWrapper")
        void withPolicy_returnsWrapped() {
            sampleConfig.setEnabledTools(List.of("tool1"));

            Optional<McpClientWrapper> result = connectionPool.getClientWithPolicy("mcp-001", List.of("tool1"), List.of("tool2"));

            assertThat(result).isPresent();
            assertThat(result.get()).isInstanceOf(ToolPolicyMcpClientWrapper.class);
        }

        @Test
        @DisplayName("无策略 - 返回原始客户端")
        void noPolicy_returnsRawClient() {
            Optional<McpClientWrapper> result = connectionPool.getClientWithPolicy("mcp-001", null, null);

            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(mockClient);
        }

        @Test
        @DisplayName("空策略列表 - 返回原始客户端")
        void emptyPolicy_returnsRawClient() {
            Optional<McpClientWrapper> result = connectionPool.getClientWithPolicy("mcp-001", Collections.emptyList(), Collections.emptyList());

            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(mockClient);
        }
    }

    @Nested
    @DisplayName("removeClient")
    class RemoveClientTest {

        @Test
        @DisplayName("移除并关闭客户端")
        void removesAndCloses() {
            when(clientFactory.buildClient(sampleConfig)).thenReturn(mockClient);
            connectionPool.registerClient("mcp-001", sampleConfig);

            connectionPool.removeClient("mcp-001");

            verify(mockClient).close();
            verify(healthChecker).removeState("mcp-001");
            assertThat(connectionPool.getClient("mcp-001")).isEmpty();
        }
    }

    @Nested
    @DisplayName("listAvailableTools")
    class ListAvailableToolsTest {

        @Test
        @DisplayName("返回全部已注册客户端的工具（挂载关系由装配链路过滤）")
        void returnsAllRegisteredClientTools() {
            sampleConfig.setEnabledTools(null);
            sampleConfig.setDisabledTools(null);
            when(clientFactory.buildClient(sampleConfig)).thenReturn(mockClient);
            connectionPool.registerClient("mcp-001", sampleConfig);

            McpToolInfo toolInfo = new McpToolInfo("tool1", "desc", "{}", "mcp-001");
            when(mockClient.listTools()).thenReturn(List.of(toolInfo));

            List<McpToolInfo> visible = connectionPool.listAvailableTools("wiki-ingest");
            assertThat(visible).hasSize(1);
        }
    }

    @Nested
    @DisplayName("shutdownAll")
    class ShutdownAllTest {

        @Test
        @DisplayName("关闭所有客户端")
        void closesAllClients() {
            when(clientFactory.buildClient(sampleConfig)).thenReturn(mockClient);
            connectionPool.registerClient("mcp-001", sampleConfig);

            when(healthChecker.getUnhealthyClientIds()).thenReturn(Collections.emptySet());

            connectionPool.shutdownAll();

            verify(mockClient).close();
            assertThat(connectionPool.getClient("mcp-001")).isEmpty();
        }
    }
}
