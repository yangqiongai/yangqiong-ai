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
import com.yangqiongai.ai.agent.mcp.client.McpHealthChecker;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MCP自动配置
 * @author yangqiong
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAutoConfiguration.class);

    /**
     * 创建McpConnectionPool
     * <p>
     * 连接池创建后，将引用注入到McpServerConfigService和McpHealthChecker，
     * 然后立即执行MCP客户端注册，确保Bean就绪时连接池可用。
     * </p>
     * @param clientFactory
     * @param healthChecker
     * @param properties
     * @param serverConfigService
     * @return
     */
    @Bean
    public McpConnectionPool mcpConnectionPool(McpClientFactory clientFactory,
                                               McpHealthChecker healthChecker,
                                               McpProperties properties,
                                               McpServerConfigService serverConfigService) {
        McpConnectionPool pool = new McpConnectionPool(clientFactory, healthChecker, properties);
        serverConfigService.setMcpConnectionPool(pool);
        healthChecker.setServerConfigService(serverConfigService);
        // 在Bean创建阶段即注册MCP客户端，确保Tomcat接收请求时连接池已就绪
        serverConfigService.initMcpConnections();
        log.info("MCP连接池初始化完成，已注册服务数: {}", pool.getRegisteredServerCodes().size());
        return pool;
    }
}
