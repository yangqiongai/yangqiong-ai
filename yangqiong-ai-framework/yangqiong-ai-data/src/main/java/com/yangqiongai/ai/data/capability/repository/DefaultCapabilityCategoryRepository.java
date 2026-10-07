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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.data.capability.entity.CapabilityCategoryEntity;
import com.yangqiongai.ai.data.capability.mapper.CapabilityCategoryMapper;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategory;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 能力分类存储
 * @author yangqiong
 */
public class DefaultCapabilityCategoryRepository implements CapabilityCategoryRepository {

    @Autowired
    private CapabilityCategoryMapper mapper;

    @Override
    public List<CapabilityCategory> findAll() {
        LambdaQueryWrapper<CapabilityCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(CapabilityCategoryEntity::getSortNum)
                .orderByAsc(CapabilityCategoryEntity::getId);
        return mapper.selectList(wrapper).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<CapabilityCategory> findByCode(String code) {
        LambdaQueryWrapper<CapabilityCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityCategoryEntity::getCode, code);
        CapabilityCategoryEntity entity = mapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(entity));
    }

    @Override
    public void create(CapabilityCategory category) {
        mapper.insert(toEntity(category));
    }

    @Override
    public void update(CapabilityCategory category) {
        // 全量更新名称/父节点/排序（parentId可置空表示移为根节点）
        LambdaUpdateWrapper<CapabilityCategoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CapabilityCategoryEntity::getId, category.getId())
                .set(CapabilityCategoryEntity::getName, category.getName())
                .set(CapabilityCategoryEntity::getParentId, category.getParentId())
                .set(CapabilityCategoryEntity::getSortNum, category.getSortNum() != null ? category.getSortNum() : 0);
        mapper.update(null, wrapper);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private CapabilityCategory toDomain(CapabilityCategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        CapabilityCategory domain = new CapabilityCategory();
        domain.setId(entity.getId());
        domain.setCode(entity.getCode());
        domain.setName(entity.getName());
        domain.setParentId(entity.getParentId());
        domain.setSortNum(entity.getSortNum());
        return domain;
    }

    private CapabilityCategoryEntity toEntity(CapabilityCategory domain) {
        CapabilityCategoryEntity entity = new CapabilityCategoryEntity();
        entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setParentId(domain.getParentId());
        entity.setSortNum(domain.getSortNum() != null ? domain.getSortNum() : 0);
        return entity;
    }
}
