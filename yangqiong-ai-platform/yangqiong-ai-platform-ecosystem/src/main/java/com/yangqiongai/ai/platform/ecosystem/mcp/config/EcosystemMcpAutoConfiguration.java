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
package com.yangqiongai.ai.platform.ecosystem.mcp.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import com.yangqiongai.ai.platform.ecosystem.mcp.ExposeWhitelistGuard;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpAppsUiContract;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpExposeService;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpExposeServiceImpl;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpPromptMapper;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpResourceMapper;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpResourceSource;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpToolMapper;
import com.yangqiongai.ai.platform.ecosystem.mcp.McpToolsCallGuard;
import com.yangqiongai.ai.platform.ecosystem.mcp.controller.McpExposeController;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 生态出口MCP服务自动配置
 * <p>
 * 协议与传输由官方SDK承载(Streamable HTTP)，能力映射与治理自研；
 * 暴露白名单默认全不暴露，ai.ecosystem.mcp.enabled 开启协议端点。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration(afterName = "com.yangqiongai.ai.agent.data.AiAgentDataAutoConfiguration")
@ConditionalOnClass(BaseMapper.class)
@ConditionalOnProperty(prefix = "ai.ecosystem", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({EcosystemMcpProperties.class, McpAppsProperties.class})
public class EcosystemMcpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EcosystemMcpAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public McpExposeService mcpExposeService() {
        return new McpExposeServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpAppsUiContract mcpAppsUiContract(McpAppsProperties appsProperties, McpExposeService exposeService) {
        return new McpAppsUiContract(appsProperties, exposeService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    public McpExposeController mcpExposeController(McpExposeService exposeService,
                                                   ObjectProvider<Tool> toolProviders) {
        return new McpExposeController(exposeService, toolProviders.stream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolsCallGuard mcpToolsCallGuard(McpExposeService exposeService) {
        return new ExposeWhitelistGuard(exposeService);
    }

    /**
     * MCP协议端点(官方SDK Streamable HTTP传输,Servlet承载)
     * @param exposeService
     * @param guard
     * @param agentTaskRepository
     * @param toolProviders
     * @param capabilityCatalog
     * @param resourceSources
     * @param properties
     * @param appsUiContract
     * @return
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnBean({AgentTaskRepository.class, AgentEngine.class})
    @ConditionalOnProperty(prefix = "ai.ecosystem.mcp", name = "enabled", havingValue = "true")
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpStreamableServlet(
            McpExposeService exposeService,
            McpToolsCallGuard guard,
            AgentTaskRepository agentTaskRepository,
            List<Tool> toolProviders,
            CapabilityCatalog capabilityCatalog,
            List<McpResourceSource> resourceSources,
            EcosystemMcpProperties properties,
            McpAppsUiContract appsUiContract,
            QueueProperties queueProperties,
            AgentTaskEnqueuer taskEnqueuer,
            AgentEngine agentEngine) {
        McpToolMapper toolMapper = new McpToolMapper(exposeService, guard, agentTaskRepository,
                toolProviders, properties.getToolCallTimeoutMillis(), appsUiContract,
                queueProperties, taskEnqueuer, agentEngine);
        McpPromptMapper promptMapper = new McpPromptMapper(exposeService, guard, capabilityCatalog);
        McpResourceMapper resourceMapper = new McpResourceMapper(exposeService, guard, resourceSources);

        List<McpServerFeatures.SyncToolSpecification> tools = toolMapper.buildToolSpecifications();
        List<McpServerFeatures.SyncPromptSpecification> prompts = promptMapper.buildPromptSpecifications();
        List<McpServerFeatures.SyncResourceSpecification> resources = resourceMapper.buildResourceSpecifications();
        log.info("MCP服务出口装配: tools={}, prompts={}, resources={}", tools.size(), prompts.size(), resources.size());

        HttpServletStreamableServerTransportProvider transportProvider =
                HttpServletStreamableServerTransportProvider.builder()
                        .mcpEndpoint(properties.getEndpoint())
                        .build();
        // 能力协商:仅在有实际暴露项时声明对应原语
        McpSchema.ServerCapabilities.Builder capabilitiesBuilder =
                new McpSchema.ServerCapabilities.Builder().tools(true);
        if (!prompts.isEmpty()) {
            capabilitiesBuilder.prompts(true);
        }
        if (!resources.isEmpty()) {
            capabilitiesBuilder.resources(true, false);
        }
        McpServer.sync(transportProvider)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                .capabilities(capabilitiesBuilder.build())
                .tools(tools)
                .prompts(prompts)
                .resources(resources)
                .build();
        return new ServletRegistrationBean<>(transportProvider, properties.getEndpoint());
    }
}
