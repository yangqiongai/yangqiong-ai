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
/**
 * 菜单管理相关类型
 * 字段与后端对齐：SystemMenuTreeNode / SystemMenuScope (ai-platform-system)
 */

/**
 * 端标识
 */
export type AppCode = 'community-admin' | 'enterprise-admin' | 'enterprise-client';

/**
 * 菜单类型
 */
export type MenuType = 'GROUP' | 'PAGE' | 'LINK';

/**
 * 差异化作用域类型（SCOPE=租户/组织维度，USER=用户维度）
 */
export type ScopeType = 'SCOPE' | 'USER';

/**
 * 同级排序方向
 */
export type MenuSortDirection = 'UP' | 'DOWN' | 'TOP';

/**
 * 菜单树节点（管理树与用户可见树通用）
 */
export interface MenuTreeNode {
  id?: number;
  menuKey?: string;
  appCode?: string;
  parentId?: number;
  menuType?: MenuType;
  name?: string;
  path?: string | null;
  icon?: string | null;
  sortOrder?: number;
  /**
   * 全局显隐：1显示/0隐藏
   */
  visible?: number;
  /**
   * 状态：1启用/0停用
   */
  status?: number;
  permissionCode?: string | null;
  featureKey?: string | null;
  children?: MenuTreeNode[];
}

/**
 * 菜单作用域差异化覆盖行
 */
export interface MenuScopeOverride {
  id?: number;
  menuId?: number;
  scopeType: ScopeType;
  scopeId: string;
  /**
   * 该作用域下显隐：1显示/0隐藏
   */
  visible: number;
}

/**
 * 菜单保存载荷（create 必填 appCode，update 后端忽略 appCode/scopeId）
 */
export interface MenuSavePayload {
  appCode?: string;
  parentId?: number;
  menuKey: string;
  menuType: MenuType;
  name: string;
  path?: string | null;
  icon?: string | null;
  sortOrder?: number;
  visible?: number;
  status?: number;
  permissionCode?: string | null;
  featureKey?: string | null;
}
