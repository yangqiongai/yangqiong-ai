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
  AuditTrailEntry,
  EvolutionMetrics,
  ExperienceEntry,
  FeedbackRecord,
  ImprovementProposal,
  PageQuery,
  PageResult,
} from '../types';

export const createEvolutionApi = (http: HttpRequest) => ({
  metrics: () => http.get<EvolutionMetrics>('/api/evolution/metrics'),
  // 返回 ResponseEntity，响应体即为业务数据，拦截器对裸对象直返，工厂兼容
  trigger: () =>
    http.post<Record<string, string>>('/api/evolution/trigger'),
  proposals: {
    pending: (limit?: number) =>
      http.get<ImprovementProposal[]>('/api/evolution/proposals/pending', {
        params: { limit },
      }),
    get: (proposalId: string) =>
      http.get<ImprovementProposal>(`/api/evolution/proposals/${proposalId}`),
  },
  experiences: {
    list: (agentCode: string, limit?: number) =>
      http.get<ExperienceEntry[]>('/api/evolution/experiences', {
        params: { agentCode, limit },
      }),
  },
  governance: {
    approve: (proposalId: string, approver: string, comment?: string) =>
      http.post<Record<string, unknown>>('/api/evolution/governance/approve', null, {
        params: { proposalId, approver, comment },
      }),
    reject: (proposalId: string, rejecter: string, reason?: string) =>
      http.post<Record<string, unknown>>('/api/evolution/governance/reject', null, {
        params: { proposalId, rejecter, reason },
      }),
    rollback: (proposalId: string) =>
      http.post<Record<string, unknown>>('/api/evolution/governance/rollback', null, {
        params: { proposalId },
      }),
    auditTrail: (proposalId: string) =>
      http.get<AuditTrailEntry[]>('/api/evolution/governance/audit-trail', {
        params: { proposalId },
      }),
  },
  feedback: {
    create: (data: Partial<FeedbackRecord>) =>
      http.post<Record<string, string>>('/api/evolution/feedback', data),
  },
});
