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
package com.yangqiongai.ai.data.memory.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.data.memory.entity.UserLongTermMemory;
import com.yangqiongai.ai.data.memory.mapper.UserLongTermMemoryMapper;
import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户长期记忆
 * @author yangqiong
 */
public class DefaultUserLongTermMemoryRepository implements UserLongTermMemoryRepository {

    private static final String MEMORY_TYPE_DEPRECATED = "DEPRECATED";

    @Autowired
    private UserLongTermMemoryMapper userLongTermMemoryMapper;

    @Override
    public void insert(UserLongTermMemoryInfo memory) {
        UserLongTermMemory entity = toEntity(memory);
        userLongTermMemoryMapper.insert(entity);
        memory.setId(entity.getId());
    }

    @Override
    public UserLongTermMemoryInfo selectById(Long id) {
        UserLongTermMemory entity = userLongTermMemoryMapper.selectById(id);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public void deleteById(Long id) {
        userLongTermMemoryMapper.deleteById(id);
    }

    @Override
    public int deleteByUserId(String userId) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId);
        return userLongTermMemoryMapper.delete(wrapper);
    }

    @Override
    public List<UserLongTermMemoryInfo> findEffectiveMemories(String userId) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId)
                .ne(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .and(w -> w.isNull(UserLongTermMemory::getValidUntil)
                        .or().gt(UserLongTermMemory::getValidUntil, LocalDateTime.now()));
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<UserLongTermMemoryInfo> findByIdsEffective(List<Long> ids) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserLongTermMemory::getId, ids)
                .ne(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .and(w -> w.isNull(UserLongTermMemory::getValidUntil)
                        .or().gt(UserLongTermMemory::getValidUntil, LocalDateTime.now()));
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public UserLongTermMemoryInfo findLatestSummaryMemory(String userId) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId)
                .eq(UserLongTermMemory::getMemoryType, "SUMMARY")
                .orderByDesc(UserLongTermMemory::getUpdateTime)
                .last("LIMIT 1");
        UserLongTermMemory entity = userLongTermMemoryMapper.selectOne(wrapper);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public void updateContentAndRelatedIds(Long id, String content, String relatedSessionIds) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, id)
                .set(UserLongTermMemory::getContent, content)
                .set(UserLongTermMemory::getRelatedSessionIds, relatedSessionIds)
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public void updateContentAndScore(Long id, String content, Integer importanceScore, Integer baseScore) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, id)
                .set(UserLongTermMemory::getContent, content)
                .set(UserLongTermMemory::getImportanceScore, importanceScore)
                .set(UserLongTermMemory::getBaseScore, baseScore)
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public void updateContentScoreAndSource(Long id, String content, Integer importanceScore, Integer baseScore, String source) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, id)
                .set(UserLongTermMemory::getContent, content)
                .set(UserLongTermMemory::getImportanceScore, importanceScore)
                .set(UserLongTermMemory::getBaseScore, baseScore)
                .set(UserLongTermMemory::getSource, source)
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public void markExpired(Long memoryId) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, memoryId)
                .set(UserLongTermMemory::getValidUntil, LocalDateTime.now())
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public List<UserLongTermMemoryInfo> findMemoriesForKeywordMatch(String userId) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId)
                .ne(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .and(w -> w.isNull(UserLongTermMemory::getValidUntil)
                        .or().gt(UserLongTermMemory::getValidUntil, LocalDateTime.now()))
                .orderByDesc(UserLongTermMemory::getUpdateTime);
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<UserLongTermMemoryInfo> findActiveMemoriesByTypeAndTags(String userId, String memoryType, String tags) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId)
                .eq(UserLongTermMemory::getMemoryType, memoryType)
                .eq(UserLongTermMemory::getTags, tags)
                .and(w -> w.isNull(UserLongTermMemory::getValidUntil)
                        .or().gt(UserLongTermMemory::getValidUntil, LocalDateTime.now()));
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void batchUpdateAccessInfo(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(UserLongTermMemory::getId, ids)
                .setSql("access_count = access_count + 1")
                .set(UserLongTermMemory::getLastAccessedAt, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public void updateImportanceScore(Long id, Integer importanceScore) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, id)
                .set(UserLongTermMemory::getImportanceScore, importanceScore)
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public List<UserLongTermMemoryInfo> scanNonDeprecatedByPage(Long lastId, int limit) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .isNotNull(UserLongTermMemory::getBaseScore)
                .gt(lastId != null, UserLongTermMemory::getId, lastId)
                .orderByAsc(UserLongTermMemory::getId)
                .last("LIMIT " + limit);
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void batchMarkAsDeprecated(List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(UserLongTermMemory::getId, memoryIds)
                .set(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public List<UserLongTermMemoryInfo> findByIdsAndTags(List<Long> ids, List<String> tagsList) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserLongTermMemory::getId, ids);
        if (tagsList != null && !tagsList.isEmpty()) {
            wrapper.and(w -> {
                for (int i = 0; i < tagsList.size(); i++) {
                    if (i == 0) {
                        w.like(UserLongTermMemory::getTags, tagsList.get(i));
                    } else {
                        w.or().like(UserLongTermMemory::getTags, tagsList.get(i));
                    }
                }
            });
        }
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<String> findAllActiveUserIds() {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserLongTermMemory::getUserId)
                .ne(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .groupBy(UserLongTermMemory::getUserId);
        List<UserLongTermMemory> records = userLongTermMemoryMapper.selectList(wrapper);
        return records.stream()
                .map(UserLongTermMemory::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    public List<UserLongTermMemoryInfo> findUserMemoriesForOrganize(String userId, int limit) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId)
                .ne(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .and(w -> w.isNull(UserLongTermMemory::getValidUntil)
                        .or().gt(UserLongTermMemory::getValidUntil, LocalDateTime.now()))
                .orderByAsc(UserLongTermMemory::getUpdateTime)
                .last("LIMIT " + limit);
        return userLongTermMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void archiveAsDeprecated(Long memoryId) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, memoryId)
                .set(UserLongTermMemory::getMemoryType, MEMORY_TYPE_DEPRECATED)
                .set(UserLongTermMemory::getValidUntil, LocalDateTime.now())
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public void updateContent(Long id, String content) {
        LambdaUpdateWrapper<UserLongTermMemory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserLongTermMemory::getId, id)
                .set(UserLongTermMemory::getContent, content)
                .set(UserLongTermMemory::getUpdateTime, LocalDateTime.now());
        userLongTermMemoryMapper.update(wrapper);
    }

    @Override
    public long countByUserId(String userId) {
        LambdaQueryWrapper<UserLongTermMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserLongTermMemory::getUserId, userId);
        return userLongTermMemoryMapper.selectCount(wrapper);
    }

    private UserLongTermMemory toEntity(UserLongTermMemoryInfo model) {
        UserLongTermMemory entity = new UserLongTermMemory();
        entity.setId(model.getId());
        entity.setUserId(model.getUserId());
        entity.setMemoryType(model.getMemoryType());
        entity.setContent(model.getContent());
        entity.setRelatedSessionIds(model.getRelatedSessionIds());
        entity.setImportanceScore(model.getImportanceScore());
        entity.setBaseScore(model.getBaseScore());
        entity.setAccessCount(model.getAccessCount());
        entity.setTags(model.getTags());
        entity.setSource(model.getSource());
        entity.setEmbedding(model.getEmbedding());
        entity.setValidFrom(model.getValidFrom());
        entity.setValidUntil(model.getValidUntil());
        entity.setLastAccessedAt(model.getLastAccessedAt());
        return entity;
    }

    private UserLongTermMemoryInfo toModel(UserLongTermMemory entity) {
        UserLongTermMemoryInfo model = new UserLongTermMemoryInfo();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setMemoryType(entity.getMemoryType());
        model.setContent(entity.getContent());
        model.setRelatedSessionIds(entity.getRelatedSessionIds());
        model.setImportanceScore(entity.getImportanceScore());
        model.setBaseScore(entity.getBaseScore());
        model.setAccessCount(entity.getAccessCount());
        model.setTags(entity.getTags());
        model.setSource(entity.getSource());
        model.setEmbedding(entity.getEmbedding());
        model.setValidFrom(entity.getValidFrom());
        model.setValidUntil(entity.getValidUntil());
        model.setLastAccessedAt(entity.getLastAccessedAt());
        return model;
    }
}
