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
package com.yangqiongai.ai.memory.agentmemory.repository;

import com.yangqiongai.ai.memory.agentmemory.model.OrgContextInfo;

import java.util.List;

/**
 * 组织上下文记忆
 * @author yangqiong
 */
public interface OrgContextRepository {

    /**
     * 插入上下文
     * @param context
     * @return
     */
    Long insert(OrgContextInfo context);

    /**
     * 更新上下文
     * @param context
     */
    void update(OrgContextInfo context);

    /**
     * 根据ID查询
     * @param id
     * @return
     */
    OrgContextInfo selectById(Long id);

    /**
     * 根据ID删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 按类型查询启用上下文
     * @param contextType
     * @return
     */
    List<OrgContextInfo> findEnabledByType(String contextType);

    /**
     * 按别名精确查询启用上下文
     * @param alias
     * @return
     */
    List<OrgContextInfo> findEnabledByAlias(String alias);

    /**
     * 按术语与类型查询
     * @param contextType
     * @param term
     * @return
     */
    List<OrgContextInfo> findByTypeAndTerm(String contextType, String term);

    /**
     * 查询全部上下文
     * @return
     */
    List<OrgContextInfo> findAll();

    /**
     * 按类型计数
     * @param contextType
     * @return
     */
    long countByType(String contextType);

    /**
     * 统计冲突条数
     * @return
     */
    long countConflicts();
}
