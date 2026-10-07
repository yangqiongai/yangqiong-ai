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
import { getAuthHeaders } from '../utils/helpers';
import type {
  PageQuery,
  PageResult,
  MybatisPageResult,
  WorkflowControlRequest,
  WorkflowDefinition,
  WorkflowDefinitionEntity,
  WorkflowDefinitionSaveRequest,
  WorkflowDefinitionUpdateRequest,
  WorkflowExecutionHistoryEntity,
  WorkflowExecuteRequest,
  WorkflowExecuteResult,
  WorkflowHistoryQuery,
  WorkflowInstanceStatus,
  WorkflowNodeTraceEntity,
  WorkflowPauseHistory,
  WorkflowValidateResult,
} from '../types';

/**
 * 将后端 MyBatis-Plus 分页结构转换为通用分页结果
 * @param result
 * @param params
 * @return
 */
const toPageResult = <T>(
  result: MybatisPageResult<T>,
  params?: PageQuery,
): PageResult<T> => ({
  list: result?.records ?? [],
  total: result?.total ?? 0,
  page: params?.page ?? 1,
  size: params?.size ?? 20,
});

export const createWorkflowApi = (http: HttpRequest) => {
  const listHistory = (params?: WorkflowHistoryQuery) =>
    http.get<MybatisPageResult<WorkflowExecutionHistoryEntity>>(
      '/api/workflow/history',
      { params },
    );

  return {
    definition: {
      list: async (params?: PageQuery) => {
        const result = await http.get<MybatisPageResult<WorkflowDefinitionEntity>>(
          '/api/workflow/definitions',
          {
            params: {
              pageNum: params?.page ?? 1,
              pageSize: params?.size ?? 20,
            },
          },
        );
        return toPageResult(result, params);
      },
      get: (definitionName: string) =>
        http.get<WorkflowDefinition>(`/api/workflow/definitions/${definitionName}`),
      create: (data: WorkflowDefinitionSaveRequest) =>
        http.post<Record<string, unknown>>('/api/workflow/definitions', data),
      update: (definitionName: string, data: WorkflowDefinitionUpdateRequest) =>
        http.put<boolean>(`/api/workflow/definitions/${definitionName}`, data),
      delete: (definitionName: string) =>
        http.delete<boolean>(`/api/workflow/definitions/${definitionName}`),
      toggle: (definitionName: string) =>
        http.post<boolean>(`/api/workflow/definitions/${definitionName}/toggle`),
    },
    execute: (req: WorkflowExecuteRequest) =>
      http.post<WorkflowExecuteResult>('/api/workflow/execute', req),
    // 流式执行走后端 /api/workflow/stream SSE 端点，data 为 `EVENT_TYPE:{eventJson}` 文本，
    // WORKFLOW_STARTED 事件携带 instanceId 供宿主调用暂停/终止/恢复接口，WORKFLOW_COMPLETE 事件 payload 携带完整执行结果JSON
    executeStream: async (
      req: WorkflowExecuteRequest,
      onEvent: (eventType: string, event: { nodeId?: string; nodeName?: string; payload?: string; instanceId?: string }) => void,
    ): Promise<void> => {
      const response = await fetch('/api/workflow/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        body: JSON.stringify(req),
        credentials: 'include',
      });
      if (!response.ok || !response.body) {
        throw new Error(`工作流流式请求失败: ${response.status}`);
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';
        for (const line of lines) {
          const data = line.trim();
          if (!data.startsWith('data:')) continue;
          const text = data.slice(5).trim();
          if (!text) continue;
          const sep = text.indexOf(':');
          if (sep <= 0) continue;
          const eventType = text.slice(0, sep);
          try {
            const event = JSON.parse(text.slice(sep + 1)) as {
              nodeId?: string;
              nodeName?: string;
              payload?: string;
              instanceId?: string;
            };
            onEvent(eventType, event);
          } catch {
            // 非JSON负载按错误事件透出，避免静默丢失
            onEvent(eventType, {});
          }
        }
      }
    },
    debugNode: (data: {
      definition: Record<string, unknown>;
      nodeId: string;
      mockVariables?: Record<string, unknown>;
      userId?: string;
      sessionId?: string;
    }) => http.post<WorkflowExecuteResult>('/api/workflow/debug/node', data),
    getWorkflowStatus: (instanceId: string) =>
      http.get<WorkflowInstanceStatus>(`/api/workflow/status/${instanceId}`),
    // 暂停/恢复可携带操作人与原因，后端记录暂停恢复流水做到有据可查
    resumeWorkflow: (instanceId: string, data?: WorkflowControlRequest) =>
      http.post<Record<string, unknown>>(`/api/workflow/resume/${instanceId}`, data),
    pauseWorkflow: (instanceId: string, data?: WorkflowControlRequest) =>
      http.post<boolean>(`/api/workflow/pause/${instanceId}`, data),
    // 查询实例的暂停恢复流水（按操作时间正序）
    pauseHistory: (instanceId: string) =>
      http.get<WorkflowPauseHistory[]>(`/api/workflow/pause-history/${instanceId}`),
    cancelWorkflow: (instanceId: string) =>
      http.post<boolean>(`/api/workflow/cancel/${instanceId}`),
    validateWorkflowDefinition: (definition: Record<string, unknown>) =>
      http.post<WorkflowValidateResult>('/api/workflow/validate', definition),
    listWorkflowHistory: listHistory,
    history: {
      list: async (params?: PageQuery & { definitionName?: string }) => {
        const result = await listHistory({
          pageNum: params?.page ?? 1,
          pageSize: params?.size ?? 20,
          definitionName: params?.definitionName,
        });
        return toPageResult(result, params);
      },
      get: (instanceId: string) =>
        http.get<WorkflowExecutionHistoryEntity>(`/api/workflow/history/${instanceId}`),
      nodes: (instanceId: string) =>
        http.get<WorkflowNodeTraceEntity[]>(`/api/workflow/history/${instanceId}/nodes`),
    },
  };
};
