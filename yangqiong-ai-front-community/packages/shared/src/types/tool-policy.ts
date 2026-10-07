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
 * 工具管控相关类型
 * 字段与后端实体对齐：ToolApprovalPolicyEntity / ToolEgressRuleEntity /
 * ToolDecisionLogEntity / PlatformToolPolicyGate.SimulateResult (governance)
 */

/**
 * 工具审批策略动作
 */
export type ToolPolicyAction = 'DENY' | 'ASK';

/**
 * 工具审批策略
 */
export interface ToolApprovalPolicy {
  id?: number;
  agentCode?: string;
  toolPattern: string;
  action: ToolPolicyAction;
  reasonTemplate?: string;
  enabled?: number;
  remark?: string;
  scopeId?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 工具网络出口白名单规则
 */
export interface ToolEgressRule {
  id?: number;
  agentCode?: string;
  hostPattern: string;
  port?: number;
  enabled?: number;
  remark?: string;
  scopeId?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 工具管控决策
 */
export type ToolDecision =
  | 'ALLOW'
  | 'DENY'
  | 'ASK_APPROVED'
  | 'ASK_REJECTED'
  | 'EGRESS_DENIED';

/**
 * 工具管控决策日志
 */
export interface ToolDecisionLog {
  id?: number;
  agentCode?: string;
  toolName?: string;
  decision: ToolDecision;
  hitRuleId?: number;
  approvalRequestId?: string;
  toolInputDigest?: string;
  runId?: string;
  scopeId?: string;
  createTime?: string;
}

/**
 * 策略试算结果
 */
export interface ToolPolicyTestResult {
  decision: ToolDecision | 'ASK';
  hitRuleId?: number;
}
