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
package com.yangqiongai.ai.agent.core.provider;

import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具箱提供者接口
 * <p>
 * 定义在ai-agent-core中，由ai-agent-tool模块实现。
 * 通过Spring自动注入，实现模块间的解耦。
 * 不引用ai-agent-mcp的具体类型，避免循环依赖。
 * </p>
 * @author yangqiong
 */
public interface ToolkitProvider {

    /**
     * 按agentCode装配工具箱（BUILTIN内置工具自动装配，CUSTOM工具按白名单挂载）
     * @param agentCode Agent编码
     * @param mcpConfigs MCP服务配置列表（通用Map表示，避免模块间类型耦合）
     * @param allowedTools 非内置工具白名单（来自agentConfig.tools与请求级覆盖合并），空=仅装配BUILTIN工具
     * @return 框架层工具箱
     */
    AgentToolkit assembleToolkit(String agentCode, List<Map<String, Object>> mcpConfigs, Set<String> allowedTools);

    /**
     * 按显式MCP server清单查询配置（未配置清单=不挂载任何MCP）
     * @param agentCode Agent编码
     * @param serverCodes agentConfig.mcpServers声明的serverCode清单
     * @return MCP配置列表（通用Map表示）
     */
    List<Map<String, Object>> resolveMcpConfigs(String agentCode, List<String> serverCodes);
}
