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
import type { IntegrationRecord } from '../types';
import type { WebhookEventConfig } from '../types/webhook';
import type { MpPage } from '../types/registry';

const BASE = '/api/agent/webhook';

/**
 * Agent 事件 Webhook API
 */
export const createWebhookApi = (http: HttpRequest) => ({
  configs: {
    page: (params?: Record<string, unknown>) =>
      http.get<MpPage<WebhookEventConfig>>(BASE, { params }),
    save: (data: Partial<WebhookEventConfig>) =>
      http.post<WebhookEventConfig>(BASE, data),
    remove: (id: string) => http.delete<void>(`${BASE}/${id}`),
    changeEnabled: (id: string, enabled: number) =>
      http.put<void>(`${BASE}/${id}/enabled/${enabled}`),
    test: (id: string) => http.post<boolean>(`${BASE}/${id}/test`),
    records: (id: string, params?: Record<string, unknown>) =>
      http.get<MpPage<IntegrationRecord>>(`${BASE}/${id}/records`, { params }),
    resend: (recordId: number) =>
      http.post<boolean>(`${BASE}/records/${recordId}/resend`),
  },
});
