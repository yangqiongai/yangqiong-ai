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
 * 治理驾驶舱类型（与后端 GovernanceDashboardSummaryResponse 对齐）
 */

/**
 * 治理信号键（十类，双端按 scope 裁剪）
 */
export type GovernanceSignalKey =
  | 'driftDetected'
  | 'slaDegraded'
  | 'costOverrun'
  | 'traceFailures'
  | 'triggerFailures'
  | 'staleMemory'
  | 'quarantinedMemory'
  | 'rotationDue'
  | 'auditAnomalies'
  | 'pendingProposals';

/**
 * 信号计数表（键=信号类型，值=待处置数量）
 */
export type GovernanceSignalCounts = Partial<Record<GovernanceSignalKey, number>>;

/**
 * 信号近7天逐日计数（下标0=6天前，下标6=今天）
 */
export type GovernanceSignalTrend = Partial<Record<GovernanceSignalKey, number[]>>;

/**
 * Agent运行摘要（星系图节点映射数据源）
 */
export interface AgentRuntimeSummary {
  agentCode: string;

  agentName: string;

  /**
   * 状态(0-禁用 1-启用)
   */
  status?: number | null;

  /**
   * 最近一次发布时间(yyyy-MM-dd HH:mm:ss)
   */
  latestPublishTime?: string | null;

  /**
   * 最近一次发布流水号
   */
  latestReleaseNo?: string | null;

  /**
   * 近7天运行次数
   */
  runs7d?: number | null;

  /**
   * 近7天失败次数
   */
  failures7d?: number | null;

  /**
   * 近30天成本USD
   */
  cost30d?: number | null;

  /**
   * 月度预算USD(未配置为null，成本超限项不计)
   */
  budgetAmount?: number | null;

  /**
   * 信号徽标(键=信号类型，值=数量)
   */
  signalFlags?: GovernanceSignalCounts;
}

/**
 * 治理信号事件条目（雷达流/Inbox 同源）
 */
export interface GovernanceRecentItem {
  type: GovernanceSignalKey;

  agentCode?: string | null;

  agentName?: string | null;

  /**
   * 一行摘要
   */
  summary?: string | null;

  /**
   * 发生时间(yyyy-MM-dd HH:mm:ss)
   */
  occurredAt?: string | null;

  /**
   * 深链路径(可空，如/agent-runs?traceId=xxx，前端渲染"查看"跳转)
   */
  link?: string | null;

  /**
   * 失败模式键(可空，处置回写定位用)
   */
  patternKey?: string | null;
}

/**
 * 趋势地平线（近7天运行量与成本，下标0=6天前）
 */
export interface GovernanceTrendSeries {
  runs7d?: number[];

  cost7d?: number[];
}

/**
 * 驾驶舱summary聚合响应（单请求驱动全部区块）
 */
export interface GovernanceDashboardSummary {
  signals: GovernanceSignalCounts;

  signalTrend7d: GovernanceSignalTrend;

  agents: AgentRuntimeSummary[];

  recent: GovernanceRecentItem[];

  trend: GovernanceTrendSeries;

  /**
   * 数据生成时间(yyyy-MM-dd HH:mm:ss)
   */
  generatedAt: string;
}
