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
package com.yangqiongai.ai.agent.data.core.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.agent.data.core.entity.AgentEntity;
import com.yangqiongai.ai.agent.data.core.mapper.AgentMapper;
import com.yangqiongai.ai.common.scope.ScopeContext;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent配置仓库默认实现
 * @author yangqiong
 */
public class DefaultAgentRepository implements AgentRepository {

    @Autowired
    private AgentMapper agentTypeMapper;

    /**
     * 缓存过期时间（分钟），默认5分钟
     */
    @Value("${ai.cache.agent-type.ttl-minutes:5}")
    private long cacheTtlMinutes;

    /**
     * 缓存最大条目数，默认1000
     */
    @Value("${ai.cache.agent-type.max-size:1000}")
    private long cacheMaxSize;

    /**
     * Caffeine本地缓存，key=scopeId:agentCode（复制模式下同一agentCode可存在于多个作用域），value=Agent
     */
    private Cache<String, Agent> cache;

    /**
     * 初始化Caffeine缓存
     */
    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .build();
    }

    @Override
    public List<Agent> list() {
        return agentTypeMapper.selectList(new LambdaQueryWrapper<AgentEntity>()).stream().map(this::toInfo).toList();
    }

    @Override
    public Agent getByCode(String agentCode) {
        if (agentCode == null) {
            return null;
        }
        // 先从缓存读取，若未命中则从数据库加载并回填缓存
        return cache.get(cacheKey(agentCode), key -> {
            // 复制模式下同一agentCode可存在于多个作用域，必须限定当前作用域
            AgentEntity entity = agentTypeMapper.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentCode, agentCode)
                            .eq(AgentEntity::getScopeId, ScopeContext.getScopeId()));
            return entity != null ? toInfo(entity) : null;
        });
    }

    @Override
    public List<Agent> listEnabled() {
        return agentTypeMapper.selectList(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getStatus, 1)
                .orderByAsc(AgentEntity::getSortOrder)).stream().map(this::toInfo).toList();
    }

    @Override
    public void save(Agent agent) {
        AgentEntity entity = toEntity(agent);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        agentTypeMapper.insert(entity);
        agent.setId(entity.getId());
        // 同步缓存
        cache.put(cacheKey(entity.getAgentCode()), toInfo(entity));
    }

    @Override
    public void updateById(Agent agent) {
        AgentEntity entity = toEntity(agent);
        entity.setUpdateTime(LocalDateTime.now());
        agentTypeMapper.updateById(entity);
        // 图标允许显式清空（恢复默认），updateById默认忽略null字段需单独置空
        if (agent.getIcon() == null) {
            LambdaUpdateWrapper<AgentEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AgentEntity::getId, entity.getId())
                    .set(AgentEntity::getIcon, null);
            agentTypeMapper.update(wrapper);
        }
        // 同步缓存
        cache.put(cacheKey(agent.getAgentCode()), agent);
    }

    @Override
    public boolean toggleStatus(String agentCode) {
        Agent info = getByCode(agentCode);
        if (info == null) {
            return false;
        }
        int newStatus = info.getStatus() == 1 ? 0 : 1;
        LambdaUpdateWrapper<AgentEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentEntity::getAgentCode, agentCode)
                .eq(AgentEntity::getScopeId, ScopeContext.getScopeId())
                .set(AgentEntity::getStatus, newStatus)
                .set(AgentEntity::getUpdateTime, LocalDateTime.now());
        boolean result = agentTypeMapper.update(null, wrapper) > 0;
        // 缓存失效，下次查询重新加载
        cache.invalidate(cacheKey(agentCode));
        return result;
    }

    @Override
    public void updateStatus(String agentCode, int status) {
        LambdaUpdateWrapper<AgentEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentEntity::getAgentCode, agentCode)
                .eq(AgentEntity::getScopeId, ScopeContext.getScopeId())
                .set(AgentEntity::getStatus, status)
                .set(AgentEntity::getUpdateTime, LocalDateTime.now());
        agentTypeMapper.update(null, wrapper);
        // 缓存失效，下次查询重新加载
        cache.invalidate(cacheKey(agentCode));
    }

    /**
     * 构建作用域感知的缓存key（同一agentCode可存在于多个作用域，防止串缓存）
     * @param agentCode
     * @return
     */
    private String cacheKey(String agentCode) {
        return ScopeContext.getScopeId() + ":" + agentCode;
    }

    private Agent toInfo(AgentEntity entity) {
        Agent info = new Agent();
        info.setId(entity.getId());
        info.setAgentCode(entity.getAgentCode());
        info.setAgentName(entity.getAgentName());
        info.setDescription(entity.getDescription());
        info.setIcon(entity.getIcon());
        info.setCategory(entity.getCategory());
        info.setDirectoryCode(entity.getDirectoryCode());
        info.setStatus(entity.getStatus());
        info.setSortOrder(entity.getSortOrder());
        info.setSessionType(entity.getSessionType());
        info.setAgentConfig(entity.getAgentConfig());
        info.setShared(entity.getShared());
        info.setOriginAgentCode(entity.getOriginAgentCode());
        info.setOriginVersionId(entity.getOriginVersionId());
        info.setScopeId(entity.getScopeId());
        info.setRemark(entity.getRemark());
        return info;
    }

    private AgentEntity toEntity(Agent info) {
        AgentEntity entity = new AgentEntity();
        entity.setId(info.getId());
        entity.setAgentCode(info.getAgentCode());
        entity.setAgentName(info.getAgentName());
        entity.setDescription(info.getDescription());
        entity.setIcon(info.getIcon());
        entity.setCategory(info.getCategory());
        entity.setDirectoryCode(info.getDirectoryCode());
        entity.setStatus(info.getStatus());
        entity.setSortOrder(info.getSortOrder());
        entity.setSessionType(info.getSessionType());
        entity.setAgentConfig(info.getAgentConfig());
        entity.setShared(info.getShared());
        entity.setOriginAgentCode(info.getOriginAgentCode());
        entity.setOriginVersionId(info.getOriginVersionId());
        entity.setRemark(info.getRemark());
        return entity;
    }
}
