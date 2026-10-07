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

import com.yangqiongai.ai.platform.ecosystem.mcp.config.McpAppsProperties;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;

import java.util.Map;

/**
 * MCP Apps UI契约
 * <p>
 * SEP-1865自研实现：仅V42白名单render_allowed=1的资源下发ui声明，
 * 声明含资源URI/renderHint/CSP；renderHint支持form/chart/card三类MVP模板，
 * 描述内[ui:form]标记可指定模板类型，非法或缺省回落card。
 * </p>
 * @author yangqiong
 */
public class McpAppsUiContract {

    /**
     * 表单模板类型
     */
    public static final String HINT_FORM = "form";

    /**
     * 图表模板类型
     */
    public static final String HINT_CHART = "chart";

    /**
     * 卡片模板类型
     */
    public static final String HINT_CARD = "card";

    /**
     * UI资源URI前缀
     */
    public static final String RESOURCE_URI_PREFIX = "ui://mcp-apps/";

    /**
     * 内容安全策略(默认拒绝一切外链与内联脚本，iframe沙箱内仅本地渲染)
     */
    public static final String CSP_POLICY = "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'";

    /**
     * MCP Apps配置
     */
    private final McpAppsProperties properties;

    /**
     * 暴露白名单服务
     */
    private final McpExposeService exposeService;

    public McpAppsUiContract(McpAppsProperties properties, McpExposeService exposeService) {
        this.properties = properties;
        this.exposeService = exposeService;
    }

    /**
     * Apps契约是否启用
     * @return
     */
    public boolean enabled() {
        return properties.isAppsEnabled();
    }

    /**
     * 构建UI契约声明(未启用/render_allowed=0返回null不下发)
     * @param exposeType
     * @param exposeCode
     * @return
     */
    public McpAppsUiDeclaration buildUiDeclaration(String exposeType, String exposeCode) {
        if (!enabled() || exposeCode == null || exposeCode.isBlank()) {
            return null;
        }
        return buildUiDeclaration(exposeService.getEnabled(exposeType, exposeCode));
    }

    /**
     * 基于白名单对象构建UI契约声明(避免重复查询)
     * @param expose
     * @return 未启用/白名单空/render_allowed=0返回null不下发
     */
    public McpAppsUiDeclaration buildUiDeclaration(McpServerExpose expose) {
        if (!enabled() || expose == null || !isRenderAllowed(expose)) {
            return null;
        }
        return new McpAppsUiDeclaration(RESOURCE_URI_PREFIX + expose.getExposeCode(),
                resolveRenderHint(expose), CSP_POLICY);
    }

    /**
     * 构建工具meta载体(声明为null时返回null)
     * @param declaration
     * @return
     */
    public Map<String, Object> uiToolMeta(McpAppsUiDeclaration declaration) {
        if (declaration == null) {
            return null;
        }
        return Map.of("ui", Map.of(
                "resourceUri", declaration.getResourceUri(),
                "renderHint", declaration.getRenderHint(),
                "csp", declaration.getCsp()));
    }

    /**
     * 解析暴露描述中的渲染提示标记([ui:form]/[ui:chart]/[ui:card],非法或缺省回落card)
     * @param expose
     * @return
     */
    public String resolveRenderHint(McpServerExpose expose) {
        String description = expose.getDescription();
        if (description != null) {
            String lower = description.toLowerCase();
            for (String hint : new String[]{HINT_FORM, HINT_CHART, HINT_CARD}) {
                if (lower.contains("[ui:" + hint + "]")) {
                    return hint;
                }
            }
        }
        return HINT_CARD;
    }

    /**
     * 按渲染提示生成MVP模板HTML(含CSP声明，iframe沙箱内本地渲染不逃逸)
     * @param declaration
     * @param payload
     * @return
     */
    public String renderUiHtml(McpAppsUiDeclaration declaration, String payload) {
        String hint = declaration.getRenderHint();
        String data = payload != null ? payload : "";
        String body;
        switch (hint) {
            case HINT_FORM:
                body = "<form class=\"mcp-app-form\"><input name=\"value\" placeholder=\"请输入\"/>"
                        + "<button type=\"submit\">提交</button></form>";
                break;
            case HINT_CHART:
                body = "<div class=\"mcp-app-chart\" data-points='" + escape(data) + "'></div>";
                break;
            default:
                body = "<div class=\"mcp-app-card\">" + escape(data) + "</div>";
                break;
        }
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"" + CSP_POLICY + "\"/>"
                + "</head><body class=\"mcp-app mcp-app-" + hint + "\">" + body + "</body></html>";
    }

    /**
     * 是否允许渲染(V42白名单render_allowed=1)
     * @param expose
     * @return
     */
    private boolean isRenderAllowed(McpServerExpose expose) {
        return expose.getRenderAllowed() != null && expose.getRenderAllowed() == 1;
    }

    /**
     * HTML转义防注入
     * @param text
     * @return
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
