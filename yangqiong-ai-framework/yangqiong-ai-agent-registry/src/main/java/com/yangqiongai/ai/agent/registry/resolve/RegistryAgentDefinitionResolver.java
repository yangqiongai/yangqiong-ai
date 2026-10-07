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
package com.yangqiongai.ai.agent.registry.resolve;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.provider.AgentDefinitionResolver;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentDefinitionMapper;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentVersionMapper;
import com.yangqiongai.ai.agent.registry.assemble.AgentAssembler;
import com.yangqiongai.ai.agent.registry.config.AgentRegistryProperties;
import com.yangqiongai.ai.agent.registry.gray.GrayDecision;
import com.yangqiongai.ai.agent.registry.gray.GrayRouter;
import com.yangqiongai.ai.agent.registry.model.VersionStatus;
import com.yangqiongai.ai.common.scope.ScopeContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 注册中心Agent定义解析器
 * <p>
 * 灰度命中→经Assembler用目标版本config_json构建Agent；未命中→null（回退默认路径）。
 * 缓存key=agentCode#userId#scopeId（灰度决策与用户相关），未命中结果以Optional.empty()缓存，
 * 避免每次请求查询灰度规则；生命周期事件按agentCode前缀失效。
 * </p>
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.registry.enabled", havingValue = "true")
public class RegistryAgentDefinitionResolver implements AgentDefinitionResolver {

    private static final Logger log = LoggerFactory.getLogger(RegistryAgentDefinitionResolver.class);

    @Autowired
    private GrayRouter grayRouter;

    @Autowired
    private AgentDefinitionMapper definitionMapper;

    @Autowired
    private AgentVersionMapper versionMapper;

    @Autowired
    private AgentRegistryProperties properties;

    /**
     * Caffeine本地缓存，key=agentCode#userId#scopeId，value=解析结果（Optional.empty()表示未命中灰度）
     */
    private Cache<String, Optional<Agent>> cache;

    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.getGrayCacheTtlSeconds(), TimeUnit.SECONDS)
                .maximumSize(properties.getGrayCacheMaxSize())
                .build();
    }

    @Override
    public Agent resolve(AgentRequest request) {
        if (request == null || request.getAgentCode() == null || request.getAgentCode().isBlank()) {
            return null;
        }
        try {
            String key = buildKey(request.getAgentCode(), request.getUserId(), request.getScopeId());
            return cache.get(key, k -> doResolve(request)).orElse(null);
        } catch (Exception e) {
            // 解析整体降级：任何异常返回null走默认路径，不阻断Agent执行
            log.warn("注册中心解析降级走默认路径: agentCode={}", request.getAgentCode(), e);
            return null;
        }
    }

    /**
     * 执行灰度解析
     * @param request
     * @return
     */
    private Optional<Agent> doResolve(AgentRequest request) {
        String agentCode = request.getAgentCode();
        GrayDecision decision = grayRouter.route(agentCode, request.getUserId(), request.getScopeId());
        if (!decision.isHit()) {
            return Optional.empty();
        }
        AgentVersion version = versionMapper.selectById(decision.getTargetVersionId());
        // 目标版本非PUBLISHED视为未命中
        if (version == null || !VersionStatus.PUBLISHED.name().equals(version.getStatus())) {
            return Optional.empty();
        }
        // 复制模式下同一agentCode可存在于多个作用域，必须限定当前作用域
        AgentDefinition definition = definitionMapper.selectOne(
                new LambdaQueryWrapper<AgentDefinition>()
                        .eq(AgentDefinition::getAgentCode, agentCode)
                        .eq(AgentDefinition::getScopeId, ScopeContext.getScopeId()));
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(AgentAssembler.assemble(definition, version));
    }

    /**
     * 按agentCode失效缓存
     * @param agentCode
     */
    public void evict(String agentCode) {
        cache.asMap().keySet().removeIf(key -> key.startsWith(agentCode + "#"));
    }

    /**
     * 构建缓存key
     * @param agentCode
     * @param userId
     * @param scopeId
     * @return
     */
    private String buildKey(String agentCode, String userId, String scopeId) {
        return agentCode + "#" + (userId == null ? "" : userId) + "#" + (scopeId == null ? "" : scopeId);
    }
}
