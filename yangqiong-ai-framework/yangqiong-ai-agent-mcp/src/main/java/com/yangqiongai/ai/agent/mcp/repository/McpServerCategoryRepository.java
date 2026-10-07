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
package com.yangqiongai.ai.agent.mcp.repository;

import com.yangqiongai.ai.agent.mcp.model.McpServerCategory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MCP服务分类存储
 * @author yangqiong
 */
public interface McpServerCategoryRepository {

    /**
     * 未分类MCP服务的虚拟分类编码（列表过滤与树计数使用）
     */
    String UNGROUPED_CODE = "__ungrouped__";

    /**
     * 查询全部分类（平铺列表）
     * @return
     */
    List<McpServerCategory> findAll();

    /**
     * 按编码查询分类
     * @param code
     * @return
     */
    Optional<McpServerCategory> findByCode(String code);

    /**
     * 解析分类子树（含自身）的全部节点编码，分类不存在时返回空集
     * @param categoryCode
     * @return
     */
    Set<String> findSubtreeCodes(String categoryCode);

    /**
     * 新增分类
     * @param category
     */
    void create(McpServerCategory category);

    /**
     * 更新分类（名称/父节点/排序）
     * @param category
     */
    void update(McpServerCategory category);

    /**
     * 按主键删除分类
     * @param id
     */
    void deleteById(Long id);
}
