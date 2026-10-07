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
package com.yangqiongai.ai.agent.data.tool.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.agent.data.tool.entity.ToolConfigCategoryEntity;
import com.yangqiongai.ai.agent.data.tool.mapper.ToolConfigCategoryMapper;
import com.yangqiongai.ai.agent.tool.model.ToolConfigCategory;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具配置分类存储
 * @author yangqiong
 */
public class DefaultToolConfigCategoryRepository implements ToolConfigCategoryRepository {

    @Autowired
    private ToolConfigCategoryMapper mapper;

    @Override
    public List<ToolConfigCategory> findAll() {
        LambdaQueryWrapper<ToolConfigCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ToolConfigCategoryEntity::getSortNum)
                .orderByAsc(ToolConfigCategoryEntity::getId);
        return mapper.selectList(wrapper).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<ToolConfigCategory> findByCode(String code) {
        LambdaQueryWrapper<ToolConfigCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ToolConfigCategoryEntity::getCode, code);
        ToolConfigCategoryEntity entity = mapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(entity));
    }

    @Override
    public Set<String> findSubtreeCodes(String categoryCode) {
        Set<String> codes = new HashSet<>();
        List<ToolConfigCategory> all = findAll();
        ToolConfigCategory start = all.stream()
                .filter(item -> categoryCode.equals(item.getCode()))
                .findFirst().orElse(null);
        if (start == null) {
            return codes;
        }
        Deque<ToolConfigCategory> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            ToolConfigCategory current = stack.pop();
            codes.add(current.getCode());
            for (ToolConfigCategory item : all) {
                if (current.getId().equals(item.getParentId())) {
                    stack.push(item);
                }
            }
        }
        return codes;
    }

    @Override
    public void create(ToolConfigCategory category) {
        mapper.insert(toEntity(category));
    }

    @Override
    public void update(ToolConfigCategory category) {
        // 全量更新名称/父节点/排序（parentId可置空表示移为根节点）
        LambdaUpdateWrapper<ToolConfigCategoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ToolConfigCategoryEntity::getId, category.getId())
                .set(ToolConfigCategoryEntity::getName, category.getName())
                .set(ToolConfigCategoryEntity::getParentId, category.getParentId())
                .set(ToolConfigCategoryEntity::getSortNum, category.getSortNum() != null ? category.getSortNum() : 0);
        mapper.update(null, wrapper);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private ToolConfigCategory toDomain(ToolConfigCategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        ToolConfigCategory domain = new ToolConfigCategory();
        domain.setId(entity.getId());
        domain.setCode(entity.getCode());
        domain.setName(entity.getName());
        domain.setParentId(entity.getParentId());
        domain.setSortNum(entity.getSortNum());
        return domain;
    }

    private ToolConfigCategoryEntity toEntity(ToolConfigCategory domain) {
        ToolConfigCategoryEntity entity = new ToolConfigCategoryEntity();
        entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setParentId(domain.getParentId());
        entity.setSortNum(domain.getSortNum() != null ? domain.getSortNum() : 0);
        return entity;
    }
}
