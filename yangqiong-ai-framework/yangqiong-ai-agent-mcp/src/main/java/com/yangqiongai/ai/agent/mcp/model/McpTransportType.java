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
package com.yangqiongai.ai.agent.mcp.model;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP传输协议类型
 * @author yangqiong
 */
public enum McpTransportType {

    /**
     * Server-Sent Events传输
     */
    SSE("sse"),

    /**
     * Streamable HTTP传输
     */
    STREAMABLE_HTTP("streamable-http"),

    /**
     * 标准输入输出传输
     */
    STDIO("stdio");

    private static final Map<String, McpTransportType> LOOKUP = buildLookup();

    private final String code;

    McpTransportType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据字符串标识解析传输类型
     * @param value
     * @return
     */
    public static McpTransportType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return STREAMABLE_HTTP;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        McpTransportType matched = LOOKUP.get(normalized);
        if (matched != null) {
            return matched;
        }
        // 兼容常见别名
        if ("streamable_http".equals(normalized) || "http".equals(normalized)) {
            return STREAMABLE_HTTP;
        }
        return STREAMABLE_HTTP;
    }

    /**
     * 根据配置推断传输类型
     * @param config
     * @return
     */
    public static McpTransportType inferFromConfig(McpServerConfig config) {
        if (config == null) {
            return STREAMABLE_HTTP;
        }
        String configured = config.getTransportType();
        if (configured != null && !configured.trim().isEmpty()) {
            return fromString(configured);
        }
        // 无显式配置时根据connectionConfig推断
        Map<String, Object> connConfig = config.getConnectionConfig();
        if (connConfig != null) {
            String command = extractString(connConfig, "command");
            if (command != null && !command.trim().isEmpty()) {
                return STDIO;
            }
            String endpoint = extractString(connConfig, "endpoint");
            if (endpoint != null && (endpoint.contains("/sse") || endpoint.contains("transport=sse"))) {
                return SSE;
            }
        }
        return STREAMABLE_HTTP;
    }

    private static Map<String, McpTransportType> buildLookup() {
        return Map.of(
                SSE.code, SSE,
                STREAMABLE_HTTP.code, STREAMABLE_HTTP,
                STDIO.code, STDIO
        );
    }

    private static String extractString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? String.valueOf(value).trim() : null;
    }
}
