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
import { SIGNAL_META, signalAutoDisposalLabel, signalItemId, type SignalSeverity } from '../constants/inbox-types';
import type {
  GovernanceRecentItem,
  GovernanceSignalKey,
} from '../types/governance';

/**
 * 已读忽略标记的localStorage键(历史遗留, 仅读取迁移不再写入)
 */
export const IGNORED_STORAGE_KEY = 'governance-inbox-ignored';

/**
 * 严重级排序权重(P1优先)
 */
const SEVERITY_ORDER: Record<SignalSeverity, number> = { P1: 0, P2: 1, P3: 2 };

/**
 * 处置反馈语气
 */
export type DisposalFeedbackTone = 'success' | 'partial' | 'failed';

/**
 * ACK记录最小结构（解析actionParams中的条目ID）
 */
export interface AckRecordLike {
  actionParams?: string | null;
}

/**
 * 收件箱过滤条件
 */
export interface InboxFilterOptions {
  selectedTypes: GovernanceSignalKey[];

  agentKeyword: string;

  showIgnored: boolean;

  ignoredIds: Set<string>;
}

/**
 * 读取localStorage遗留的已忽略条目ID(只读迁移, 不再写回)
 * @return
 */
export const readLegacyIgnoredIds = (): Set<string> => {
  try {
    const raw = localStorage.getItem(IGNORED_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return new Set(Array.isArray(parsed) ? parsed.filter((id) => typeof id === 'string') : []);
  } catch {
    return new Set();
  }
};

/**
 * 从后端ACK记录中解析已读条目ID集合（参数非法的记录按Agent级已读兜底跳过）
 * @param records
 * @return
 */
export const collectAckedItemIds = (records?: AckRecordLike[]): Set<string> => {
  const ids = new Set<string>();
  for (const record of records ?? []) {
    if (!record.actionParams) {
      continue;
    }
    try {
      const parsed = JSON.parse(record.actionParams) as { itemId?: unknown };
      if (typeof parsed.itemId === 'string') {
        ids.add(parsed.itemId);
      }
    } catch {
      // 参数非法的ACK记录按Agent级已读兜底
    }
  }
  return ids;
};

/**
 * 合并已读集合（后端ACK记录+localStorage遗留ID）
 * @param ackedIds
 * @param legacyIds
 * @return
 */
export const mergeIgnoredIds = (ackedIds: Set<string>, legacyIds: Set<string>): Set<string> =>
  new Set<string>([...ackedIds, ...legacyIds]);

/**
 * 按信号类型/Agent关键词/已读状态过滤并按严重级与发生时间排序
 * @param items
 * @param options
 * @return
 */
export const filterInboxRows = (
  items: GovernanceRecentItem[],
  options: InboxFilterOptions,
): GovernanceRecentItem[] => {
  const keyword = options.agentKeyword.trim().toLowerCase();
  return (items ?? [])
    .filter((item) => options.selectedTypes.length === 0 || options.selectedTypes.includes(item.type))
    .filter((item) => !keyword
      || (item.agentName ?? '').toLowerCase().includes(keyword)
      || (item.agentCode ?? '').toLowerCase().includes(keyword))
    .filter((item) => options.showIgnored || !options.ignoredIds.has(signalItemId(item)))
    .sort((a, b) => {
      const severityDiff = (SEVERITY_ORDER[SIGNAL_META[a.type]?.severity ?? 'P3'])
          - (SEVERITY_ORDER[SIGNAL_META[b.type]?.severity ?? 'P3']);
      if (severityDiff !== 0) {
        return severityDiff;
      }
      return String(b.occurredAt ?? '').localeCompare(String(a.occurredAt ?? ''));
    });
};

/**
 * 挑选支持一键自动处置的选中项（需配置自动处置标签且已绑定Agent）
 * @param items
 * @return
 */
export const pickAutoDisposalItems = (items: GovernanceRecentItem[]): GovernanceRecentItem[] =>
  items.filter((item) => signalAutoDisposalLabel(item.type) && item.agentCode);

/**
 * 处置附加参数（失败模式处置需携带模式键回写，其余信号无附加参数）
 * @param item
 * @return
 */
export const buildDisposalParams = (
  item: GovernanceRecentItem,
): Record<string, unknown> | undefined =>
  item.patternKey ? { patternKey: item.patternKey } : undefined;

/**
 * 处置结果状态映射为反馈语气
 * @param status
 * @return
 */
export const disposalFeedbackTone = (status: string): DisposalFeedbackTone =>
  status === 'SUCCESS' ? 'success' : status === 'PARTIAL' ? 'partial' : 'failed';
