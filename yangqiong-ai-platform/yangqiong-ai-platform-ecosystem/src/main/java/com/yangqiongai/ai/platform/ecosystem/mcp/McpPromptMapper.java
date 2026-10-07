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

import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 能力提示词映射
 * <p>
 * 将开放能力目录中的能力(含提示词模板)映射为SDK prompt provider，
 * 仅输出启用白名单内的能力，取用时按参数做简单模板替换。
 * </p>
 * @author yangqiong
 */
public class McpPromptMapper {

    private static final Logger log = LoggerFactory.getLogger(McpPromptMapper.class);

    private final McpExposeService exposeService;

    private final McpToolsCallGuard guard;

    private final CapabilityCatalog capabilityCatalog;

    public McpPromptMapper(McpExposeService exposeService, McpToolsCallGuard guard,
                           CapabilityCatalog capabilityCatalog) {
        this.exposeService = exposeService;
        this.guard = guard;
        this.capabilityCatalog = capabilityCatalog;
    }

    /**
     * 构建SDK提示词规范列表(仅含启用白名单内的能力)
     * @return
     */
    public List<McpServerFeatures.SyncPromptSpecification> buildPromptSpecifications() {
        List<McpServerFeatures.SyncPromptSpecification> specifications = new ArrayList<>();
        for (McpServerExpose expose : exposeService.listEnabled(McpServerExpose.TYPE_PROMPT)) {
            CapabilitySpec spec = capabilityCatalog.get(expose.getExposeCode());
            if (spec == null) {
                log.warn("MCP出口引用的能力不存在: {}", expose.getExposeCode());
                continue;
            }
            McpSchema.Prompt prompt = new McpSchema.Prompt(
                    expose.getExposeCode(),
                    resolveDisplayName(expose, spec),
                    resolveDescription(expose, spec),
                    List.of());
            specifications.add(new McpServerFeatures.SyncPromptSpecification(prompt,
                    this.promptHandler(expose.getExposeCode())));
        }
        return specifications;
    }

    /**
     * 取用提示词:校验白名单后按参数渲染能力提示词模板
     * @param capabilityCode
     * @return
     */
    private java.util.function.BiFunction<McpSyncServerExchange, McpSchema.GetPromptRequest, McpSchema.GetPromptResult>
            promptHandler(String capabilityCode) {
        return (exchange, request) -> {
            guard.check(McpServerExpose.TYPE_PROMPT, capabilityCode);
            CapabilitySpec spec = capabilityCatalog.get(capabilityCode);
            if (spec == null) {
                throw new IllegalStateException("能力不存在: " + capabilityCode);
            }
            String content = render(spec, request.arguments());
            McpSchema.PromptMessage message = new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent(content));
            return new McpSchema.GetPromptResult(resolveDescription(null, spec), List.of(message));
        };
    }

    /**
     * 渲染提示词模板(简单${key}替换)
     * @param spec
     * @param args
     * @return
     */
    private String render(CapabilitySpec spec, Map<String, Object> args) {
        String template = spec.getPromptTemplateContent() != null
                ? spec.getPromptTemplateContent() : spec.getPromptTemplate();
        if (template == null || template.isBlank()) {
            template = spec.getDescription() == null ? "" : spec.getDescription();
        }
        String content = template;
        if (args != null) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                content = content.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return content;
    }

    private String resolveDisplayName(McpServerExpose expose, CapabilitySpec spec) {
        return expose.getDisplayName() != null && !expose.getDisplayName().isBlank()
                ? expose.getDisplayName() : spec.getName();
    }

    private String resolveDescription(McpServerExpose expose, CapabilitySpec spec) {
        if (expose != null && expose.getDescription() != null && !expose.getDescription().isBlank()) {
            return expose.getDescription();
        }
        return spec.getDescription();
    }
}
