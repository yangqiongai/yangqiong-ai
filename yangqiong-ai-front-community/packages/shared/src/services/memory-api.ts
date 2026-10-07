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
  ChatMemoryRecord,
  MemoryPageResult,
  PageQuery,
} from '../types';

export const createMemoryApi = (http: HttpRequest) => ({
  list: (params?: PageQuery) =>
    http.get<MemoryPageResult>('/api/memory', { params }),
  search: (params: PageQuery & { keyword: string }) =>
    http.get<ChatMemoryRecord[]>('/api/memory/search', { params }),
  get: (id: string) => http.get<ChatMemoryRecord>(`/api/memory/${id}`),
  delete: (id: string) => http.delete<void>(`/api/memory/${id}`),
  batchDelete: (ids: string[]) =>
    http.post<void>('/api/memory/batch-delete', { ids }),
});
