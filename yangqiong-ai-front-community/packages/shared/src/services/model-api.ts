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
import type { ModelInfo, ModelTestResult, PageQuery, PageResult } from '../types';

export const createModelApi = (http: HttpRequest) => ({
  list: async (params?: PageQuery): Promise<PageResult<ModelInfo>> => {
    const data = await http.get<ModelInfo[]>('/api/model/info');
    const keyword = (params?.keyword ?? '').trim().toLowerCase();
    const filtered = keyword
      ? data.filter((m) =>
          [m.provider, m.modelCode, m.modelName].some(
            (v) => v != null && v.toLowerCase().includes(keyword),
          ),
        )
      : data;
    const page = params?.page ?? 1;
    const size = params?.size ?? 10;
    const start = (page - 1) * size;
    return { list: filtered.slice(start, start + size), total: filtered.length, page, size };
  },
  get: (modelCode: string) => http.get<ModelInfo>(`/api/model/info/${modelCode}`),
  create: (data: Omit<ModelInfo, 'id' | 'createTime' | 'updateTime'>) =>
    http.post<ModelInfo>('/api/model/info', data),
  update: (data: Partial<ModelInfo>) => http.put<ModelInfo>('/api/model/info', data),
  delete: (modelCode: string) => http.delete<void>(`/api/model/info/${modelCode}`),
  testConnectivity: (data: Partial<ModelInfo>) =>
    http.post<ModelTestResult>('/api/model/info/test', data),
});
