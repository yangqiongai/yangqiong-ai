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
import { GaugeChart } from 'echarts/charts';
import { CanvasRenderer } from 'echarts/renderers';
import type { EChartsType } from 'echarts/core';
import { HEALTH_BAND_COLORS, healthBand } from '../../utils/health-score';
import { useCountUp } from '../../utils/use-count-up';
import { formatDate } from '../../utils/helpers';

echarts.use([GaugeChart, CanvasRenderer]);

/**
 * 装饰外环缓旋keyframes
 */
const RING_KEYFRAMES = `
@keyframes governance-ring-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
`;

/**
 * 全局健康环属性
 */
export interface HealthRingProps {
  /**
   * 全局健康分
   */
  score: number;

  /**
   * Agent总数
   */
  agentCount: number;

  /**
   * 待处置信号总数
   */
  pendingTotal: number;

  /**
   * 数据生成时间
   */
  generatedAt: string;

  /**
   * 全域平稳(全部信号为0时显示正反馈文案)
   */
  allClear: boolean;

  /**
   * 动效降级(prefers-reduced-motion)
   */
  reducedMotion: boolean;
}

/**
 * 全局健康环(ECharts gauge仅画弧线本体，数字与文案由DOM层呈现)
 * @param props
 * @return
 */
export function HealthRing(props: HealthRingProps): JSX.Element {
  const { score, agentCount, pendingTotal, generatedAt, allClear, reducedMotion } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<EChartsType | null>(null);
  const display = useCountUp(score, reducedMotion);
  const band = healthBand(score);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }
    const chart = chartRef.current ?? echarts.init(containerRef.current);
    chartRef.current = chart;
    chart.setOption({
      animation: !reducedMotion,
      animationDuration: 600,
      series: [{
        type: 'gauge',
        startAngle: 220,
        endAngle: -40,
        min: 0,
        max: 100,
        radius: '96%',
        progress: {
          show: true,
          width: 14,
          roundCap: true,
          itemStyle: {
            color: HEALTH_BAND_COLORS[band],
            shadowBlur: 16,
            shadowColor: HEALTH_BAND_COLORS[band],
          },
        },
        axisLine: { lineStyle: { width: 14, color: [[1, 'rgba(255,255,255,0.08)']] } },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: { show: false },
        data: [{ value: score }],
      }],
    }, { notMerge: true, lazyUpdate: true });
    const handleResize = () => chart.resize();
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
      chart.dispose();
      chartRef.current = null;
    };
  }, [score, band, reducedMotion]);

  return (
    <div className="relative flex h-44 w-44 shrink-0 items-center justify-center" data-testid="health-ring">
      <style>{RING_KEYFRAMES}</style>
      <span
        className="absolute inset-1 rounded-full border border-white/10"
        style={reducedMotion ? undefined : { animation: 'governance-ring-spin 60s linear infinite' }}
      />
      <div ref={containerRef} className="absolute inset-0" />
      <div className="relative z-10 flex flex-col items-center">
        <span
          className="font-mono text-4xl font-semibold tabular-nums"
          style={{ color: HEALTH_BAND_COLORS[band], textShadow: `0 0 18px ${HEALTH_BAND_COLORS[band]}` }}
        >
          {display}
        </span>
        <span className="mt-1 text-xs text-slate-300">{allClear ? '全域平稳' : '全局健康分'}</span>
      </div>
      <div className="absolute -bottom-7 left-1/2 w-max -translate-x-1/2 whitespace-nowrap text-center text-xs text-slate-400">
        {agentCount} Agent · {pendingTotal} 待处置 · 更新于 {formatDate(generatedAt, 'HH:mm')}
      </div>
    </div>
  );
}
