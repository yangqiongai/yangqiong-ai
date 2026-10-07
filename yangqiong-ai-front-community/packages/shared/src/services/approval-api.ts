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
import type { PageQuery, PageResult, PendingRequestInfo } from '../types';

/**
 * 审批列表查询参数（后端仅支持待审批查询，其余条件由前端过滤）
 */
export type ApprovalListQuery = PageQuery & {
  keyword?: string;
  status?: string;
  resourceType?: string;
};

export const createApprovalApi = (http: HttpRequest) => ({
  // 后端 /api/approval/pending 返回全部待审批（非分页），此处做前端过滤+分页包装
  list: async (params?: ApprovalListQuery) => {
    const pending = (await http.get<PendingRequestInfo[]>('/api/approval/pending')) ?? [];
    const keyword = (params?.keyword ?? '').trim().toLowerCase();
    const status = params?.status;
    const resourceType = params?.resourceType;
    const filtered = pending.filter((t) => {
      if (status && status !== 'PENDING' && t.status !== status) {
        return false;
      }
      if (resourceType && t.resourceType !== resourceType) {
        return false;
      }
      if (keyword) {
        const hit = [t.requestId, t.userId, t.targetName].some(
          (v) => typeof v === 'string' && v.toLowerCase().includes(keyword),
        );
        if (!hit) {
          return false;
        }
      }
      return true;
    });
    const page = params?.page ?? 1;
    const size = params?.size ?? 10;
    const start = (page - 1) * size;
    return {
      list: filtered.slice(start, start + size),
      total: filtered.length,
      page,
      size,
    } as PageResult<PendingRequestInfo>;
  },
  get: (requestId: string) => http.get<PendingRequestInfo>(`/api/approval/${requestId}`),
  // 后端按 approve/reject 端点处理审批决策
  handle: (
    decision: {
      requestId: string;
      approved: boolean;
      rejectReason?: string;
      approvedBy?: string;
      responsePayload?: unknown;
    },
  ) =>
    decision.approved
      ? http.post<Record<string, unknown>>(
          `/api/approval/${decision.requestId}/approve`,
          {
            approvedBy: decision.approvedBy ?? 'admin',
            responsePayload: decision.responsePayload,
          },
        )
      : http.post<Record<string, unknown>>(
          `/api/approval/${decision.requestId}/reject`,
          {
            rejectedBy: decision.approvedBy ?? 'admin',
            reason: decision.rejectReason ?? '',
          },
        ),
  // SSE 订阅：不走 axios，直接使用相对路径的 EventSource
  // 后端以命名事件 approval_required 推送，需用 addEventListener 监听（默认 message 收不到）
  subscribeStream: (
    sessionId: string,
    onMessage: (data: string) => void,
    onError?: (event: Event) => void,
  ): EventSource => {
    const source = new EventSource(`/api/approval/stream/${sessionId}`);
    source.addEventListener('approval_required', (event) =>
      onMessage((event as MessageEvent).data),
    );
    source.addEventListener('message', (event) =>
      onMessage((event as MessageEvent).data),
    );
    if (onError) {
      source.onerror = onError;
    }
    return source;
  },
});
