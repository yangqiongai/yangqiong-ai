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
  ToolApprovalPolicy,
  ToolDecisionLog,
  ToolEgressRule,
  ToolPolicyTestResult,
} from '../types';
import type { MpPage } from '../types/registry';

const BASE = '/api/agent/tool-policy';

/**
 * 工具管控 API
 */
export const createToolPolicyApi = (http: HttpRequest) => ({
  policies: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<ToolApprovalPolicy>>(`${BASE}/policies`, { params }),
    save: (data: Partial<ToolApprovalPolicy>) =>
      http.post<ToolApprovalPolicy>(`${BASE}/policies`, data),
    remove: (id: number) => http.delete<void>(`${BASE}/policies/${id}`),
    changeEnabled: (id: number, enabled: number) =>
      http.put<void>(`${BASE}/policies/${id}/enabled/${enabled}`),
  },
  egressRules: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<ToolEgressRule>>(`${BASE}/egress-rules`, { params }),
    save: (data: Partial<ToolEgressRule>) =>
      http.post<ToolEgressRule>(`${BASE}/egress-rules`, data),
    remove: (id: number) => http.delete<void>(`${BASE}/egress-rules/${id}`),
    changeEnabled: (id: number, enabled: number) =>
      http.put<void>(`${BASE}/egress-rules/${id}/enabled/${enabled}`),
  },
  decisionLogs: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<ToolDecisionLog>>(`${BASE}/decision-logs`, { params }),
  },
  test: (data: { toolName: string; toolInput?: Record<string, unknown> }) =>
    http.post<ToolPolicyTestResult>(`${BASE}/test`, data),
});
