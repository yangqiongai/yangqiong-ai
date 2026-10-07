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

import java.util.List;
import java.util.Map;

/**
 * MCP服务配置
 * @author yangqiong
 */
public class McpServerConfig {

    /**
     * 服务编码
     */
    private String serverCode;

    /**
     * 服务名称
     */
    private String serverName;

    /**
     * 传输类型(STDIO/SSE/STREAMABLE_HTTP)
     */
    private String transportType;

    /**
     * 连接配置
     */
    private Map<String, Object> connectionConfig;

    /**
     * 启用的工具列表
     */
    private List<String> enabledTools;

    /**
     * 禁用的工具列表
     */
    private List<String> disabledTools;

    /**
     * 服务状态
     */
    private int serverStatus;

    /**
     * 所属分类编码（空为未分类）
     */
    private String category;

    public String getServerCode() {
        return serverCode;
    }

    public void setServerCode(String serverCode) {
        this.serverCode = serverCode;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public Map<String, Object> getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(Map<String, Object> connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public List<String> getEnabledTools() {
        return enabledTools;
    }

    public void setEnabledTools(List<String> enabledTools) {
        this.enabledTools = enabledTools;
    }

    public List<String> getDisabledTools() {
        return disabledTools;
    }

    public void setDisabledTools(List<String> disabledTools) {
        this.disabledTools = disabledTools;
    }

    public int getServerStatus() {
        return serverStatus;
    }

    public void setServerStatus(int serverStatus) {
        this.serverStatus = serverStatus;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
