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
 * 集成相关类型
 * 字段与后端实体对齐：IntegrationRecord (ai-platform-integration/entity，继承 ScopeEntity)
 * CallbackContext / CallbackResult (ai-platform-integration/callback)
 */

/**
 * 集成记录
 * 对应后端 com.maizi.ai.platform.integration.entity.IntegrationRecord（继承 ScopeEntity）
 */
export interface IntegrationRecord {
  id?: number;
  messageId?: string;
  channel?: string;
  status?: string;
  response?: string;
  sendTime?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 回调上下文
 * 对应后端 com.maizi.ai.platform.integration.callback.CallbackContext
 */
export interface CallbackContext {
  actionCode?: string;
  alertId?: string;
  sessionId?: string;
  taskId?: string;
  userId?: string;
  extra?: Record<string, unknown>;
}

/**
 * 回调结果
 * 对应后端 com.maizi.ai.platform.integration.callback.CallbackResult
 */
export interface CallbackResult {
  success: boolean;
  message?: string;
}

/**
 * 渠道类型选项
 */
export const INTEGRATION_CHANNEL_OPTIONS = [
  { value: 'DINGTALK', label: '钉钉' },
  { value: 'FEISHU', label: '飞书' },
  { value: 'WECOM', label: '企业微信' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'WEBHOOK', label: 'Webhook' },
] as const;

/**
 * 集成渠道配置
 * 对应后端 IntegrationChannelConfig（表 ai_integration_channel_config）
 */
export interface IntegrationChannelConfig {
  id?: string;
  channelType?: string;
  webhookUrl?: string;
  secret?: string;
  enabled?: number;
  extra?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 告警级别选项
 */
export const ALERT_LEVEL_OPTIONS = [
  { value: 'INFO', label: '提示' },
  { value: 'WARNING', label: '警告' },
  { value: 'CRITICAL', label: '严重' },
] as const;

/**
 * 卡片按钮
 * 对应后端 CardButton
 */
export interface CardButton {
  label?: string;
  actionCode?: string;
  actionType?: 'CALLBACK' | 'URL';
  value?: string;
}

/**
 * 告警规则
 * 对应后端 AlertRuleEntity（表 ai_alert_rule）
 */
export interface AlertRuleConfig {
  id?: string;
  ruleName?: string;
  eventType?: string;
  level?: string;
  throttleSeconds?: number;
  actionable?: number;
  actions?: string;
  enabled?: number;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 告警实例
 * 对应后端 AlertInstance（表 ai_alert_instance）
 */
export interface AlertInstance {
  id?: number;
  ruleId?: number;
  title?: string;
  content?: string;
  level?: string;
  status?: string;
  createTime?: string;
  resolveTime?: string;
}
