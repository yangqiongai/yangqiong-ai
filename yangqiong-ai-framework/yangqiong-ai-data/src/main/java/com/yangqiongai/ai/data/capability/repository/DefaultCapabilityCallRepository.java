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
package com.yangqiongai.ai.data.capability.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.data.capability.entity.CapabilityCallRecordEntity;
import com.yangqiongai.ai.data.capability.mapper.CapabilityCallRecordMapper;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRecord;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 能力调用仓储
 * @author yangqiong
 */
public class DefaultCapabilityCallRepository implements CapabilityCallRepository {

    @Autowired
    private CapabilityCallRecordMapper mapper;

    @Override
    public void save(CapabilityCallRecord record) {
        if (record == null || record.getId() == null) {
            return;
        }
        CapabilityCallRecordEntity entity = toEntity(record);
        LambdaQueryWrapper<CapabilityCallRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityCallRecordEntity::getCallId, record.getId());
        CapabilityCallRecordEntity existing = mapper.selectOne(wrapper);
        if (existing != null) {
            entity.setId(existing.getId());
            mapper.updateById(entity);
        } else {
            mapper.insert(entity);
        }
    }

    @Override
    public CapabilityCallRecord findById(String id) {
        LambdaQueryWrapper<CapabilityCallRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityCallRecordEntity::getCallId, id);
        CapabilityCallRecordEntity entity = mapper.selectOne(wrapper);
        return toDomain(entity);
    }

    @Override
    public List<CapabilityCallRecord> findByCapability(String capability) {
        LambdaQueryWrapper<CapabilityCallRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityCallRecordEntity::getCapability, capability);
        List<CapabilityCallRecordEntity> entities = mapper.selectList(wrapper);
        return entities.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<CapabilityCallRecord> findAll() {
        List<CapabilityCallRecordEntity> entities = mapper.selectList(null);
        return entities.stream().map(this::toDomain).collect(Collectors.toList());
    }

    private CapabilityCallRecord toDomain(CapabilityCallRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        CapabilityCallRecord record = new CapabilityCallRecord();
        record.setId(entity.getCallId());
        record.setCapability(entity.getCapability());
        record.setCaller(entity.getCaller());
        record.setScopeId(entity.getScopeId());
        record.setDedupKey(entity.getDedupKey());
        record.setRequestFingerprint(entity.getRequestFingerprint());
        record.setInput(fromJson(entity.getRequestBody()));
        record.setOutput(fromJson(entity.getResponseBody()));
        record.setStatus(entity.getStatus());
        record.setDurationMillis(entity.getDurationMillis() != null ? entity.getDurationMillis() : 0);
        record.setErrorMessage(entity.getErrorMessage());
        record.setStartedAt(parseInstant(entity.getStartedAt()));
        record.setFinishedAt(parseInstant(entity.getFinishedAt()));
        return record;
    }

    private CapabilityCallRecordEntity toEntity(CapabilityCallRecord record) {
        CapabilityCallRecordEntity entity = new CapabilityCallRecordEntity();
        entity.setCallId(record.getId());
        entity.setCapability(record.getCapability());
        entity.setCaller(record.getCaller());
        entity.setScopeId(record.getScopeId());
        entity.setDedupKey(record.getDedupKey());
        entity.setRequestFingerprint(record.getRequestFingerprint());
        entity.setRequestBody(toJsonString(record.getInput()));
        entity.setResponseBody(toJsonString(record.getOutput()));
        entity.setStatus(record.getStatus());
        entity.setDurationMillis(record.getDurationMillis());
        entity.setErrorMessage(record.getErrorMessage());
        entity.setStartedAt(toIsoString(record.getStartedAt()));
        entity.setFinishedAt(toIsoString(record.getFinishedAt()));
        return entity;
    }

    /**
     * 序列化为JSON文本（空值返回null，失败兜底原字符串）
     * @param value
     * @return
     */
    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 反序列化JSON文本为对象（空值返回null，失败兜底原文本）
     * @param json
     * @return
     */
    private Object fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 时间转ISO字符串
     * @param instant
     * @return
     */
    private String toIsoString(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    /**
     * ISO字符串转时间（非法格式返回null）
     * @param text
     * @return
     */
    private Instant parseInstant(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            return null;
        }
    }
}