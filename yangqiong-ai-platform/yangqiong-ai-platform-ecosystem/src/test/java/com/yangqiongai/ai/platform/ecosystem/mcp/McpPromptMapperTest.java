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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 能力提示词映射测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class McpPromptMapperTest {

    @Mock
    private McpExposeService exposeService;

    @Mock
    private McpToolsCallGuard guard;

    @Mock
    private CapabilityCatalog capabilityCatalog;

    private McpServerExpose promptExpose(String code) {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_PROMPT);
        expose.setExposeCode(code);
        expose.setDisplayName("对外名称");
        expose.setDescription("对外描述");
        expose.setEnabled(1);
        return expose;
    }

    private CapabilitySpec capability(String code) {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode(code);
        spec.setName("内部名称");
        spec.setDescription("能力描述");
        spec.setPromptTemplateContent("请分析${topic}主题");
        return spec;
    }

    @Test
    void buildPromptSpecificationsShouldPreferExposeMeta() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_PROMPT))
                .thenReturn(List.of(promptExpose("cap_1")));
        when(capabilityCatalog.get("cap_1")).thenReturn(capability("cap_1"));
        McpPromptMapper mapper = new McpPromptMapper(exposeService, guard, capabilityCatalog);

        List<McpServerFeatures.SyncPromptSpecification> specs = mapper.buildPromptSpecifications();

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).prompt().name()).isEqualTo("cap_1");
        assertThat(specs.get(0).prompt().title()).isEqualTo("对外名称");
        assertThat(specs.get(0).prompt().description()).isEqualTo("对外描述");
    }

    @Test
    void buildPromptSpecificationsShouldSkipMissingCapability() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_PROMPT))
                .thenReturn(List.of(promptExpose("cap_missing")));
        when(capabilityCatalog.get("cap_missing")).thenReturn(null);
        McpPromptMapper mapper = new McpPromptMapper(exposeService, guard, capabilityCatalog);

        assertThat(mapper.buildPromptSpecifications()).isEmpty();
    }

    @Test
    void promptHandlerShouldRenderTemplate() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_PROMPT))
                .thenReturn(List.of(promptExpose("cap_1")));
        when(capabilityCatalog.get("cap_1")).thenReturn(capability("cap_1"));
        McpServerFeatures.SyncPromptSpecification spec = new McpPromptMapper(exposeService, guard, capabilityCatalog)
                .buildPromptSpecifications().get(0);

        McpSchema.GetPromptResult result = spec.promptHandler()
                .apply(null, new McpSchema.GetPromptRequest("cap_1", Map.of("topic", "销量")));

        assertThat(result.description()).isEqualTo("能力描述");
        McpSchema.PromptMessage message = result.messages().get(0);
        assertThat(message.role()).isEqualTo(McpSchema.Role.USER);
        assertThat(((McpSchema.TextContent) message.content()).text()).isEqualTo("请分析销量主题");
    }

    @Test
    void promptHandlerShouldFallbackToDescriptionWhenTemplateBlank() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_PROMPT))
                .thenReturn(List.of(promptExpose("cap_1")));
        CapabilitySpec spec = capability("cap_1");
        spec.setPromptTemplateContent(" ");
        spec.setPromptTemplate(null);
        when(capabilityCatalog.get("cap_1")).thenReturn(spec);
        McpServerFeatures.SyncPromptSpecification promptSpec = new McpPromptMapper(exposeService, guard, capabilityCatalog)
                .buildPromptSpecifications().get(0);

        McpSchema.GetPromptResult result = promptSpec.promptHandler()
                .apply(null, new McpSchema.GetPromptRequest("cap_1", Map.of()));

        assertThat(((McpSchema.TextContent) result.messages().get(0).content()).text()).isEqualTo("能力描述");
    }

    @Test
    void promptHandlerShouldRejectWhenGuardDenies() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_PROMPT))
                .thenReturn(List.of(promptExpose("cap_1")));
        when(capabilityCatalog.get("cap_1")).thenReturn(capability("cap_1"));
        org.mockito.Mockito.doThrow(new AiException(AiErrorCode.FORBIDDEN.getCode(), "资源未开放"))
                .when(guard).check(McpServerExpose.TYPE_PROMPT, "cap_1");
        McpServerFeatures.SyncPromptSpecification spec = new McpPromptMapper(exposeService, guard, capabilityCatalog)
                .buildPromptSpecifications().get(0);

        assertThatThrownBy(() -> spec.promptHandler()
                .apply(null, new McpSchema.GetPromptRequest("cap_1", Map.of())))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("资源未开放");
    }

    @Test
    void promptHandlerShouldFailWhenCapabilityRemoved() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_PROMPT))
                .thenReturn(List.of(promptExpose("cap_1")));
        when(capabilityCatalog.get("cap_1")).thenReturn(capability("cap_1"));
        McpServerFeatures.SyncPromptSpecification spec = new McpPromptMapper(exposeService, guard, capabilityCatalog)
                .buildPromptSpecifications().get(0);
        when(capabilityCatalog.get("cap_1")).thenReturn(null);

        assertThatThrownBy(() -> spec.promptHandler()
                .apply(null, new McpSchema.GetPromptRequest("cap_1", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("能力不存在");
    }
}
