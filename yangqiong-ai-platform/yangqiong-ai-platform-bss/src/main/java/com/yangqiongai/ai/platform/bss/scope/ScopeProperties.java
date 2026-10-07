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
package com.yangqiongai.ai.platform.bss.scope;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 作用域配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.scope")
public class ScopeProperties {

    /**
     * 是否启用作用域隔离
     */
    private Boolean enabled = Boolean.TRUE;

    /**
     * 默认作用域ID
     */
    private String defaultScopeId = "default";

    /**
     * 作用域请求头名称
     */
    private String headerName = "X-Scope-Id";

    /**
     * 跳过作用域拦截的表名列表
     */
    private List<String> ignoreTables = new ArrayList<>();

    /**
     * 管理员userId列表
     */
    private List<String> admins = new ArrayList<>();

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultScopeId() {
        return defaultScopeId;
    }

    public void setDefaultScopeId(String defaultScopeId) {
        this.defaultScopeId = defaultScopeId;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public List<String> getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(List<String> ignoreTables) {
        this.ignoreTables = ignoreTables;
    }

    public List<String> getAdmins() {
        return admins;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }
}
