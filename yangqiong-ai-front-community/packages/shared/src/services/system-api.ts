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
import type { AiUser, MenuSavePayload, MenuScopeOverride, MenuSortDirection, MenuTreeNode, PageQuery, PageResult } from '../types';

export const createSystemApi = (http: HttpRequest) => ({
  user: {
    // 后端返回 MyBatis-Plus Page（records/current/size/total），包装为统一 PageResult
    list: async (params?: PageQuery) => {
      const res = await http.get<{
        records?: AiUser[];
        total?: number;
        current?: number;
        size?: number;
      }>('/api/system/user/list', { params });
      return {
        list: res?.records ?? [],
        total: res?.total ?? 0,
        page: res?.current ?? params?.page ?? 1,
        size: res?.size ?? params?.size ?? 10,
      } as PageResult<AiUser>;
    },
    get: (id: string) => http.get<AiUser>(`/api/system/user/${id}`),
    create: (data: Omit<AiUser, 'id' | 'createTime' | 'updateTime' | 'deleted'>) =>
      http.post<AiUser>('/api/system/user', data),
    update: (id: string, data: Partial<AiUser>) =>
      http.put<AiUser>(`/api/system/user/${id}`, data),
    delete: (id: string) => http.delete<void>(`/api/system/user/${id}`),
    status: (id: string, status: number) =>
      http.put<void>(`/api/system/user/${id}/status`, { status }),
    password: (id: string, password: string) =>
      http.put<void>(`/api/system/user/${id}/password`, { password }),
  },
  menu: {
    tree: (appCode: string) =>
      http.get<MenuTreeNode[]>('/api/system/menus/tree', { params: { appCode } }),
    my: (appCode: string) =>
      http.get<MenuTreeNode[]>('/api/system/menus/my', { params: { appCode } }),
    create: (data: MenuSavePayload) => http.post<MenuTreeNode>('/api/system/menus', data),
    update: (id: number, data: Partial<MenuSavePayload>) =>
      http.put<MenuTreeNode>(`/api/system/menus/${id}`, data),
    delete: (id: number) => http.delete<void>(`/api/system/menus/${id}`),
    sort: (id: number, direction: MenuSortDirection) =>
      http.put<void>(`/api/system/menus/${id}/sort`, { direction }),
    scopes: (id: number) => http.get<MenuScopeOverride[]>(`/api/system/menus/${id}/scopes`),
    saveScopes: (id: number, scopes: MenuScopeOverride[]) =>
      http.put<void>(`/api/system/menus/${id}/scopes`, scopes),
  },
});
