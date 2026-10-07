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
import com.yangqiongai.ai.data.capability.entity.CapabilityDefinitionEntity;
import com.yangqiongai.ai.data.capability.mapper.CapabilityDefinitionMapper;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinition;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 能力定义存储
 * @author yangqiong
 */
public class DefaultCapabilityDefinitionRepository implements CapabilityDefinitionRepository {

    @Autowired
    private CapabilityDefinitionMapper mapper;

    @Override
    public List<CapabilityDefinition> findAll() {
        List<CapabilityDefinitionEntity> entities = mapper.selectList(null);
        return entities.stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<CapabilityDefinition> findByCode(String code) {
        LambdaQueryWrapper<CapabilityDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionEntity::getCode, code);
        CapabilityDefinitionEntity entity = mapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(entity));
    }

    @Override
    public void create(CapabilityDefinition domain) {
        if (domain == null) {
            return;
        }
        CapabilityDefinitionEntity entity = toEntity(domain);
        mapper.insert(entity);
    }

    @Override
    public void override(String code, Map<String, Object> overrideFields) {
        if (code == null || overrideFields == null || overrideFields.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<CapabilityDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionEntity::getCode, code);
        CapabilityDefinitionEntity entity = mapper.selectOne(wrapper);
        if (entity == null) {
            entity = new CapabilityDefinitionEntity();
            entity.setCode(code);
            entity.setDefinitionJson(toJsonString(overrideFields));
            entity.setEnabled(true);
            mapper.insert(entity);
        } else {
            entity.setDefinitionJson(toJsonString(overrideFields));
            mapper.updateById(entity);
        }
    }

    @Override
    public void disable(String code) {
        LambdaQueryWrapper<CapabilityDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionEntity::getCode, code);
        CapabilityDefinitionEntity entity = mapper.selectOne(wrapper);
        if (entity == null) {
            entity = new CapabilityDefinitionEntity();
            entity.setCode(code);
            entity.setEnabled(false);
            mapper.insert(entity);
        } else {
            entity.setEnabled(false);
            mapper.updateById(entity);
        }
    }

    @Override
    public void enable(String code) {
        LambdaQueryWrapper<CapabilityDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionEntity::getCode, code);
        CapabilityDefinitionEntity entity = mapper.selectOne(wrapper);
        if (entity != null) {
            entity.setEnabled(true);
            mapper.updateById(entity);
        }
    }

    @Override
    public void delete(String code) {
        LambdaQueryWrapper<CapabilityDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionEntity::getCode, code);
        mapper.delete(wrapper);
    }

    private CapabilityDefinition toDomain(CapabilityDefinitionEntity entity) {
        if (entity == null) {
            return null;
        }
        CapabilityDefinition domain =
                new CapabilityDefinition();
        domain.setId(entity.getId());
        domain.setCode(entity.getCode());
        domain.setName(entity.getName());
        domain.setDescription(entity.getDescription());
        domain.setDefinitionJson(entity.getDefinitionJson());
        domain.setInputSchema(entity.getInputSchema());
        domain.setOutputSchema(entity.getOutputSchema());
        domain.setPromptTemplate(entity.getPromptTemplate());
        domain.setEnabled(entity.getEnabled());
        return domain;
    }

    private CapabilityDefinitionEntity toEntity(CapabilityDefinition domain) {
        CapabilityDefinitionEntity entity = new CapabilityDefinitionEntity();
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setDefinitionJson(domain.getDefinitionJson());
        entity.setInputSchema(domain.getInputSchema());
        entity.setOutputSchema(domain.getOutputSchema());
        entity.setPromptTemplate(domain.getPromptTemplate());
        entity.setEnabled(domain.getEnabled() != null ? domain.getEnabled() : true);
        return entity;
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return Collections.singletonMap("error", "序列化失败").toString();
        }
    }
}