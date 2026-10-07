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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP服务出口配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.ecosystem.mcp")
public class EcosystemMcpProperties {

    /**
     * 是否启用MCP服务出口(默认关闭,配套白名单使用)
     */
    private boolean enabled = false;

    /**
     * 服务端名称(握手serverInfo)
     */
    private String serverName = "yangqiong-ai-mcp";

    /**
     * 服务端版本
     */
    private String serverVersion = "1.0.0";

    /**
     * 协议端点路径
     */
    private String endpoint = "/mcp";

    /**
     * 平台工具同步执行等待上限(毫秒)
     */
    private long toolCallTimeoutMillis = 30_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public long getToolCallTimeoutMillis() {
        return toolCallTimeoutMillis;
    }

    public void setToolCallTimeoutMillis(long toolCallTimeoutMillis) {
        this.toolCallTimeoutMillis = toolCallTimeoutMillis;
    }
}
