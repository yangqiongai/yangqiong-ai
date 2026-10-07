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
package com.yangqiongai.ai.platform.system.service;

import com.yangqiongai.ai.platform.system.dto.SystemMenuTreeNode;
import com.yangqiongai.ai.platform.system.entity.SystemMenu;
import com.yangqiongai.ai.platform.system.entity.SystemMenuScope;

import java.util.List;

/**
 * 菜单目录管理
 * @author yangqiong
 */
public interface SystemMenuService {

    /**
     * 查询管理树（含隐藏与停用，菜单管理页用）
     * @param appCode
     * @return
     */
    List<SystemMenuTreeNode> tree(String appCode);

    /**
     * 查询当前用户可见菜单树（运行端核心：status→visible→feature_key→scope覆盖→权限过滤）
     * @param appCode
     * @return
     */
    List<SystemMenuTreeNode> myMenus(String appCode);

    /**
     * 创建菜单
     * @param systemMenu
     * @return
     */
    SystemMenu create(SystemMenu systemMenu);

    /**
     * 更新菜单
     * @param id
     * @param systemMenu
     * @return
     */
    SystemMenu update(Long id, SystemMenu systemMenu);

    /**
     * 删除菜单（有子节点或被作用域覆盖引用时拒绝）
     * @param id
     * @return
     */
    void delete(Long id);

    /**
     * 调整同级排序
     * @param id
     * @param direction
     * @return
     */
    void sort(Long id, String direction);

    /**
     * 查询菜单的作用域覆盖配置
     * @param menuId
     * @return
     */
    List<SystemMenuScope> scopes(Long menuId);

    /**
     * 全量替换菜单的作用域覆盖配置
     * @param menuId
     * @param scopes
     * @return
     */
    void saveScopes(Long menuId, List<SystemMenuScope> scopes);
}
