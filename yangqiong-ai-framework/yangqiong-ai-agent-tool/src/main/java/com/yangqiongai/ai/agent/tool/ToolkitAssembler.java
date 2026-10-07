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
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.ToolConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具箱装配器
 * @author yangqiong
 */
public class ToolkitAssembler {

    private static final Logger log = LoggerFactory.getLogger(ToolkitAssembler.class);

    private final McpConnectionPool mcpConnectionPool;

    /**
     * 项目工具提供者列表
     */
    private final List<Tool> toolProviders;

    /**
     * 工具配置服务(可选,用于白名单和启用状态过滤)
     */
    private final ToolConfigManager toolConfigManager;

    public ToolkitAssembler(McpConnectionPool mcpConnectionPool,
                            List<Tool> toolProviders,
                            ToolConfigManager toolConfigManager) {
        this.mcpConnectionPool = mcpConnectionPool;
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
        this.toolConfigManager = toolConfigManager;
    }

    /**
     * 按Agent编码和工具白名单装配工具箱
     * <p>
     * 装配规则：BUILTIN内置工具自动装配不过滤；CUSTOM工具按白名单挂载（未配置=不挂载）；
     * MCP按显式清单传入，未传入=不挂载任何MCP。
     * </p>
     * @param agentCode
     * @param mcpConfigs MCP服务配置清单（agentConfig.mcpServers显式声明）
     * @param allowedTools 非内置工具白名单（agentConfig.tools与请求级覆盖合并后的清单）
     * @return
     */
    public Toolkit assemble(String agentCode, List<McpServerConfig> mcpConfigs, Set<String> allowedTools) {
        return assemble(agentCode, mcpConfigs, allowedTools, null);
    }

    /**
     * 装配Toolkit（在MCP配置基础上并入外部工具集，连接器/示例等场景使用）
     * @param agentCode
     * @param mcpConfigs MCP服务配置清单（agentConfig.mcpServers显式声明）
     * @param allowedTools 非内置工具白名单
     * @param extraTools 外部产出的AgentTool列表，可空
     * @return
     */
    public Toolkit assemble(String agentCode, List<McpServerConfig> mcpConfigs, Set<String> allowedTools, List<AgentTool> extraTools) {
        Toolkit toolkit = new Toolkit();
        Set<String> whitelist = allowedTools != null ? allowedTools : Set.of();

        // 注册项目工具（BUILTIN自动装配，CUSTOM按白名单挂载，叠加禁用过滤）
        int toolAdded = 0;
        for (Tool provider : toolProviders) {
            String toolCode = provider.getToolCode();
            if (!isBuiltin(provider) && !whitelist.contains(toolCode)) {
                log.debug("工具 {} 为CUSTOM且不在 agentCode={} 的白名单中,跳过", toolCode, agentCode);
                continue;
            }
            if (!isToolEnabled(toolCode)) {
                log.debug("工具 {} 已禁用,跳过", toolCode);
                continue;
            }
            toolkit.addTool(provider);
            toolAdded++;
        }

        // 注册MCP工具（按显式清单传入，serverStatus过滤仍生效）
        int[] mcpAdded = {0};
        if (mcpConfigs != null) {
            for (McpServerConfig config : mcpConfigs) {
                if (config.getServerStatus() != 1) continue;

                String serverCode = config.getServerCode();
                if (!mcpConnectionPool.getClient(serverCode).isPresent()) {
                    log.warn("MCP服务{}在连接池中不存在，可能未注册成功或已被下线，跳过工具注册", serverCode);
                    continue;
                }

                mcpConnectionPool.getClientWithPolicy(
                        serverCode,
                        config.getEnabledTools(),
                        config.getDisabledTools()
                ).ifPresent(client -> {
                    List<McpToolInfo> tools = client.listTools();
                    tools.forEach(tool -> {
                        tool.setServerCode(serverCode);
                        toolkit.addMcpTool(tool, client);
                    });
                    mcpAdded[0]++;
                });
            }
        }

        // 并入外部工具集(连接器等场景)，命名冲突时外部工具跳过并告警
        int extraAdded = 0;
        if (extraTools != null && !extraTools.isEmpty()) {
            Set<String> existingNames = new HashSet<>();
            for (Tool provider : toolProviders) {
                existingNames.add(provider.getToolCode());
            }
            for (AgentTool extra : extraTools) {
                if (extra == null || extra.getName() == null) {
                    continue;
                }
                if (!existingNames.add(extra.getName())) {
                    log.warn("外部工具{}与已装配工具重名,跳过: agentCode={}", extra.getName(), agentCode);
                    continue;
                }
                toolkit.addTool(extra);
                extraAdded++;
            }
        }

        // 设置MCP工具调用超时和健康检查器
        toolkit.setMcpToolCallTimeoutSeconds(mcpConnectionPool.getToolCallTimeoutSeconds());
        toolkit.setMcpHealthChecker(mcpConnectionPool.getHealthChecker());

        log.info("装配工具箱: agentCode={}, whitelistSize={}, tools={}, mcpServers={}, extraTools={}, mcpTimeout={}s",
                agentCode, whitelist.size(), toolAdded, mcpAdded[0], extraAdded,
                mcpConnectionPool.getToolCallTimeoutSeconds());

        return toolkit;
    }

    /**
     * 判断是否内置工具（BUILTIN分类自动装配不过滤）
     * @param tool
     * @return
     */
    private boolean isBuiltin(Tool tool) {
        return ToolCategory.BUILTIN == tool.getToolCategory();
    }

    /**
     * 检查工具是否启用(数据库中tool_status=1)
     */
    private boolean isToolEnabled(String toolCode) {
        if (toolConfigManager == null) {
            return true;
        }
        try {
            ToolConfigInfo config = toolConfigManager.getByToolCode(toolCode);
            // 未登记的工具默认放行(同步尚未执行或刚新增的工具)
            return config == null || (config.getToolStatus() != null && config.getToolStatus() == 1);
        } catch (Exception e) {
            log.warn("查询工具启用状态失败,默认放行: toolCode={}", toolCode, e);
            return true;
        }
    }
}
