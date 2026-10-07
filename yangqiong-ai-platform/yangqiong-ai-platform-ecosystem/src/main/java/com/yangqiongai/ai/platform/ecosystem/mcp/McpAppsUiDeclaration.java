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
package com.yangqiongai.ai.platform.ecosystem.mcp;

/**
 * MCP Apps UI契约声明
 * <p>
 * SEP-1865自研载体：工具meta携带ui资源声明，前端iframe沙箱渲染器按renderHint选择模板。
 * </p>
 * @author yangqiong
 */
public class McpAppsUiDeclaration {

    /**
     * UI资源URI(ui://mcp-apps/{编码})
     */
    private final String resourceUri;

    /**
     * 渲染提示(form表单/chart图表/card卡片)
     */
    private final String renderHint;

    /**
     * 内容安全策略(拒绝外链脚本)
     */
    private final String csp;

    public McpAppsUiDeclaration(String resourceUri, String renderHint, String csp) {
        this.resourceUri = resourceUri;
        this.renderHint = renderHint;
        this.csp = csp;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public String getRenderHint() {
        return renderHint;
    }

    public String getCsp() {
        return csp;
    }
}
