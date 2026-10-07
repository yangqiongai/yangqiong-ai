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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP配置属性
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.mcp")
public class McpProperties {

    /**
     * 是否启用MCP功能
     */
    private boolean enabled = true;

    /**
     * MCP初始化连接超时时间（秒）
     */
    private int initTimeoutSeconds = 5;

    /**
     * MCP工具调用超时时间（秒）
     */
    private int toolCallTimeoutSeconds = 10;

    /**
     * 连续失败次数阈值，超过后自动下线
     */
    private int maxConsecutiveFailures = 3;

    /**
     * 连续空结果次数阈值，超过后自动下线
     * <p>
     * 部分MCP工具）因环境问题返回空结果而非错误，
     * 需要单独追踪连续空结果次数，达到阈值后触发下线。
     * </p>
     */
    private int maxConsecutiveEmptyResults = 5;

    /**
     * 健康检查间隔（秒）
     */
    private int healthCheckIntervalSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getInitTimeoutSeconds() {
        return initTimeoutSeconds;
    }

    public void setInitTimeoutSeconds(int initTimeoutSeconds) {
        this.initTimeoutSeconds = initTimeoutSeconds;
    }

    public int getToolCallTimeoutSeconds() {
        return toolCallTimeoutSeconds;
    }

    public void setToolCallTimeoutSeconds(int toolCallTimeoutSeconds) {
        this.toolCallTimeoutSeconds = toolCallTimeoutSeconds;
    }

    public int getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public int getMaxConsecutiveEmptyResults() {
        return maxConsecutiveEmptyResults;
    }

    public void setMaxConsecutiveEmptyResults(int maxConsecutiveEmptyResults) {
        this.maxConsecutiveEmptyResults = maxConsecutiveEmptyResults;
    }

    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }

    public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }
}
