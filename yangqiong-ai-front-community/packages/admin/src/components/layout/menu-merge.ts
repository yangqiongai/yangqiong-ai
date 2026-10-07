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
import type { MenuConfigGroup, MenuConfigItem } from './menu-config';
import { MENU_GROUPS } from './menu-config';
import { resolveMenuIcon } from './menu-icons';
import type { MenuTreeNode } from '@yangqiong/shared';

/**
 * 将菜单库树转换为侧边栏分组配置
 * <p>
 * 根级分组直接映射为菜单分组；根级页面/外链节点防御性包裹为单项分组。
 * @param tree
 * @return
 */
export function convertDbTreeToGroups(tree: MenuTreeNode[]): MenuConfigGroup[] {
  const groups: MenuConfigGroup[] = [];
  for (const node of tree) {
    if (node.menuType === 'GROUP') {
      groups.push({
        key: node.menuKey ?? `group-${node.id}`,
        label: node.name ?? node.menuKey ?? '',
        icon: resolveMenuIcon(node.icon),
        children: toItems(node.children ?? []),
      });
      continue;
    }
    // 根级页面/外链包裹为单项分组，保证侧边栏结构一致
    const item = toItem(node);
    if (item) {
      groups.push({
        key: `root-${node.menuKey ?? node.id}`,
        label: item.label,
        icon: item.icon,
        children: [item],
      });
    }
  }
  return groups;
}

/**
 * 合并库菜单分组与插件注入分组（库优先，插件按 path 去重追加）
 * <p>
 * 插件菜单中 path 已存在于库的条目被库覆盖；分组键与库分组相同时子项并入库分组；
 * 去重后为空的插件分组整体丢弃。
 * @param dbTree
 * @param pluginGroups
 * @return
 */
export function mergeDbMenuGroups(
  dbTree: MenuTreeNode[],
  pluginGroups: MenuConfigGroup[],
): MenuConfigGroup[] {
  const dbGroups = convertDbTreeToGroups(dbTree);
  const dbPaths = new Set(dbGroups.flatMap((g) => g.children.map((c) => c.path)));
  const groupsByKey = new Map(dbGroups.map((g) => [g.key, g]));

  for (const pluginGroup of pluginGroups) {
    const uniqueChildren = pluginGroup.children.filter((c) => !dbPaths.has(c.path));
    if (uniqueChildren.length === 0) {
      continue;
    }
    const existing = groupsByKey.get(pluginGroup.key);
    if (existing) {
      existing.children.push(...uniqueChildren);
      continue;
    }
    const group: MenuConfigGroup = { ...pluginGroup, children: [...uniqueChildren] };
    groupsByKey.set(group.key, group);
    dbGroups.push(group);
  }
  return dbGroups;
}

/**
 * 端分流解析侧边栏菜单分组
 * <p>
 * 社区端：库优先并静态防御追加（社区库已全量收录，行为与改造前一致）；企业端：仅用库树，
 * 社区静态菜单一律不并入；库未加载时社区端回退静态全量，企业端回退插件声明的企业菜单组。
 * @param dbTree
 * @param isCommunity
 * @param extraMenuGroups
 * @param pluginGroups
 * @return
 */
export function resolveMenuGroups(
  dbTree: MenuTreeNode[] | undefined,
  isCommunity: boolean,
  extraMenuGroups: MenuConfigGroup[],
  pluginGroups: MenuConfigGroup[],
): MenuConfigGroup[] {
  if (dbTree) {
    return isCommunity ? mergeDbMenuGroups(dbTree, extraMenuGroups) : convertDbTreeToGroups(dbTree);
  }
  if (isCommunity) {
    return [...MENU_GROUPS, ...pluginGroups];
  }
  // 企业端回退插件声明的企业菜单组，未声明时兜底社区静态（防御）
  return pluginGroups.length ? pluginGroups : MENU_GROUPS;
}

/**
 * 树节点列表转菜单项
 * @param nodes
 * @return
 */
function toItems(nodes: MenuTreeNode[]): MenuConfigItem[] {
  return nodes.map(toItem).filter((item): item is MenuConfigItem => item != null);
}

/**
 * 树节点转菜单项，页面/外链必须有路径
 * @param node
 * @return
 */
function toItem(node: MenuTreeNode): MenuConfigItem | null {
  if (!node.path) {
    return null;
  }
  return {
    key: node.menuKey ?? `menu-${node.id}`,
    label: node.name ?? node.menuKey ?? '',
    path: node.path,
    icon: resolveMenuIcon(node.icon),
  };
}
