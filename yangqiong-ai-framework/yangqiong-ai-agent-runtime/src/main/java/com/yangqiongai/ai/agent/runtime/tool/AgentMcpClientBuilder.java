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
package com.yangqiongai.ai.agent.runtime.tool;

import java.util.List;
import java.util.Map;

/**
 * Agent MCP客户端构建器
 * @author yangqiong
 */
public interface AgentMcpClientBuilder {

    /**
     * 构建Stdio传输的MCP客户端
     * @param serverCode
     * @param command
     * @param args
     * @param env
     * @return
     */
    AgentMcpClient buildStdio(String serverCode, String command, List<String> args, Map<String, String> env);

    /**
     * 构建SSE传输的MCP客户端
     * @param serverCode
     * @param endpoint
     * @param headers
     * @return
     */
    AgentMcpClient buildSse(String serverCode, String endpoint, Map<String, String> headers);

    /**
     * 健康检查
     * @param client
     * @return
     */
    boolean healthCheck(AgentMcpClient client);
}
