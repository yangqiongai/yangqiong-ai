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
 * 运行回放类型
 * 字段与后端对齐：AgentTraceController /runs /spans /steps 返回结构 (agent-data trace)
 */

/**
 * 运行列表行(根Span联查任务信息)
 */
export interface TraceRunRow {
  traceId: string;
  taskId?: string;
  agentCode?: string;
  sessionId?: string;
  status?: string;
  errorMessage?: string;
  durationMs?: number;
  startTime?: string;
  spanCount?: number;
  taskStatus?: string;
  userInput?: string;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  parentTaskId?: string;
  forkCallSeq?: number;
}

/**
 * Span树节点
 */
export interface SpanNode {
  spanId: string;
  parentSpanId?: string;
  traceId: string;
  operation?: string;
  status?: string;
  errorMessage?: string;
  durationMs?: number;
  startTime?: string;
  attributes?: Record<string, unknown> | string | null;
  children: SpanNode[];
}

/**
 * 任务步骤时间线节点
 */
export interface TaskStep {
  id?: number;
  taskId?: string;
  stepOrder?: number;

  /**
   * 模型调用序号（仅LLM_CALL步骤返回，从1递增，与ContextSnapshot.callSeq同口径）
   */
  callSeq?: number;
  stepType?: string;
  agentName?: string;
  stepContent?: string;
  toolName?: string;
  toolInput?: string;
  toolOutput?: string;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  durationMs?: number;
  createTime?: string;
}

/**
 * 运行列表查询参数
 */
export interface TraceRunQuery {
  agentCode?: string;
  taskId?: string;
  status?: string;
  start?: string;
  end?: string;
  pageNum?: number;
  pageSize?: number;
}

/**
 * 上下文快照消息
 */
export interface ContextMessage {
  role?: string;
  content?: string;
  source?: string;
  truncated?: boolean;
}

/**
 * 上下文快照摘要
 */
export interface ContextSummary {
  callSeq?: number;
  modelCode?: string;
  msgCount?: number;
  totalChars?: number;
  createdAt?: string;
}

/**
 * 上下文快照明细
 */
export interface ContextDetail {
  taskId?: string;
  callSeq?: number;
  modelCode?: string;
  messages?: ContextMessage[];
}

/**
 * 轨迹分叉请求
 */
export interface TaskForkPayload {
  callSeq?: number;
  userInputOverride?: string;
  remark?: string;
}
