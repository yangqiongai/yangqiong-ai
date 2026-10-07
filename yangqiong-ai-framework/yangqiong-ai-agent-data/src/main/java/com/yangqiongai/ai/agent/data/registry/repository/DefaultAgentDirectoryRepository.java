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
package com.yangqiongai.ai.agent.data.registry.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDirectoryEntity;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentDirectoryMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

/**
 * 智能体目录存储
 * @author yangqiong
 */
public class DefaultAgentDirectoryRepository implements AgentDirectoryRepository {

    @Autowired
    private AgentDirectoryMapper mapper;

    @Override
    public List<AgentDirectoryEntity> findAll() {
        LambdaQueryWrapper<AgentDirectoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AgentDirectoryEntity::getSortNum)
                .orderByAsc(AgentDirectoryEntity::getId);
        return mapper.selectList(wrapper);
    }

    @Override
    public Optional<AgentDirectoryEntity> findByCode(String code) {
        LambdaQueryWrapper<AgentDirectoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentDirectoryEntity::getCode, code);
        return Optional.ofNullable(mapper.selectOne(wrapper));
    }

    @Override
    public void create(AgentDirectoryEntity directory) {
        mapper.insert(directory);
    }

    @Override
    public void update(AgentDirectoryEntity directory) {
        // 全量更新名称/父节点/排序（parentId可置空表示移为根节点）
        LambdaUpdateWrapper<AgentDirectoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentDirectoryEntity::getId, directory.getId())
                .set(AgentDirectoryEntity::getName, directory.getName())
                .set(AgentDirectoryEntity::getParentId, directory.getParentId())
                .set(AgentDirectoryEntity::getSortNum, directory.getSortNum() != null ? directory.getSortNum() : 0);
        mapper.update(null, wrapper);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }
}
