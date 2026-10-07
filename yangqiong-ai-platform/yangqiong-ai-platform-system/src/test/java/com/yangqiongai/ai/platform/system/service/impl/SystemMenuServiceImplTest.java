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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.bss.scope.auth.AuthContext;
import com.yangqiongai.ai.platform.bss.scope.auth.AuthContextHolder;
import com.yangqiongai.ai.platform.system.dto.SystemMenuTreeNode;
import com.yangqiongai.ai.platform.system.entity.SystemMenu;
import com.yangqiongai.ai.platform.system.entity.SystemMenuScope;
import com.yangqiongai.ai.platform.system.mapper.SystemMenuMapper;
import com.yangqiongai.ai.platform.system.mapper.SystemMenuScopeMapper;
import com.yangqiongai.ai.platform.system.spi.EnvironmentMenuFeatureGate;
import com.yangqiongai.ai.platform.system.spi.MenuAccessFilter;
import com.yangqiongai.ai.platform.system.spi.PermitAllMenuAccessFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单目录管理单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class SystemMenuServiceImplTest {

    @Mock
    private SystemMenuMapper systemMenuMapper;

    @Mock
    private SystemMenuScopeMapper systemMenuScopeMapper;

    @Mock
    private MenuAccessFilter menuAccessFilter;

    private MockEnvironment environment;

    private SystemMenuServiceImpl systemMenuService;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        systemMenuService = new SystemMenuServiceImpl();
        ReflectionTestUtils.setField(systemMenuService, "systemMenuMapper", systemMenuMapper);
        ReflectionTestUtils.setField(systemMenuService, "systemMenuScopeMapper", systemMenuScopeMapper);
        ReflectionTestUtils.setField(systemMenuService, "menuAccessFilter", menuAccessFilter);
        ReflectionTestUtils.setField(systemMenuService, "menuFeatureGate", new EnvironmentMenuFeatureGate(environment));
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    /**
     * 构建菜单
     * @param id
     * @param parentId
     * @param menuKey
     * @param menuType
     * @param visible
     * @param status
     * @return
     */
    private SystemMenu buildMenu(Long id, Long parentId, String menuKey, String menuType, Integer visible, Integer status) {
        SystemMenu menu = new SystemMenu();
        menu.setId(id);
        menu.setAppCode("community-admin");
        menu.setMenuKey(menuKey);
        menu.setParentId(parentId);
        menu.setMenuType(menuType);
        menu.setName(menuKey);
        menu.setSortOrder(1);
        menu.setVisible(visible);
        menu.setStatus(status);
        return menu;
    }

    /**
     * 打桩按端查询菜单
     * @param menus
     */
    private void stubAppMenus(List<SystemMenu> menus) {
        when(systemMenuMapper.selectList(any(Wrapper.class))).thenReturn(menus);
    }

    @Test
    void myMenusFiltersDisabledAndHiddenMenus() {
        SystemMenu enabled = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        SystemMenu disabled = buildMenu(2L, 0L, "m2", "PAGE", 1, 0);
        SystemMenu hidden = buildMenu(3L, 0L, "m3", "PAGE", 0, 1);
        stubAppMenus(new ArrayList<>(List.of(enabled, disabled, hidden)));
        // 无鉴权上下文时作用域覆盖不发生查询
        when(menuAccessFilter.filter(anyList(), any(AuthContext.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SystemMenuTreeNode> result = systemMenuService.myMenus("community-admin");

        assertThat(result).extracting(SystemMenuTreeNode::getId).containsExactly(1L);
    }

    @Test
    void myMenusFiltersExplicitFalseFeatureKeyOnly() {
        SystemMenu noKey = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        SystemMenu explicitTrue = buildMenu(2L, 0L, "m2", "PAGE", 1, 1);
        explicitTrue.setFeatureKey("ai.agent.evolution.enabled");
        SystemMenu explicitFalse = buildMenu(3L, 0L, "m3", "PAGE", 1, 1);
        explicitFalse.setFeatureKey("ai.agent.triggers.enabled");
        environment.setProperty("ai.agent.evolution.enabled", "true");
        environment.setProperty("ai.agent.triggers.enabled", "false");
        stubAppMenus(new ArrayList<>(List.of(noKey, explicitTrue, explicitFalse)));
        when(menuAccessFilter.filter(anyList(), any(AuthContext.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SystemMenuTreeNode> result = systemMenuService.myMenus("community-admin");

        // 配置缺失与显式true均显示，仅显式false隐藏
        assertThat(result).extracting(SystemMenuTreeNode::getId).containsExactly(1L, 2L);
    }

    @Test
    void myMenusAppliesScopeOverridePriority() {
        // 全局隐藏，SCOPE强制显示，但USER隐藏——USER优先级最高应隐藏
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 0, 1);
        stubAppMenus(new ArrayList<>(List.of(menu)));
        SystemMenuScope scopeOn = new SystemMenuScope();
        scopeOn.setMenuId(1L);
        scopeOn.setScopeType("SCOPE");
        scopeOn.setScopeId("tenant-1");
        scopeOn.setVisible(1);
        SystemMenuScope userOff = new SystemMenuScope();
        userOff.setMenuId(1L);
        userOff.setScopeType("USER");
        userOff.setScopeId("user-1");
        userOff.setVisible(0);
        when(systemMenuScopeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(scopeOn, userOff));
        when(menuAccessFilter.filter(anyList(), any(AuthContext.class))).thenAnswer(inv -> inv.getArgument(0));
        AuthContextHolder.set(new AuthContext("user-1", "tenant-1", null, null, null, null, false));

        assertThat(systemMenuService.myMenus("community-admin")).isEmpty();

        // 无USER覆盖时SCOPE强制显示生效
        when(systemMenuScopeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(scopeOn));
        AuthContextHolder.set(new AuthContext("user-2", "tenant-1", null, null, null, null, false));

        List<SystemMenuTreeNode> result = systemMenuService.myMenus("community-admin");
        assertThat(result).extracting(SystemMenuTreeNode::getId).containsExactly(1L);
    }

    @Test
    void myMenusHidesOverrideForOtherScopes() {
        // 覆盖命中时隐藏、未命中时显示；wrapper的SQL条件匹配由数据库执行，单测覆盖管线两分支
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        stubAppMenus(new ArrayList<>(List.of(menu)));
        SystemMenuScope scopeOff = new SystemMenuScope();
        scopeOff.setMenuId(1L);
        scopeOff.setScopeType("SCOPE");
        scopeOff.setScopeId("tenant-1");
        scopeOff.setVisible(0);
        when(menuAccessFilter.filter(anyList(), any(AuthContext.class))).thenAnswer(inv -> inv.getArgument(0));

        // 命中SCOPE覆盖：隐藏
        when(systemMenuScopeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(scopeOff));
        AuthContextHolder.set(new AuthContext("user-1", "tenant-1", null, null, null, null, false));
        assertThat(systemMenuService.myMenus("community-admin")).isEmpty();

        // 未命中覆盖：其他租户正常显示
        when(systemMenuScopeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        AuthContextHolder.set(new AuthContext("user-1", "tenant-2", null, null, null, null, false));
        List<SystemMenuTreeNode> result = systemMenuService.myMenus("community-admin");
        assertThat(result).extracting(SystemMenuTreeNode::getId).containsExactly(1L);
    }

    @Test
    void myMenusDropsEmptyGroupsAndHidesChildrenOfFilteredParents() {
        // 分组g1子级全被隐藏应剔除；分组g2自身隐藏时其子页面一并隐藏
        SystemMenu group1 = buildMenu(1L, 0L, "g1", "GROUP", 1, 1);
        SystemMenu group1Child = buildMenu(11L, 1L, "c1", "PAGE", 0, 1);
        SystemMenu group2 = buildMenu(2L, 0L, "g2", "GROUP", 0, 1);
        SystemMenu group2Child = buildMenu(21L, 2L, "c2", "PAGE", 1, 1);
        stubAppMenus(new ArrayList<>(List.of(group1, group1Child, group2, group2Child)));
        when(menuAccessFilter.filter(anyList(), any(AuthContext.class))).thenAnswer(inv -> inv.getArgument(0));

        List<SystemMenuTreeNode> result = systemMenuService.myMenus("community-admin");

        assertThat(result).isEmpty();
    }

    @Test
    void myMenusKeepsGroupsInAdminTree() {
        // 管理树保留空分组且含停用/隐藏项
        SystemMenu group = buildMenu(1L, 0L, "g1", "GROUP", 1, 1);
        SystemMenu hidden = buildMenu(2L, 0L, "m2", "PAGE", 0, 1);
        SystemMenu disabled = buildMenu(3L, 0L, "m3", "PAGE", 1, 0);
        stubAppMenus(new ArrayList<>(List.of(group, hidden, disabled)));

        List<SystemMenuTreeNode> result = systemMenuService.tree("community-admin");

        assertThat(result).extracting(SystemMenuTreeNode::getId).containsExactly(1L, 2L, 3L);
        verify(menuAccessFilter, never()).filter(anyList(), any(AuthContext.class));
    }

    @Test
    void createFillsDefaultsAndPlatformScope() {
        SystemMenu menu = buildMenu(null, 0L, "new-menu", "PAGE", null, null);
        menu.setPath("/new");
        menu.setSortOrder(null);
        when(systemMenuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(systemMenuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        systemMenuService.create(menu);

        ArgumentCaptor<SystemMenu> captor = ArgumentCaptor.forClass(SystemMenu.class);
        verify(systemMenuMapper).insert(captor.capture());
        SystemMenu inserted = captor.getValue();
        assertThat(inserted.getScopeId()).isEqualTo("default");
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getVisible()).isEqualTo(1);
        assertThat(inserted.getSortOrder()).isEqualTo(1);
    }

    @Test
    void createRejectsDuplicateMenuKey() {
        SystemMenu menu = buildMenu(null, 0L, "dup", "PAGE", 1, 1);
        menu.setPath("/dup");
        when(systemMenuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> systemMenuService.create(menu))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("菜单语义键已存在");
    }

    @Test
    void createRejectsPageWithoutPath() {
        SystemMenu menu = buildMenu(null, 0L, "no-path", "PAGE", 1, 1);

        assertThatThrownBy(() -> systemMenuService.create(menu))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("必须配置路由路径");
    }

    @Test
    void createRejectsNonGroupParent() {
        SystemMenu parent = buildMenu(9L, 0L, "parent-page", "PAGE", 1, 1);
        parent.setPath("/parent");
        SystemMenu menu = buildMenu(null, 9L, "child", "PAGE", 1, 1);
        menu.setPath("/child");
        when(systemMenuMapper.selectById(9L)).thenReturn(parent);

        assertThatThrownBy(() -> systemMenuService.create(menu))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("上级菜单必须是分组");
    }

    @Test
    void createRejectsUnknownMenuType() {
        SystemMenu menu = buildMenu(null, 0L, "bad-type", "WIDGET", 1, 1);

        assertThatThrownBy(() -> systemMenuService.create(menu))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("非法菜单类型");
    }

    @Test
    void updateClearsImmutableFields() {
        SystemMenu existMenu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        existMenu.setPath("/m1");
        SystemMenu update = buildMenu(1L, 0L, "m1-renamed", "PAGE", 1, 1);
        update.setPath("/m1-renamed");
        update.setAppCode("enterprise-client");
        update.setScopeId("tenant-1");
        when(systemMenuMapper.selectById(1L)).thenReturn(existMenu);
        when(systemMenuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        systemMenuService.update(1L, update);

        ArgumentCaptor<SystemMenu> captor = ArgumentCaptor.forClass(SystemMenu.class);
        verify(systemMenuMapper).updateById(captor.capture());
        SystemMenu updated = captor.getValue();
        // 归属端与作用域不可通过更新接口修改
        assertThat(updated.getAppCode()).isNull();
        assertThat(updated.getScopeId()).isNull();
    }

    @Test
    void deleteRejectsMenuWithChildren() {
        SystemMenu menu = buildMenu(1L, 0L, "g1", "GROUP", 1, 1);
        when(systemMenuMapper.selectById(1L)).thenReturn(menu);
        when(systemMenuMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> systemMenuService.delete(1L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("存在子菜单");
        verify(systemMenuMapper, never()).deleteById(1L);
    }

    @Test
    void deleteRejectsMenuWithScopeOverrides() {
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        when(systemMenuMapper.selectById(1L)).thenReturn(menu);
        when(systemMenuMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(systemMenuScopeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> systemMenuService.delete(1L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("作用域覆盖");
    }

    @Test
    void sortSwapsWithNeighbor() {
        SystemMenu first = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        first.setSortOrder(1);
        SystemMenu second = buildMenu(2L, 0L, "m2", "PAGE", 1, 1);
        second.setSortOrder(2);
        when(systemMenuMapper.selectById(2L)).thenReturn(second);
        when(systemMenuMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(first, second)));

        systemMenuService.sort(2L, "UP");

        ArgumentCaptor<SystemMenu> captor = ArgumentCaptor.forClass(SystemMenu.class);
        verify(systemMenuMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        List<SystemMenu> updates = captor.getAllValues();
        // 相邻交换显示顺序
        assertThat(updates).extracting(SystemMenu::getId, SystemMenu::getSortOrder)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 2),
                        org.assertj.core.groups.Tuple.tuple(2L, 1));
    }

    @Test
    void sortRejectsUnknownDirection() {
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        when(systemMenuMapper.selectById(1L)).thenReturn(menu);

        assertThatThrownBy(() -> systemMenuService.sort(1L, "SIDEWAYS"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("非法排序方向");
    }

    @Test
    void saveScopesReplacesAll() {
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        when(systemMenuMapper.selectById(1L)).thenReturn(menu);
        SystemMenuScope scope = new SystemMenuScope();
        scope.setScopeType("SCOPE");
        scope.setScopeId("tenant-1");
        scope.setVisible(0);

        systemMenuService.saveScopes(1L, new ArrayList<>(List.of(scope)));

        verify(systemMenuScopeMapper).delete(any(Wrapper.class));
        ArgumentCaptor<SystemMenuScope> captor = ArgumentCaptor.forClass(SystemMenuScope.class);
        verify(systemMenuScopeMapper).insert(captor.capture());
        assertThat(captor.getValue().getMenuId()).isEqualTo(1L);
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void saveScopesRejectsInvalidScopeType() {
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        when(systemMenuMapper.selectById(1L)).thenReturn(menu);
        SystemMenuScope scope = new SystemMenuScope();
        scope.setScopeType("ORG");
        scope.setScopeId("org-1");
        scope.setVisible(0);

        assertThatThrownBy(() -> systemMenuService.saveScopes(1L, new ArrayList<>(List.of(scope))))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("非法覆盖维度");
        verify(systemMenuScopeMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void permitAllFilterReturnsMenusUnchanged() {
        PermitAllMenuAccessFilter filter = new PermitAllMenuAccessFilter();
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        List<SystemMenu> menus = new ArrayList<>(List.of(menu));

        assertThat(filter.filter(menus, AuthContext.EMPTY)).isSameAs(menus);
    }

    @Test
    void myMenusRequiresAppCode() {
        assertThatThrownBy(() -> systemMenuService.myMenus(" "))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("端标识不能为空");
    }

    @Test
    void myMenusPassesContextToAccessFilter() {
        SystemMenu menu = buildMenu(1L, 0L, "m1", "PAGE", 1, 1);
        stubAppMenus(new ArrayList<>(List.of(menu)));
        when(systemMenuScopeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(menuAccessFilter.filter(anyList(), any(AuthContext.class))).thenReturn(new ArrayList<>());
        AuthContext context = new AuthContext("user-1", "tenant-1", null, null, null, null, false);
        AuthContextHolder.set(context);

        systemMenuService.myMenus("community-admin");

        verify(menuAccessFilter).filter(anyList(), eq(context));
    }
}
