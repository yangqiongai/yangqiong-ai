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
package com.yangqiongai.ai.agent.mcp;

import com.yangqiongai.ai.agent.mcp.client.McpClientFactory;
import com.yangqiongai.ai.agent.mcp.client.McpClientWrapper;
import com.yangqiongai.ai.agent.mcp.client.McpHealthChecker;
import com.yangqiongai.ai.agent.mcp.client.ToolPolicyMcpClientWrapper;
import com.yangqiongai.ai.agent.mcp.model.McpConnectionTestResult;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP连接池
 * @author yangqiong
 */
public class McpConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionPool.class);

    private final Map<String, McpClientWrapper> clientCache = new ConcurrentHashMap<>();

    private final Map<String, McpServerConfig> configCache = new ConcurrentHashMap<>();

    private final McpClientFactory clientFactory;

    private final McpHealthChecker healthChecker;

    private final McpProperties properties;

    /**
     * 构造方法
     * @param clientFactory
     * @param healthChecker
     * @param properties
     */
    public McpConnectionPool(McpClientFactory clientFactory, McpHealthChecker healthChecker,
                             McpProperties properties) {
        this.clientFactory = clientFactory;
        this.healthChecker = healthChecker;
        this.properties = properties;
        // 注入连接池引用到健康检查器，支持自动下线
        healthChecker.setConnectionPool(this);
    }

    /**
     * 注册客户端
     * @param mcpId
     * @param config
     * @return true=注册成功，false=注册失败
     */
    public boolean registerClient(String mcpId, McpServerConfig config) {
        if (mcpId == null || mcpId.trim().isEmpty() || config == null) {
            return false;
        }
        String normalizedId = mcpId.trim();
        try {
            McpClientWrapper wrapper = clientFactory.buildClient(config);
            clientCache.put(normalizedId, wrapper);
            configCache.put(normalizedId, config);
            // 初始化健康状态为成功
            healthChecker.recordCheckResult(normalizedId, true, 0, 0);
            log.info("注册MCP客户端成功: mcpId={}", normalizedId);
            return true;
        } catch (Exception e) {
            log.error("注册MCP客户端失败: mcpId={}", normalizedId, e);
            // 初始化连接失败也记录到健康检查器，触发自动下线
            healthChecker.recordCheckResult(normalizedId, false, 0, 0);
            return false;
        }
    }

    /**
     * 获取已缓存的客户端
     * @param mcpId
     * @return
     */
    public Optional<McpClientWrapper> getClient(String mcpId) {
        if (mcpId == null || mcpId.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(clientCache.get(mcpId.trim()));
    }

    /**
     * 获取带策略的客户端
     * @param mcpId
     * @param enabledTools
     * @param disabledTools
     * @return
     */
    public Optional<McpClientWrapper> getClientWithPolicy(String mcpId, List<String> enabledTools, List<String> disabledTools) {
        return getClient(mcpId).map(raw -> {
            boolean hasEnabled = enabledTools != null && !enabledTools.isEmpty();
            boolean hasDisabled = disabledTools != null && !disabledTools.isEmpty();
            if (hasEnabled || hasDisabled) {
                return new ToolPolicyMcpClientWrapper(raw, enabledTools, disabledTools);
            }
            return raw;
        });
    }

    /**
     * 移除并关闭客户端
     * @param mcpId
     */
    public void removeClient(String mcpId) {
        if (mcpId == null || mcpId.trim().isEmpty()) {
            return;
        }
        String normalizedId = mcpId.trim();
        McpClientWrapper removed = clientCache.remove(normalizedId);
        configCache.remove(normalizedId);
        healthChecker.removeState(normalizedId);
        if (removed != null) {
            try {
                removed.close();
                log.info("移除MCP客户端: mcpId={}", normalizedId);
            } catch (Exception e) {
                log.warn("关闭MCP客户端异常: mcpId={}", normalizedId, e);
            }
        }
    }

    /**
     * 列出Agent可见的工具
     * <p>
     * Agent与MCP的挂载关系由agentConfig.mcpServers正向清单控制，
     * 连接池层面返回全部已注册服务器的工具，可见性由装配链路过滤。
     * </p>
     * @param agentCode
     * @return
     */
    public List<McpToolInfo> listAvailableTools(String agentCode) {
        return clientCache.values().stream()
                .flatMap(wrapper -> wrapper.listTools().stream())
                .toList();
    }

    /**
     * 列出所有已注册客户端的工具
     * @return
     */
    public List<McpToolInfo> listAllTools() {
        return clientCache.values().stream()
                .flatMap(wrapper -> wrapper.listTools().stream())
                .toList();
    }

    /**
     * 测试连接
     * @param config
     * @return
     */
    public McpConnectionTestResult testConnection(McpServerConfig config) {
        if (config == null) {
            return McpConnectionTestResult.fail("配置为空");
        }
        String mcpId = config.getServerCode();
        long startTime = System.currentTimeMillis();
        McpClientWrapper tempClient = null;
        try {
            tempClient = clientFactory.buildClient(config);
            List<McpToolInfo> rawTools = tempClient.listTools();
            int rawToolCount = rawTools.size();

            // 应用策略过滤计算过滤后工具数量
            List<String> enabledTools = config.getEnabledTools();
            List<String> disabledTools = config.getDisabledTools();
            int filteredToolCount = rawToolCount;
            if ((enabledTools != null && !enabledTools.isEmpty())
                    || (disabledTools != null && !disabledTools.isEmpty())) {
                ToolPolicyMcpClientWrapper policyWrapper = new ToolPolicyMcpClientWrapper(tempClient, enabledTools, disabledTools);
                filteredToolCount = policyWrapper.listTools().size();
            }

            long latency = System.currentTimeMillis() - startTime;
            healthChecker.recordCheckResult(mcpId, true, filteredToolCount, latency);
            return McpConnectionTestResult.ok(rawToolCount, filteredToolCount, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            healthChecker.recordCheckResult(mcpId, false, 0, latency);
            log.error("MCP连接测试失败: mcpId={}", mcpId, e);
            return McpConnectionTestResult.fail(extractErrorMessage(e));
        } finally {
            if (tempClient != null) {
                try {
                    tempClient.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
        }
    }

    /**
     * 获取所有已注册的服务编码
     * @return
     */
    public Set<String> getRegisteredServerCodes() {
        return Collections.unmodifiableSet(clientCache.keySet());
    }

    /**
     * 获取工具调用超时时间（秒）
     * @return
     */
    public int getToolCallTimeoutSeconds() {
        return properties.getToolCallTimeoutSeconds();
    }

    /**
     * 获取健康检查器
     * @return
     */
    public McpHealthChecker getHealthChecker() {
        return healthChecker;
    }

    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        Throwable cause = e.getCause();
        while (cause != null && (message == null || message.isEmpty())) {
            message = cause.getMessage();
            cause = cause.getCause();
        }
        return message != null ? message : "未知异常";
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("开始关闭所有MCP客户端，当前注册数: {}", clientCache.size());
        clientCache.forEach((mcpId, wrapper) -> {
            try {
                wrapper.close();
            } catch (Exception e) {
                log.warn("关闭MCP客户端异常: mcpId={}", mcpId, e);
            }
        });
        clientCache.clear();
        configCache.clear();
        healthChecker.getUnhealthyClientIds().forEach(healthChecker::removeState);
        log.info("所有MCP客户端已关闭");
    }
}
