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
import { SIGNAL_META } from '../constants/inbox-types';
import type { AgentRuntimeSummary, GovernanceSignalCounts, GovernanceSignalKey } from '../types';

/**
 * 健康分段阈值（>=85 青绿 / 60-84 琥珀 / <60 品红）
 */
export const HEALTH_GOOD_THRESHOLD = 85;

/**
 * 警告分段阈值
 */
export const HEALTH_WARN_THRESHOLD = 60;

/**
 * 健康分段
 */
export type HealthBand = 'good' | 'warn' | 'danger';

/**
 * 健康分段语义色（青绿/琥珀/品红，图表与徽标共用）
 */
export const HEALTH_BAND_COLORS: Record<HealthBand, string> = {
  good: '#34d399',
  warn: '#fbbf24',
  danger: '#fb7185',
};

/**
 * 信号扣分权重（方案 5.1.3：规则透明可解释）
 */
export const HEALTH_SIGNAL_WEIGHTS: Record<GovernanceSignalKey, number> = {
  driftDetected: 5,
  slaDegraded: 0,
  costOverrun: 0,
  traceFailures: 10,
  triggerFailures: 10,
  staleMemory: 2,
  quarantinedMemory: 2,
  rotationDue: 5,
  auditAnomalies: 10,
  pendingProposals: 3,
};

/**
 * 运行失败率扣分上限
 */
export const FAILURE_RATE_PENALTY_CAP = 40;

/**
 * 成本超预算扣分上限
 */
export const COST_OVERRUN_PENALTY_CAP = 20;

/**
 * 运行失败率扣分系数(每100%失败率扣200分)
 */
export const FAILURE_RATE_FACTOR = 200;

/**
 * 成本超预算扣分系数(超预算比例×100)
 */
export const COST_OVERRUN_FACTOR = 100;

/**
 * 健康分计算输入
 */
export interface HealthScoreInput {
  runs7d?: number | null;

  failures7d?: number | null;

  cost30d?: number | null;

  budgetAmount?: number | null;

  /**
   * 信号徽标(漂移/触发失败/记忆/轮换/审计等按Agent计数的键)
   */
  signalFlags?: GovernanceSignalCounts;

  /**
   * 进化提案待确认数(全局信号，非Agent徽标)
   */
  pendingProposals?: number | null;
}

/**
 * 健康分计算选项
 */
export interface HealthScoreOptions {
  /**
   * 社区端：企业专属信号项整项剔除(不参与计算，非按0计)
   */
  community?: boolean;
}

/**
 * 判定健康分段
 * @param score 健康分
 * @return
 */
export function healthBand(score: number): HealthBand {
  if (score >= HEALTH_GOOD_THRESHOLD) {
    return 'good';
  }
  return score >= HEALTH_WARN_THRESHOLD ? 'warn' : 'danger';
}

/**
 * 计算单个Agent健康分(下限0，规则透明可解释)
 * @param input 运行摘要输入
 * @param options 计算选项
 * @return
 */
export function computeHealthScore(input: HealthScoreInput,
                                   options?: HealthScoreOptions): number {
  const community = options?.community === true;
  const flags = input.signalFlags ?? {};
  let score = 100;

  // 信号扣减(社区端企业信号整项剔除：跳过而非按0计)
  for (const key of Object.keys(HEALTH_SIGNAL_WEIGHTS) as GovernanceSignalKey[]) {
    const weight = HEALTH_SIGNAL_WEIGHTS[key];
    if (weight <= 0) {
      continue;
    }
    if (community && SIGNAL_META[key]?.enterpriseOnly) {
      continue;
    }
    const count = key === 'pendingProposals'
        ? input.pendingProposals ?? 0
        : flags[key] ?? 0;
    if (count > 0) {
      score -= count * weight;
    }
  }

  // 运行失败率(上限-40)
  const runs = input.runs7d ?? 0;
  const failures = input.failures7d ?? 0;
  if (runs > 0 && failures > 0) {
    const rate = failures / runs;
    score -= Math.min(rate * FAILURE_RATE_FACTOR, FAILURE_RATE_PENALTY_CAP);
  }

  // 成本超预算比例(上限-20，未配置预算不计)
  const cost = input.cost30d;
  const budget = input.budgetAmount;
  if (cost != null && budget != null && budget > 0 && cost > budget) {
    const overrunRatio = cost / budget - 1;
    score -= Math.min(overrunRatio * COST_OVERRUN_FACTOR, COST_OVERRUN_PENALTY_CAP);
  }

  return Math.max(0, Math.round(score));
}

/**
 * 从Agent运行摘要构建健康分输入
 * @param agent 运行摘要
 * @return
 */
export function healthScoreInputOf(agent: AgentRuntimeSummary): HealthScoreInput {
  return {
    runs7d: agent.runs7d ?? 0,
    failures7d: agent.failures7d ?? 0,
    cost30d: agent.cost30d ?? null,
    budgetAmount: agent.budgetAmount ?? null,
    signalFlags: agent.signalFlags ?? {},
  };
}

/**
 * 计算Agent健康分明细映射
 * @param agents 运行摘要清单
 * @param options 计算选项
 * @return agentCode → 健康分
 */
export function computeHealthScores(agents: AgentRuntimeSummary[],
                                    options?: HealthScoreOptions): Map<string, number> {
  const result = new Map<string, number>();
  for (const agent of agents) {
    result.set(agent.agentCode, computeHealthScore(healthScoreInputOf(agent), options));
  }
  return result;
}

/**
 * 计算全局健康分(全部Agent健康分算术平均，无Agent时100)
 * @param agents 运行摘要清单
 * @param options 计算选项
 * @return
 */
export function computeGlobalHealth(agents: AgentRuntimeSummary[],
                                    options?: HealthScoreOptions): number {
  if (!agents.length) {
    return 100;
  }
  const total = agents.reduce((sum, agent) => sum + computeHealthScore(healthScoreInputOf(agent), options), 0);
  return Math.round(total / agents.length);
}
