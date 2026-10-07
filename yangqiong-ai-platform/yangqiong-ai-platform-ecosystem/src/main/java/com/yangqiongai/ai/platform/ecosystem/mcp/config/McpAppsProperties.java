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
 * MCP Apps配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.mcp")
public class McpAppsProperties {

    /**
     * 是否启用MCP Apps UI契约下发(默认关闭)
     */
    private boolean appsEnabled = false;

    public boolean isAppsEnabled() {
        return appsEnabled;
    }

    public void setAppsEnabled(boolean appsEnabled) {
        this.appsEnabled = appsEnabled;
    }
}
