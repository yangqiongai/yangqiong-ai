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
  GraphBuildRecordInfo,
  GraphBuildRequest,
  GraphHealthStatus,
  GraphRetrieveRequest,
  GraphSchema,
  GraphVersionInfo,
  PageQuery,
  PageResult,
} from '../types';

/**
 * 图谱统计
 */
export interface GraphStats {
  entityCount?: number;

  tripleCount?: number;

  communityCount?: number;

  quality?: string;

  [key: string]: unknown;
}

/**
 * 路径查询请求
 */
export interface GraphPathQueryRequest {
  kbId: string;

  query: string;

  maxHops?: number;

  topK?: number;
}

/**
 * 图谱检索结果路径三元组
 */
export interface GraphPathItem {
  subject?: string;

  predicate?: string;

  object?: string;

  score?: number;
}

export const createGraphApi = (http: HttpRequest) => ({
  build: (req: GraphBuildRequest) =>
    http.post<GraphBuildRecordInfo>('/api/graph/build', req),
  buildRecord: {
    list: (kbId: string, params?: PageQuery) =>
      http.get<PageResult<GraphBuildRecordInfo>>('/api/graph/build/list', {
        params: { kbId, ...params },
      }),
    get: (buildId: string) =>
      http.get<GraphBuildRecordInfo>(`/api/graph/build/${buildId}`),
    resume: (buildId: string) =>
      http.post<string>(`/api/graph/build/${buildId}/resume`),
    rebuild: (kbId: string) =>
      http.post<string>(`/api/graph/build/kb/${kbId}/rebuild`),
    remove: (kbId: string) =>
      http.delete<void>(`/api/graph/build/${kbId}`),
  },
  admin: {
    stats: (kbId: string) =>
      http.get<GraphStats>(`/api/graph/admin/stats/${kbId}`),
    delete: (kbId: string) => http.delete<void>(`/api/graph/admin/${kbId}`),
  },
  retrieve: (req: GraphRetrieveRequest) =>
    http.post<unknown>('/api/graph/retrieve', req),
  retrievePaths: (req: GraphPathQueryRequest) =>
    http.post<GraphPathItem[]>('/api/graph/retrieve/paths', req),
  retrieveIrCoT: (req: GraphRetrieveRequest) =>
    http.post<unknown>('/api/graph/retrieve/ircot', req),
  schema: {
    get: (kbId: string) => http.get<GraphSchema>(`/api/graph/schema/${kbId}`),
    put: (kbId: string, schema: Partial<GraphSchema>) =>
      http.put<GraphSchema>(`/api/graph/schema/${kbId}`, schema),
    append: (kbId: string, type: string, name: string) =>
      http.post<void>(`/api/graph/schema/${kbId}/append`, null, {
        params: { type, name },
      }),
    rollback: (kbId: string, version: number) =>
      http.delete<void>(`/api/graph/schema/${kbId}/rollback/${version}`),
  },
  version: {
    list: (kbId: string) =>
      http.get<GraphVersionInfo[]>(`/api/graph/version/${kbId}`),
    active: (kbId: string) =>
      http.get<GraphVersionInfo>(`/api/graph/version/${kbId}/active`),
    rollback: (kbId: string, version: number) =>
      http.post<unknown>(`/api/graph/version/${kbId}/rollback/${version}`),
  },
  health: () => http.get<GraphHealthStatus>('/api/graph/health'),
});
