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
 * 事件 Webhook 相关类型
 * 字段与后端实体对齐：WebhookConfig / IntegrationRecord (ai-platform-integration)
 */

/**
 * 可订阅的运行时事件类型（run 级）
 */
export const WEBHOOK_EVENT_OPTIONS = [
  { value: 'AGENT_START', label: 'Agent 启动' },
  { value: 'AGENT_END', label: 'Agent 结束' },
  { value: 'AGENT_RESULT', label: 'Agent 结果' },
  { value: 'COMPLETED', label: '运行完成' },
  { value: 'ERROR', label: '运行错误' },
  { value: 'REQUIRE_USER_CONFIRM', label: '待人工确认' },
  { value: 'INTERRUPTED', label: '运行中断' },
  { value: 'MODEL_CALL_START', label: '模型调用开始' },
  { value: 'MODEL_CALL_END', label: '模型调用结束' },
  { value: 'TOOL_CALL_START', label: '工具调用开始' },
  { value: 'TOOL_CALL_END', label: '工具调用结束' },
] as const;

/**
 * Webhook 事件订阅配置
 */
export interface WebhookEventConfig {
  id?: string;
  url: string;
  secret?: string;
  eventTypes?: string;
  agentFilter?: string;
  retryCount?: number;
  retryInterval?: number;
  timeout?: number;
  enabled?: number;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * Webhook 投递记录状态
 */
export type WebhookRecordStatus =
  | 'SENDING'
  | 'SUCCESS'
  | 'FAILED'
  | 'SKIPPED';
