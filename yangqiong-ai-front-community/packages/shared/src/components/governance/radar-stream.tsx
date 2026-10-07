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
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/zh-cn';
import { useMemo } from 'react';
import { ArrowRight } from 'lucide-react';
import type { GovernanceRecentItem } from '../../types';
import { SIGNAL_META, ENTERPRISE_DISPOSAL_TIP, resolveDisposalRoute, signalKeysForScope } from '../../constants/inbox-types';
import { EnterpriseLockIcon } from './enterprise-lock-icon';

dayjs.extend(relativeTime);
dayjs.locale('zh-cn');

/**
 * 信号雷达流属性
 */
export interface RadarStreamProps {
  /**
   * 合并时间轴事件流(summary端点recent直出)
   */
  recent: GovernanceRecentItem[];

  /**
   * 待处置信号总数
   */
  pendingTotal: number;

  /**
   * 社区端(处置路由按communityRoute解析)
   */
  community?: boolean;

  /**
   * 全部处置入口(跳转Inbox)
   */
  onDisposeAll?: () => void;

  /**
   * 条目处置跳转(应用侧执行路由跳转)
   */
  onOpenRoute?: (route: string) => void;
}

/**
 * 信号雷达流(垂直时间轴事件流，替代"按页面逐个查看"的信号发现方式)
 * @param props
 * @return
 */
export function RadarStream(props: RadarStreamProps): JSX.Element {
  const { recent, pendingTotal, community = false, onDisposeAll, onOpenRoute } = props;
  // 社区端防御性裁剪：企业专属信号条目即使混入数据源也不渲染
  const visibleItems = useMemo(() => {
    if (!community) {
      return recent;
    }
    const visibleKeys = new Set(signalKeysForScope(true));
    return recent.filter((item) => visibleKeys.has(item.type));
  }, [recent, community]);
  const handleItemClick = (item: GovernanceRecentItem) => {
    const route = resolveDisposalRoute(item.type, item.agentCode, community);
    if (route) {
      onOpenRoute?.(route);
    }
  };
  return (
    <div
      className="flex h-full min-h-0 flex-col rounded-2xl border border-white/10 bg-white/[0.04] backdrop-blur-xl"
      data-testid="signal-radar-stream"
    >
      <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-slate-200">信号雷达</span>
          <span className="rounded-full bg-white/10 px-2 py-0.5 font-mono text-xs text-slate-300">{pendingTotal}</span>
        </div>
        {community ? (
          <span className="flex items-center gap-1.5" title={ENTERPRISE_DISPOSAL_TIP}>
            <EnterpriseLockIcon className="text-amber-300" />
            <button
              type="button"
              disabled
              className="flex cursor-not-allowed items-center gap-1 text-xs text-slate-500"
            >
              全部处置
              <ArrowRight size={12} />
            </button>
          </span>
        ) : (
          <button
            type="button"
            onClick={onDisposeAll}
            className="flex items-center gap-1 text-xs text-sky-300 transition-colors hover:text-sky-200"
          >
            全部处置
            <ArrowRight size={12} />
          </button>
        )}
      </div>
      <div className="min-h-0 flex-1 space-y-1 overflow-auto p-2">
        {visibleItems.length === 0 ? (
          <div className="flex h-full items-center justify-center text-xs text-slate-500">暂无待处置信号</div>
        ) : (
          visibleItems.map((item, index) => {
            const meta = SIGNAL_META[item.type];
            return (
              <button
                key={`${item.type}-${item.agentCode ?? ''}-${item.occurredAt ?? ''}-${index}`}
                type="button"
                disabled={community}
                title={community ? ENTERPRISE_DISPOSAL_TIP : undefined}
                onClick={community ? undefined : () => handleItemClick(item)}
                className={`flex w-full items-start gap-2.5 rounded-lg px-2 py-2 text-left transition-colors ${
                  community ? 'cursor-not-allowed opacity-60' : 'hover:bg-white/5'
                }`}
              >
                <span
                  className="mt-1.5 inline-block h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: meta?.color, boxShadow: `0 0 8px ${meta?.color}` }}
                />
                <span className="min-w-0 flex-1">
                  <span className="flex items-baseline gap-2">
                    <span className="text-xs font-medium" style={{ color: meta?.color }}>{meta?.label ?? item.type}</span>
                    <span className="truncate text-xs text-slate-300">{item.agentName || item.agentCode}</span>
                    <span className="ml-auto shrink-0 text-[10px] text-slate-500">
                      {item.occurredAt ? dayjs(item.occurredAt).fromNow() : ''}
                    </span>
                  </span>
                  {item.summary ? (
                    <span className="mt-0.5 block truncate text-xs text-slate-400">{item.summary}</span>
                  ) : null}
                </span>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
