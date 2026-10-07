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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MCP Apps UI契约测试
 * @author yangqiong
 */
@DisplayName("MCP Apps UI契约测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpAppsUiContractTest {

    @Mock
    private McpExposeService exposeService;

    private McpAppsProperties properties;

    private McpAppsUiContract contract;

    @BeforeEach
    void setUp() {
        properties = new McpAppsProperties();
        contract = new McpAppsUiContract(properties, exposeService);
    }

    private McpServerExpose expose(String code, int renderAllowed) {
        McpServerExpose item = new McpServerExpose();
        item.setExposeType(McpServerExpose.TYPE_TOOL);
        item.setExposeCode(code);
        item.setRenderAllowed(renderAllowed);
        item.setEnabled(1);
        return item;
    }

    @Test
    @DisplayName("apps未启用不下发ui声明")
    void disabledShouldNotDeclare() {
        McpAppsUiDeclaration declaration = contract.buildUiDeclaration(expose("demo_tool", 1));
        assertThat(declaration).isNull();
        verify(exposeService, never()).getEnabled(McpServerExpose.TYPE_TOOL, "demo_tool");
    }

    @Test
    @DisplayName("render_allowed=0不下发ui声明")
    void renderNotAllowedShouldNotDeclare() {
        properties.setAppsEnabled(true);
        McpAppsUiDeclaration declaration = contract.buildUiDeclaration(expose("demo_tool", 0));
        assertThat(declaration).isNull();
    }

    @Test
    @DisplayName("白名单对象为空不下发ui声明")
    void nullExposeShouldNotDeclare() {
        properties.setAppsEnabled(true);
        assertThat(contract.buildUiDeclaration(null)).isNull();
    }

    @Test
    @DisplayName("render_allowed=1按编码下发ui声明")
    void renderAllowedShouldDeclare() {
        properties.setAppsEnabled(true);
        when(exposeService.getEnabled(McpServerExpose.TYPE_TOOL, "demo_tool"))
                .thenReturn(expose("demo_tool", 1));
        McpAppsUiDeclaration declaration = contract.buildUiDeclaration(McpServerExpose.TYPE_TOOL, "demo_tool");
        assertThat(declaration).isNotNull();
        assertThat(declaration.getResourceUri()).isEqualTo(McpAppsUiContract.RESOURCE_URI_PREFIX + "demo_tool");
        assertThat(declaration.getRenderHint()).isEqualTo(McpAppsUiContract.HINT_CARD);
        assertThat(declaration.getCsp()).isEqualTo(McpAppsUiContract.CSP_POLICY);
    }

    @Test
    @DisplayName("描述标记指定渲染提示")
    void descriptionMarkerShouldResolveHint() {
        properties.setAppsEnabled(true);
        McpServerExpose formExpose = expose("form_tool", 1);
        formExpose.setDescription("数据录入 [ui:form]");
        assertThat(contract.resolveRenderHint(formExpose)).isEqualTo(McpAppsUiContract.HINT_FORM);

        McpServerExpose chartExpose = expose("chart_tool", 1);
        chartExpose.setDescription("趋势图 [ui:chart]");
        assertThat(contract.resolveRenderHint(chartExpose)).isEqualTo(McpAppsUiContract.HINT_CHART);

        McpServerExpose cardExpose = expose("card_tool", 1);
        cardExpose.setDescription("普通卡片 [ui:unknown]");
        assertThat(contract.resolveRenderHint(cardExpose)).isEqualTo(McpAppsUiContract.HINT_CARD);

        McpServerExpose plainExpose = expose("plain_tool", 1);
        assertThat(contract.resolveRenderHint(plainExpose)).isEqualTo(McpAppsUiContract.HINT_CARD);
    }

    @Test
    @DisplayName("声明为null时meta载体为null")
    void nullDeclarationShouldReturnNullMeta() {
        assertThat(contract.uiToolMeta(null)).isNull();
    }

    @Test
    @DisplayName("三类MVP模板渲染且CSP拒绝外链脚本")
    void renderUiHtmlShouldCoverThreeTemplates() {
        properties.setAppsEnabled(true);
        for (String hint : new String[]{McpAppsUiContract.HINT_FORM, McpAppsUiContract.HINT_CHART,
                McpAppsUiContract.HINT_CARD}) {
            McpAppsUiDeclaration declaration =
                    new McpAppsUiDeclaration(McpAppsUiContract.RESOURCE_URI_PREFIX + "x", hint,
                            McpAppsUiContract.CSP_POLICY);
            String html = contract.renderUiHtml(declaration, "演示数据");
            assertThat(html).contains("Content-Security-Policy").contains(McpAppsUiContract.CSP_POLICY);
            // CSP默认拒绝一切脚本外链,模板内无外部script标签
            assertThat(html).doesNotContain("<script");
            assertThat(html).doesNotContain("http://").doesNotContain("https://");
            if (McpAppsUiContract.HINT_FORM.equals(hint)) {
                assertThat(html).contains("mcp-app-form");
            } else if (McpAppsUiContract.HINT_CHART.equals(hint)) {
                assertThat(html).contains("mcp-app-chart");
            } else {
                assertThat(html).contains("mcp-app-card");
            }
        }
    }

    @Test
    @DisplayName("模板载荷转义防注入")
    void renderUiHtmlShouldEscapePayload() {
        properties.setAppsEnabled(true);
        McpAppsUiDeclaration declaration =
                new McpAppsUiDeclaration(McpAppsUiContract.RESOURCE_URI_PREFIX + "x", McpAppsUiContract.HINT_CARD,
                        McpAppsUiContract.CSP_POLICY);
        String html = contract.renderUiHtml(declaration, "<img src=x onerror=alert(1)>");
        assertThat(html).doesNotContain("<img");
        assertThat(html).contains("&lt;img");
    }
}
