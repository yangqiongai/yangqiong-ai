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
package com.yangqiongai.ai.agent.mcp.client;

import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 工具策略MCP客户端包装器
 * @author yangqiong
 */
public class ToolPolicyMcpClientWrapper implements McpClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyMcpClientWrapper.class);

    private final McpClientWrapper delegate;

    private final Set<String> allowedToolNames;

    private final Set<String> blockedToolNames;

    private final List<String> enabledTools;

    private final List<String> disabledTools;

    public ToolPolicyMcpClientWrapper(McpClientWrapper delegate, List<String> enabledTools, List<String> disabledTools) {
        this.delegate = delegate;
        this.enabledTools = enabledTools;
        this.disabledTools = disabledTools;
        this.allowedToolNames = normalizeToSet(enabledTools);
        this.blockedToolNames = normalizeToSet(disabledTools);
    }

    @Override
    public List<McpToolInfo> listTools() {
        return delegate.listTools().stream()
                .filter(this::isToolVisible)
                .toList();
    }

    @Override
    public String callTool(String toolName, String arguments) {
        if (!isToolNameVisible(toolName)) {
            throw new SecurityException("MCP工具被策略禁止调用" + toolName);
        }
        return delegate.callTool(toolName, arguments);
    }

    @Override
    public CompletableFuture<List<McpToolInfo>> listToolsAsync() {
        return delegate.listToolsAsync().thenApply(tools ->
                tools.stream().filter(this::isToolVisible).toList()
        );
    }

    @Override
    public CompletableFuture<String> callToolAsync(String toolName, String arguments) {
        if (!isToolNameVisible(toolName)) {
            return CompletableFuture.failedFuture(
                    new SecurityException("MCP工具被策略禁止调用" + toolName));
        }
        return delegate.callToolAsync(toolName, arguments);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public String getServerCode() {
        return delegate.getServerCode();
    }

    /**
     * 获取缓存的工具
     * @param toolName
     * @return
     */
    public McpToolInfo getCachedTool(String toolName) {
        if (!isToolNameVisible(toolName)) {
            return null;
        }
        return delegate.listTools().stream()
                .filter(tool -> tool.getName().equals(toolName))
                .findFirst()
                .orElse(null);
    }

    private boolean isToolVisible(McpToolInfo tool) {
        return isToolNameVisible(tool.getName());
    }

    private boolean isToolNameVisible(String toolName) {
        String normalized = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        // 黑名单优
        if (blockedToolNames.contains(normalized)) {
            return false;
        }
        // 白名单为空则全部允许
        return allowedToolNames.isEmpty() || allowedToolNames.contains(normalized);
    }

    private static Set<String> normalizeToSet(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        return names.stream()
                .filter(name -> name != null && !name.trim().isEmpty())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
