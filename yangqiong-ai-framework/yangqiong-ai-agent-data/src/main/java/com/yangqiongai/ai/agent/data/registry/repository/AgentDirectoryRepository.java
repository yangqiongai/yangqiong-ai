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

import com.yangqiongai.ai.agent.data.registry.entity.AgentDirectoryEntity;

import java.util.List;
import java.util.Optional;

/**
 * 智能体目录存储
 * @author yangqiong
 */
public interface AgentDirectoryRepository {

    /**
     * 未分类智能体的虚拟目录编码（树计数使用）
     */
    String UNGROUPED_CODE = "__ungrouped__";

    /**
     * 查询全部目录（平铺列表）
     * @return
     */
    List<AgentDirectoryEntity> findAll();

    /**
     * 按编码查询目录
     * @param code
     * @return
     */
    Optional<AgentDirectoryEntity> findByCode(String code);

    /**
     * 新增目录
     * @param directory
     */
    void create(AgentDirectoryEntity directory);

    /**
     * 更新目录（名称/父节点/排序）
     * @param directory
     */
    void update(AgentDirectoryEntity directory);

    /**
     * 按主键删除目录
     * @param id
     */
    void deleteById(Long id);
}
