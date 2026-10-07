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
import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Alert } from 'antd';
import { Inbox, Loader2 } from 'lucide-react';
import {
  AgentGalaxy,
  AgentSummaryDrawer,
  HealthRing,
  HeroMetrics,
  OnboardingEmptyState,
  RadarStream,
  SignalHexBand,
  computeGlobalHealth,
} from '@yangqiong/shared';
import type { AgentRuntimeSummary, GovernanceSignalCounts } from '@yangqiong/shared';
import { governanceDashboardApi } from '@/services/governance-dashboard-api';
import { governanceOnboardingApi } from '@/services/governance-onboarding-api';
import { api } from '@/services';
import { buildAgent360Tabs } from './Agent360Page';

/**
 * 深色画布样式(深空蓝黑径向渐变+1px坐标网格线，仅作用于驾驶舱容器)
 */
const CANVAS_STYLE: React.CSSProperties = {
  backgroundImage:
      'radial-gradient(ellipse at 50% -20%, #16233F 0%, #0B1220 55%),'
      + 'linear-gradient(rgba(148,163,184,0.06) 1px, transparent 1px),'
      + 'linear-gradient(90deg, rgba(148,163,184,0.06) 1px, transparent 1px)',
  backgroundSize: '100% 100%, 40px 40px, 40px 40px',
};

/**
 * 治理驾驶舱(社区裁剪版：全局唯一深色画布页，3类运行信号→星系→处置闭环，不含趋势地平线)
 * @return
 */
export const GovernanceDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const [drawerAgent, setDrawerAgent] = useState<AgentRuntimeSummary | null>(null);
  const reducedMotion = useMemo(
      () => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
      [],
  );
  const summaryQuery = useQuery({
    queryKey: ['governance-dashboard-summary'],
    queryFn: governanceDashboardApi.summary,
    staleTime: 30_000,
  });
  const summary = summaryQuery.data;

  const onboardingQuery = useQuery({
    queryKey: ['governance-onboarding-status'],
    queryFn: governanceOnboardingApi.status,
  });
  const onboardingStatus = onboardingQuery.data;

  const signals: GovernanceSignalCounts = useMemo(() => summary?.signals ?? {}, [summary]);
  const pendingTotal = useMemo(
      // 后端Long序列化为字符串(Number化防加号拼接)
      () => Object.values(signals).reduce((sum, count) => sum + Number(count ?? 0), 0),
      [signals],
  );
  const globalScore = useMemo(
      () => computeGlobalHealth(summary?.agents ?? [], { community: true }),
      [summary],
  );

  if (onboardingStatus && !onboardingStatus.hasAgent) {
    return (
      <OnboardingEmptyState
        status={onboardingStatus}
        title="等待 Agent 接入"
        description="当前还没有 Agent 数据。Agent 定义与运行由接入侧完成后，此处将展示治理面板。"
        steps={[]}
        onNavigate={navigate}
      />
    );
  }

  if (summaryQuery.isLoading) {
    return (
      <div className="relative min-h-[60vh] overflow-hidden rounded-2xl p-5" style={CANVAS_STYLE}>
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Loader2 size={16} className="animate-spin" />
          驾驶舱数据加载中…
        </div>
      </div>
    );
  }

  if (summaryQuery.isError) {
    return (
      <div className="relative min-h-[60vh] overflow-hidden rounded-2xl p-5" style={CANVAS_STYLE}>
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Inbox size={20} />
          驾驶舱数据加载失败，请稍后重试
        </div>
      </div>
    );
  }

  return (
    <div
      className="relative min-h-[calc(100vh-8.5rem)] overflow-hidden rounded-2xl p-4"
      style={CANVAS_STYLE}
      data-testid="governance-dashboard"
    >
      <span
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-40 opacity-15"
        style={{ background: 'linear-gradient(90deg, #0a5bd8, #6d28d9)', filter: 'blur(120px)' }}
      />
      <div className="relative z-10 flex flex-col gap-4">
        <header className="flex items-center justify-between">
          <div>
            <h1 className="text-lg font-semibold text-slate-100">治理驾驶舱</h1>
            <p className="mt-0.5 text-xs text-slate-400">运行风险全局第一屏：漂移 → 失败 → 成本处置闭环</p>
          </div>
        </header>
        {/* 处置能力已收回企业版，社区版仅保留信号概览引导 */}
        <Alert type="info" showIcon message="治理信号处置为企业版能力，社区版仅展示信号概览" />
        <section className="flex flex-col items-center gap-4 rounded-2xl border border-white/10 bg-white/[0.04] px-5 py-4 backdrop-blur-xl lg:flex-row lg:gap-2">
          <HealthRing
            score={globalScore}
            agentCount={summary?.agents.length ?? 0}
            pendingTotal={pendingTotal}
            generatedAt={summary?.generatedAt ?? ''}
            allClear={pendingTotal === 0}
            reducedMotion={reducedMotion}
          />
          <HeroMetrics agents={summary?.agents ?? []} />
          <SignalHexBand
            signals={signals}
            signalTrend7d={summary?.signalTrend7d}
            community
            reducedMotion={reducedMotion}
            onSignalClick={(key) => navigate(`/governance-inbox?type=${key}`)}
          />
        </section>
        <section className="grid min-h-[55vh] grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
          <div className="min-h-[560px] rounded-2xl border border-white/10 bg-white/[0.04] p-2 backdrop-blur-xl">
            <AgentGalaxy
              agents={summary?.agents ?? []}
              community
              reducedMotion={reducedMotion}
              onAgentClick={(agentCode) => {
                const agent = summary?.agents.find((item) => item.agentCode === agentCode) ?? null;
                setDrawerAgent(agent);
              }}
              onAgentDoubleClick={(agentCode) => navigate(`/governance-agents/${agentCode}`)}
            />
          </div>
          <div className="min-h-[420px]">
            <RadarStream
              recent={summary?.recent ?? []}
              pendingTotal={pendingTotal}
              community
              onDisposeAll={() => navigate('/governance-inbox')}
              onOpenRoute={(route) => navigate(route)}
            />
          </div>
        </section>
      </div>
      <AgentSummaryDrawer
        agent={drawerAgent}
        community
        onClose={() => setDrawerAgent(null)}
        onOpen360={(agentCode) => navigate(`/governance-agents/${agentCode}`)}
        fetchRuns={(agentCode) => api.trace.runs({ agentCode, pageSize: 5 })}
        tabs={drawerAgent ? buildAgent360Tabs(drawerAgent.agentCode, drawerAgent) : undefined}
      />
    </div>
  );
};
