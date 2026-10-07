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
import { useEffect, useMemo, useRef, useState } from 'react';
import * as echarts from 'echarts/core';
import { GraphChart, EffectScatterChart } from 'echarts/charts';
import { TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsType } from 'echarts/core';
import type { AgentRuntimeSummary, GovernanceSignalCounts, GovernanceSignalKey } from '../../types';
import { SIGNAL_META, signalKeysForScope } from '../../constants/inbox-types';
import { HEALTH_BAND_COLORS, computeHealthScores, healthBand } from '../../utils/health-score';

echarts.use([GraphChart, EffectScatterChart, TooltipComponent, CanvasRenderer]);

/**
 * 星系视图模式
 */
export type GalaxyViewMode = 'galaxy' | 'orbit' | 'list';

/**
 * 聚类降级节点阈值(>300 自动切聚类)
 */
export const CLUSTER_THRESHOLD = 300;

/**
 * 聚焦淡出透明度
 */
const FOCUS_OPACITY = 0.15;

/**
 * 节点半径范围(近30天成本开方映射)
 */
const NODE_RADIUS_MIN = 8;

/**
 * 节点最大半径
 */
const NODE_RADIUS_MAX = 28;

/**
 * 单击/双击区分间隔(ms)
 */
const CLICK_DETECT_MS = 250;

/**
 * Agent星系图属性
 */
export interface AgentGalaxyProps {
  /**
   * Agent运行摘要清单
   */
  agents: AgentRuntimeSummary[];

  /**
   * 社区端(健康分剔除企业信号、图例裁剪)
   */
  community?: boolean;

  /**
   * 动效降级(prefers-reduced-motion：布局动画冻结/涟漪关闭)
   */
  reducedMotion?: boolean;

  /**
   * 单击节点(滑出360°摘要抽屉)
   */
  onAgentClick?: (agentCode: string) => void;

  /**
   * 双击节点(进入360°全页)
   */
  onAgentDoubleClick?: (agentCode: string) => void;
}

/**
 * 图节点/边构建结果
 */
interface GalaxyGraphData {
  nodes: Record<string, unknown>[];

  links: Record<string, unknown>[];
}

/**
 * 节点半径映射(近30天成本开方，无成本按7d运行量)
 * @param agent 运行摘要
 * @return
 */
function nodeRadius(agent: AgentRuntimeSummary): number {
  const cost = agent.cost30d ?? 0;
  const runs = agent.runs7d ?? 0;
  const basis = cost > 0 ? cost : runs;
  if (basis <= 0) {
    return NODE_RADIUS_MIN;
  }
  return Math.min(NODE_RADIUS_MAX, NODE_RADIUS_MIN + Math.sqrt(basis));
}

/**
 * 旗标主信号(取计数最大者，聚类分组依据)
 * @param flags 信号徽标
 * @return
 */
function primarySignal(flags?: GovernanceSignalCounts): GovernanceSignalKey | null {
  if (!flags) {
    return null;
  }
  let best: GovernanceSignalKey | null = null;
  let bestCount = 0;
  for (const [key, count] of Object.entries(flags)) {
    if ((count ?? 0) > bestCount) {
      best = key as GovernanceSignalKey;
      bestCount = count ?? 0;
    }
  }
  return best;
}

/**
 * 聚焦淡出样式(聚焦信号下非相关元素降透明度)
 * @param related 是否与聚焦信号相关
 * @param focusSignal 当前聚焦信号
 * @return
 */
function dimStyle(related: boolean, focusSignal: GovernanceSignalKey | null): Record<string, unknown> {
  return focusSignal && !related ? { opacity: FOCUS_OPACITY } : {};
}

/**
 * 构建星系图数据(信号虚拟节点=问题星团中心，仅异常Agent有连线)
 * @param agents 运行摘要清单
 * @param scores 健康分映射
 * @param options cluster聚类/focusSignal聚焦/orbit轨道
 * @return
 */
export function buildGraphData(
    agents: AgentRuntimeSummary[],
    scores: Map<string, number>,
    options: { cluster: boolean; focusSignal: GovernanceSignalKey | null; orbit: boolean },
): GalaxyGraphData {
  const { cluster, focusSignal, orbit } = options;
  // ECharts graph节点以id(=agent:agentCode)为唯一键，同码副本会致图数据索引错位白屏，仅保留首个
  const seenCodes = new Set<string>();
  const uniqueAgents = agents.filter((agent) => {
    if (seenCodes.has(agent.agentCode)) {
      return false;
    }
    seenCodes.add(agent.agentCode);
    return true;
  });
  const nodes: Record<string, unknown>[] = [];
  const links: Record<string, unknown>[] = [];

  if (cluster) {
    // 聚类降级：按主信号分组为团簇节点，平稳簇独立成团，点击下钻
    const clusters = new Map<string, AgentRuntimeSummary[]>();
    for (const agent of uniqueAgents) {
      const key = primarySignal(agent.signalFlags) ?? '__healthy';
      const bucket = clusters.get(key) ?? [];
      bucket.push(agent);
      clusters.set(key, bucket);
    }
    for (const [key, members] of clusters) {
      const isSignal = key !== '__healthy';
      const meta = isSignal ? SIGNAL_META[key as GovernanceSignalKey] : null;
      nodes.push({
        id: `cluster:${key}`,
        name: isSignal ? `${meta?.label} (${members.length})` : `平稳 (${members.length})`,
        symbolSize: Math.min(64, 24 + Math.sqrt(members.length) * 4),
        itemStyle: {
          color: isSignal ? meta?.color : HEALTH_BAND_COLORS.good,
          shadowBlur: 14,
          shadowColor: isSignal ? meta?.color : HEALTH_BAND_COLORS.good,
          ...dimStyle(!isSignal || key === focusSignal, focusSignal),
        },
        __clusterKey: key,
      });
    }
    return { nodes, links };
  }

  const signalTotals = new Map<GovernanceSignalKey, number>();
  for (const agent of agents) {
    for (const [key, count] of Object.entries(agent.signalFlags ?? {})) {
      if (count) {
        signalTotals.set(key as GovernanceSignalKey, (signalTotals.get(key as GovernanceSignalKey) ?? 0) + count);
      }
    }
  }
  for (const [key, total] of signalTotals) {
    const meta = SIGNAL_META[key];
    if (!meta) {
      continue;
    }
    nodes.push({
      id: `signal:${key}`,
      name: meta.label,
      symbol: 'diamond',
      symbolSize: Math.min(40, 14 + total * 2),
      itemStyle: {
        color: meta.color,
        shadowBlur: 18,
        shadowColor: meta.color,
        ...dimStyle(key === focusSignal, focusSignal),
      },
    });
  }

  const seenNames = new Set<string>();
  uniqueAgents.forEach((agent) => {
    const score = scores.get(agent.agentCode) ?? 100;
    const band = healthBand(score);
    const abnormal = band !== 'good';
    let x: number | undefined;
    let y: number | undefined;
    if (orbit) {
      // 轨道模式：健康分越低轨道半径越小(内圈即待治理)
      const radius = band === 'good' ? 320 : band === 'warn' ? 220 : 130;
      const ringMates = uniqueAgents.filter((item) => {
        const itemScore = scores.get(item.agentCode) ?? 100;
        return healthBand(itemScore) === band;
      });
      const ringIndex = ringMates.findIndex((item) => item.agentCode === agent.agentCode);
      const angle = (2 * Math.PI * ringIndex) / Math.max(1, ringMates.length);
      x = 420 + radius * Math.cos(angle);
      y = 320 + radius * Math.sin(angle);
    }
    const relatedToFocus = !focusSignal || Boolean(agent.signalFlags?.[focusSignal]);
    // ECharts graph 以 name 为节点唯一键，重名会导致渲染崩溃，追加 agentCode 消歧
    const baseName = agent.agentName || agent.agentCode;
    const nodeName = seenNames.has(baseName) ? `${baseName} (${agent.agentCode})` : baseName;
    seenNames.add(nodeName);
    nodes.push({
      id: `agent:${agent.agentCode}`,
      name: nodeName,
      x,
      y,
      symbolSize: nodeRadius(agent),
      itemStyle: {
        color: HEALTH_BAND_COLORS[band],
        shadowBlur: abnormal ? 16 : 8,
        shadowColor: HEALTH_BAND_COLORS[band],
        ...dimStyle(relatedToFocus, focusSignal),
      },
      __agentCode: agent.agentCode,
      __score: score,
      __agentFlags: agent.signalFlags ?? {},
    });
    for (const [key, count] of Object.entries(agent.signalFlags ?? {})) {
      if (!count || !SIGNAL_META[key as GovernanceSignalKey]) {
        continue;
      }
      const signalKey = key as GovernanceSignalKey;
      const related = !focusSignal || signalKey === focusSignal;
      links.push({
        source: `agent:${agent.agentCode}`,
        target: `signal:${signalKey}`,
        lineStyle: {
          color: SIGNAL_META[signalKey].color,
          width: 1.5,
          type: signalKey === 'triggerFailures' ? 'dashed' : 'solid',
          opacity: focusSignal && !related ? FOCUS_OPACITY : 0.75,
          curveness: 0.1,
        },
      });
    }
  });
  return { nodes, links };
}

/**
 * 深色玻璃拟态tooltip(禁用默认白底)
 * @param params 图表事件参数
 * @return
 */
function tooltipFormatter(params: unknown): string {
  const data = (params as { data?: Record<string, unknown> }).data ?? {};
  const agentCode = data.__agentCode as string | undefined;
  if (!agentCode) {
    const clusterKey = data.__clusterKey as string | undefined;
    return clusterKey
        ? `<span style="color:#e2e8f0">${String(data.name ?? '')}</span><br/><span style="color:#94a3b8">点击团簇展开下钻</span>`
        : '';
  }
  const name = String(data.name ?? '');
  const score = Number(data.__score ?? 100);
  const flags = (data.__agentFlags ?? {}) as Record<string, number>;
  const flagHtml = Object.entries(flags)
      .filter(([, count]) => count > 0)
      .map(([key, count]) => {
        const meta = SIGNAL_META[key as GovernanceSignalKey];
        return `<span style="color:${meta?.color ?? '#94a3b8'}">${meta?.label ?? key}×${count}</span>`;
      })
      .join(' ');
  return `<div style="background:#111B2E;border:1px solid rgba(255,255,255,0.1);border-radius:8px;`
      + `padding:8px 12px;backdrop-filter:blur(8px);font-size:12px">`
      + `<div style="color:#e2e8f0;font-weight:600">${name}</div>`
      + `<div style="color:#94a3b8;margin-top:2px">${agentCode}</div>`
      + `<div style="margin-top:4px;color:${score >= 85 ? '#34d399' : score >= 60 ? '#fbbf24' : '#fb7185'}">`
      + `健康分 ${score}</div>`
      + (flagHtml ? `<div style="margin-top:4px">${flagHtml}</div>` : '')
      + `</div>`;
}

/**
 * 单击/双击路由器（单击延时区分双击，双击打断未触发的单击）
 * @param onClick 单击回调
 * @param onDoubleClick 双击回调
 * @return
 */
export function createAgentClickRouter(onClick: (agentCode: string) => void,
                                       onDoubleClick: (agentCode: string) => void): {
  handleClick: (agentCode: string) => void;

  handleDoubleClick: (agentCode: string) => void;

  dispose: () => void;
} {
  let timer: number | null = null;
  return {
    handleClick: (agentCode) => {
      if (timer != null) {
        window.clearTimeout(timer);
        timer = null;
        return;
      }
      timer = window.setTimeout(() => {
        timer = null;
        onClick(agentCode);
      }, CLICK_DETECT_MS);
    },
    handleDoubleClick: (agentCode) => {
      if (timer != null) {
        window.clearTimeout(timer);
        timer = null;
      }
      onDoubleClick(agentCode);
    },
    dispose: () => {
      if (timer != null) {
        window.clearTimeout(timer);
        timer = null;
      }
    },
  };
}

/**
 * Agent星系图(力导向/轨道双模式+聚类降级+信号聚焦，替代传统表格的核心视图)
 * @param props
 * @return
 */
export function AgentGalaxy(props: AgentGalaxyProps): JSX.Element {
  const { agents, community = false, reducedMotion = false, onAgentClick, onAgentDoubleClick } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<EChartsType | null>(null);
  const [viewMode, setViewMode] = useState<GalaxyViewMode>('galaxy');
  const [focusSignal, setFocusSignal] = useState<GovernanceSignalKey | null>(null);
  const [expandedClusters, setExpandedClusters] = useState<Set<string>>(new Set());

  const scores = useMemo(() => computeHealthScores(agents, { community }), [agents, community]);
  const cluster = agents.length > CLUSTER_THRESHOLD;
  const legendKeys = useMemo(
      () => signalKeysForScope(community).filter((key) =>
          agents.some((agent) => (agent.signalFlags?.[key] ?? 0) > 0)),
      [agents, community],
  );

  useEffect(() => {
    if (viewMode === 'list') {
      return;
    }
    if (!containerRef.current) {
      return;
    }
    const expanded = expandedClusters;
    const effectiveAgents = cluster
        ? agents.filter((agent) => {
          const key = primarySignal(agent.signalFlags) ?? '__healthy';
          return !expanded.has(key);
        })
        : agents;
    const graphData = buildGraphData(effectiveAgents, scores, {
      cluster: cluster && expanded.size === 0,
      focusSignal,
      orbit: viewMode === 'orbit',
    });

    const chart = chartRef.current ?? echarts.init(containerRef.current);
    chartRef.current = chart;
    chart.setOption({
      animation: !reducedMotion,
      animationDuration: 600,
      tooltip: {
        show: true,
        trigger: 'item',
        backgroundColor: 'rgba(17,27,46,0.95)',
        borderWidth: 0,
        padding: 0,
        formatter: tooltipFormatter,
        extraCssText: 'backdrop-filter:blur(8px);',
      },
      series: [{
        type: 'graph',
        layout: viewMode === 'orbit' || graphData.nodes.length <= 1 ? 'none' : 'force',
        roam: true,
        draggable: true,
        force: {
          repulsion: 120,
          edgeLength: [60, 140],
          layoutAnimation: !reducedMotion && graphData.nodes.length <= CLUSTER_THRESHOLD,
          gravity: 0.08,
        },
        label: { show: graphData.nodes.length <= 80, color: '#cbd5e1', fontSize: 10, position: 'bottom' },
        emphasis: { focus: 'adjacency', scale: 1.15 },
        data: graphData.nodes,
        links: graphData.links,
      }],
    }, { notMerge: false, lazyUpdate: true });

    const router = createAgentClickRouter(
        (agentCode) => onAgentClick?.(agentCode),
        (agentCode) => onAgentDoubleClick?.(agentCode),
    );
    const handleClick = (params: { data?: unknown }) => {
      const data = (params.data ?? {}) as Record<string, unknown>;
      const clusterKey = data.__clusterKey as string | undefined;
      if (clusterKey) {
        // 团簇下钻：展开为成员节点
        setExpandedClusters((prev) => new Set([...prev, clusterKey]));
        return;
      }
      const agentCode = data.__agentCode as string | undefined;
      if (agentCode) {
        router.handleClick(agentCode);
      }
    };
    const handleDoubleClick = (params: { data?: unknown }) => {
      const agentCode = ((params.data ?? {}) as Record<string, unknown>).__agentCode as string | undefined;
      if (agentCode) {
        router.handleDoubleClick(agentCode);
      }
    };
    chart.on('click', handleClick);
    chart.on('dblclick', handleDoubleClick);
    const handleResize = () => chart.resize();
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      router.dispose();
      chart.off('click', handleClick);
      chart.off('dblclick', handleDoubleClick);
    };
  }, [agents, scores, viewMode, focusSignal, community, reducedMotion, cluster, expandedClusters, onAgentClick,
      onAgentDoubleClick]);

  useEffect(() => () => {
    chartRef.current?.dispose();
    chartRef.current = null;
  }, []);

  const toggleFocus = (key: GovernanceSignalKey) => {
    setFocusSignal((prev) => (prev === key ? null : key));
  };

  const viewButton = (mode: GalaxyViewMode, label: string) => (
    <button
      key={mode}
      type="button"
      onClick={() => setViewMode(mode)}
      className={`rounded-md px-2.5 py-1 text-xs transition-colors ${
        viewMode === mode
            ? 'bg-white/15 text-white'
            : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'
      }`}
    >
      {label}
    </button>
  );

  return (
    <div className="relative flex h-full flex-col" data-testid="agent-galaxy">
      <div className="absolute top-3 right-3 z-10 flex items-center gap-1 rounded-lg border border-white/10 bg-[#111B2E]/80 p-1 backdrop-blur">
        {viewButton('galaxy', '星系')}
        {viewButton('orbit', '轨道')}
        {viewButton('list', '列表')}
      </div>
      {viewMode === 'list' ? (
        <div className="h-full overflow-auto rounded-xl border border-white/10 bg-white/[0.03]">
          <table className="w-full text-left text-sm">
            <thead className="sticky top-0 bg-[#111B2E] text-xs text-slate-400">
              <tr>
                <th className="px-4 py-2">Agent</th>
                <th className="px-4 py-2">健康分</th>
                <th className="px-4 py-2">信号</th>
                <th className="px-4 py-2">近7天运行</th>
                <th className="px-4 py-2">近30天成本</th>
              </tr>
            </thead>
            <tbody>
              {agents.map((agent) => {
                const score = scores.get(agent.agentCode) ?? 100;
                const band = healthBand(score);
                return (
                  <tr
                    key={agent.agentCode}
                    className="cursor-pointer border-t border-white/5 hover:bg-white/5"
                    onClick={() => onAgentClick?.(agent.agentCode)}
                    onDoubleClick={() => onAgentDoubleClick?.(agent.agentCode)}
                  >
                    <td className="px-4 py-2 text-slate-200">{agent.agentName || agent.agentCode}</td>
                    <td className="px-4 py-2" style={{ color: HEALTH_BAND_COLORS[band] }}>{score}</td>
                    <td className="px-4 py-2">
                      {Object.entries(agent.signalFlags ?? {})
                          .filter(([, count]) => count > 0)
                          .map(([key, count]) => (
                            <span
                              key={key}
                              className="mr-1 inline-block rounded px-1 text-xs"
                              style={{ color: SIGNAL_META[key as GovernanceSignalKey]?.color }}
                            >
                              {SIGNAL_META[key as GovernanceSignalKey]?.label ?? key}×{count}
                            </span>
                          ))}
                    </td>
                    <td className="px-4 py-2 text-slate-300">{agent.runs7d ?? 0}</td>
                    <td className="px-4 py-2 text-slate-300">${(agent.cost30d ?? 0).toFixed(2)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <div ref={containerRef} className="min-h-0 flex-1" />
      )}
      {legendKeys.length > 0 ? (
        <div className="flex flex-wrap items-center gap-2 px-3 pb-2 pt-1">
          {legendKeys.map((key) => {
            const meta = SIGNAL_META[key];
            const focused = focusSignal === key;
            return (
              <button
                key={key}
                type="button"
                onClick={() => toggleFocus(key)}
                className={`flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs transition-opacity ${
                  focused ? 'border-white/30 bg-white/10' : 'border-white/10 hover:bg-white/5'
                } ${focusSignal && !focused ? 'opacity-40' : ''}`}
              >
                <span
                  className="inline-block h-2 w-2 rounded-full"
                  style={{ backgroundColor: meta.color, boxShadow: `0 0 6px ${meta.color}` }}
                />
                <span className="text-slate-300">{meta.label}</span>
              </button>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}

