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
package com.yangqiongai.ai.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.bss.scope.auth.AuthContext;
import com.yangqiongai.ai.platform.bss.scope.auth.AuthContextHolder;
import com.yangqiongai.ai.platform.system.dto.SystemMenuTreeNode;
import com.yangqiongai.ai.platform.system.entity.SystemMenu;
import com.yangqiongai.ai.platform.system.entity.SystemMenuScope;
import com.yangqiongai.ai.platform.system.mapper.SystemMenuMapper;
import com.yangqiongai.ai.platform.system.mapper.SystemMenuScopeMapper;
import com.yangqiongai.ai.platform.system.service.SystemMenuService;
import com.yangqiongai.ai.platform.system.spi.MenuAccessFilter;
import com.yangqiongai.ai.platform.system.spi.MenuFeatureGate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单目录管理
 * @author yangqiong
 */
@Service
public class SystemMenuServiceImpl implements SystemMenuService {

    /**
     * 启用状态
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 全局显示
     */
    private static final int VISIBLE_ON = 1;

    /**
     * 全局隐藏
     */
    private static final int VISIBLE_OFF = 0;

    /**
     * 分组类型
     */
    private static final String MENU_TYPE_GROUP = "GROUP";

    /**
     * 页面类型
     */
    private static final String MENU_TYPE_PAGE = "PAGE";

    /**
     * 外链类型
     */
    private static final String MENU_TYPE_LINK = "LINK";

    /**
     * 租户作用域覆盖维度
     */
    private static final String SCOPE_TYPE_SCOPE = "SCOPE";

    /**
     * 用户覆盖维度
     */
    private static final String SCOPE_TYPE_USER = "USER";

    /**
     * 平台级作用域
     */
    private static final String PLATFORM_SCOPE = "default";

    /**
     * 排序方向：上移
     */
    private static final String SORT_UP = "UP";

    /**
     * 排序方向：下移
     */
    private static final String SORT_DOWN = "DOWN";

    /**
     * 排序方向：置顶
     */
    private static final String SORT_TOP = "TOP";

    @Autowired
    private SystemMenuMapper systemMenuMapper;

    @Autowired
    private SystemMenuScopeMapper systemMenuScopeMapper;

    @Autowired
    private MenuAccessFilter menuAccessFilter;

    @Autowired
    private MenuFeatureGate menuFeatureGate;

    @Override
    public List<SystemMenuTreeNode> tree(String appCode) {
        requireAppCode(appCode);
        List<SystemMenu> menus = selectByApp(appCode);
        return buildTree(menus, false);
    }

    @Override
    public List<SystemMenuTreeNode> myMenus(String appCode) {
        requireAppCode(appCode);
        AuthContext context = AuthContextHolder.get();
        // 拼装管线：status → feature_key → visible覆盖 → 权限过滤 → 构树
        List<SystemMenu> menus = new ArrayList<>();
        for (SystemMenu menu : selectByApp(appCode)) {
            if (menu.getStatus() != null && menu.getStatus() == STATUS_ENABLED) {
                menus.add(menu);
            }
        }
        menus = filterByFeatureKey(menus);
        Map<Long, Integer> overrides = loadOverrides(menus, context);
        menus = applyVisibleOverride(menus, overrides);
        menus = menuAccessFilter.filter(menus, context);
        return buildTree(menus, true);
    }

    @Override
    public SystemMenu create(SystemMenu systemMenu) {
        validateMenu(systemMenu, null);
        systemMenu.setId(null);
        systemMenu.setScopeId(PLATFORM_SCOPE);
        if (systemMenu.getStatus() == null) {
            systemMenu.setStatus(STATUS_ENABLED);
        }
        if (systemMenu.getVisible() == null) {
            systemMenu.setVisible(VISIBLE_ON);
        }
        if (systemMenu.getSortOrder() == null) {
            systemMenu.setSortOrder(nextSortOrder(systemMenu.getParentId(), systemMenu.getAppCode()));
        }
        systemMenuMapper.insert(systemMenu);
        return systemMenu;
    }

    @Override
    public SystemMenu update(Long id, SystemMenu systemMenu) {
        SystemMenu existMenu = requireMenu(id);
        validateMenu(systemMenu, id);
        systemMenu.setId(id);
        // 不允许通过更新接口调整归属端、作用域与删除标记
        systemMenu.setAppCode(null);
        systemMenu.setScopeId(null);
        systemMenu.setDeleted(null);
        if (systemMenu.getSortOrder() == null) {
            systemMenu.setSortOrder(existMenu.getSortOrder());
        }
        systemMenuMapper.updateById(systemMenu);
        return systemMenuMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        requireMenu(id);
        LambdaQueryWrapper<SystemMenu> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(SystemMenu::getParentId, id);
        if (systemMenuMapper.selectCount(childWrapper) > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "存在子菜单，不允许删除: " + id);
        }
        LambdaQueryWrapper<SystemMenuScope> scopeWrapper = new LambdaQueryWrapper<>();
        scopeWrapper.eq(SystemMenuScope::getMenuId, id);
        if (systemMenuScopeMapper.selectCount(scopeWrapper) > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "存在作用域覆盖配置，请先清理: " + id);
        }
        systemMenuMapper.deleteById(id);
    }

    @Override
    public void sort(Long id, String direction) {
        SystemMenu menu = requireMenu(id);
        if (!SORT_UP.equals(direction) && !SORT_DOWN.equals(direction) && !SORT_TOP.equals(direction)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "非法排序方向: " + direction);
        }
        List<SystemMenu> siblings = selectByApp(menu.getAppCode()).stream()
                .filter(m -> m.getParentId() != null && m.getParentId().equals(menu.getParentId()))
                .sorted(Comparator.comparing(SystemMenu::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SystemMenu::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int index = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(id)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        Integer targetOrder;
        if (SORT_TOP.equals(direction)) {
            if (index == 0) {
                return;
            }
            targetOrder = siblings.get(0).getSortOrder() - 1;
        } else {
            int neighbor = SORT_UP.equals(direction) ? index - 1 : index + 1;
            if (neighbor < 0 || neighbor >= siblings.size()) {
                return;
            }
            targetOrder = siblings.get(neighbor).getSortOrder();
            SystemMenu neighborUpdate = new SystemMenu();
            neighborUpdate.setId(siblings.get(neighbor).getId());
            neighborUpdate.setSortOrder(menu.getSortOrder());
            systemMenuMapper.updateById(neighborUpdate);
        }
        SystemMenu update = new SystemMenu();
        update.setId(id);
        update.setSortOrder(targetOrder);
        systemMenuMapper.updateById(update);
    }

    @Override
    public List<SystemMenuScope> scopes(Long menuId) {
        requireMenu(menuId);
        LambdaQueryWrapper<SystemMenuScope> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemMenuScope::getMenuId, menuId);
        wrapper.orderByAsc(SystemMenuScope::getScopeType, SystemMenuScope::getScopeId);
        return systemMenuScopeMapper.selectList(wrapper);
    }

    @Override
    public void saveScopes(Long menuId, List<SystemMenuScope> scopes) {
        requireMenu(menuId);
        if (scopes == null) {
            scopes = new ArrayList<>();
        }
        for (SystemMenuScope scope : scopes) {
            if (!SCOPE_TYPE_SCOPE.equals(scope.getScopeType()) && !SCOPE_TYPE_USER.equals(scope.getScopeType())) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "非法覆盖维度: " + scope.getScopeType());
            }
            if (!StringUtils.hasText(scope.getScopeId())) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "覆盖作用域ID不能为空");
            }
            if (scope.getVisible() == null || (scope.getVisible() != VISIBLE_ON && scope.getVisible() != VISIBLE_OFF)) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "非法覆盖显隐值: " + scope.getVisible());
            }
        }
        LambdaQueryWrapper<SystemMenuScope> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemMenuScope::getMenuId, menuId);
        systemMenuScopeMapper.delete(wrapper);
        for (SystemMenuScope scope : scopes) {
            scope.setId(null);
            scope.setMenuId(menuId);
            systemMenuScopeMapper.insert(scope);
        }
    }

    /**
     * 按端查询全部未删除菜单并按显示顺序排序
     * @param appCode
     * @return
     */
    private List<SystemMenu> selectByApp(String appCode) {
        LambdaQueryWrapper<SystemMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemMenu::getAppCode, appCode);
        // 复制为可变列表避免对来源不可变集合排序失败
        List<SystemMenu> menus = new ArrayList<>(systemMenuMapper.selectList(wrapper));
        menus.sort(Comparator
                .comparing(SystemMenu::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SystemMenu::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return menus;
    }

    /**
     * 过滤特性开关关闭的菜单（未绑定feature_key或开关判定通过则保留）
     * @param menus
     * @return
     */
    private List<SystemMenu> filterByFeatureKey(List<SystemMenu> menus) {
        List<SystemMenu> result = new ArrayList<>();
        for (SystemMenu menu : menus) {
            if (!StringUtils.hasText(menu.getFeatureKey()) || menuFeatureGate.enabled(menu.getFeatureKey())) {
                result.add(menu);
            }
        }
        return result;
    }

    /**
     * 加载当前用户命中的显隐覆盖（USER优先于SCOPE）
     * @param menus
     * @param context
     * @return
     */
    private Map<Long, Integer> loadOverrides(List<SystemMenu> menus, AuthContext context) {
        if (menus.isEmpty()) {
            return Map.of();
        }
        String scopeId = context.getScopeId();
        String userId = context.getUserId();
        if (!StringUtils.hasText(scopeId) && !StringUtils.hasText(userId)) {
            return Map.of();
        }
        List<Long> menuIds = menus.stream().map(SystemMenu::getId).toList();
        LambdaQueryWrapper<SystemMenuScope> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SystemMenuScope::getMenuId, menuIds);
        // OR 组合收敛在同一嵌套段内，保证 menu_id 约束不因 OR 优先级被绕过
        wrapper.and(w -> {
            if (StringUtils.hasText(scopeId)) {
                w.or(a -> a.eq(SystemMenuScope::getScopeType, SCOPE_TYPE_SCOPE).eq(SystemMenuScope::getScopeId, scopeId));
            }
            if (StringUtils.hasText(userId)) {
                w.or(b -> b.eq(SystemMenuScope::getScopeType, SCOPE_TYPE_USER).eq(SystemMenuScope::getScopeId, userId));
            }
        });
        Map<Long, Integer> overrides = new HashMap<>();
        // 同一次查询内先落SCOPE覆盖，USER覆盖后写实现优先级覆盖
        for (SystemMenuScope override : systemMenuScopeMapper.selectList(wrapper)) {
            if (SCOPE_TYPE_USER.equals(override.getScopeType())) {
                continue;
            }
            overrides.put(override.getMenuId(), override.getVisible());
        }
        for (SystemMenuScope override : systemMenuScopeMapper.selectList(wrapper)) {
            if (SCOPE_TYPE_USER.equals(override.getScopeType())) {
                overrides.put(override.getMenuId(), override.getVisible());
            }
        }
        return overrides;
    }

    /**
     * 叠加显隐覆盖：覆盖值优先于全局visible
     * @param menus
     * @param overrides
     * @return
     */
    private List<SystemMenu> applyVisibleOverride(List<SystemMenu> menus, Map<Long, Integer> overrides) {
        if (overrides.isEmpty()) {
            // 无覆盖时仅保留全局显示的菜单
            List<SystemMenu> result = new ArrayList<>();
            for (SystemMenu menu : menus) {
                if (menu.getVisible() == null || menu.getVisible() == VISIBLE_ON) {
                    result.add(menu);
                }
            }
            return result;
        }
        List<SystemMenu> result = new ArrayList<>();
        for (SystemMenu menu : menus) {
            Integer override = overrides.get(menu.getId());
            if (override != null) {
                if (override == VISIBLE_ON) {
                    result.add(menu);
                }
                continue;
            }
            if (menu.getVisible() == null || menu.getVisible() == VISIBLE_ON) {
                result.add(menu);
            }
        }
        return result;
    }

    /**
     * 构建菜单树；管理树保留空分组，运行端剔除子级全空的分组
     * @param menus
     * @param dropEmptyGroup
     * @return
     */
    private List<SystemMenuTreeNode> buildTree(List<SystemMenu> menus, boolean dropEmptyGroup) {
        Map<Long, List<SystemMenu>> byParent = new LinkedHashMap<>();
        Set<Long> presentIds = menus.stream().map(SystemMenu::getId).collect(java.util.stream.Collectors.toSet());
        for (SystemMenu menu : menus) {
            byParent.computeIfAbsent(menu.getParentId() == null ? 0L : menu.getParentId(), k -> new ArrayList<>()).add(menu);
        }
        return assembleChildren(byParent, presentIds, 0L, dropEmptyGroup);
    }

    /**
     * 递归装配树节点
     * @param byParent
     * @param presentIds
     * @param parentId
     * @param dropEmptyGroup
     * @return
     */
    private List<SystemMenuTreeNode> assembleChildren(Map<Long, List<SystemMenu>> byParent, Set<Long> presentIds,
                                                      Long parentId, boolean dropEmptyGroup) {
        List<SystemMenuTreeNode> nodes = new ArrayList<>();
        for (SystemMenu menu : byParent.getOrDefault(parentId, new ArrayList<>())) {
            // 父节点被过滤时子节点一并隐藏
            if (menu.getParentId() != null && menu.getParentId() != 0L && !presentIds.contains(menu.getParentId())) {
                continue;
            }
            List<SystemMenuTreeNode> children = assembleChildren(byParent, presentIds, menu.getId(), dropEmptyGroup);
            if (dropEmptyGroup && MENU_TYPE_GROUP.equals(menu.getMenuType()) && children.isEmpty()) {
                continue;
            }
            SystemMenuTreeNode node = new SystemMenuTreeNode(menu);
            node.setChildren(children);
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 校验菜单基础字段与唯一性
     * @param systemMenu
     * @param excludeId
     */
    private void validateMenu(SystemMenu systemMenu, Long excludeId) {
        if (!StringUtils.hasText(systemMenu.getAppCode())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "端标识不能为空");
        }
        if (!StringUtils.hasText(systemMenu.getMenuKey())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "菜单语义键不能为空");
        }
        if (!StringUtils.hasText(systemMenu.getName())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "菜单名称不能为空");
        }
        String menuType = systemMenu.getMenuType();
        if (!MENU_TYPE_GROUP.equals(menuType) && !MENU_TYPE_PAGE.equals(menuType) && !MENU_TYPE_LINK.equals(menuType)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "非法菜单类型: " + menuType);
        }
        if ((MENU_TYPE_PAGE.equals(menuType) || MENU_TYPE_LINK.equals(menuType)) && !StringUtils.hasText(systemMenu.getPath())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "页面与外链菜单必须配置路由路径");
        }
        Long parentId = systemMenu.getParentId() == null ? 0L : systemMenu.getParentId();
        if (parentId != 0L) {
            SystemMenu parent = systemMenuMapper.selectById(parentId);
            if (parent == null || !MENU_TYPE_GROUP.equals(parent.getMenuType())) {
                throw new AiException(AiErrorCode.PARAM_ERROR, "上级菜单必须是分组: " + parentId);
            }
        }
        // app_code 内语义键唯一
        LambdaQueryWrapper<SystemMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemMenu::getAppCode, systemMenu.getAppCode());
        wrapper.eq(SystemMenu::getMenuKey, systemMenu.getMenuKey());
        if (excludeId != null) {
            wrapper.ne(SystemMenu::getId, excludeId);
        }
        if (systemMenuMapper.selectCount(wrapper) > 0) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "菜单语义键已存在: " + systemMenu.getMenuKey());
        }
    }

    /**
     * 计算同级下一个显示顺序
     * @param parentId
     * @param appCode
     * @return
     */
    private int nextSortOrder(Long parentId, String appCode) {
        int max = 0;
        for (SystemMenu menu : selectByApp(appCode)) {
            if (menu.getParentId() != null && menu.getParentId().equals(parentId)
                    && menu.getSortOrder() != null && menu.getSortOrder() > max) {
                max = menu.getSortOrder();
            }
        }
        return max + 1;
    }

    /**
     * 加载菜单，不存在时抛出业务异常
     * @param id
     * @return
     */
    private SystemMenu requireMenu(Long id) {
        SystemMenu menu = systemMenuMapper.selectById(id);
        if (menu == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, "菜单不存在: " + id);
        }
        return menu;
    }

    /**
     * 校验端标识非空
     * @param appCode
     */
    private void requireAppCode(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "端标识不能为空");
        }
    }
}
