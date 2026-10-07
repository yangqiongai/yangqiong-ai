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
import { useMemo } from 'react';
import { AlertTriangle } from 'lucide-react';
import type { AgentRuntimeSummary } from '../../types';

/**
 * 指标格语义色(失败率阈值：0-2%健康 2-5%关注 >5%风险)
 */
function failureRateColor(rate: number): string {
  if (rate < 0.02) return '#34d399';
  if (rate < 0.05) return '#fbbf24';
  return '#fb7185';
}

/**
 * 紧凑数字显示(过万缩写为"x.x万"，避免长数字撑破版面；后端Long序列化为字符串，求和前需Number化)
 * @param value
 * @return
 */
function compactNumber(value: number): string {
  if (value >= 10000) {
    return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
  }
  return value.toLocaleString();
}

/**
 * 英雄区指标带(驾驶舱上半屏中部：运行规模与质量KPI，健康环与信号蜂巢之间的信息补位)
 * @param props
 * @return
 */
export function HeroMetrics(props: { agents: AgentRuntimeSummary[] }): JSX.Element {
  const { agents } = props;

  const metrics = useMemo(() => {
    const total = agents.length;
    const active = agents.filter((agent) => agent.status === 1).length;
    const runs7d = agents.reduce((sum, agent) => sum + Number(agent.runs7d ?? 0), 0);
    const failures7d = agents.reduce((sum, agent) => sum + Number(agent.failures7d ?? 0), 0);
    const failureRate = runs7d > 0 ? failures7d / runs7d : 0;
    const cost30d = agents.reduce((sum, agent) => sum + Number(agent.cost30d ?? 0), 0);
    const budgetAgents = agents.filter((agent) => agent.budgetAmount != null);
    const budgetTotal = budgetAgents.reduce((sum, agent) => sum + Number(agent.budgetAmount ?? 0), 0);
    return { total, active, runs7d, failures7d, failureRate, cost30d, budgetTotal: budgetTotal > 0 ? budgetTotal : null };
  }, [agents]);

  const cells: Array<{ label: string; value: string; color?: string; hint?: string; warn?: boolean }> = [
    { label: 'Agent 总数', value: String(metrics.total), hint: `启用 ${metrics.active}` },
    { label: '近 7 天运行', value: compactNumber(metrics.runs7d), hint: `失败 ${compactNumber(metrics.failures7d)}` },
    {
      label: '近 7 天失败率',
      value: `${(metrics.failureRate * 100).toFixed(1)}%`,
      color: failureRateColor(metrics.failureRate),
      warn: metrics.failureRate >= 0.05,
    },
    {
      label: '近 30 天成本',
      value: `$${metrics.cost30d.toFixed(2)}`,
      hint: metrics.budgetTotal != null ? `预算 $${metrics.budgetTotal.toFixed(0)}` : undefined,
      warn: metrics.budgetTotal != null && metrics.cost30d > metrics.budgetTotal,
    },
  ];

  return (
    <div
      className="grid flex-1 grid-cols-2 gap-y-5 self-center px-2 lg:grid-cols-4 lg:divide-x lg:divide-white/10"
      data-testid="hero-metrics"
    >
      {cells.map((cell) => (
        <div key={cell.label} className="flex flex-col items-center justify-center gap-1 px-4">
          <span className="text-xs text-slate-400">{cell.label}</span>
          <span
            className="flex items-center gap-1.5 font-mono text-2xl font-semibold tabular-nums"
            style={{ color: cell.color ?? '#e2e8f0' }}
          >
            {cell.value}
            {cell.warn ? <AlertTriangle size={15} className="text-rose-400" aria-label="已超阈值" /> : null}
          </span>
          {cell.hint ? <span className="text-[10px] text-slate-500">{cell.hint}</span> : null}
        </div>
      ))}
    </div>
  );
}
