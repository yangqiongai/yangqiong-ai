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
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { ExternalLink, Loader2, X } from 'lucide-react';
import type { AgentRuntimeSummary, TraceRunRow } from '../../types';
import { SIGNAL_META } from '../../constants/inbox-types';
import { HEALTH_BAND_COLORS, computeGlobalHealth, healthBand } from '../../utils/health-score';
import { formatDate } from '../../utils/helpers';

/**
 * 抽屉内嵌360 Tab项(children由应用侧注入，非激活Tab不挂载)
 */
export interface AgentSummaryDrawerTab {
  key: string;

  label: string;

  children: ReactNode;
}

/**
 * 360°摘要抽屉属性
 */
export interface AgentSummaryDrawerProps {
  /**
   * 当前预览的Agent(null=收起)
   */
  agent: AgentRuntimeSummary | null;

  /**
   * 社区端(健康分剔除企业信号)
   */
  community?: boolean;

  /**
   * 关闭抽屉
   */
  onClose: () => void;

  /**
   * 进入360°全页
   */
  onOpen360?: (agentCode: string) => void;

  /**
   * 按需拉取该Agent最近运行(打开抽屉时触发，应用侧注入数据源)
   */
  fetchRuns?: (agentCode: string) => Promise<TraceRunRow[]>;

  /**
   * 内嵌360° Tab(传入时抽屉加宽为全量信息模式：头部摘要+Tab区，免去二次跳转)
   */
  tabs?: AgentSummaryDrawerTab[];
}

/**
 * 360°摘要抽屉(单击星系节点滑出：传入tabs时为全量模式，抽屉加宽占满画布主区域并内嵌360°Tab免二次跳转)
 * @param props
 * @return
 */
export function AgentSummaryDrawer(props: AgentSummaryDrawerProps): JSX.Element {
  const { agent, community = false, onClose, onOpen360, fetchRuns, tabs } = props;
  const [runs, setRuns] = useState<TraceRunRow[]>([]);
  const [runsLoading, setRunsLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    if (!agent?.agentCode || !fetchRuns) {
      setRuns([]);
      return;
    }
    let cancelled = false;
    setRunsLoading(true);
    fetchRuns(agent.agentCode)
        .then((data) => {
          if (!cancelled) {
            setRuns(data ?? []);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setRuns([]);
          }
        })
        .finally(() => {
          if (!cancelled) {
            setRunsLoading(false);
          }
        });
    return () => {
      cancelled = true;
    };
  }, [agent, fetchRuns]);

  useEffect(() => {
    setActiveTab('overview');
  }, [agent?.agentCode]);

  const score = agent ? computeGlobalHealth([agent], { community }) : 100;
  const band = healthBand(score);
  const activeFlags = Object.entries(agent?.signalFlags ?? {}).filter(([, count]) => count > 0);
  const fullMode = !!tabs?.length && !!agent;

  /**
   * 摘要信息块(指标格+信号明细+最近运行，浅色/深色双主题供全量与摘要模式共用)
   * @return
   */
  const renderSummary = (): JSX.Element => {
    const cardCls = fullMode ? 'border border-slate-200 bg-white' : 'border border-white/5 bg-white/[0.03]';
    const labelCls = fullMode ? 'text-slate-400' : 'text-slate-500';
    const valueCls = fullMode ? 'text-slate-800' : 'text-slate-200';
    return (
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-2 text-xs md:grid-cols-4">
          <div className={`rounded-lg px-3 py-2 ${cardCls}`}>
            <div className={labelCls}>近 7 天运行</div>
            <div className={`font-mono ${valueCls}`}>{agent?.runs7d ?? 0}</div>
          </div>
          <div className={`rounded-lg px-3 py-2 ${cardCls}`}>
            <div className={labelCls}>近 7 天失败</div>
            <div className={`font-mono ${valueCls}`}>{agent?.failures7d ?? 0}</div>
          </div>
          <div className={`rounded-lg px-3 py-2 ${cardCls}`}>
            <div className={labelCls}>近 30 天成本</div>
            <div className={`font-mono ${valueCls}`}>${(agent?.cost30d ?? 0).toFixed(2)}</div>
          </div>
          <div className={`rounded-lg px-3 py-2 ${cardCls}`}>
            <div className={labelCls}>月度预算</div>
            <div className={`font-mono ${valueCls}`}>
              {agent?.budgetAmount != null ? `$${agent.budgetAmount.toFixed(2)}` : '未配置'}
            </div>
          </div>
          <div className={`col-span-2 rounded-lg px-3 py-2 md:col-span-4 ${cardCls}`}>
            <div className={labelCls}>最近发布</div>
            <div className={`truncate font-mono ${valueCls}`}>
              {agent?.latestPublishTime
                  ? `${agent.latestReleaseNo ?? ''} · ${formatDate(agent.latestPublishTime)}`
                  : '暂无发布'}
            </div>
          </div>
        </div>
        <div>
          <div className={`mb-1.5 text-xs ${fullMode ? 'text-slate-500' : 'text-slate-500'}`}>信号明细</div>
          {activeFlags.length === 0 ? (
            <div className={`rounded-lg px-3 py-2 text-xs ${cardCls} ${fullMode ? 'text-slate-500' : 'text-slate-500'}`}>
              无治理信号
            </div>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {activeFlags.map(([key, count]) => {
                const meta = SIGNAL_META[key as keyof typeof SIGNAL_META];
                return (
                  <span
                    key={key}
                    className="rounded-full border px-2 py-0.5 text-xs"
                    style={{
                      color: meta?.color,
                      borderColor: `${meta?.color}55`,
                      backgroundColor: `${meta?.color}14`,
                    }}
                  >
                    {meta?.label ?? key} × {count}
                  </span>
                );
              })}
            </div>
          )}
        </div>
        {fetchRuns ? (
          <div>
            <div className="mb-1.5 text-xs text-slate-500">最近 5 条运行</div>
            {runsLoading ? (
              <div className="flex items-center gap-2 px-1 py-2 text-xs text-slate-500">
                <Loader2 size={12} className="animate-spin" />
                加载中…
              </div>
            ) : runs.length === 0 ? (
              <div className={`rounded-lg px-3 py-2 text-xs text-slate-500 ${cardCls}`}>
                近期无运行记录
              </div>
            ) : (
              <div className="space-y-1">
                {runs.slice(0, 5).map((run) => (
                  <div key={run.traceId} className={`rounded-lg px-3 py-2 ${cardCls}`}>
                    <div className="flex items-center justify-between gap-2">
                      <span className={`text-xs font-medium ${run.status === 'FAILED'
                          ? (fullMode ? 'text-rose-600' : 'text-rose-300')
                          : (fullMode ? 'text-emerald-600' : 'text-emerald-300')}`}
                      >
                        {run.status ?? 'UNKNOWN'}
                      </span>
                      <span className="text-[10px] text-slate-500">{formatDate(run.startTime)}</span>
                    </div>
                    {run.userInput ? (
                      <div className={`mt-0.5 truncate text-xs ${fullMode ? 'text-slate-600' : 'text-slate-400'}`}>{run.userInput}</div>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : null}
      </div>
    );
  };

  return (
    <>
      <div
        className={`fixed inset-0 z-40 bg-black/40 backdrop-blur-sm transition-opacity duration-500 ${
          agent ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
        onClick={onClose}
      />
      <aside
        className={`fixed right-0 top-0 z-50 flex h-full flex-col border-l border-white/10 bg-[#0B1220]/95 shadow-2xl backdrop-blur-xl transition-transform duration-500 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          fullMode ? 'w-[calc(100vw-16.5rem)] max-w-full' : 'w-[420px] max-w-full'
        } ${agent ? 'translate-x-0' : 'translate-x-full'}`}
        data-testid="agent-summary-drawer"
        aria-hidden={!agent}
      >
        {agent ? (
          <>
            <div className="flex items-center gap-3 border-b border-white/10 px-5 py-3">
              <div className="min-w-0 flex-1">
                <div className="truncate text-base font-semibold text-slate-100">{agent.agentName || agent.agentCode}</div>
                <div className="mt-0.5 font-mono text-xs text-slate-400">{agent.agentCode}</div>
              </div>
              <span
                className="rounded-lg px-2 py-0.5 font-mono text-sm font-semibold"
                style={{ color: HEALTH_BAND_COLORS[band], backgroundColor: 'rgba(255,255,255,0.06)' }}
              >
                健康分 {score}
              </span>
              <span className={`rounded-full px-2 py-0.5 text-xs ${agent.status === 1
                  ? 'bg-emerald-500/15 text-emerald-300'
                  : 'bg-slate-500/15 text-slate-400'}`}
              >
                {agent.status === 1 ? '启用' : '禁用'}
              </span>
              {fullMode && onOpen360 ? (
                <button
                  type="button"
                  onClick={() => onOpen360(agent.agentCode)}
                  className="flex items-center gap-1 rounded-md border border-white/10 px-2.5 py-1.5 text-xs text-slate-300 transition-colors hover:bg-white/10 hover:text-slate-100"
                >
                  完整页
                  <ExternalLink size={12} />
                </button>
              ) : null}
              <button
                type="button"
                onClick={onClose}
                className="rounded-md p-1 text-slate-400 transition-colors hover:bg-white/10 hover:text-slate-200"
                aria-label="关闭摘要抽屉"
              >
                <X size={16} />
              </button>
            </div>
            {fullMode ? (
              <div className="flex min-h-0 flex-1 flex-col bg-[#F8FAFC]">
                <div className="max-h-[46%] shrink-0 overflow-auto border-b border-slate-200 bg-white px-4 py-3">
                  {renderSummary()}
                </div>
                <div className="z-10 flex flex-wrap items-end gap-0.5 border-b border-slate-200 bg-white px-3 pt-1.5">
                  {(tabs ?? []).map((tab) => (
                    <button
                      key={tab.key}
                      type="button"
                      data-testid={`drawer-tab-${tab.key}`}
                      onClick={() => setActiveTab(tab.key)}
                      className={`rounded-t-md px-3 py-2 text-xs transition-colors ${
                        activeTab === tab.key
                          ? 'bg-[#0a5bd8] font-medium text-white'
                          : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800'
                      }`}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>
                <div className="min-h-0 flex-1 overflow-auto p-4">
                  {(tabs ?? []).map((tab) => (
                    <div key={tab.key} hidden={tab.key !== activeTab}>
                      {tab.key === activeTab ? tab.children : null}
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="min-h-0 flex-1 overflow-auto px-5 py-4">{renderSummary()}</div>
            )}
            {!fullMode ? (
              <div className="border-t border-white/10 px-5 py-4">
                <button
                  type="button"
                  onClick={() => onOpen360?.(agent.agentCode)}
                  className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-sky-400/30 bg-sky-400/10 px-4 py-2.5 text-sm text-sky-300 transition-colors hover:bg-sky-400/20"
                >
                  进入 360° 详情
                  <ExternalLink size={14} />
                </button>
              </div>
            ) : null}
          </>
        ) : null}
      </aside>
    </>
  );
}
