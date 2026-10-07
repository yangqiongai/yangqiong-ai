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

import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.agent.core.provider.ToolkitProvider;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具箱提供者实现
 * <p>
 * 桥接ai-agent-core的ToolkitProvider接口与ai-agent-tool的ToolkitAssembler/McpServerConfigService，
 * 按正向单源清单装配：BUILTIN内置工具自动装配，CUSTOM工具按白名单挂载，MCP按显式serverCode清单挂载。
 * </p>
 *
 * @author yangqiong
 */
@Component
public class DefaultToolkitProvider implements ToolkitProvider {

    @Autowired
    private ToolkitAssembler toolkitAssembler;

    @Autowired
    private McpServerConfigService mcpServerConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 外部工具收集者列表(可选，连接器等场景由平台侧提供)
     */
    @Autowired(required = false)
    private List<ToolExtraContributor> extraContributors;

    @Override
    public AgentToolkit assembleToolkit(String agentCode, List<Map<String, Object>> mcpConfigs, Set<String> allowedTools) {
        List<McpServerConfig> configs = mcpConfigs.stream()
                .map(this::toMcpServerConfig)
                .toList();
        Toolkit toolkit = toolkitAssembler.assemble(agentCode, configs, allowedTools, collectExtraTools(agentCode));
        return toolkit.toAgentToolkit();
    }

    /**
     * 收集外部工具(单个收集者异常时跳过不影响主装配)
     * @param agentCode
     * @return
     */
    private List<AgentTool> collectExtraTools(String agentCode) {
        if (extraContributors == null || extraContributors.isEmpty()) {
            return null;
        }
        List<AgentTool> extraTools = new ArrayList<>();
        for (ToolExtraContributor contributor : extraContributors) {
            try {
                List<AgentTool> tools = contributor.collectTools(agentCode);
                if (tools != null && !tools.isEmpty()) {
                    extraTools.addAll(tools);
                }
            } catch (Exception e) {
                org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultToolkitProvider.class);
                log.warn("外部工具收集者{}执行失败,跳过", contributor.getClass().getSimpleName(), e);
            }
        }
        return extraTools;
    }

    @Override
    public List<Map<String, Object>> resolveMcpConfigs(String agentCode, List<String> serverCodes) {
        // 未配置清单=不挂载任何MCP
        if (serverCodes == null || serverCodes.isEmpty()) {
            return List.of();
        }
        List<McpServerConfig> configs = mcpServerConfigService.listByServerCodes(serverCodes);
        return configs.stream()
                .map(this::toMap)
                .toList();
    }

    private McpServerConfig toMcpServerConfig(Map<String, Object> map) {
        try {
            return objectMapper.convertValue(map, McpServerConfig.class);
        } catch (Exception e) {
            McpServerConfig config = new McpServerConfig();
            config.setServerCode((String) map.get("serverCode"));
            config.setServerName((String) map.get("serverName"));
            config.setTransportType((String) map.get("transportType"));
            config.setServerStatus(map.get("serverStatus") instanceof Number n ? n.intValue() : 1);
            return config;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(McpServerConfig config) {
        return objectMapper.convertValue(config, Map.class);
    }
}
