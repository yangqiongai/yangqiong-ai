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
  EvalCompareResult,
  EvalDataset,
  EvalDatasetCase,
  EvalRun,
  EvalRunCase,
  EvalRunTriggerRequest,
  EvalDatasetSaveRequest,
  EvaluationReport,
  PageResult,
} from '../types';

const BASE = '/api/agent/evaluation';

// 后端 okPage 的 data 为数组（total 在 metaData，拦截器丢弃），满页时总数+1 支持翻页
const toPageResult = <T>(list: T[], pageNum = 1, pageSize = 10): PageResult<T> => {
  const page = Math.max(1, pageNum);
  const size = Math.max(1, pageSize);
  const total = list.length === size ? page * size + 1 : (page - 1) * size + list.length;
  return { list, total, page, size };
};

export const createEvaluationApi = (http: HttpRequest) => ({
  dataset: {
    page: async (params?: {
      keyword?: string;
      status?: string;
      pageNum?: number;
      pageSize?: number;
    }) => {
      const list =
        (await http.get<EvalDataset[]>(`${BASE}/datasets`, { params })) ?? [];
      return toPageResult(list, params?.pageNum, params?.pageSize);
    },
    create: (data: EvalDatasetSaveRequest) =>
      http.post<EvalDataset>(`${BASE}/datasets`, data),
    update: (id: number, data: EvalDatasetSaveRequest) =>
      http.put<EvalDataset>(`${BASE}/datasets/${id}`, data),
    delete: (id: number) => http.delete<void>(`${BASE}/datasets/${id}`),
    cases: (id: number) => http.get<EvalDatasetCase[]>(`${BASE}/datasets/${id}/cases`),
  },
  run: {
    trigger: (data: EvalRunTriggerRequest) =>
      http.post<{ runId: number; status: string }>(`${BASE}/runs`, data),
    page: async (params?: {
      agentCode?: string;
      datasetCode?: string;
      status?: string;
      pageNum?: number;
      pageSize?: number;
    }) => {
      const list = (await http.get<EvalRun[]>(`${BASE}/runs`, { params })) ?? [];
      return toPageResult(list, params?.pageNum, params?.pageSize);
    },
    get: (id: number, includeReport?: boolean) =>
      http.get<EvalRun>(`${BASE}/runs/${id}`, { params: { includeReport: includeReport ?? false } }),
    cases: async (id: number, params?: { pageNum?: number; pageSize?: number }) => {
      const list =
        (await http.get<EvalRunCase[]>(`${BASE}/runs/${id}/cases`, { params })) ?? [];
      return toPageResult(list, params?.pageNum, params?.pageSize);
    },
    cancel: (id: number) => http.post<void>(`${BASE}/runs/${id}/cancel`),
    compare: (leftId: number, rightId: number) =>
      http.get<EvalCompareResult>(`${BASE}/runs/compare`, { params: { leftId, rightId } }),
  },
  // 手动评测（单例/JSON批量/文件导入，同步返回报告）
  evaluate: (dataset: EvalDataset) => http.post<EvaluationReport>(`${BASE}/evaluate`, dataset),
  evaluateJson: (json: string) => http.post<EvaluationReport>(`${BASE}/evaluate/json`, json),
  evaluateFromFile: (filePath: string) =>
    http.post<EvaluationReport>(`${BASE}/evaluate/file`, null, { params: { filePath } }),
});
