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
import { LineChart } from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';
import type { GovernanceSignalCounts, GovernanceSignalKey } from '../../types';
import { useCountUp } from '../../utils/use-count-up';
import {
  ENTERPRISE_DISPOSAL_TIP,
  RUNTIME_HEX_ORDER,
  SIGNAL_META,
  TRUST_HEX_ORDER,
  signalKeysForScope,
} from '../../constants/inbox-types';
import { EnterpriseLockIcon } from './enterprise-lock-icon';

echarts.use([LineChart, CanvasRenderer]);

/**
 * 蜂巢卡尺寸(px)
 */
const HEX_SIZE = 80;

/**
 * 六边形裁切(pointy-top，垂直无缝拼片)
 */
const HEX_CLIP = 'polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%)';

/**
 * 六边形拼片垂直叠接量(px，负margin实现无缝拼片)
 */
const HEX_OVERLAP = Math.round(HEX_SIZE * 0.15);

/**
 * 呼吸发光动画样式(按严重级强度)
 */
const GLOW_STYLE = {
  P1: '0 0 18px rgba(251,113,133,0.7), inset 0 0 12px rgba(251,113,133,0.35)',
  P2: '0 0 16px rgba(251,191,36,0.7), inset 0 0 10px rgba(251,191,36,0.3)',
  P3: '0 0 12px rgba(148,163,184,0.55), inset 0 0 8px rgba(148,163,184,0.25)',
} as const;

/**
 * 呼吸发光keyframes(全站唯一深色驾驶舱作用域)
 */
const BREATHE_KEYFRAMES = `
@keyframes hex-breathe {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 0.7; }
}
`;

/**
 * 信号蜂巢带属性
 */
export interface SignalHexBandProps {
  /**
   * 信号计数(键=信号类型)
   */
  signals: GovernanceSignalCounts;

  /**
   * 各信号近7天逐日计数(hover sparkline)
   */
  signalTrend7d?: Partial<Record<GovernanceSignalKey, number[]>>;

  /**
   * 社区端：仅渲染左簇信号1-3，右簇整簇隐藏不留位
   */
  community?: boolean;

  /**
   * 动效降级(prefers-reduced-motion，计数直显/无呼吸发光)
   */
  reducedMotion?: boolean;

  /**
   * 蜂巢卡点击(跳转 Inbox 预置类型过滤)
   */
  onSignalClick?: (key: GovernanceSignalKey) => void;
}

/**
 * 蜂巢内迷你趋势线(无坐标轴，仅hover时挂载)
 * @param props
 * @return
 */
function HexSparkline(props: { color: string; trend: number[] }): JSX.Element {
  const { color, trend } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const chart = echarts.init(containerRef.current);
    chart.setOption({
      animation: false,
      grid: { left: 2, right: 2, top: 2, bottom: 2 },
      xAxis: { type: 'category', show: false, data: trend.map((_, index) => index) },
      yAxis: { type: 'value', show: false },
      series: [{
        type: 'line',
        data: trend,
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 2, shadowBlur: 6, shadowColor: color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color },
            { offset: 1, color: 'rgba(0,0,0,0)' },
          ]),
          opacity: 0.35,
        },
      }],
    });
    return () => chart.dispose();
  }, [color, trend]);
  return <div ref={containerRef} className="h-8 w-20" />;
}

/**
 * 单格蜂巢卡
 * @param props
 * @return
 */
function HexCell(props: {
  meta: (typeof SIGNAL_META)[GovernanceSignalKey];
  count: number;
  trend?: number[];
  reducedMotion: boolean;
  locked?: boolean;
  onSignalClick?: (key: GovernanceSignalKey) => void;
}): JSX.Element {
  const { meta, count, trend, reducedMotion, locked = false, onSignalClick } = props;
  const [hovered, setHovered] = useState(false);
  const display = useCountUp(count, reducedMotion);
  const active = count > 0;

  return (
    <button
      type="button"
      disabled={locked}
      title={locked ? ENTERPRISE_DISPOSAL_TIP : undefined}
      onClick={locked ? undefined : () => onSignalClick?.(meta.key)}
      onMouseEnter={locked ? undefined : () => setHovered(true)}
      onMouseLeave={locked ? undefined : () => setHovered(false)}
      className={`relative flex flex-col items-center justify-center outline-none transition-transform duration-200 ${
        locked ? 'cursor-not-allowed' : 'hover:-translate-y-1 focus-visible:-translate-y-1'
      }`}
      style={{ width: HEX_SIZE, height: HEX_SIZE * 1.15, clipPath: HEX_CLIP }}
      aria-label={`${meta.label} ${count} 项待处置`}
    >
      <span
        className={`absolute inset-0 transition-colors duration-300 ${
          active ? '' : 'border border-white/10 bg-white/[0.03]'
        }`}
        style={active ? {
          background: 'rgba(255,255,255,0.05)',
          boxShadow: GLOW_STYLE[meta.severity],
          animation: reducedMotion ? undefined : 'hex-breathe 2.4s ease-in-out infinite',
        } : undefined}
      />
      <span className="relative z-10 flex flex-col items-center gap-1">
        <span className="flex items-center gap-1.5">
          <span
            className="inline-block h-2 w-2 rounded-full"
            style={{
              backgroundColor: active ? meta.color : 'rgba(148,163,184,0.4)',
              boxShadow: active ? `0 0 8px ${meta.color}` : 'none',
            }}
          />
          <span
            className="font-mono text-2xl font-semibold tabular-nums"
            style={{ color: active ? meta.color : '#64748b' }}
          >
            {display}
          </span>
        </span>
        <span className="text-xs text-slate-300">{meta.label}</span>
        <span
          className="text-[10px] font-medium tracking-wider"
          style={{ color: active ? meta.color : '#475569' }}
        >
          {meta.severity}
        </span>
        {hovered && trend && trend.some((value) => value > 0) ? (
          <span className="absolute top-full left-1/2 z-20 -translate-x-1/2 pt-1">
            <span className="block rounded-lg border border-white/10 bg-[#111B2E]/95 px-2 py-1 backdrop-blur">
              <HexSparkline color={meta.color} trend={trend} />
            </span>
          </span>
        ) : null}
      </span>
    </button>
  );
}

/**
 * 信号蜂巢带(左簇运行域双端，右簇信任合规域企业专属整簇渲染)
 * @param props
 * @return
 */
export function SignalHexBand(props: SignalHexBandProps): JSX.Element {
  const { signals, signalTrend7d, community = false, reducedMotion = false, onSignalClick } = props;
  const visibleKeys = useMemo(() => new Set(signalKeysForScope(community)), [community]);
  const runtimeKeys = RUNTIME_HEX_ORDER.filter((key) => visibleKeys.has(key));
  const trustKeys = community ? [] : TRUST_HEX_ORDER.filter((key) => visibleKeys.has(key));

  return (
    <div className="flex items-center gap-6" data-testid="signal-hex-band">
      <style>{BREATHE_KEYFRAMES}</style>
      <HexCluster
        keys={runtimeKeys}
        signals={signals}
        signalTrend7d={signalTrend7d}
        reducedMotion={reducedMotion}
        locked={community}
        onSignalClick={onSignalClick}
      />
      {trustKeys.length > 0 ? (
        <HexCluster
          keys={trustKeys}
          signals={signals}
          signalTrend7d={signalTrend7d}
          reducedMotion={reducedMotion}
          locked={community}
          onSignalClick={onSignalClick}
        />
      ) : null}
    </div>
  );
}

/**
 * 蜂巢簇(同簇横向拼片，非首格负margin左向叠接成蜂窝锯齿；locked时卡下方挂锁定图标)
 * @param props
 * @return
 */
function HexCluster(props: {
  keys: GovernanceSignalKey[];
  signals: GovernanceSignalCounts;
  signalTrend7d?: Partial<Record<GovernanceSignalKey, number[]>>;
  reducedMotion: boolean;
  locked?: boolean;
  onSignalClick?: (key: GovernanceSignalKey) => void;
}): JSX.Element {
  const { keys, signals, signalTrend7d, reducedMotion, locked = false, onSignalClick } = props;
  return (
    <div className="flex flex-row items-center">
      {keys.map((key, index) => (
        <div key={key} style={index > 0 ? { marginLeft: -HEX_OVERLAP } : undefined}>
          <HexCell
            meta={SIGNAL_META[key]}
            count={signals[key] ?? 0}
            trend={signalTrend7d?.[key]}
            reducedMotion={reducedMotion}
            locked={locked}
            onSignalClick={onSignalClick}
          />
          {locked ? (
            <div className="flex justify-center">
              <span title={ENTERPRISE_DISPOSAL_TIP} className="text-amber-300">
                <EnterpriseLockIcon />
              </span>
            </div>
          ) : null}
        </div>
      ))}
    </div>
  );
}
