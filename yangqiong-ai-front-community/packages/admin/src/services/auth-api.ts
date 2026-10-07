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
import { request } from './api-client';
import type {
  CurrentUser,
  LoginPayload,
  LoginResult,
  PlatformAdminLoginPayload,
} from '@yangqiong/shared';

/**
 * 平台管理员登录
 * @param payload
 * @return
 */
export function loginPlatformAdmin(
  payload: PlatformAdminLoginPayload
): Promise<LoginResult> {
  return request.post<LoginResult>('/api/auth/login/platform-admin', payload);
}

/**
 * 租户用户登录，用于平台管理员切换租户上下文换发 token
 * @param payload
 * @return
 */
export function login(payload: LoginPayload): Promise<LoginResult> {
  return request.post<LoginResult>('/api/auth/login', payload);
}

/**
 * 获取当前登录用户
 * @return
 */
export function me(): Promise<CurrentUser> {
  return request.get<CurrentUser>('/api/auth/me');
}
