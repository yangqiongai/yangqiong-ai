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
 * Agent 相关类型
 * 字段与后端实体对齐：AgentTypeInfo / AgentResult (ai-agent-core)
 * SchedulerInfo (ai-agent-local) / SchedulerController 内部请求类 (ai-platform-api)
 */

/**
 * Agent 类型信息
 * 对应后端 com.maizi.ai.agent.core.model.AgentTypeInfo
 */
export interface AgentTypeInfo {
  id?: number;
  typeCode: string;
  typeName: string;
  typeDescription?: string;
  typeIcon?: string;
  typeCategory?: string;

  /**
   * 所属目录编码（空为未分类，目录树管理用）
   */
  directoryCode?: string;

  typeStatus?: number;
  typeOrder?: number;
  sessionType?: string;
  agentConfig?: string;

  /**
   * 作用域ID（default为平台共享，租户自建为租户ID）
   */
  scopeId?: string;

  /**
   * 平台下发模板标记（1-平台域共享模板，创建租户时复制到租户域；0-普通Agent）
   */
  shared?: number;

  /**
   * 复制来源Agent编码（租户域副本专用，平台域模板为空）
   */
  originAgentCode?: string;

  /**
   * 是否支持图片(1-支持 0-不支持，取自Agent绑定模型的支持能力)
   */
  supportImage?: number;

  remark?: string;
  updateTime?: string;
}

/**
 * 智能体目录树节点
 * 对应后端 AgentDirectoryService.DirectoryNode
 */
export interface AgentDirectoryNode {
  id: number;
  code: string;
  name: string;
  parentId?: number | null;
  sortNum?: number;
  agentCount?: number;
  children: AgentDirectoryNode[];
}

/**
 * 智能体目录树查询结果（含未分类计数）
 */
export interface AgentDirectoryTree {
  nodes: AgentDirectoryNode[];
  ungroupedCount: number;
}

/**
 * 智能体目录节点保存请求（create/update 共用，编码创建后不可修改）
 */
export interface AgentDirectorySaveRequest {
  code?: string;
  name: string;
  parentId?: number | null;
  sortNum?: number;
}

/**
 * Agent 对话请求
 * 对应后端 com.maizi.ai.agent.core.model.request.AgentRequest
 */
export interface AgentChatRequest {
  agentCode: string;
  sessionId?: string;
  input?: unknown[];
  userId?: string;
  scopeId?: string;
  body?: Record<string, unknown>;
}

/**
 * Agent 对话响应
 * 对应后端 com.maizi.ai.agent.core.model.result.AgentResult
 */
export interface AgentChatResponse {
  output?: unknown[];
  finalPayload?: Record<string, unknown>;
  body?: Record<string, unknown>;
  success: boolean;
  errorMessage?: string;
  tokenMetrics?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
  paused?: boolean;
  pausedRequestId?: string;
  quotaWarning?: string;
}

/**
 * 澄清提问信息
 * 对应后端 clarification_required SSE 事件 payload
 */
export interface ClarificationRequestInfo {
  /**
   * 触发提问的工具调用ID
   */
  toolCallId: string;

  /**
   * AI 的问题
   */
  question: string;

  /**
   * 候选选项列表（可选，引擎结构化透传，渲染为可点击选择按钮）
   */
  options?: string[];
}

/**
 * 待确认工具调用项
 * 对应后端 confirm_required SSE 事件 payload 中 toolCalls 数组元素
 */
export interface ConfirmToolCallInfo {
  /**
   * 工具调用ID
   */
  toolUseId: string;

  /**
   * 工具名称
   */
  toolName: string;

  /**
   * 工具入参
   */
  input?: Record<string, unknown>;
}

/**
 * 引擎工具确认请求信息
 * 对应后端 confirm_required SSE 事件 payload
 */
export interface ConfirmRequestInfo {
  /**
   * 请求ID（即会话ID，批准/拒绝时回传）
   */
  requestId: string;

  /**
   * 会话ID
   */
  sessionId: string;

  /**
   * 待确认工具调用清单
   */
  toolCalls: ConfirmToolCallInfo[];
}

/**
 * 预算/配额告警信息
 * 对应后端 budget_warning SSE 事件 payload（budgetType: TOKEN/COST=run内预算, QUOTA=事前配额）
 */
export interface QuotaWarningInfo {
  /**
   * 告警类型
   */
  budgetType?: string;

  /**
   * 告警级别（WARN/EXCEEDED）
   */
  level?: string;

  /**
   * 告警的Agent编码（QUOTA类型携带）
   */
  agentCode?: string;

  /**
   * 告警说明
   */
  message?: string;
}

/**
 * 调度任务
 * 对应后端 com.maizi.ai.agent.local.model.SchedulerInfo
 */
export interface SchedulerJob {
  id?: number;
  scheduleId: string;
  userId?: string;
  taskName?: string;
  agentCode: string;
  cronExpression: string;
  inputText?: string;
  description?: string;
  body?: string;
  enabled?: boolean;
  nextFireTime?: string;
  lastFireTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 创建调度任务请求
 * 对应后端 SchedulerController.CreateScheduleRequest
 */
export interface CreateScheduleRequest {
  userId?: string;
  taskName?: string;
  agentCode: string;
  cronExpression: string;
  inputText?: string;
  description?: string;
  body?: Record<string, unknown>;
}

/**
 * 更新调度任务请求
 * 对应后端 SchedulerController.UpdateScheduleRequest
 */
export interface UpdateScheduleRequest {
  taskName?: string;
  cronExpression?: string;
  inputText?: string;
  description?: string;
  body?: Record<string, unknown>;
}

/**
 * 调度执行历史记录
 * 对应后端 ScheduleLogInfo
 */
export interface ScheduleLog {
  id?: number | string;
  scheduleId?: string;
  agentCode?: string;
  userId?: string;
  inputText?: string;
  outputText?: string;
  success?: boolean;
  errorMessage?: string;
  durationMs?: number;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  fireTime?: string;
  createTime?: string;
}

/**
 * 调度执行历史分页
 */
export interface ScheduleLogPage {
  total: number;
  list: ScheduleLog[];
}
