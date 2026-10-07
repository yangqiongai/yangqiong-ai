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
 * 通用类型定义
 */

/**
 * 通用接口返回结果
 */
export interface ApiResult<T = unknown> {
  success: boolean;
  code: number;
  message: string;
  data: T;
}

/**
 * 分页查询结果
 */
export interface PageResult<T = unknown> {
  list: T[];

  /**
   * 后端 MyBatis-Plus 分页风格的记录列表（部分接口返回此字段）
   */
  records?: T[];

  /**
   * 当前页码（MyBatis-Plus 风格，与 page 等价）
   */
  current?: number;
  total: number;
  page: number;
  size: number;
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  page: number;
  size: number;
  keyword?: string;
  [key: string]: unknown;
}

/**
 * 通用分类树节点
 * 树接口响应结构：{ nodes: CategoryTreeNode[]; ungroupedCount: number }
 */
export interface CategoryTreeNode {
  id: number;
  code: string;
  name: string;
  parentId?: number | null;
  sortNum?: number;
  children?: CategoryTreeNode[];
}

/**
 * 通用分类保存请求（新增/编辑共用，code 创建后不可改）
 */
export interface CategorySaveRequest {
  code?: string;
  name?: string;
  parentId?: number | null;
  sortNum?: number;
}

/**
 * 平台用户角色
 */
export type UserRole = 'PLATFORM_ADMIN' | 'PLATFORM_VIEWER';

/**
 * 租户内角色
 */
export type TenantRole =
  | 'TENANT_OWNER'
  | 'TENANT_ADMIN'
  | 'TENANT_MEMBER'
  | 'TENANT_VIEWER';

/**
 * 启用状态
 */
export const StatusEnum = {
  ENABLED: 'ENABLED',
  DISABLED: 'DISABLED',
} as const;

export type StatusEnum = (typeof StatusEnum)[keyof typeof StatusEnum];
