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
 * 认证相关类型
 */
import type { TenantRole } from './common';

/**
 * 租户用户登录参数
 */
export interface LoginPayload {
  username: string;
  password: string;
  tenantId?: string;
}

/**
 * 平台管理员登录参数
 */
export interface PlatformAdminLoginPayload {
  username: string;
  password: string;
}

/**
 * 登录返回的用户简略信息
 */
export interface LoginUserBrief {
  id: string;
  name: string;
}

/**
 * 登录返回结果
 */
export interface LoginResult {
  token: string;
  user: LoginUserBrief;
  tenantId?: string;
  platformAdmin: boolean;
  tenantRole?: TenantRole;
}

/**
 * 当前登录用户上下文
 */
export interface CurrentUser {
  userId: string;
  tenantId?: string;
  platformAdmin: boolean;
  tenantRole?: TenantRole;
}
