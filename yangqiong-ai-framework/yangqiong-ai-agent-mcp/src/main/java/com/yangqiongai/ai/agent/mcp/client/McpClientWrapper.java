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
package com.yangqiongai.ai.agent.mcp.client;

import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * MCP客户端包装器
 */
public interface McpClientWrapper {

    /**
     * 列出可用工具
     * @return
     */
    List<McpToolInfo> listTools();

    /**
     * 异步列出可用工具
     * @return
     */
    CompletableFuture<List<McpToolInfo>> listToolsAsync();

    /**
     * 调用工具
     * @param toolName
     * @param arguments
     * @return
     */
    String callTool(String toolName, String arguments);

    /**
     * 异步调用工具
     * @param toolName
     * @param arguments
     * @return
     */
    CompletableFuture<String> callToolAsync(String toolName, String arguments);

    /**
     * 关闭客户端
     */
    void close();

    /**
     * 获取服务编码
     * @return
     */
    String getServerCode();
}
