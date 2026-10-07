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
package com.yangqiongai.ai.agent.tool.repository;

import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;

import java.util.List;
import java.util.Set;

/**
 * 工具配置仓库
 * @author yangqiong
 */
public interface ToolConfigRepository {

    /**
     * 查询所有工具配置
     * @return
     */
    List<ToolConfigInfo> listAll();

    /**
     * 按状态查询工具配置
     * @param status
     * @return
     */
    List<ToolConfigInfo> listByStatus(Integer status);

    /**
     * 按toolCode查询工具配置
     * @param toolCode
     * @return
     */
    ToolConfigInfo getByToolCode(String toolCode);

    /**
     * 保存工具配置
     * @param config
     */
    void save(ToolConfigInfo config);

    /**
     * 按ID更新
     * @param config
     */
    void updateById(ToolConfigInfo config);

    /**
     * 切换工具启用/禁用状态
     * @param toolCode
     * @return
     */
    boolean toggleStatus(String toolCode);

    /**
     * 按关键字分页查询工具配置（匹配toolCode/toolName，支持按分类子树过滤）
     * @param keyword
     * @param categoryCode 分类节点编码（空为不过滤，__ungrouped__为未分类）
     * @param subtreeCodes 分类子树编码集合
     * @param offset
     * @param limit
     * @return
     */
    List<ToolConfigInfo> searchByKeyword(String keyword, String categoryCode, Set<String> subtreeCodes, int offset, int limit);

    /**
     * 统计关键字匹配的工具配置总数（支持按分类子树过滤）
     * @param keyword
     * @param categoryCode 分类节点编码（空为不过滤，__ungrouped__为未分类）
     * @param subtreeCodes 分类子树编码集合
     * @return
     */
    long countByKeyword(String keyword, String categoryCode, Set<String> subtreeCodes);

    /**
     * 按toolCode删除工具配置
     * @param toolCode
     * @return
     */
    boolean deleteByToolCode(String toolCode);
}
