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
  ConnectorCredential,
  ConnectorDescriptor,
  ConnectorInstance,
  ConnectorToolDefinition,
  ProviderCatalogItem,
} from '../types/connector';
import type { MpPage } from '../types/registry';

const BASE = '/api/agent/connector';

export const createConnectorApi = (http: HttpRequest) => ({
  providers: () => http.get<ProviderCatalogItem[]>(`${BASE}/providers`),
  credentials: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<ConnectorCredential>>(`${BASE}/credentials`, { params }),
    save: (data: Partial<ConnectorCredential>) =>
      http.post<ConnectorCredential>(`${BASE}/credentials`, data),
    remove: (id: string) => http.delete<void>(`${BASE}/credentials/${id}`),
    /**
     * 连通性测试，返回失败原因，通过时为null
     */
    test: (id: string) => http.post<string | null>(`${BASE}/credentials/${id}/test`),
  },
  instances: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<ConnectorInstance>>(`${BASE}/instances`, { params }),
    save: (data: Partial<ConnectorInstance>) =>
      http.post<ConnectorInstance>(`${BASE}/instances`, data),
    remove: (id: string) => http.delete<void>(`${BASE}/instances/${id}`),
    changeStatus: (id: string, enabled: boolean) =>
      http.put<void>(`${BASE}/instances/${id}/status`, undefined, { params: { enabled } }),
    tools: (id: string) =>
      http.get<ConnectorToolDefinition[]>(`${BASE}/instances/${id}/tools`),
    callbackUrl: (id: string) => http.get<string>(`${BASE}/instances/${id}/callback-url`),
  },
});
