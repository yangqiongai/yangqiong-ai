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
package com.yangqiongai.ai.platform.connector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.agent.tool.ToolExtraContributor;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorInstanceMapper;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接器工具收集者
 * <p>
 * 实现框架ToolExtraContributor：按agentCode收集启用实例产出的连接器工具，
 * 经ToolkitAssembler重载并入运行链路。工具命名空间connector_{instanceCode}_{toolName}，
 * 60s本地缓存TTL可配，实例启停最迟一个TTL生效。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorToolkitContributor implements ToolExtraContributor {

    private static final Logger log = LoggerFactory.getLogger(ConnectorToolkitContributor.class);

    /**
     * 工具命名空间前缀
     */
    public static final String TOOL_PREFIX = "connector_";

    /**
     * 连接器实例Mapper
     */
    @Autowired
    private ConnectorInstanceMapper instanceMapper;

    /**
     * 提供商注册中心
     */
    @Autowired
    private ConnectorRegistry registry;

    /**
     * 凭证托管
     */
    @Autowired
    private ConnectorCredentialService credentialService;

    /**
     * 工具装配缓存TTL（毫秒）
     */
    @Value("${ai.connector.tool-cache-ms:60000}")
    private long toolCacheMs;

    /**
     * 工具缓存（agentCode -> 缓存条目）
     */
    private final Map<String, CacheEntry> toolCache = new ConcurrentHashMap<>();

    public ConnectorToolkitContributor() {
    }

    @Override
    public List<AgentTool> collectTools(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        CacheEntry entry = toolCache.get(agentCode);
        if (entry != null && now < entry.expireAt) {
            return entry.tools;
        }
        List<AgentTool> tools = loadTools(agentCode);
        toolCache.put(agentCode, new CacheEntry(tools, now + toolCacheMs));
        return tools;
    }

    /**
     * 实时查询启用实例并产出工具（单个实例异常时跳过不影响其余实例）
     * @param agentCode
     * @return
     */
    private List<AgentTool> loadTools(String agentCode) {
        List<ConnectorInstance> instances = instanceMapper.selectList(
                new LambdaQueryWrapper<ConnectorInstance>()
                        .eq(ConnectorInstance::getStatus, ConnectorCredentialService.STATUS_ENABLED));
        List<AgentTool> tools = new ArrayList<>();
        for (ConnectorInstance instance : instances) {
            try {
                ConnectorProvider provider = registry.getProvider(instance.getProviderCode());
                if (provider == null || !provider.supports(instance)) {
                    continue;
                }
                // agentCode不匹配时跳过（实例绑定目标Agent仅约束入站，出站工具对所有Agent可用）
                ConnectorCredentialView credential = credentialService.loadCredentialView(instance.getCredentialId());
                for (AgentTool tool : provider.createTools(instance, credential)) {
                    tools.add(new NamespacedTool(tool, instance.getInstanceCode()));
                }
            } catch (Exception e) {
                log.warn("连接器实例{}工具产出失败,跳过: {}", instance.getInstanceCode(), e.getMessage());
            }
        }
        if (!tools.isEmpty()) {
            log.debug("收集连接器工具: agentCode={}, tools={}", agentCode, tools.size());
        }
        return tools;
    }

    /**
     * 缓存条目
     * @author yangqiong
     */
    private record CacheEntry(List<AgentTool> tools, long expireAt) {
    }

    /**
     * 命名空间工具装饰器
     * <p>
     * 对外暴露connector_{instanceCode}_{toolName}唯一名，内部调用委托原始工具。
     * </p>
     * @author yangqiong
     */
    static class NamespacedTool implements AgentTool {

        /**
         * 委托的原始工具
         */
        private final AgentTool delegate;

        /**
         * 带命名空间的唯一工具名
         */
        private final String namespacedName;

        NamespacedTool(AgentTool delegate, String instanceCode) {
            this.delegate = delegate;
            this.namespacedName = TOOL_PREFIX + instanceCode + "_" + delegate.getName();
        }

        @Override
        public String getName() {
            return namespacedName;
        }

        @Override
        public String getDescription() {
            return delegate.getDescription();
        }

        @Override
        public Map<String, Object> getParameters() {
            return delegate.getParameters();
        }

        @Override
        public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
            return delegate.callAsync(param);
        }

        @Override
        public String getToolCategory() {
            return "connector";
        }
    }
}
