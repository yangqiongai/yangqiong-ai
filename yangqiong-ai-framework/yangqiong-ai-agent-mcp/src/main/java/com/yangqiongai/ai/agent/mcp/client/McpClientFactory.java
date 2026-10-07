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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.mcp.McpProperties;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.model.McpTransportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP客户端工厂
 * @author yangqiong
 */
@Component
public class McpClientFactory {

    private static final Logger log = LoggerFactory.getLogger(McpClientFactory.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 传输类型到构建策略的映射，替代if-else分支
     */
    private final Map<McpTransportType, Function<McpServerConfig, McpClientWrapper>> buildStrategies;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpAuthConfigurator authConfigurator;

    @Autowired
    private McpProperties mcpProperties;

    /**
     * SSE与Stdio传输的桥接构建器，由适配器模块可选注入
     */
    @Autowired(required = false)
    private McpClientBridgeFactory bridgeFactory;

    public McpClientFactory() {
        this.buildStrategies = new LinkedHashMap<>();
        this.buildStrategies.put(McpTransportType.STREAMABLE_HTTP, this::buildStreamableHttp);
        this.buildStrategies.put(McpTransportType.SSE, this::buildSse);
        this.buildStrategies.put(McpTransportType.STDIO, this::buildStdio);
    }

    /**
     * 构建MCP客户端
     * @param config
     * @return
     */
    public McpClientWrapper buildClient(McpServerConfig config) {
        McpTransportType transportType = determineTransportType(config);
        Function<McpServerConfig, McpClientWrapper> strategy = buildStrategies.get(transportType);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的MCP传输类型: " + transportType);
        }
        return strategy.apply(config);
    }

    /**
     * 判断传输类型
     * @param config
     * @return
     */
    public McpTransportType determineTransportType(McpServerConfig config) {
        return McpTransportType.inferFromConfig(config);
    }

    /**
     * 获取工具调用超时时间
     * @return
     */
    public Duration getToolCallTimeout() {
        return Duration.ofSeconds(mcpProperties.getToolCallTimeoutSeconds());
    }

    /**
     * 获取初始化超时时间
     * @return
     */
    public Duration getInitTimeout() {
        return Duration.ofSeconds(mcpProperties.getInitTimeoutSeconds());
    }

    private McpClientWrapper buildStreamableHttp(McpServerConfig config) {
        String endpoint = extractEndpoint(config);
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("StreamableHttp传输需要配置endpoint");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        authConfigurator.configureAuth(headers, config);
        Duration timeout = resolveTimeout(config);

        StreamableHttpWrapper wrapper = new StreamableHttpWrapper(
                config.getServerCode(),
                endpoint.trim(),
                timeout,
                headers,
                objectMapper
        );
        wrapper.initialize();
        log.info("构建StreamableHttp客户端: serverCode={}, endpoint={}", config.getServerCode(), endpoint);
        return wrapper;
    }

    private McpClientWrapper buildSse(McpServerConfig config) {
        String endpoint = extractEndpoint(config);
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("SSE传输需要配置endpoint");
        }
        if (bridgeFactory == null) {
            throw new IllegalStateException("SSE传输需要McpClientBridgeFactory支持（请引入yangqiong-ai-agent-scope模块）");
        }
        Duration initTimeout = resolveInitTimeout(config);
        Map<String, String> headers = new LinkedHashMap<>();
        authConfigurator.configureAuth(headers, config);

        McpClientWrapper wrapper = bridgeFactory.buildSseBridge(
                config.getServerCode(), endpoint.trim(), headers, initTimeout, resolveToolCallTimeout(config));
        log.info("构建SSE客户端: serverCode={}, endpoint={}", config.getServerCode(), endpoint);
        return wrapper;
    }

    private McpClientWrapper buildStdio(McpServerConfig config) {
        String command = extractCommand(config);
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Stdio传输需要配置command");
        }
        if (bridgeFactory == null) {
            throw new IllegalStateException("Stdio传输需要McpClientBridgeFactory支持（请引入yangqiong-ai-agent-scope模块）");
        }
        Duration initTimeout = resolveInitTimeout(config);

        McpClientWrapper wrapper = bridgeFactory.buildStdioBridge(
                config.getServerCode(), command.trim(),
                extractCommandArgs(config), extractEnvConfig(config),
                initTimeout, resolveToolCallTimeout(config));
        log.info("构建Stdio客户端: serverCode={}, command={}", config.getServerCode(), command);
        return wrapper;
    }

    /**
     * 解析工具调用超时时间
     * @param config
     * @return
     */
    private Duration resolveToolCallTimeout(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return getToolCallTimeout();
        }
        Object timeoutObj = config.getConnectionConfig().get("toolCallTimeoutSeconds");
        if (timeoutObj instanceof Number num && num.intValue() > 0) {
            return Duration.ofSeconds(num.intValue());
        }
        return getToolCallTimeout();
    }

    /**
     * 解析初始化超时时间
     * @param config
     * @return
     */
    private Duration resolveInitTimeout(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return getInitTimeout();
        }
        Object timeoutObj = config.getConnectionConfig().get("initTimeoutSeconds");
        if (timeoutObj instanceof Number num && num.intValue() > 0) {
            return Duration.ofSeconds(num.intValue());
        }
        return getInitTimeout();
    }

    private Duration resolveTimeout(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return DEFAULT_TIMEOUT;
        }
        Object timeoutObj = config.getConnectionConfig().get("timeoutSeconds");
        if (timeoutObj instanceof Number num && num.intValue() > 0) {
            return Duration.ofSeconds(num.intValue());
        }
        return DEFAULT_TIMEOUT;
    }

    private String extractEndpoint(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return null;
        }
        Object value = config.getConnectionConfig().get("endpoint");
        return value != null ? String.valueOf(value) : null;
    }

    private String extractCommand(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return null;
        }
        Object value = config.getConnectionConfig().get("command");
        return value != null ? String.valueOf(value) : null;
    }

    private List<String> extractCommandArgs(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return List.of();
        }
        Object value = config.getConnectionConfig().get("commandArgs");
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
    }

    private Map<String, String> extractEnvConfig(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return Map.of();
        }
        Object value = config.getConnectionConfig().get("envConfig");
        if (value instanceof Map<?, ?> raw) {
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(String.valueOf(k), String.valueOf(v));
                }
            });
            return result;
        }
        return Map.of();
    }
}
