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
import com.yangqiongai.ai.data.capability.entity.CapabilityDefinitionHistoryEntity;
import com.yangqiongai.ai.data.capability.mapper.CapabilityDefinitionHistoryMapper;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistory;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 能力定义版本历史存储
 * @author yangqiong
 */
public class DefaultCapabilityDefinitionHistoryRepository implements CapabilityDefinitionHistoryRepository {

    @Autowired
    private CapabilityDefinitionHistoryMapper mapper;

    @Override
    public void save(CapabilityDefinitionHistory domain) {
        if (domain == null) {
            return;
        }
        mapper.insert(toEntity(domain));
    }

    @Override
    public List<CapabilityDefinitionHistory> findByCode(String code) {
        LambdaQueryWrapper<CapabilityDefinitionHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionHistoryEntity::getCapabilityCode, code);
        wrapper.orderByDesc(CapabilityDefinitionHistoryEntity::getId);
        return mapper.selectList(wrapper).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CapabilityDefinitionHistory> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toDomain(mapper.selectById(id)));
    }

    @Override
    public void deleteByCode(String code) {
        LambdaQueryWrapper<CapabilityDefinitionHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CapabilityDefinitionHistoryEntity::getCapabilityCode, code);
        mapper.delete(wrapper);
    }

    private CapabilityDefinitionHistory toDomain(CapabilityDefinitionHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        CapabilityDefinitionHistory domain = new CapabilityDefinitionHistory();
        domain.setId(entity.getId());
        domain.setCapabilityCode(entity.getCapabilityCode());
        domain.setVersion(entity.getVersion());
        domain.setName(entity.getName());
        domain.setOperation(entity.getOperation());
        domain.setDefinitionJson(entity.getDefinitionJson());
        domain.setCreateTime(entity.getCreateTime());
        return domain;
    }

    private CapabilityDefinitionHistoryEntity toEntity(CapabilityDefinitionHistory domain) {
        CapabilityDefinitionHistoryEntity entity = new CapabilityDefinitionHistoryEntity();
        entity.setCapabilityCode(domain.getCapabilityCode());
        entity.setVersion(domain.getVersion());
        entity.setName(domain.getName());
        entity.setOperation(domain.getOperation());
        entity.setDefinitionJson(domain.getDefinitionJson());
        return entity;
    }
}
