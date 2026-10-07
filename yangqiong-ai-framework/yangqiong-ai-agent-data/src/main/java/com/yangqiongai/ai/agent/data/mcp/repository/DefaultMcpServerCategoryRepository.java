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
package com.yangqiongai.ai.agent.data.mcp.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.agent.data.mcp.entity.McpServerCategoryEntity;
import com.yangqiongai.ai.agent.data.mcp.mapper.McpServerCategoryMapper;
import com.yangqiongai.ai.agent.mcp.model.McpServerCategory;
import com.yangqiongai.ai.agent.mcp.repository.McpServerCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP服务分类存储
 * @author yangqiong
 */
public class DefaultMcpServerCategoryRepository implements McpServerCategoryRepository {

    @Autowired
    private McpServerCategoryMapper mapper;

    @Override
    public List<McpServerCategory> findAll() {
        LambdaQueryWrapper<McpServerCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(McpServerCategoryEntity::getSortNum)
                .orderByAsc(McpServerCategoryEntity::getId);
        return mapper.selectList(wrapper).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<McpServerCategory> findByCode(String code) {
        LambdaQueryWrapper<McpServerCategoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpServerCategoryEntity::getCode, code);
        McpServerCategoryEntity entity = mapper.selectOne(wrapper);
        return Optional.ofNullable(toDomain(entity));
    }

    @Override
    public Set<String> findSubtreeCodes(String categoryCode) {
        Set<String> codes = new HashSet<>();
        List<McpServerCategory> all = findAll();
        McpServerCategory start = all.stream()
                .filter(item -> categoryCode.equals(item.getCode()))
                .findFirst().orElse(null);
        if (start == null) {
            return codes;
        }
        Deque<McpServerCategory> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            McpServerCategory current = stack.pop();
            codes.add(current.getCode());
            for (McpServerCategory item : all) {
                if (current.getId().equals(item.getParentId())) {
                    stack.push(item);
                }
            }
        }
        return codes;
    }

    @Override
    public void create(McpServerCategory category) {
        mapper.insert(toEntity(category));
    }

    @Override
    public void update(McpServerCategory category) {
        // 全量更新名称/父节点/排序（parentId可置空表示移为根节点）
        LambdaUpdateWrapper<McpServerCategoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(McpServerCategoryEntity::getId, category.getId())
                .set(McpServerCategoryEntity::getName, category.getName())
                .set(McpServerCategoryEntity::getParentId, category.getParentId())
                .set(McpServerCategoryEntity::getSortNum, category.getSortNum() != null ? category.getSortNum() : 0);
        mapper.update(null, wrapper);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private McpServerCategory toDomain(McpServerCategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        McpServerCategory domain = new McpServerCategory();
        domain.setId(entity.getId());
        domain.setCode(entity.getCode());
        domain.setName(entity.getName());
        domain.setParentId(entity.getParentId());
        domain.setSortNum(entity.getSortNum());
        return domain;
    }

    private McpServerCategoryEntity toEntity(McpServerCategory domain) {
        McpServerCategoryEntity entity = new McpServerCategoryEntity();
        entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setParentId(domain.getParentId());
        entity.setSortNum(domain.getSortNum() != null ? domain.getSortNum() : 0);
        return entity;
    }
}
