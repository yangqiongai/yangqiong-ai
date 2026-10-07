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
import { describe, expect, it } from 'vitest';
import { AppstoreOutlined, ToolOutlined } from '@ant-design/icons';
import { convertDbTreeToGroups, mergeDbMenuGroups, resolveMenuGroups } from './menu-merge';
import { MENU_GROUPS } from './menu-config';
import type { MenuTreeNode } from '@yangqiong/shared';

const dbTree: MenuTreeNode[] = [
  {
    id: 1101,
    menuKey: 'system',
    menuType: 'GROUP',
    name: '系统管理',
    icon: 'AuditOutlined',
    children: [
      {
        id: 1233,
        menuKey: 'users',
        parentId: 1101,
        menuType: 'PAGE',
        name: '用户管理',
        path: '/users',
        icon: 'UserOutlined',
        children: [],
      },
    ],
  },
  {
    id: 1102,
    menuKey: 'governance',
    menuType: 'GROUP',
    name: '治理中心',
    icon: 'SafetyOutlined',
    children: [
      {
        id: 1227,
        menuKey: 'triggers',
        parentId: 1102,
        menuType: 'PAGE',
        name: '触发器',
        path: '/triggers',
        icon: 'ThunderboltOutlined',
        children: [],
      },
    ],
  },
];

const pluginGroups = [
  {
    key: 'system',
    label: '系统管理（插件）',
    icon: ToolOutlined,
    children: [
      { key: 'tenant-admin', label: '租户管理', path: '/tenant-admin', icon: ToolOutlined },
      // path 与库重复，应被库覆盖剔除
      { key: 'users', label: '用户管理', path: '/users', icon: ToolOutlined },
    ],
  },
  {
    key: 'enterprise',
    label: '企业扩展',
    icon: ToolOutlined,
    children: [
      { key: 'billing', label: '计费', path: '/billing', icon: ToolOutlined },
    ],
  },
  {
    // 全部子项被库覆盖后整组丢弃
    key: 'legacy',
    label: '遗留分组',
    icon: ToolOutlined,
    children: [
      { key: 'users-legacy', label: '用户管理', path: '/users', icon: ToolOutlined },
    ],
  },
];

describe('convertDbTreeToGroups', () => {
  it('分组节点映射为侧边栏分组并解析图标', () => {
    const groups = convertDbTreeToGroups(dbTree);
    expect(groups).toHaveLength(2);
    expect(groups[0].key).toBe('system');
    expect(groups[0].children[0].path).toBe('/users');
  });

  it('未注册图标名回落默认图标', () => {
    const groups = convertDbTreeToGroups([
      {
        id: 1,
        menuKey: 'g',
        menuType: 'GROUP',
        name: 'G',
        icon: 'NotExistsOutlined',
        children: [],
      },
    ]);
    expect(groups[0].icon).toBe(AppstoreOutlined);
  });

  it('根级页面节点防御性包裹为单项分组', () => {
    const groups = convertDbTreeToGroups([
      { id: 2, menuKey: 'home', menuType: 'PAGE', name: '工作台', path: '/', children: [] },
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0].key).toBe('root-home');
    expect(groups[0].children[0].path).toBe('/');
  });

  it('无路径的页面节点被跳过', () => {
    const groups = convertDbTreeToGroups(dbTree);
    expect(groups[0].children.every((c) => !!c.path)).toBe(true);
  });
});

describe('mergeDbMenuGroups', () => {
  it('库菜单优先，插件菜单按path去重后追加', () => {
    const merged = mergeDbMenuGroups(dbTree, pluginGroups);
    const systemGroup = merged.find((g) => g.key === 'system');
    // 插件子项并入同名库分组，重复path被剔除
    expect(systemGroup?.children.map((c) => c.path)).toEqual(['/users', '/tenant-admin']);
    // 新键插件分组追加在库分组之后
    expect(merged.map((g) => g.key)).toEqual(['system', 'governance', 'enterprise']);
    // 全部子项被覆盖的插件分组整组丢弃
    expect(merged.find((g) => g.key === 'legacy')).toBeUndefined();
  });

  it('空库树时等于插件分组全量', () => {
    const merged = mergeDbMenuGroups([], pluginGroups);
    expect(merged.map((g) => g.key)).toEqual(['system', 'enterprise', 'legacy']);
  });

  it('库分组图标解析不受插件分组影响', () => {
    const merged = mergeDbMenuGroups(dbTree, pluginGroups);
    const systemGroup = merged.find((g) => g.key === 'system');
    // 库分组 icon 来自库配置（AuditOutlined），保留库优先语义
    expect(systemGroup?.label).toBe('系统管理');
    expect(systemGroup?.icon).not.toBe(ToolOutlined);
  });

  it('静态种子菜单与库合并后无重复path', () => {
    // 静态 MENU_GROUPS 作为"插件侧"输入的极端场景：合并后path唯一
    const merged = mergeDbMenuGroups(dbTree, MENU_GROUPS);
    const paths = merged.flatMap((g) => g.children.map((c) => c.path));
    expect(new Set(paths).size).toBe(paths.length);
  });
});

describe('resolveMenuGroups', () => {
  it('企业端有库树时仅用库树，社区静态与插件菜单一律不并入', () => {
    const groups = resolveMenuGroups(dbTree, false, MENU_GROUPS, pluginGroups);
    // 结果等于库树直转，不含插件 /billing、静态 /dashboard 等社区菜单
    expect(groups.map((g) => g.key)).toEqual(['system', 'governance']);
    const paths = groups.flatMap((g) => g.children.map((c) => c.path));
    expect(paths).toEqual(['/users', '/triggers']);
    expect(paths).not.toContain('/billing');
    expect(paths).not.toContain('/dashboard');
  });

  it('企业端库未加载时回退插件声明的企业菜单组', () => {
    const groups = resolveMenuGroups(undefined, false, MENU_GROUPS, pluginGroups);
    expect(groups.map((g) => g.key)).toEqual(['system', 'enterprise', 'legacy']);
  });

  it('企业端库未加载且插件未声明菜单时兜底社区静态全量', () => {
    const groups = resolveMenuGroups(undefined, false, MENU_GROUPS, []);
    expect(groups.map((g) => g.key)).toEqual(MENU_GROUPS.map((g) => g.key));
  });

  it('社区端有库树时保持库优先+静态防御追加行为', () => {
    const groups = resolveMenuGroups(dbTree, true, MENU_GROUPS, []);
    // 合并行为与 mergeDbMenuGroups 一致：库分组在前，静态去重追加在后
    expect(groups.map((g) => g.key)).toEqual(mergeDbMenuGroups(dbTree, MENU_GROUPS).map((g) => g.key));
  });

  it('社区端库未加载时回退静态全量+插件组（与改造前 getMenuGroups 一致）', () => {
    const groups = resolveMenuGroups(undefined, true, MENU_GROUPS, pluginGroups);
    expect(groups.map((g) => g.key)).toEqual([...MENU_GROUPS.map((g) => g.key), 'system', 'enterprise', 'legacy']);
  });
});
