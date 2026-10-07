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
  CapabilitySpec,
  CapabilityCategory,
  CapabilityCategoryNode,
  CapabilityDefinitionHistory,
  PageQuery,
  PageResult,
} from '../types';

export const createOpenCapabilityApi = (http: HttpRequest) => ({
  list: (params?: PageQuery & { categoryCode?: string }) =>
    http.get<PageResult<CapabilitySpec>>('/api/open-capability/list', { params }),
  get: (code: string) => http.get<CapabilitySpec>(`/api/open-capability/${code}`),
  create: (data: Omit<CapabilitySpec, 'code'>) =>
    http.post<CapabilitySpec>('/api/open-capability', data),
  update: (code: string, data: Partial<CapabilitySpec>) =>
    http.put<CapabilitySpec>(`/api/open-capability/${code}`, data),
  delete: (code: string) => http.delete<void>(`/api/open-capability/${code}`),
  history: (code: string) =>
    http.get<CapabilityDefinitionHistory[]>(`/api/open-capability/${code}/history`),
  historyDetail: (code: string, historyId: number) =>
    http.get<CapabilityDefinitionHistory>(`/api/open-capability/${code}/history/${historyId}`),
  rollback: (code: string, historyId: number) =>
    http.post<CapabilitySpec>(`/api/open-capability/${code}/rollback/${historyId}`),
  categoryTree: () =>
    http.get<{ nodes: CapabilityCategoryNode[]; ungroupedCount: number }>(
      '/api/open-capability/category/tree'
    ),
  createCategory: (data: CapabilityCategory) =>
    http.post<CapabilityCategory>('/api/open-capability/category', data),
  updateCategory: (id: number, data: CapabilityCategory) =>
    http.put<CapabilityCategory>(`/api/open-capability/category/${id}`, data),
  deleteCategory: (id: number) => http.delete<void>(`/api/open-capability/category/${id}`),
});
