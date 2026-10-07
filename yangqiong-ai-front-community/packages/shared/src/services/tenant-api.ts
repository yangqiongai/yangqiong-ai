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
import type { HttpRequest } from './http';
import type {
  Tenant,
  TenantMember,
  TenantQuota,
  TenantUsage,
} from '../types';

/**
 * 租户 API（社区保留终端用户侧只读方法）
 * 运营管理方法（增删改/启停/升降级/套餐）见企业版 shared-enterprise
 */
export const createTenantApi = (http: HttpRequest) => ({
  get: (id: string) => http.get<Tenant>(`/api/tenant/${id}`),
  quota: (tenantId: string) =>
    http.get<TenantQuota>(`/api/tenant/${tenantId}/quota`),
  members: (tenantId: string) =>
    http.get<TenantMember[]>(`/api/tenant/${tenantId}/members`),
  usage: (tenantId: string) =>
    http.get<TenantUsage>(`/api/tenant/${tenantId}/usage`),
});
