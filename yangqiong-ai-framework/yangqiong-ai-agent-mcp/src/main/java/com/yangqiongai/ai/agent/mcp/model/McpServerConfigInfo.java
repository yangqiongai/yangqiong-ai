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

import java.time.LocalDateTime;

/**
 * MCP服务配置
 * @author yangqiong
 */
public class McpServerConfigInfo {

    /**
     * 主键
     */
    private Long id;

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
     * 连接配置(JSON)
     */
    private String connectionConfig;

    /**
     * 启用的工具列表(JSON)
     */
    private String enabledTools;

    /**
     * 禁用的工具列表(JSON)
     */
    private String disabledTools;

    /**
     * 服务状态(0-禁用 1-启用)
     */
    private Integer serverStatus;

    /**
     * 备注
     */
    private String remark;

    /**
     * 下线原因
     */
    private String offlineReason;

    /**
     * 所属分类编码（空为未分类）
     */
    private String category;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getConnectionConfig() {
        return connectionConfig;
    }

    public void setConnectionConfig(String connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public String getEnabledTools() {
        return enabledTools;
    }

    public void setEnabledTools(String enabledTools) {
        this.enabledTools = enabledTools;
    }

    public String getDisabledTools() {
        return disabledTools;
    }

    public void setDisabledTools(String disabledTools) {
        this.disabledTools = disabledTools;
    }

    public Integer getServerStatus() {
        return serverStatus;
    }

    public void setServerStatus(Integer serverStatus) {
        this.serverStatus = serverStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getOfflineReason() {
        return offlineReason;
    }

    public void setOfflineReason(String offlineReason) {
        this.offlineReason = offlineReason;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
