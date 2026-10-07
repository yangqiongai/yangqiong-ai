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
import type { StatusEnum, TenantRole } from '../types';

export * from './inbox-types';

/**
 * 应用端别
 */
export type AppScope = 'admin' | 'client';

/**
 * 状态标签
 */
export const STATUS_LABEL: Record<StatusEnum, string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
};

/**
 * 租户角色标签
 * 兼容两套角色值：旧四档（TENANT_*）与企业版成员表三档（TENANT_ADMIN/MEMBER/READONLY）
 */
export const TENANT_ROLE_LABEL: Record<TenantRole | 'MEMBER' | 'READONLY', string> = {
  TENANT_OWNER: '租户所有者',
  TENANT_ADMIN: '租户管理员',
  TENANT_MEMBER: '租户成员',
  TENANT_VIEWER: '只读成员',
  MEMBER: '成员',
  READONLY: '只读',
};

/**
 * 本地存储键名
 */
export const STORAGE_KEYS = {
  ADMIN_AUTH_STORAGE: 'admin-auth-storage',
  ADMIN_THEME: 'admin-theme',
  LOCALE: 'locale',
} as const;

/**
 * 应用对应的 Token 存储 key
 */
export const TOKEN_KEYS: Record<AppScope, string> = {
  admin: 'admin-auth-storage',
  client: 'client-auth-storage',
};
