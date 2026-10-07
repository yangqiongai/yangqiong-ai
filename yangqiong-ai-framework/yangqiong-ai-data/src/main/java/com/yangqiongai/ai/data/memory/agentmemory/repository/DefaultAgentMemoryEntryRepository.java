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
package com.yangqiongai.ai.data.memory.agentmemory.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.data.memory.agentmemory.entity.AgentMemoryEntry;
import com.yangqiongai.ai.data.memory.agentmemory.mapper.AgentMemoryEntryMapper;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent运行记忆条目
 * @author yangqiong
 */
public class DefaultAgentMemoryEntryRepository implements AgentMemoryEntryRepository {

    @Autowired
    private AgentMemoryEntryMapper agentMemoryEntryMapper;

    @Override
    public Long insert(AgentMemoryEntryInfo model) {
        AgentMemoryEntry entity = toEntity(model);
        agentMemoryEntryMapper.insert(entity);
        model.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public AgentMemoryEntryInfo selectById(Long id) {
        AgentMemoryEntry entity = agentMemoryEntryMapper.selectById(id);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public AgentMemoryEntryInfo findActiveByInputHash(String agentCode, String userAnchor, String inputHash) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getAgentCode, agentCode)
                .eq(AgentMemoryEntry::getUserAnchor, userAnchor)
                .eq(AgentMemoryEntry::getInputHash, inputHash)
                .eq(AgentMemoryEntry::getStatus, AgentMemoryEntryInfo.STATUS_ACTIVE)
                .last("LIMIT 1");
        AgentMemoryEntry entity = agentMemoryEntryMapper.selectOne(wrapper);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public List<AgentMemoryEntryInfo> findActiveByAgentAndUser(String agentCode, String userAnchor, int limit) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getAgentCode, agentCode)
                .eq(AgentMemoryEntry::getUserAnchor, userAnchor)
                .eq(AgentMemoryEntry::getStatus, AgentMemoryEntryInfo.STATUS_ACTIVE)
                .orderByDesc(AgentMemoryEntry::getConfidence)
                .last("LIMIT " + limit);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<AgentMemoryEntryInfo> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AgentMemoryEntry::getId, ids);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<AgentMemoryEntryInfo> findExpiredTtl(LocalDateTime now, int limit) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getStatus, AgentMemoryEntryInfo.STATUS_ACTIVE)
                .isNotNull(AgentMemoryEntry::getTtlExpireTime)
                .lt(AgentMemoryEntry::getTtlExpireTime, now)
                .last("LIMIT " + limit);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<AgentMemoryEntryInfo> scanByStatus(String status, Long lastId, int limit) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getStatus, status)
                .gt(lastId != null, AgentMemoryEntry::getId, lastId)
                .orderByAsc(AgentMemoryEntry::getId)
                .last("LIMIT " + limit);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void updateStatus(Long id, String status) {
        LambdaUpdateWrapper<AgentMemoryEntry> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentMemoryEntry::getId, id)
                .set(AgentMemoryEntry::getStatus, status)
                .set(AgentMemoryEntry::getUpdateTime, LocalDateTime.now());
        agentMemoryEntryMapper.update(wrapper);
    }

    @Override
    public void updateConfidence(Long id, Double confidence) {
        LambdaUpdateWrapper<AgentMemoryEntry> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentMemoryEntry::getId, id)
                .set(AgentMemoryEntry::getConfidence, confidence)
                .set(AgentMemoryEntry::getUpdateTime, LocalDateTime.now());
        agentMemoryEntryMapper.update(wrapper);
    }

    @Override
    public void updateContent(Long id, String content) {
        LambdaUpdateWrapper<AgentMemoryEntry> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentMemoryEntry::getId, id)
                .set(AgentMemoryEntry::getContent, content)
                .setSql("version_no = version_no + 1")
                .set(AgentMemoryEntry::getUpdateTime, LocalDateTime.now());
        agentMemoryEntryMapper.update(wrapper);
    }

    @Override
    public void batchUpdateAccessInfo(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<AgentMemoryEntry> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(AgentMemoryEntry::getId, ids)
                .setSql("access_count = access_count + 1")
                .set(AgentMemoryEntry::getLastAccessedAt, LocalDateTime.now());
        agentMemoryEntryMapper.update(wrapper);
    }

    @Override
    public List<AgentMemoryEntryInfo> findByUserAnchor(String userAnchor) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getUserAnchor, userAnchor);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<AgentMemoryEntryInfo> findByAgentCode(String agentCode) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getAgentCode, agentCode);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public int deleteByUserAnchor(String userAnchor) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getUserAnchor, userAnchor);
        return agentMemoryEntryMapper.delete(wrapper);
    }

    @Override
    public int deleteByAgentCode(String agentCode) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMemoryEntry::getAgentCode, agentCode);
        return agentMemoryEntryMapper.delete(wrapper);
    }

    @Override
    public void deleteById(Long id) {
        agentMemoryEntryMapper.deleteById(id);
    }

    @Override
    public List<AgentMemoryEntryInfo> findPage(String agentCode, String userAnchor, String memoryType,
                                                String status, int offset, int limit) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = buildCondition(agentCode, userAnchor, memoryType, status);
        wrapper.orderByDesc(AgentMemoryEntry::getCreateTime)
                .last("LIMIT " + offset + "," + limit);
        return agentMemoryEntryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public long countByCondition(String agentCode, String userAnchor, String memoryType, String status) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = buildCondition(agentCode, userAnchor, memoryType, status);
        return agentMemoryEntryMapper.selectCount(wrapper);
    }

    @Override
    public long countByStatus(String status) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), AgentMemoryEntry::getStatus, status);
        return agentMemoryEntryMapper.selectCount(wrapper);
    }

    /**
     * 构建条件查询包装
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @param status
     * @return
     */
    private LambdaQueryWrapper<AgentMemoryEntry> buildCondition(String agentCode, String userAnchor,
                                                                String memoryType, String status) {
        LambdaQueryWrapper<AgentMemoryEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(agentCode != null && !agentCode.isBlank(), AgentMemoryEntry::getAgentCode, agentCode)
                .eq(userAnchor != null && !userAnchor.isBlank(), AgentMemoryEntry::getUserAnchor, userAnchor)
                .eq(memoryType != null && !memoryType.isBlank(), AgentMemoryEntry::getMemoryType, memoryType)
                .eq(status != null && !status.isBlank(), AgentMemoryEntry::getStatus, status);
        return wrapper;
    }

    private AgentMemoryEntry toEntity(AgentMemoryEntryInfo model) {
        AgentMemoryEntry entity = new AgentMemoryEntry();
        entity.setId(model.getId());
        entity.setAgentCode(model.getAgentCode());
        entity.setScopeId(model.getScopeId());
        entity.setUserAnchor(model.getUserAnchor());
        entity.setMemoryType(model.getMemoryType());
        entity.setContent(model.getContent());
        entity.setEmbeddingRef(model.getEmbeddingRef());
        entity.setSourceTaskId(model.getSourceTaskId());
        entity.setSourceUser(model.getSourceUser());
        entity.setConfidence(model.getConfidence());
        entity.setTtlExpireTime(model.getTtlExpireTime());
        entity.setStatus(model.getStatus());
        entity.setInputHash(model.getInputHash());
        entity.setAccessCount(model.getAccessCount());
        entity.setLastAccessedAt(model.getLastAccessedAt());
        entity.setVersionNo(model.getVersionNo());
        return entity;
    }

    private AgentMemoryEntryInfo toModel(AgentMemoryEntry entity) {
        AgentMemoryEntryInfo model = new AgentMemoryEntryInfo();
        model.setId(entity.getId());
        model.setAgentCode(entity.getAgentCode());
        model.setScopeId(entity.getScopeId());
        model.setUserAnchor(entity.getUserAnchor());
        model.setMemoryType(entity.getMemoryType());
        model.setContent(entity.getContent());
        model.setEmbeddingRef(entity.getEmbeddingRef());
        model.setSourceTaskId(entity.getSourceTaskId());
        model.setSourceUser(entity.getSourceUser());
        model.setConfidence(entity.getConfidence());
        model.setTtlExpireTime(entity.getTtlExpireTime());
        model.setStatus(entity.getStatus());
        model.setInputHash(entity.getInputHash());
        model.setAccessCount(entity.getAccessCount());
        model.setLastAccessedAt(entity.getLastAccessedAt());
        model.setVersionNo(entity.getVersionNo());
        model.setCreateTime(entity.getCreateTime());
        model.setUpdateTime(entity.getUpdateTime());
        return model;
    }
}
