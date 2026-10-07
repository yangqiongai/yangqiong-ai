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

import com.yangqiongai.ai.agent.mcp.McpConnectionPool;
import com.yangqiongai.ai.agent.mcp.McpProperties;
import com.yangqiongai.ai.agent.mcp.model.McpConnectionTestResult;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfigInfo;
import com.yangqiongai.ai.agent.mcp.repository.McpServerConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP客户端健康检查器
 * <p>
 * 跟踪每个MCP服务的健康状态，包括连接检查结果和工具调用结果。
 * 连续失败超过阈值后自动从连接池下线并同步更新数据库状态为禁用，防止拖垮服务。
 * 定时检查数据库中状态被手动恢复为启用的服务，自动重新上线。
 * </p>
 * <p>
 * 连接池和配置服务通过setter延迟注入，由McpAutoConfiguration统一设置，
 * 避免McpHealthChecker→McpServerConfigService→McpConnectionPool→McpHealthChecker循环依赖。
 * </p>
 * @author yangqiong
 */
@Component
public class McpHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(McpHealthChecker.class);

    /**
     * 健康状态记录
     */
    private final Map<String, HealthState> healthStates = new ConcurrentHashMap<>();

    /**
     * MCP连接池引用（延迟注入，避免循环依赖）
     */
    private McpConnectionPool connectionPool;

    /**
     * MCP服务配置服务（延迟注入，避免循环依赖）
     */
    private McpServerConfigService serverConfigService;

    /**
     * MCP配置属性
     */
    private final McpProperties properties;

    public McpHealthChecker(McpProperties properties) {
        this.properties = properties;
    }

    /**
     * 设置连接池引用（由McpAutoConfiguration注入）
     * @param connectionPool
     */
    public void setConnectionPool(McpConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /**
     * 设置配置服务引用（由McpAutoConfiguration注入）
     * @param serverConfigService
     */
    public void setServerConfigService(McpServerConfigService serverConfigService) {
        this.serverConfigService = serverConfigService;
    }

    /**
     * 检查单个客户端健康状态
     * @param mcpId
     * @return
     */
    public McpConnectionTestResult checkHealth(String mcpId) {
        HealthState state = healthStates.get(mcpId);
        if (state == null) {
            return McpConnectionTestResult.fail("客户端未注册: " + mcpId);
        }
        return McpConnectionTestResult.ok(state.toolCount, state.lastLatencyMs);
    }

    /**
     * 记录连接健康检查结果
     * @param mcpId
     * @param success
     * @param toolCount
     * @param latencyMs
     */
    public void recordCheckResult(String mcpId, boolean success, int toolCount, long latencyMs) {
        boolean shouldOffline = updateHealthState(mcpId, success, false, toolCount, latencyMs);
        if (shouldOffline) {
            autoOffline(mcpId, "连接健康检查连续" + getMaxConsecutiveFailures() + "次失败");
        }
    }

    /**
     * 记录工具调用结果
     * @param mcpId
     * @param success
     */
    public void recordToolCallResult(String mcpId, boolean success) {
        boolean shouldOffline = updateHealthState(mcpId, success, false, 0, 0);
        if (shouldOffline) {
            autoOffline(mcpId, "工具调用连续" + getMaxConsecutiveFailures() + "次失败");
        }
    }

    /**
     * 记录工具调用返回空结果
     * <p>
     * 部分MCP工具因环境问题返回空结果而非错误
     * 需要单独追踪连续空结果次数，达到阈值后触发自动下线。
     * </p>
     * @param mcpId
     */
    public void recordEmptyResult(String mcpId) {
        boolean shouldOffline = updateHealthState(mcpId, true, true, 0, 0);
        if (shouldOffline) {
            autoOffline(mcpId, "工具调用连续" + getMaxConsecutiveEmptyResults() + "次返回空结果");
        }
    }

    /**
     * 更新健康状态，返回是否触发自动下线
     * @param mcpId
     * @param success
     * @param emptyResult 是否为空结果（调用成功但返回空结果）
     * @param toolCount
     * @param latencyMs
     * @return 是否触发自动下线
     */
    private boolean updateHealthState(String mcpId, boolean success, boolean emptyResult,
                                      int toolCount, long latencyMs) {
        int maxFailures = getMaxConsecutiveFailures();
        int maxEmptyResults = getMaxConsecutiveEmptyResults();
        boolean[] shouldOffline = {false};
        healthStates.compute(mcpId, (key, existing) -> {
            if (existing == null) {
                int failures = success ? 0 : 1;
                int emptyCount = (success && emptyResult) ? 1 : 0;
                shouldOffline[0] = (!success && failures >= maxFailures)
                        || (emptyCount >= maxEmptyResults);
                return new HealthState(success, emptyResult, toolCount, latencyMs,
                        failures, emptyCount, System.currentTimeMillis());
            }
            int failures = success ? 0 : existing.consecutiveFailures + 1;
            int emptyCount = (success && emptyResult) ? existing.consecutiveEmptyResults + 1 : 0;
            shouldOffline[0] = (!success && failures >= maxFailures)
                    || (emptyCount >= maxEmptyResults);
            return new HealthState(success, emptyResult, toolCount, latencyMs,
                    failures, emptyCount, System.currentTimeMillis());
        });
        HealthState current = healthStates.get(mcpId);
        if (current != null && !shouldOffline[0]) {
            if (current.consecutiveFailures > 0) {
                log.warn("MCP客户端调用失败({}/{}): mcpId={}", current.consecutiveFailures, maxFailures, mcpId);
            }
            if (current.consecutiveEmptyResults > 0) {
                log.warn("MCP工具返回空结果({}/{}): mcpId={}", current.consecutiveEmptyResults, maxEmptyResults, mcpId);
            }
        }
        return shouldOffline[0];
    }

    /**
     * 自动下线不健康的MCP客户端
     * @param mcpId
     * @param reason
     */
    private void autoOffline(String mcpId, String reason) {
        log.error("MCP客户端自动下线: mcpId={}, 原因={}", mcpId, reason);
        if (connectionPool != null) {
            try {
                connectionPool.removeClient(mcpId);
                log.info("MCP客户端已从连接池移除: mcpId={}", mcpId);
            } catch (Exception e) {
                log.error("MCP客户端从连接池移除失败: mcpId={}", mcpId, e);
            }
        }
        // 同步更新数据库：状态设为禁用 + 记录下线原因
        if (serverConfigService != null) {
            try {
                serverConfigService.autoOffline(mcpId, reason);
                log.info("MCP服务数据库已更新为禁用并记录下线原因: mcpId={}, reason={}", mcpId, reason);
            } catch (Exception e) {
                log.error("MCP服务数据库状态更新失败: mcpId={}", mcpId, e);
            }
        }
    }

    /**
     * 判断客户端是否健康
     * @param mcpId
     * @return
     */
    public boolean isHealthy(String mcpId) {
        HealthState state = healthStates.get(mcpId);
        return state != null && state.consecutiveFailures < getMaxConsecutiveFailures();
    }

    /**
     * 移除健康状态记录
     * @param mcpId
     */
    public void removeState(String mcpId) {
        healthStates.remove(mcpId);
    }

    /**
     * 获取所有不健康的客户端ID
     * @return
     */
    public Set<String> getUnhealthyClientIds() {
        int maxFailures = getMaxConsecutiveFailures();
        return healthStates.entrySet().stream()
                .filter(entry -> entry.getValue().consecutiveFailures >= maxFailures)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 定时健康检查：下线不健康的客户端 + 自动上线手动恢复的服务
     */
    @Scheduled(fixedRateString = "${ai.mcp.health-check-interval-seconds:300}000",
            initialDelayString = "${ai.mcp.health-check-interval-seconds:300}000")
    public void scheduledHealthCheck() {
        checkAndOfflineUnhealthy();
        checkAndRecoverServices();
    }

    /**
     * 检查并下线不健康的客户端
     */
    private void checkAndOfflineUnhealthy() {
        if (healthStates.isEmpty()) {
            return;
        }
        log.info("开始MCP客户端定时健康检查，当前注册数: {}", healthStates.size());
        Set<String> unhealthy = getUnhealthyClientIds();
        if (!unhealthy.isEmpty()) {
            log.warn("检测到不健康的MCP客户端: {}", unhealthy);
            for (String mcpId : unhealthy) {
                autoOffline(mcpId, "定时健康检查发现连续" + getMaxConsecutiveFailures() + "次失败");
            }
        }
    }

    /**
     * 检查数据库中被手动恢复为启用的服务，尝试自动重新上线
     * <p>
     * 判断逻辑：数据库 server_status=1 且 offline_reason 不为空 且 连接池中不存在该服务
     * </p>
     */
    private void checkAndRecoverServices() {
        if (serverConfigService == null || connectionPool == null) {
            return;
        }
        try {
            List<McpServerConfigInfo> recoverable = serverConfigService.findRecoverableServices();
            if (recoverable.isEmpty()) {
                return;
            }
            log.info("检测到待恢复的MCP服务: count={}", recoverable.size());
            for (McpServerConfigInfo info : recoverable) {
                String serverCode = info.getServerCode();
                try {
                    McpServerConfig config = serverConfigService.toModel(info);
                    connectionPool.registerClient(serverCode, config);
                    // 注册成功后清空下线原因
                    info.setOfflineReason(null);
                    info.setUpdateTime(java.time.LocalDateTime.now());
                    serverConfigService.updateInfoById(info);
                    log.info("MCP服务自动上线成功: serverCode={}", serverCode);
                } catch (Exception e) {
                    log.warn("MCP服务自动上线失败（将在下次检查时重试）: serverCode={}", serverCode, e);
                }
            }
        } catch (Exception e) {
            log.error("检查待恢复MCP服务异常", e);
        }
    }

    private int getMaxConsecutiveFailures() {
        return properties.getMaxConsecutiveFailures();
    }

    private int getMaxConsecutiveEmptyResults() {
        return properties.getMaxConsecutiveEmptyResults();
    }

    private static class HealthState {

        final boolean lastSuccess;
        final boolean lastEmptyResult;
        final int toolCount;
        final long lastLatencyMs;
        final int consecutiveFailures;
        final int consecutiveEmptyResults;
        final long lastCheckTime;

        HealthState(boolean lastSuccess, boolean lastEmptyResult, int toolCount, long lastLatencyMs,
                    int consecutiveFailures, int consecutiveEmptyResults, long lastCheckTime) {
            this.lastSuccess = lastSuccess;
            this.lastEmptyResult = lastEmptyResult;
            this.toolCount = toolCount;
            this.lastLatencyMs = lastLatencyMs;
            this.consecutiveFailures = consecutiveFailures;
            this.consecutiveEmptyResults = consecutiveEmptyResults;
            this.lastCheckTime = lastCheckTime;
        }
    }
}
