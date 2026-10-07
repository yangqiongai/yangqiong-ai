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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.data.memory.entity.ChatMemory;
import com.yangqiongai.ai.data.memory.mapper.ChatMemoryMapper;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.MemoryPageResult;
import com.yangqiongai.ai.memory.repository.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对话记忆
 * @author yangqiong
 */
public class DefaultChatMemoryRepository implements ChatMemoryRepository {

    @Autowired
    private ChatMemoryMapper chatMemoryMapper;

    @Override
    public ChatMemoryRecord saveMessage(ChatMemoryRecord record) {
        ChatMemory entity = toEntity(record);
        chatMemoryMapper.insert(entity);
        record.setId(entity.getId());
        return record;
    }

    @Override
    public List<ChatMemoryRecord> findBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        // create_time为秒级精度，同一轮对话的消息可能落在同一秒，需按雪花id次级排序保证消息顺序稳定
        wrapper.eq(ChatMemory::getSessionId, sessionId)
                .orderByAsc(ChatMemory::getCreateTime)
                .orderByAsc(ChatMemory::getId);
        return chatMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemory::getSessionId, sessionId);
        chatMemoryMapper.delete(wrapper);
    }

    @Override
    public long countByUserId(String userId) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemory::getUserId, userId);
        return chatMemoryMapper.selectCount(wrapper);
    }

    @Override
    public long sumTokensByUserId(String userId) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemory::getUserId, userId).isNotNull(ChatMemory::getTokenCount);
        List<ChatMemory> messages = chatMemoryMapper.selectList(wrapper);
        return messages.stream()
                .mapToLong(m -> {
                    if (m.getTotalTokens() != null && m.getTotalTokens() > 0) {
                        return m.getTotalTokens();
                    }
                    return m.getTokenCount() != null ? m.getTokenCount() : 0;
                })
                .sum();
    }

    @Override
    public long sumTokensByUserIdAndCreateTimeAfter(String userId, LocalDateTime startTime) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMemory::getUserId, userId)
                .isNotNull(ChatMemory::getTokenCount)
                .ge(ChatMemory::getCreateTime, startTime);
        List<ChatMemory> messages = chatMemoryMapper.selectList(wrapper);
        return messages.stream()
                .mapToLong(m -> {
                    if (m.getTotalTokens() != null && m.getTotalTokens() > 0) {
                        return m.getTotalTokens();
                    }
                    return m.getTokenCount() != null ? m.getTokenCount() : 0;
                })
                .sum();
    }

    @Override
    public MemoryPageResult findPage(String sessionId, String userId, String messageRole,
                                     String scopeId, String keyword, int page, int size) {
        LambdaQueryWrapper<ChatMemory> wrapper = buildCriteriaWrapper(sessionId, userId, messageRole, scopeId, keyword);
        wrapper.orderByDesc(ChatMemory::getCreateTime);
        Page<ChatMemory> entityPage = chatMemoryMapper.selectPage(new Page<>(page, size), wrapper);
        List<ChatMemoryRecord> records = entityPage.getRecords().stream()
                .map(this::toModel).collect(Collectors.toList());
        return new MemoryPageResult(page, size, entityPage.getTotal(), records);
    }

    @Override
    public List<ChatMemoryRecord> search(String keyword, String scopeId, int limit) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ChatMemory::getMessageContent, keyword);
        }
        if (scopeId != null && !scopeId.isBlank()) {
            wrapper.eq(ChatMemory::getScopeId, scopeId);
        }
        wrapper.orderByDesc(ChatMemory::getCreateTime);
        wrapper.last("LIMIT " + Math.max(limit, 1));
        return chatMemoryMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public ChatMemoryRecord findById(Long id) {
        ChatMemory entity = chatMemoryMapper.selectById(id);
        return entity == null ? null : toModel(entity);
    }

    @Override
    public void deleteById(Long id) {
        chatMemoryMapper.deleteById(id);
    }

    @Override
    public int deleteBatch(String sessionId, String userId, String scopeId) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        boolean hasCondition = false;
        if (sessionId != null && !sessionId.isBlank()) {
            wrapper.eq(ChatMemory::getSessionId, sessionId);
            hasCondition = true;
        }
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(ChatMemory::getUserId, userId);
            hasCondition = true;
        }
        if (scopeId != null && !scopeId.isBlank()) {
            wrapper.eq(ChatMemory::getScopeId, scopeId);
            hasCondition = true;
        }
        if (!hasCondition) {
            return 0;
        }
        return chatMemoryMapper.delete(wrapper);
    }

    @Override
    public List<Map<String, Object>> statsByRole(String scopeId) {
        QueryWrapper<ChatMemory> qw = new QueryWrapper<>();
        qw.select("message_role AS messageRole, COUNT(*) AS total");
        if (scopeId != null && !scopeId.isBlank()) {
            qw.eq("scope_id", scopeId);
        }
        qw.groupBy("message_role");
        List<Map<String, Object>> raw = chatMemoryMapper.selectMaps(qw);
        List<Map<String, Object>> result = new ArrayList<>(raw.size());
        for (Map<String, Object> row : raw) {
            result.add(normalizeStatRow(row));
        }
        return result;
    }

    private LambdaQueryWrapper<ChatMemory> buildCriteriaWrapper(String sessionId, String userId,
                                                                String messageRole, String scopeId, String keyword) {
        LambdaQueryWrapper<ChatMemory> wrapper = new LambdaQueryWrapper<>();
        if (sessionId != null && !sessionId.isBlank()) {
            wrapper.eq(ChatMemory::getSessionId, sessionId);
        }
        if (userId != null && !userId.isBlank()) {
            wrapper.eq(ChatMemory::getUserId, userId);
        }
        if (messageRole != null && !messageRole.isBlank()) {
            wrapper.eq(ChatMemory::getMessageRole, messageRole);
        }
        if (scopeId != null && !scopeId.isBlank()) {
            wrapper.eq(ChatMemory::getScopeId, scopeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(ChatMemory::getMessageContent, keyword);
        }
        return wrapper;
    }

    private Map<String, Object> normalizeStatRow(Map<String, Object> row) {
        Map<String, Object> normalized = new java.util.HashMap<>();
        normalized.put("messageRole", firstValue(row, "messageRole", "messagerole", "message_role"));
        normalized.put("total", firstValue(row, "total", "TOTAL"));
        return normalized;
    }

    private Object firstValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private ChatMemory toEntity(ChatMemoryRecord model) {
        ChatMemory entity = new ChatMemory();
        entity.setId(model.getId());
        entity.setMessageId(model.getMessageId());
        entity.setSessionId(model.getSessionId());
        entity.setUserId(model.getUserId());
        entity.setScopeId(model.getScopeId());
        entity.setMessageRole(model.getMessageRole());
        entity.setMessageContent(model.getMessageContent());
        entity.setTokenCount(model.getTokenCount());
        entity.setInputTokens(model.getInputTokens());
        entity.setOutputTokens(model.getOutputTokens());
        entity.setTotalTokens(model.getTotalTokens());
        entity.setExecutionTime(model.getExecutionTime());
        entity.setImportanceScore(model.getImportanceScore());
        entity.setBody(model.getBody());
        return entity;
    }

    private ChatMemoryRecord toModel(ChatMemory entity) {
        ChatMemoryRecord model = new ChatMemoryRecord();
        model.setId(entity.getId());
        model.setMessageId(entity.getMessageId());
        model.setSessionId(entity.getSessionId());
        model.setUserId(entity.getUserId());
        model.setMessageRole(entity.getMessageRole());
        model.setMessageContent(entity.getMessageContent());
        model.setTokenCount(entity.getTokenCount());
        model.setInputTokens(entity.getInputTokens());
        model.setOutputTokens(entity.getOutputTokens());
        model.setTotalTokens(entity.getTotalTokens());
        model.setExecutionTime(entity.getExecutionTime());
        model.setImportanceScore(entity.getImportanceScore());
        model.setBody(entity.getBody());
        model.setCreateTime(entity.getCreateTime());
        model.setScopeId(entity.getScopeId());
        return model;
    }
}
