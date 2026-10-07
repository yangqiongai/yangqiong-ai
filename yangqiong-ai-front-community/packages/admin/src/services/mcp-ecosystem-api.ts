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
import type { PageResult } from '@yangqiong/shared';
import { request } from './api-client';

/**
 * MCP 出口白名单
 * 字段与后端 McpServerExpose 实体对齐
 */
export interface McpServerExposeInfo {
  id?: number;

  scopeId?: string;

  /**
   * 暴露类型(TOOL/AGENT/PROMPT/RESOURCE)
   */
  exposeType?: string;

  /**
   * 暴露编码(工具编码/代理编码/能力编码/资源来源键)
   */
  exposeCode?: string;

  /**
   * 对外显示名称
   */
  displayName?: string;

  /**
   * 对外描述（可含 [ui:form]/[ui:chart] 渲染提示标记）
   */
  description?: string;

  /**
   * 是否允许MCP Apps渲染(0否/1是)
   */
  renderAllowed?: number;

  /**
   * 是否启用(0否/1是)
   */
  enabled?: number;

  /**
   * 备注
   */
  remark?: string;
}

/**
 * A2A 代理卡
 * 字段与后端 AgentCardController(well-known 端点) 返回结构对齐
 */
export interface A2aAgentCardInfo {
  name?: string;

  description?: string;

  version?: string;

  url?: string;

  preferredTransport?: string;

  supportedInterfaces?: { transport?: string; url?: string }[];

  capabilities?: { streaming?: boolean; pushNotifications?: boolean };

  defaultInputModes?: string[];

  defaultOutputModes?: string[];

  skills?: { id?: string; name?: string; description?: string; tags?: string[] }[];

  /**
   * 签名卡签名段(JWS，卡签名开启时返回)
   */
  signatures?: { protectedHeader?: string; signature?: string }[];

  /**
   * 公钥指纹（后端契约预留，未返回时前端不展示）
   */
  publicKeyFingerprint?: string;
}

/**
 * Agent 运行记忆条目
 * 字段与后端 AgentMemoryEntryInfo 对齐
 */
export interface AgentMemoryEntryInfo {
  id?: number;

  agentCode?: string;

  userAnchor?: string;

  /**
   * 记忆类型(EPISODIC/SEMANTIC/PROCEDURAL)
   */
  memoryType?: string;

  content?: string;

  sourceTaskId?: string;

  sourceUser?: string;

  /**
   * 置信度(0-1)
   */
  confidence?: number;

  /**
   * 状态(ACTIVE/STALE/QUARANTINED/ERASED)
   */
  status?: string;

  accessCount?: number;

  lastAccessedAt?: string;

  versionNo?: number;

  createTime?: string;

  updateTime?: string;
}

/**
 * Agent 记忆分页结果
 */
export interface AgentMemoryPageResult {
  total: number;

  page: number;

  size: number;

  records: AgentMemoryEntryInfo[];
}

/**
 * Agent 触发规则
 * 字段与后端 AgentTriggerEntity 对齐
 */
export interface AgentTriggerInfo {
  id?: number;

  scopeId?: string;

  /**
   * 触发器编码(唯一)
   */
  triggerCode?: string;

  name?: string;

  /**
   * 触发类型(CRON/EVENT/WEBHOOK/FILE)
   */
  triggerType?: string;

  agentCode?: string;

  userAnchor?: string;

  /**
   * CRON表达式(CRON类型必填)
   */
  cronExpr?: string;

  /**
   * 内置事件源(EVENT类型)
   */
  eventSource?: string;

  /**
   * 输入指令模板，支持 {payload} 占位符
   */
  payloadTemplate?: string;

  /**
   * 文件监听根目录(FILE类型，企业增强)
   */
  watchDir?: string;

  /**
   * 文件后缀过滤(逗号分隔，企业增强)
   */
  fileSuffixes?: string;

  notifyWebhook?: string;

  dailyQuota?: number;

  dedupWindowSeconds?: number;

  /**
   * 是否启用(1启用/0停用)
   */
  enabled?: number;

  lastFireTime?: string;
}

/**
 * Agent 触发器分页结果
 */
export interface AgentTriggerPageResult {
  total: number;

  page: number;

  size: number;

  records: AgentTriggerInfo[];
}

/**
 * Agent 治理生态接口集合（MCP出口/A2A卡片/Agent记忆/触发器）
 */
export const mcpEcosystemApi = {
  expose: {
    // 后端返回全量数组，此处按筛选条件过滤后做内存分页
    page: async (
      params?: { page?: number; size?: number; exposeType?: string; exposeCode?: string },
    ): Promise<PageResult<McpServerExposeInfo>> => {
      const list = await request.get<McpServerExposeInfo[]>('/api/ecosystem/mcp/expose', {
        params: { exposeType: params?.exposeType, exposeCode: params?.exposeCode },
      });
      const page = params?.page ?? 1;
      const size = params?.size ?? 10;
      const start = (page - 1) * size;
      return {
        list: (list ?? []).slice(start, start + size),
        total: list?.length ?? 0,
        page,
        size,
      };
    },
    // 保存为 upsert 语义(类型+编码唯一，存在即覆盖)
    save: (data: Partial<McpServerExposeInfo>) =>
      request.post<McpServerExposeInfo>('/api/ecosystem/mcp/expose', data),
    toggle: (id: number | string) =>
      request.post<void>(`/api/ecosystem/mcp/expose/${id}/toggle`),
    remove: (id: number | string) =>
      request.delete<void>(`/api/ecosystem/mcp/expose/${id}`),
    // 可暴露的运行时工具选项(与对外MCP工具索引同一标识)
    toolOptions: () =>
      request.get<{ name: string; description?: string }[]>(
        '/api/ecosystem/mcp/expose/tool-options',
      ),
  },
  card: {
    // 后端仅提供 /.well-known/agent-card.json 动态端点，无独立预览接口，预览复用该端点
    preview: (agentCode: string) =>
      request.get<A2aAgentCardInfo>('/.well-known/agent-card.json', {
        params: { agentCode },
      }),
  },
  agentMemory: {
    list: (params?: {
      agentCode?: string;
      memoryType?: string;
      status?: string;
      page?: number;
      size?: number;
    }) => request.get<AgentMemoryPageResult>('/api/agent-memory/entries', { params }),
    // 社区版仅提供归档处置(后端归档后条目进入 STALE 状态)
    archive: (id: number | string) =>
      request.post<AgentMemoryEntryInfo>(`/api/agent-memory/entries/${id}/archive`),
  },
  trigger: {
    list: (params?: {
      triggerType?: string;
      agentCode?: string;
      enabled?: number;
      page?: number;
      size?: number;
    }) => request.get<AgentTriggerPageResult>('/api/agent-trigger/list', { params }),
    create: (data: Partial<AgentTriggerInfo>) =>
      request.post<AgentTriggerInfo>('/api/agent-trigger', data),
    update: (data: Partial<AgentTriggerInfo>) =>
      request.put<void>('/api/agent-trigger', data),
    toggle: (id: number | string, enabled: boolean) =>
      request.put<void>(`/api/agent-trigger/${id}/enabled/${enabled ? 1 : 0}`),
    remove: (id: number | string) => request.delete<void>(`/api/agent-trigger/${id}`),
  },
};
