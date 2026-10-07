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

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP客户端桥接构建器
 * <p>
 * 解耦McpClientFactory对底层MCP SDK的依赖，
 * SSE与Stdio传输的客户端构建由适配器模块实现并注入。
 * </p>
 * @author yangqiong
 */
public interface McpClientBridgeFactory {

    /**
     * 构建SSE传输的MCP客户端
     * @param serverCode
     * @param endpoint
     * @param authHeaders
     * @param initTimeout
     * @param toolCallTimeout
     * @return
     */
    McpClientWrapper buildSseBridge(String serverCode, String endpoint, Map<String, String> authHeaders,
                                    Duration initTimeout, Duration toolCallTimeout);

    /**
     * 构建Stdio传输的MCP客户端
     * @param serverCode
     * @param command
     * @param args
     * @param env
     * @param initTimeout
     * @param toolCallTimeout
     * @return
     */
    McpClientWrapper buildStdioBridge(String serverCode, String command, List<String> args, Map<String, String> env,
                                      Duration initTimeout, Duration toolCallTimeout);
}
