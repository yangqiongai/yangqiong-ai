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
package com.yangqiongai.ai.agent.mcp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.mcp.McpConnectionPool;
import com.yangqiongai.ai.agent.mcp.model.McpConnectionTestResult;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfig;
import com.yangqiongai.ai.agent.mcp.model.McpServerConfigInfo;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP服务配置服务
 * <p>
 * 负责MCP服务配置的业务逻辑，包括启动时自动注册到连接池。
 * 数据访问通过McpServerConfigRepository实现。
 * </p>
 * @author yangqiong
 */
@Service
public class McpServerConfigService {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfigService.class);

    @Autowired
    private McpServerConfigRepository mcpServerConfigRepository;

    /**
     * MCP连接池（延迟注入，避免循环依赖）
     */
    private McpConnectionPool mcpConnectionPool;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 设置连接池引用（由McpAutoConfiguration注入，避免循环依赖）
     * @param mcpConnectionPool
     */
    public void setMcpConnectionPool(McpConnectionPool mcpConnectionPool) {
        this.mcpConnectionPool = mcpConnectionPool;
    }

    /**
     * 从数据库加载启用的MCP配置并注册到连接池
     * <p>
     * 由McpAutoConfiguration在Bean创建阶段显式调用，
     * 确保Tomcat接收请求时连接池已就绪。
     * </p>
     */
    public void initMcpConnections() {
        if (mcpConnectionPool == null) {
            log.warn("MCP连接池未就绪，跳过启动注册");
            return;
        }
        List<McpServerConfigInfo> enabledConfigs = mcpServerConfigRepository.listEnabled();
        if (enabledConfigs.isEmpty()) {
            log.info("数据库中无启用的MCP服务配置");
            return;
        }
        int success = 0;
        for (McpServerConfigInfo info : enabledConfigs) {
            try {
                McpServerConfig config = toModel(info);
                if (mcpConnectionPool.registerClient(info.getServerCode(), config)) {
                    success++;
                }
            } catch (Exception e) {
                log.error("启动注册MCP服务失败: serverCode={}", info.getServerCode(), e);
            }
        }
        log.info("MCP服务启动注册完成: 总数={}, 成功={}", enabledConfigs.size(), success);
    }

    /**
     * 查询所有配置
     * @return
     */
    public List<McpServerConfigInfo> list() {
        return mcpServerConfigRepository.list();
    }

    /**
     * 根据serverCode查询配置
     * @param serverCode
     * @return
     */
    public McpServerConfigInfo getByServerCode(String serverCode) {
        return mcpServerConfigRepository.getByServerCode(serverCode);
    }

    /**
     * 查询所有启用的配置
     * @return
     */
    public List<McpServerConfigInfo> listEnabled() {
        return mcpServerConfigRepository.listEnabled();
    }

    /**
     * 按serverCode显式清单查询MCP配置
     * @param serverCodes agentConfig.mcpServers声明的serverCode清单
     * @return
     */
    public List<McpServerConfig> listByServerCodes(List<String> serverCodes) {
        List<McpServerConfigInfo> infos = mcpServerConfigRepository.listByServerCodes(serverCodes);
        return infos.stream().map(this::toModel).toList();
    }

    /**
     * 注册新MCP服务（保存到数据库并注册到连接池）
     * @param config
     * @return
     */
    public McpServerConfigInfo register(McpServerConfig config) {
        McpServerConfigInfo info = toInfo(config);
        info.setServerStatus(1);
        mcpServerConfigRepository.save(info);
        if (mcpConnectionPool != null) {
            mcpConnectionPool.registerClient(config.getServerCode(), config);
        }
        log.info("注册MCP服务: serverCode={}", config.getServerCode());
        return info;
    }

    /**
     * 更新MCP服务配置
     * @param serverCode
     * @param config
     * @return
     */
    public boolean updateConfig(String serverCode, McpServerConfig config) {
        McpServerConfigInfo existing = getByServerCode(serverCode);
        if (existing == null) {
            return false;
        }
        McpServerConfigInfo updated = toInfo(config);
        updated.setId(existing.getId());
        updated.setServerCode(serverCode);
        updated.setUpdateTime(LocalDateTime.now());
        mcpServerConfigRepository.updateById(updated);
        if (existing.getServerStatus() == 1 && mcpConnectionPool != null) {
            mcpConnectionPool.removeClient(serverCode);
            McpServerConfig newConfig = toModel(updated);
            mcpConnectionPool.registerClient(serverCode, newConfig);
        }
        return true;
    }

    /**
     * 按ID更新配置信息
     * @param info
     */
    public void updateInfoById(McpServerConfigInfo info) {
        mcpServerConfigRepository.updateById(info);
    }

    /**
     * 切换服务状态
     * @param serverCode
     * @param status 0-禁用 1-启用
     * @return
     */
    public boolean toggleStatus(String serverCode, int status) {
        McpServerConfigInfo info = getByServerCode(serverCode);
        if (info == null) {
            return false;
        }
        boolean result = mcpServerConfigRepository.toggleStatus(serverCode, status);
        if (result && mcpConnectionPool != null) {
            if (status == 0) {
                mcpConnectionPool.removeClient(serverCode);
            } else {
                McpServerConfigInfo updated = getByServerCode(serverCode);
                if (updated != null) {
                    mcpConnectionPool.registerClient(serverCode, toModel(updated));
                }
            }
        }
        return result;
    }

    /**
     * 自动下线：更新状态为禁用并记录下线原因
     * @param serverCode
     * @param reason
     * @return
     */
    public boolean autoOffline(String serverCode, String reason) {
        boolean result = mcpServerConfigRepository.autoOffline(serverCode, reason);
        if (result && mcpConnectionPool != null) {
            mcpConnectionPool.removeClient(serverCode);
            log.info("MCP服务自动下线完成: serverCode={}, reason={}", serverCode, reason);
        }
        return result;
    }

    /**
     * 查询数据库中状态为启用但连接池中不存在的服务（即被手动恢复的服务）
     * @return
     */
    public List<McpServerConfigInfo> findRecoverableServices() {
        if (mcpConnectionPool == null) {
            return List.of();
        }
        Set<String> registeredCodes = mcpConnectionPool.getRegisteredServerCodes();
        return mcpServerConfigRepository.findRecoverableServices().stream()
                .filter(info -> !registeredCodes.contains(info.getServerCode()))
                .toList();
    }

    /**
     * 测试MCP服务连接
     * @param serverCode
     * @return
     */
    public McpConnectionTestResult testConnection(String serverCode) {
        McpServerConfigInfo info = getByServerCode(serverCode);
        if (info == null) {
            return McpConnectionTestResult.fail("服务配置不存在: " + serverCode);
        }
        return mcpConnectionPool.testConnection(toModel(info));
    }

    /**
     * 测试新配置连接（不保存）
     * @param config
     * @return
     */
    public McpConnectionTestResult testNewConnection(McpServerConfig config) {
        return mcpConnectionPool.testConnection(config);
    }

    /**
     * 注销MCP服务（从数据库删除并从连接池移除）
     * @param serverCode
     * @return
     */
    public boolean unregister(String serverCode) {
        McpServerConfigInfo info = getByServerCode(serverCode);
        if (info == null) {
            return false;
        }
        boolean result = mcpServerConfigRepository.removeById(info.getId());
        if (result && mcpConnectionPool != null) {
            mcpConnectionPool.removeClient(serverCode);
        }
        return result;
    }

    /**
     * 列出指定服务的可用工具
     * @param serverCode
     * @return
     */
    public List<McpToolInfo> listServerTools(String serverCode) {
        if (mcpConnectionPool == null) {
            return Collections.emptyList();
        }
        return mcpConnectionPool.getClient(serverCode)
                .map(client -> client.listTools())
                .orElse(Collections.emptyList());
    }

    /**
     * 获取所有已注册的服务编码
     * @return
     */
    public Set<String> getRegisteredServerCodes() {
        if (mcpConnectionPool == null) {
            return Set.of();
        }
        return mcpConnectionPool.getRegisteredServerCodes();
    }

    /**
     * 列出所有可用工具
     * @return
     */
    public List<McpToolInfo> listAllTools() {
        if (mcpConnectionPool == null) {
            return Collections.emptyList();
        }
        return mcpConnectionPool.listAllTools();
    }

    // ==================== 转换方法 ====================

    /**
     * McpServerConfigInfo → McpServerConfig
     * @param info
     * @return
     */
    public McpServerConfig toModel(McpServerConfigInfo info) {
        McpServerConfig config = new McpServerConfig();
        config.setServerCode(info.getServerCode());
        config.setServerName(info.getServerName());
        config.setTransportType(info.getTransportType());
        config.setConnectionConfig(parseJsonMap(info.getConnectionConfig()));
        config.setEnabledTools(parseJsonList(info.getEnabledTools()));
        config.setDisabledTools(parseJsonList(info.getDisabledTools()));
        config.setServerStatus(info.getServerStatus() != null ? info.getServerStatus() : 1);
        config.setCategory(info.getCategory());
        return config;
    }

    /**
     * McpServerConfig → McpServerConfigInfo
     * @param config
     * @return
     */
    private McpServerConfigInfo toInfo(McpServerConfig config) {
        McpServerConfigInfo info = new McpServerConfigInfo();
        info.setServerCode(config.getServerCode());
        info.setServerName(config.getServerName());
        info.setTransportType(config.getTransportType());
        info.setConnectionConfig(toJson(config.getConnectionConfig()));
        info.setEnabledTools(toJson(config.getEnabledTools()));
        info.setDisabledTools(toJson(config.getDisabledTools()));
        info.setServerStatus(config.getServerStatus());
        info.setCategory(config.getCategory());
        return info;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析JSON列表失败: {}", json, e);
            return null;
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析JSON对象失败: {}", json, e);
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("序列化JSON失败", e);
            return null;
        }
    }
}
