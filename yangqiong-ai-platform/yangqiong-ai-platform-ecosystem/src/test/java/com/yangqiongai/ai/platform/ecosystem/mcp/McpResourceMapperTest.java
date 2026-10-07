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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 知识资源映射测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class McpResourceMapperTest {

    @Mock
    private McpExposeService exposeService;

    @Mock
    private McpToolsCallGuard guard;

    /**
     * 测试用资源来源
     */
    static class DemoResourceSource implements McpResourceSource {

        @Override
        public boolean supports(String sourceCode) {
            return "kb_doc".equals(sourceCode);
        }

        @Override
        public String read(String sourceCode) {
            return "知识内容";
        }
    }

    private McpServerExpose resourceExpose(String code) {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_RESOURCE);
        expose.setExposeCode(code);
        expose.setDisplayName("知识文档");
        expose.setDescription("资源描述");
        expose.setEnabled(1);
        return expose;
    }

    @Test
    void buildResourceSpecificationsShouldUseExposeMeta() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_RESOURCE))
                .thenReturn(List.of(resourceExpose("kb_doc")));
        McpResourceMapper mapper = new McpResourceMapper(exposeService, guard, List.of(new DemoResourceSource()));

        List<McpServerFeatures.SyncResourceSpecification> specs = mapper.buildResourceSpecifications();

        assertThat(specs).hasSize(1);
        McpSchema.Resource resource = specs.get(0).resource();
        assertThat(resource.uri()).isEqualTo(McpResourceMapper.RESOURCE_URI_PREFIX + "kb_doc");
        assertThat(resource.name()).isEqualTo("知识文档");
        assertThat(resource.description()).isEqualTo("资源描述");
        assertThat(resource.mimeType()).isEqualTo("text/markdown");
    }

    @Test
    void buildResourceSpecificationsShouldFallbackMimeWithoutSource() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_RESOURCE))
                .thenReturn(List.of(resourceExpose("kb_doc")));
        McpResourceMapper mapper = new McpResourceMapper(exposeService, guard, List.of());

        List<McpServerFeatures.SyncResourceSpecification> specs = mapper.buildResourceSpecifications();

        assertThat(specs.get(0).resource().mimeType()).isEqualTo("text/markdown");
    }

    @Test
    void readHandlerShouldReturnTextContent() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_RESOURCE))
                .thenReturn(List.of(resourceExpose("kb_doc")));
        McpServerFeatures.SyncResourceSpecification spec = new McpResourceMapper(exposeService, guard,
                List.of(new DemoResourceSource())).buildResourceSpecifications().get(0);

        McpSchema.ReadResourceResult result = spec.readHandler().apply(null, null);

        McpSchema.TextResourceContents contents = (McpSchema.TextResourceContents) result.contents().get(0);
        assertThat(contents.uri()).isEqualTo(McpResourceMapper.RESOURCE_URI_PREFIX + "kb_doc");
        assertThat(contents.mimeType()).isEqualTo("text/markdown");
        assertThat(contents.text()).isEqualTo("知识内容");
    }

    @Test
    void readHandlerShouldFailWhenSourceMissing() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_RESOURCE))
                .thenReturn(List.of(resourceExpose("kb_doc")));
        McpServerFeatures.SyncResourceSpecification spec = new McpResourceMapper(exposeService, guard, List.of())
                .buildResourceSpecifications().get(0);

        assertThatThrownBy(() -> spec.readHandler().apply(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("资源来源未注册");
    }

    @Test
    void readHandlerShouldRejectWhenGuardDenies() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_RESOURCE))
                .thenReturn(List.of(resourceExpose("kb_doc")));
        doThrow(new AiException(AiErrorCode.FORBIDDEN.getCode(), "资源未开放"))
                .when(guard).check(McpServerExpose.TYPE_RESOURCE, "kb_doc");
        McpServerFeatures.SyncResourceSpecification spec = new McpResourceMapper(exposeService, guard,
                List.of(new DemoResourceSource())).buildResourceSpecifications().get(0);

        assertThatThrownBy(() -> spec.readHandler().apply(null, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("资源未开放");
    }

    @Test
    void readContentShouldThrowWhenNoSourceSupports() {
        McpResourceMapper mapper = new McpResourceMapper(exposeService, guard, List.of());

        assertThatThrownBy(() -> mapper.readContent("unknown"))
                .isInstanceOf(IllegalStateException.class);
    }
}
