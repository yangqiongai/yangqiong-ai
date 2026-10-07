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

import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * MCP认证配置器
 * @author yangqiong
 */
@Component
public class McpAuthConfigurator {

    private static final Logger log = LoggerFactory.getLogger(McpAuthConfigurator.class);

    /**
     * 认证模板注册表，按authType匹配对应的配置策略
     */
    private final Map<String, BiConsumer<Map<String, String>, Map<String, String>>> authTemplates;

    public McpAuthConfigurator() {
        this.authTemplates = new LinkedHashMap<>();
        authTemplates.put("bearer", this::applyBearerAuth);
        authTemplates.put("basic", this::applyBasicAuth);
        authTemplates.put("apikey", this::applyApiKeyAuth);
        authTemplates.put("header", this::applyHeaderAuth);
    }

    /**
     * 配置认证头信息
     * @param headers
     * @param config
     */
    public void configureAuth(Map<String, String> headers, McpServerConfig config) {
        Map<String, String> authConfig = resolveAuthConfig(config);
        if (authConfig.isEmpty()) {
            return;
        }
        String authType = resolveAuthType(config);
        BiConsumer<Map<String, String>, Map<String, String>> applier = authTemplates.get(authType);
        if (applier != null) {
            applier.accept(headers, authConfig);
        } else {
            // 未知类型直接透传所有键值对
            applyHeaderAuth(headers, authConfig);
        }
    }

    /**
     * 解析认证类型
     * @param config
     * @return
     */
    private String resolveAuthType(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return "header";
        }
        Object authType = config.getConnectionConfig().get("authType");
        if (authType == null || String.valueOf(authType).trim().isEmpty()) {
            return "header";
        }
        return String.valueOf(authType).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 解析认证配置
     * @param config
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> resolveAuthConfig(McpServerConfig config) {
        if (config == null || config.getConnectionConfig() == null) {
            return Map.of();
        }
        Object authConfigObj = config.getConnectionConfig().get("authConfig");
        if (authConfigObj == null) {
            return Map.of();
        }
        if (authConfigObj instanceof Map) {
            Map<String, Object> raw = (Map<String, Object>) authConfigObj;
            Map<String, String> normalized = new LinkedHashMap<>();
            raw.entrySet().stream()
                    .filter(e -> e.getKey() != null && !e.getKey().trim().isEmpty() && e.getValue() != null)
                    .forEach(e -> normalized.put(e.getKey().trim(), String.valueOf(e.getValue())));
            return normalized;
        }
        log.warn("MCP认证配置格式不正确，忽略认证信息: serverCode={}", config.getServerCode());
        return Map.of();
    }

    /**
     * Bearer Token认证
     * @param headers
     * @param authConfig
     */
    private void applyBearerAuth(Map<String, String> headers, Map<String, String> authConfig) {
        String token = authConfig.getOrDefault("token", "").trim();
        if (!token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }
    }

    /**
     * Basic认证
     * @param headers
     * @param authConfig
     */
    private void applyBasicAuth(Map<String, String> headers, Map<String, String> authConfig) {
        String authorization = authConfig.getOrDefault("authorization", "").trim();
        if (!authorization.isEmpty()) {
            headers.put("Authorization", authorization);
        }
    }

    /**
     * API Key认证
     * @param headers
     * @param authConfig
     */
    private void applyApiKeyAuth(Map<String, String> headers, Map<String, String> authConfig) {
        String keyName = authConfig.getOrDefault("keyName", "X-API-Key").trim();
        String keyValue = authConfig.getOrDefault("keyValue", "").trim();
        if (!keyValue.isEmpty()) {
            headers.put(keyName, keyValue);
        }
    }

    /**
     * 自定义Header透传
     * @param headers
     * @param authConfig
     */
    private void applyHeaderAuth(Map<String, String> headers, Map<String, String> authConfig) {
        authConfig.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().trim().isEmpty())
                .filter(e -> e.getValue() != null && !e.getValue().trim().isEmpty())
                .forEach(e -> headers.put(e.getKey().trim(), e.getValue().trim()));
    }
}
