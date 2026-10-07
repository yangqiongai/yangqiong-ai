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
  AgentConfigDrift,
  AgentConfigProfile,
  AgentDefinition,
  AgentGrayRule,
  AgentRelease,
  AgentVersion,
  ConfigPackageImportResult,
  GrayPreviewResult,
  MpPage,
} from '../types';

const BASE = '/api/agent/registry';

export const createRegistryApi = (http: HttpRequest) => ({
  definition: {
    page: (params?: { pageNum?: number; pageSize?: number; status?: string; keyword?: string }) =>
      http.get<MpPage<AgentDefinition>>(`${BASE}/definitions`, { params }),
    get: (agentCode: string) =>
      http.get<AgentDefinition>(`${BASE}/definitions/${agentCode}`),
    create: (data: Partial<AgentDefinition>) =>
      http.post<AgentDefinition>(`${BASE}/definitions`, data),
    update: (agentCode: string, data: Partial<AgentDefinition>) =>
      http.put<AgentDefinition>(`${BASE}/definitions/${agentCode}`, data),
    updateStatus: (agentCode: string, enabled: boolean) =>
      http.put<AgentDefinition>(`${BASE}/definitions/${agentCode}/status`, { enabled }),
    updateCardEnabled: (agentCode: string, enabled: boolean) =>
      http.put<AgentDefinition>(`${BASE}/definitions/${agentCode}/card-status`, { enabled }),
    delete: (agentCode: string) => http.delete<void>(`${BASE}/definitions/${agentCode}`),
    disable: (agentCode: string, operator?: string) =>
      http.post<void>(`${BASE}/definitions/${agentCode}/disable`, { operator }),
  },
  version: {
    create: (agentCode: string, data: Partial<AgentVersion>) =>
      http.post<AgentVersion>(`${BASE}/definitions/${agentCode}/versions`, data),
    updateDraft: (versionId: number, data: Partial<AgentVersion>) =>
      http.put<AgentVersion>(`${BASE}/versions/${versionId}`, data),
    list: (agentCode: string) =>
      http.get<AgentVersion[]>(`${BASE}/definitions/${agentCode}/versions`),
    get: (versionId: number) => http.get<AgentVersion>(`${BASE}/versions/${versionId}`),
    submit: (versionId: number, operator?: string) =>
      http.post<void>(`${BASE}/versions/${versionId}/submit`, { operator }),
    publish: (versionId: number, evalRunId?: number, operator?: string) =>
      http.post<AgentRelease>(`${BASE}/versions/${versionId}/publish`, { evalRunId, operator }),
    rollback: (versionId: number, reason?: string, operator?: string) =>
      http.post<AgentRelease>(`${BASE}/versions/${versionId}/rollback`, { reason, operator }),
    publishGray: (versionId: number, rule: Partial<AgentGrayRule>) =>
      http.post<AgentRelease>(`${BASE}/versions/${versionId}/publish-gray`, rule),
    deprecate: (versionId: number, operator?: string) =>
      http.post<void>(`${BASE}/versions/${versionId}/deprecate`, { operator }),
    reject: (versionId: number, reason?: string, operator?: string) =>
      http.post<void>(`${BASE}/versions/${versionId}/reject`, { reason, operator }),
    backToDraft: (versionId: number, operator?: string) =>
      http.post<void>(`${BASE}/versions/${versionId}/back-to-draft`, { operator }),
  },
  grayRule: {
    create: (data: Partial<AgentGrayRule>) =>
      http.post<AgentGrayRule>(`${BASE}/gray-rules`, data),
    list: (agentCode: string) =>
      http.get<AgentGrayRule[]>(`${BASE}/gray-rules`, { params: { agentCode } }),
    toggle: (ruleId: number) =>
      http.put<AgentGrayRule>(`${BASE}/gray-rules/${ruleId}/status`),
    delete: (ruleId: number) => http.delete<void>(`${BASE}/gray-rules/${ruleId}`),
    preview: (agentCode: string, userId: string, scopeId?: string) =>
      http.post<GrayPreviewResult>(`${BASE}/gray-rules/preview`, { agentCode, userId, scopeId }),
  },
  releases: (agentCode: string) =>
    http.get<AgentRelease[]>(`${BASE}/releases`, { params: { agentCode } }),
  // 环境配置档（后端 /api/agent/profile，registry 开关下）
  profile: {
    list: (agentCode: string) =>
      http.get<AgentConfigProfile[]>('/api/agent/profile', { params: { agentCode } }),
    get: (agentCode: string, profileCode: string) =>
      http.get<AgentConfigProfile>('/api/agent/profile/detail', {
        params: { agentCode, profileCode },
      }),
    current: () => http.get<string>('/api/agent/profile/current'),
    save: (data: Partial<AgentConfigProfile>) =>
      http.post<AgentConfigProfile>('/api/agent/profile', data),
    delete: (id: number) => http.delete<void>(`/api/agent/profile/${id}`),
  },
  // 配置漂移（后端 /api/agent/drift，registry 开关下）
  drift: {
    list: (params?: { status?: string; agentCode?: string }) =>
      http.get<AgentConfigDrift[]>('/api/agent/drift', { params }),
    repair: (id: number, operator?: string) =>
      http.post<AgentConfigDrift>(`/api/agent/drift/${id}/repair`, { operator }),
    ignore: (id: number) => http.post<AgentConfigDrift>(`/api/agent/drift/${id}/ignore`),
  },
  // 配置包（后端 /api/agent/package，registry 开关下）
  configPackage: {
    export: (agentCodes?: string[]) =>
      http.post<string>('/api/agent/package/export', { agentCodes }),
    import: (packageJson: string) =>
      http.post<ConfigPackageImportResult>('/api/agent/package/import', { packageJson }),
  },
});
