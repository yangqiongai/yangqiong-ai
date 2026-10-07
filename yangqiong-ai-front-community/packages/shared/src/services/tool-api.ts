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
/**
 * 工具配置与MCP服务端接口
 */
import type { HttpRequest } from './http';
import type { CategorySaveRequest, CategoryTreeNode, PageQuery, PageResult, ToolConfigInfo, ToolUsageInfo, McpServerConfigInfo, McpToolInfo, McpConnectionTestResult } from '../types';

export interface ToolCreateRequest {
  toolCode: string;
  toolName: string;
  toolDesc?: string;
  toolType: string;
  toolConfig?: string;
  toolStatus?: number;
  remark?: string;
  category?: string;
}

export interface ToolUpdateRequest {
  toolName?: string;
  toolDesc?: string;
  toolType?: string;
  toolConfig?: string;
  toolStatus?: number;
  remark?: string;
  category?: string;
}

export interface McpServerCreateRequest {
  serverCode: string;
  serverName: string;
  transportType: string;
  connectionConfig?: string;
  enabledTools?: string;
  serverStatus?: number;
  remark?: string;
  category?: string;
}

export interface McpServerUpdateRequest {
  serverName?: string;
  transportType?: string;
  connectionConfig?: string;
  enabledTools?: string;
  serverStatus?: number;
  remark?: string;
  category?: string;
}

/**
 * 将后端 MyBatis-Plus Page（records/current/size/total）包装为统一 PageResult
 * @param res
 * @param params
 * @return
 */
function toPageResult<T>(res: { records?: T[]; total?: number; current?: number; size?: number } | null | undefined, params?: PageQuery): PageResult<T> {
  return {
    list: res?.records ?? [],
    total: res?.total ?? 0,
    page: res?.current ?? params?.page ?? 1,
    size: res?.size ?? params?.size ?? 10,
  };
}

/**
 * 对未分页的后端全量列表做客户端过滤与分页，包装为统一 PageResult
 * @param items
 * @param params
 * @param match
 * @return
 */
function toClientPageResult<T>(items: T[], params: PageQuery | undefined, match: (item: T, keyword: string) => boolean): PageResult<T> {
  const keyword = (params?.keyword ?? '').trim().toLowerCase();
  const filtered = keyword ? items.filter((item) => match(item, keyword)) : items;
  const page = params?.page ?? 1;
  const size = params?.size ?? (filtered.length || 10);
  const start = (page - 1) * size;
  return {
    list: filtered.slice(start, start + size),
    total: filtered.length,
    page,
    size,
  };
}

/**
 * 将前端表单的 connectionConfig JSON 字符串转换为后端对象，enabledTools 逗号串/JSON串转换为数组
 * @param payload
 * @return
 */
function toMcpServerPayload(payload: McpServerCreateRequest | McpServerUpdateRequest): Record<string, unknown> {
  const body: Record<string, unknown> = { ...payload, connectionConfig: undefined, enabledTools: undefined };
  const configText = payload.connectionConfig;
  if (configText && configText.trim()) {
    try {
      body.connectionConfig = JSON.parse(configText);
    } catch {
      // 非法JSON直接透传，由后端校验报错
      body.connectionConfig = configText;
    }
  }
  const toolsText = payload.enabledTools;
  if (typeof toolsText === 'string' && toolsText.trim()) {
    const text = toolsText.trim();
    if (text.startsWith('[')) {
      try {
        body.enabledTools = JSON.parse(text);
      } catch {
        body.enabledTools = text.split(',').map((item) => item.trim()).filter(Boolean);
      }
    } else {
      body.enabledTools = text.split(',').map((item) => item.trim()).filter(Boolean);
    }
  }
  return body;
}

export const createToolApi = (http: HttpRequest) => ({
  config: {
    list: (params?: PageQuery & { categoryCode?: string }) =>
      http
        .get<{ records?: ToolConfigInfo[]; total?: number; current?: number; size?: number }>('/api/tool-config/list', {
          params: {
            page: params?.page,
            size: params?.size,
            keyword: params?.keyword,
            categoryCode: params?.categoryCode || undefined,
          },
        })
        .then((res) => toPageResult<ToolConfigInfo>(res, params)),
    get: (toolCode: string) => http.get<ToolConfigInfo | null>(`/api/tool-config/${toolCode}`),
    create: (data: ToolCreateRequest) => http.post<ToolConfigInfo | null>('/api/tool-config', data),
    update: (toolCode: string, data: ToolUpdateRequest) =>
      http.put<ToolConfigInfo | null>(`/api/tool-config/${toolCode}`, data),
    delete: (toolCode: string) => http.delete<void>(`/api/tool-config/${toolCode}`),
    toggle: (toolCode: string) => http.post<unknown>(`/api/tool-config/toggle/${toolCode}`),
    category: {
      tree: () =>
        http.get<{ nodes: CategoryTreeNode[]; ungroupedCount: number }>(
          '/api/tool-config/category/tree'
        ),
      create: (data: CategorySaveRequest) => http.post<unknown>('/api/tool-config/category', data),
      update: (id: number, data: CategorySaveRequest) =>
        http.put<unknown>(`/api/tool-config/category/${id}`, data),
      delete: (id: number) => http.delete<void>(`/api/tool-config/category/${id}`),
    },
  },
  mcp: {
    list: (params?: PageQuery & { categoryCode?: string }) =>
      http
        .get<McpServerConfigInfo[]>('/api/mcp/server', { params: { categoryCode: params?.categoryCode || undefined } })
        .then((items) =>
          toClientPageResult<McpServerConfigInfo>(items ?? [], params, (item, keyword) =>
            item.serverCode?.toLowerCase().includes(keyword) || item.serverName?.toLowerCase().includes(keyword))),
    get: (serverCode: string) => http.get<McpServerConfigInfo | null>(`/api/mcp/server/${serverCode}`),
    create: (data: McpServerCreateRequest) =>
      http.post<McpServerConfigInfo | null>('/api/mcp/server', toMcpServerPayload(data)),
    update: (serverCode: string, data: McpServerUpdateRequest) =>
      http.put<McpServerConfigInfo | null>(`/api/mcp/server/${serverCode}`, toMcpServerPayload(data)),
    delete: (serverCode: string) => http.delete<void>(`/api/mcp/server/${serverCode}`),
    tools: (serverCode: string) => http.get<McpToolInfo[]>(`/api/mcp/server/${serverCode}/tools`),
    test: (serverCode: string) => http.post<McpConnectionTestResult | null>(`/api/mcp/server/${serverCode}/test`),
    category: {
      tree: () =>
        http.get<{ nodes: CategoryTreeNode[]; ungroupedCount: number }>(
          '/api/mcp/server/category/tree'
        ),
      create: (data: CategorySaveRequest) => http.post<unknown>('/api/mcp/server/category', data),
      update: (id: number, data: CategorySaveRequest) =>
        http.put<unknown>(`/api/mcp/server/category/${id}`, data),
      delete: (id: number) => http.delete<void>(`/api/mcp/server/category/${id}`),
    },
  },
  usage: {
    top: async (params?: PageQuery) => {
      const size = params?.size ?? 20;
      const items = await http.get<ToolUsageInfo[]>('/api/agent/tool/usage/top', { params: { limit: size } });
      const keyword = (params?.keyword ?? '').trim().toLowerCase();
      if (!keyword) return items ?? [];
      return (items ?? []).filter((item) =>
        item.toolCode?.toLowerCase().includes(keyword));
    },
    get: (toolCode: string) => http.get<ToolUsageInfo | null>(`/api/agent/tool/usage/${toolCode}`),
  },
});
