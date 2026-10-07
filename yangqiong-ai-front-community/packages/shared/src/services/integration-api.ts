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
import type { IntegrationRecord, PageQuery, PageResult } from '../types';
import type { IntegrationChannelConfig, AlertRuleConfig, AlertInstance } from '../types/integration';
import type { MpPage } from '../types/registry';

const CHANNEL_BASE = '/api/integration/channel';
const ALERT_RULE_BASE = '/api/integration/alert/rule';

export const createIntegrationApi = (http: HttpRequest) => ({
  list: (params?: PageQuery) =>
    http.get<PageResult<IntegrationRecord>>('/api/integration/record/list', {
      params,
    }),
  failed: (params?: PageQuery) =>
    http.get<PageResult<IntegrationRecord>>('/api/integration/record/failed', {
      params,
    }),
  channels: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<IntegrationChannelConfig>>(CHANNEL_BASE, { params }),
    save: (data: Partial<IntegrationChannelConfig>) =>
      http.post<IntegrationChannelConfig>(CHANNEL_BASE, data),
    remove: (id: string) => http.delete<void>(`${CHANNEL_BASE}/${id}`),
    changeEnabled: (id: string, enabled: number) =>
      http.put<void>(`${CHANNEL_BASE}/${id}/enabled/${enabled}`),
    test: (id: string) => http.post<boolean>(`${CHANNEL_BASE}/${id}/test`),
  },
  alertRules: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<AlertRuleConfig>>(ALERT_RULE_BASE, { params }),
    save: (data: Partial<AlertRuleConfig>) =>
      http.post<AlertRuleConfig>(ALERT_RULE_BASE, data),
    remove: (id: string) => http.delete<void>(`${ALERT_RULE_BASE}/${id}`),
    changeEnabled: (id: string, enabled: number) =>
      http.put<void>(`${ALERT_RULE_BASE}/${id}/enabled/${enabled}`),
    instances: (params?: Record<string, unknown>) =>
      http.get<MpPage<AlertInstance>>(`${ALERT_RULE_BASE}/instances`, { params }),
  },
});
