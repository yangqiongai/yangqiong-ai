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

import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 知识资源映射
 * <p>
 * 暴露白名单提供资源元数据(uri=resource://{code})，
 * 内容经McpResourceSource扩展点读取。
 * </p>
 * @author yangqiong
 */
public class McpResourceMapper {

    private static final Logger log = LoggerFactory.getLogger(McpResourceMapper.class);

    /**
     * 资源URI前缀
     */
    public static final String RESOURCE_URI_PREFIX = "resource://";

    private final McpExposeService exposeService;

    private final McpToolsCallGuard guard;

    private final List<McpResourceSource> resourceSources;

    public McpResourceMapper(McpExposeService exposeService, McpToolsCallGuard guard,
                             List<McpResourceSource> resourceSources) {
        this.exposeService = exposeService;
        this.guard = guard;
        this.resourceSources = resourceSources != null ? resourceSources : List.of();
    }

    /**
     * 构建SDK资源规范列表(仅含启用白名单项)
     * @return
     */
    public List<McpServerFeatures.SyncResourceSpecification> buildResourceSpecifications() {
        List<McpServerFeatures.SyncResourceSpecification> specifications = new ArrayList<>();
        for (McpServerExpose expose : exposeService.listEnabled(McpServerExpose.TYPE_RESOURCE)) {
            McpSchema.Resource resource = McpSchema.Resource.builder()
                    .uri(RESOURCE_URI_PREFIX + expose.getExposeCode())
                    .name(resolveName(expose))
                    .description(expose.getDescription())
                    .mimeType(resolveMimeType(expose.getExposeCode()))
                    .build();
            specifications.add(new McpServerFeatures.SyncResourceSpecification(resource,
                    this.readHandler(expose.getExposeCode())));
        }
        return specifications;
    }

    /**
     * 读取资源:校验白名单后经来源扩展点取内容
     * @param sourceCode
     * @return
     */
    private java.util.function.BiFunction<McpSyncServerExchange, McpSchema.ReadResourceRequest, McpSchema.ReadResourceResult>
            readHandler(String sourceCode) {
        return (exchange, request) -> {
            guard.check(McpServerExpose.TYPE_RESOURCE, sourceCode);
            String content = readContent(sourceCode);
            String mimeType = resolveMimeType(sourceCode);
            return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(RESOURCE_URI_PREFIX + sourceCode, mimeType, content)));
        };
    }

    /**
     * 经来源扩展点读取内容
     * @param sourceCode
     * @return
     */
    public String readContent(String sourceCode) {
        Optional<McpResourceSource> source = resourceSources.stream()
                .filter(candidate -> candidate.supports(sourceCode))
                .findFirst();
        if (source.isEmpty()) {
            throw new IllegalStateException("资源来源未注册: " + sourceCode);
        }
        return source.get().read(sourceCode);
    }

    private String resolveMimeType(String sourceCode) {
        return resourceSources.stream()
                .filter(candidate -> candidate.supports(sourceCode))
                .findFirst()
                .map(source -> source.mimeType(sourceCode))
                .orElse("text/markdown");
    }

    private String resolveName(McpServerExpose expose) {
        return expose.getDisplayName() != null && !expose.getDisplayName().isBlank()
                ? expose.getDisplayName() : expose.getExposeCode();
    }
}
