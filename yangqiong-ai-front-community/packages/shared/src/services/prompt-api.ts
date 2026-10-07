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
  ABTestConfig,
  ABTestResult,
  PageQuery,
  PageResult,
  PromptVersion,
} from '../types';

export const createPromptApi = (http: HttpRequest) => ({
  version: {
    create: (data: Omit<PromptVersion, 'versionId' | 'createTime'>) =>
      http.post<PromptVersion>('/api/prompt/version', data),
    publish: (versionId: string) =>
      http.put<PromptVersion>(`/api/prompt/version/${versionId}/publish`),
    archive: (versionId: string) =>
      http.put<PromptVersion>(`/api/prompt/version/${versionId}/archive`),
    rollback: (versionId: string) =>
      http.put<PromptVersion>(`/api/prompt/version/${versionId}/rollback`),
    list: (params?: PageQuery) =>
      http.get<PageResult<PromptVersion>>('/api/prompt/version/list', {
        params,
      }),
    published: (promptCode: string) =>
      http.get<PromptVersion>('/api/prompt/version/published', {
        params: { promptCode },
      }),
  },
  abtest: {
    start: (config: Omit<ABTestConfig, 'id' | 'createTime'>) =>
      http.post<ABTestConfig>('/api/prompt/abtest', config),
    stop: (id: string) => http.put<ABTestConfig>(`/api/prompt/abtest/${id}/stop`),
    result: (id: string) =>
      http.get<ABTestResult>(`/api/prompt/abtest/${id}/result`),
    report: (params?: PageQuery) =>
      http.get<PageResult<ABTestConfig>>('/api/prompt/abtest/report', {
        params,
      }),
  },
});
