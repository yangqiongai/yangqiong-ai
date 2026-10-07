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
  ContextDetail,
  ContextSummary,
  SpanNode,
  TaskForkPayload,
  TaskStep,
  TraceRunQuery,
  TraceRunRow,
} from '../types';

const BASE = '/api/agent/trace';

export const createTraceApi = (http: HttpRequest) => ({
  // 后端 okPage 的 data 为数组（total 在 metaData，拦截器丢弃），实际返回列表
  runs: (params?: TraceRunQuery) =>
    http.get<TraceRunRow[]>(`${BASE}/runs`, { params }),
  spans: (traceId: string) =>
    http.get<SpanNode[]>(`${BASE}/spans`, { params: { traceId } }),
  steps: (taskId: string) =>
    http.get<TaskStep[]>(`${BASE}/steps`, { params: { taskId } }),
  deleteRun: (traceId: string) =>
    http.delete<void>(`${BASE}/runs`, { params: { traceId } }),
  replay: (taskId: string) =>
    http.post<{ originalTaskId: string; newTaskId: string }>(
      `${BASE}/replay`,
      null,
      { params: { taskId } },
    ),
  // 检查点（后端 /api/agent/run，checkpoint 开关下）
  checkpoint: {
    list: (taskId: string) =>
      http.get<Record<string, unknown>[]>(`/api/agent/run/${taskId}/checkpoints`),
    resume: (taskId: string, checkpointKey?: string) =>
      http.post<{ taskId: string }>(`/api/agent/run/${taskId}/resume`, {
        checkpointKey,
      }),
    delete: (taskId: string, key: string) =>
      http.delete<void>(`/api/agent/run/${taskId}/checkpoints/${key}`),
  },
  // 上下文快照（每轮模型调用前的完整输入）
  fetchContextList: (taskId: string) =>
    http.get<ContextSummary[]>(`${BASE}/${taskId}/contexts`),
  fetchContextDetail: (taskId: string, callSeq: number) =>
    http.get<ContextDetail>(`${BASE}/${taskId}/contexts/${callSeq}`),
  // 从指定调用轮次分叉新任务
  forkTask: (taskId: string, payload: TaskForkPayload) =>
    http.post<{ newTaskId: string }>(`${BASE}/${taskId}/fork`, payload),
});
