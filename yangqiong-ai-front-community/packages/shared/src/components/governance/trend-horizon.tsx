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
import { useEffect, useRef } from 'react';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { TooltipComponent, GridComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';

echarts.use([LineChart, TooltipComponent, GridComponent, CanvasRenderer]);

/**
 * 趋势地平线属性
 */
export interface TrendHorizonProps {
  /**
   * 近7天逐日运行量(下标0=6天前)
   */
  runs7d?: number[];

  /**
   * 近7天逐日成本USD
   */
  cost7d?: number[];

  /**
   * 动效降级
   */
  reducedMotion?: boolean;
}

/**
 * 趋势环比箭头(运行量升为绿、成本升为红)
 * @param current 最新值
 * @param previous 上一日值
 * @param upIsGood 上升是否为利好
 * @return
 */
function TrendArrow(props: { current?: number; previous?: number; upIsGood?: boolean }): JSX.Element | null {
  const { current, previous, upIsGood = true } = props;
  if (current == null || previous == null || previous === 0 || current === previous) {
    return null;
  }
  const up = current > previous;
  const good = up === upIsGood;
  const color = good ? '#34d399' : '#fb7185';
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  return (
    <span className="inline-flex items-center gap-0.5 text-xs" style={{ color }}>
      <Icon size={12} />
      {Math.abs(Math.round(((current - previous) / previous) * 100))}%
    </span>
  );
}

/**
 * 趋势地平线(双条无框渐变面积sparkline，近7天运行量/成本)
 * @param props
 * @return
 */
export function TrendHorizon(props: TrendHorizonProps): JSX.Element {
  const { runs7d, cost7d, reducedMotion = false } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const hasData = (runs7d?.length ?? 0) > 0 || (cost7d?.length ?? 0) > 0;
    if (!hasData) {
      return;
    }
    const chart = echarts.init(containerRef.current);
    const basis = (runs7d?.length ?? 0) > 0 ? runs7d! : cost7d!;
    chart.setOption({
      animation: !reducedMotion,
      animationDuration: 600,
      tooltip: {
        show: true,
        trigger: 'axis',
        backgroundColor: 'rgba(17,27,46,0.95)',
        borderWidth: 0,
        textStyle: { color: '#e2e8f0', fontSize: 12 },
        extraCssText: 'backdrop-filter:blur(8px);border:1px solid rgba(255,255,255,0.1);',
      },
      grid: { left: 4, right: 4, top: 8, bottom: 4 },
      xAxis: { type: 'category', show: false, data: basis.map((_, index) => index) },
      yAxis: [
        { type: 'value', show: false },
        { type: 'value', show: false },
      ],
      series: [
        {
          name: '运行量',
          type: 'line',
          data: runs7d ?? [],
          smooth: true,
          symbol: 'none',
          yAxisIndex: 0,
          lineStyle: { color: '#22d3ee', width: 2, shadowBlur: 6, shadowColor: '#22d3ee' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(34,211,238,0.35)' },
              { offset: 1, color: 'rgba(34,211,238,0)' },
            ]),
          },
        },
        {
          name: '成本',
          type: 'line',
          data: cost7d ?? [],
          smooth: true,
          symbol: 'none',
          yAxisIndex: 1,
          lineStyle: { color: '#a78bfa', width: 2, shadowBlur: 6, shadowColor: '#a78bfa' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(167,139,250,0.35)' },
              { offset: 1, color: 'rgba(167,139,250,0)' },
            ]),
          },
        },
      ],
    });
    const handleResize = () => chart.resize();
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
      chart.dispose();
    };
  }, [runs7d, cost7d, reducedMotion]);

  const runsLatest = runs7d?.[runs7d.length - 1];
  const runsPrev = runs7d?.[runs7d.length - 2];
  const costLatest = cost7d?.[cost7d.length - 1];
  const costPrev = cost7d?.[cost7d.length - 2];

  return (
    <div className="rounded-2xl border border-white/10 bg-white/[0.04] p-4 backdrop-blur-xl" data-testid="trend-horizon">
      <div className="flex items-end justify-between gap-6">
        <div className="flex shrink-0 items-end gap-8">
          <div>
            <div className="flex items-center gap-1.5 text-xs text-slate-400">
              近 7 天运行量
              <TrendArrow current={runsLatest} previous={runsPrev} upIsGood={true} />
            </div>
            <div className="font-mono text-xl font-semibold text-cyan-300 tabular-nums">{runsLatest ?? 0}</div>
          </div>
          <div>
            <div className="flex items-center gap-1.5 text-xs text-slate-400">
              近 7 天成本
              <TrendArrow current={costLatest} previous={costPrev} upIsGood={false} />
            </div>
            <div className="font-mono text-xl font-semibold text-violet-300 tabular-nums">
              ${(costLatest ?? 0).toFixed(2)}
            </div>
          </div>
        </div>
        <div ref={containerRef} className="h-16 min-w-0 flex-1" />
      </div>
    </div>
  );
}
