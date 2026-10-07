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
import type { GovernanceRecentItem, GovernanceSignalKey } from '../types';

/**
 * 信号严重级
 */
export type SignalSeverity = 'P1' | 'P2' | 'P3';

/**
 * 信号域（运行域双端可见；信任与合规域企业专属）
 */
export type SignalDomain = 'runtime' | 'trust';

/**
 * 信号处置动作映射
 */
export interface SignalDisposal {
  /**
   * 处置动作文案
   */
  label: string;

  /**
   * 跳转路由模板(:agentCode 占位，Inbox/雷达流按 agentCode 实例化)
   */
  route: string;

  /**
   * 社区端替代路由(缺省同 route)
   */
  communityRoute?: string;

  /**
   * 一键处置动作文案(后端处置动作注册表支持时配置, 缺省不支持一键处置)
   */
  autoLabel?: string;
}

/**
 * 信号元数据（蜂巢卡/Inbox/雷达流/健康分共用口径）
 */
export interface SignalMeta {
  key: GovernanceSignalKey;

  /**
   * 信号中文名
   */
  label: string;

  domain: SignalDomain;

  severity: SignalSeverity;

  /**
   * 语义色（T22 规范：漂移黄/审计红/提案蓝/轮换黄/隔离紫/触发器红/SLA红/成本黄/记忆紫）
   */
  color: string;

  /**
   * 企业专属（社区端蜂巢整簇隐藏、健康分整项剔除、后端按键裁剪）
   */
  enterpriseOnly: boolean;

  disposal: SignalDisposal;
}

/**
 * 十类信号元数据注册表
 */
export const SIGNAL_META: Record<GovernanceSignalKey, SignalMeta> = {
  driftDetected: {
    key: 'driftDetected',
    label: '配置漂移',
    domain: 'runtime',
    severity: 'P2',
    color: '#fbbf24',
    enterpriseOnly: false,
    disposal: {
      label: '查看漂移详情',
      route: '/governance/agents/:agentCode?tab=config',
      communityRoute: '/agents?tab=drift',
      autoLabel: '一键修复',
    },
  },
  slaDegraded: {
    key: 'slaDegraded',
    label: 'SLA退化',
    domain: 'runtime',
    severity: 'P1',
    color: '#fb7185',
    enterpriseOnly: false,
    disposal: {
      label: '查看运行',
      route: '/agent-runs?agentCode=:agentCode',
      communityRoute: '/trace-runs?agentCode=:agentCode',
      autoLabel: '回归评测',
    },
  },
  costOverrun: {
    key: 'costOverrun',
    label: '成本超限',
    domain: 'runtime',
    severity: 'P2',
    color: '#fbbf24',
    enterpriseOnly: false,
    disposal: {
      label: '查看账单',
      route: '/agent-cost?agentCode=:agentCode',
      communityRoute: '/governance-agents/:agentCode?tab=cost',
      autoLabel: '预算收紧',
    },
  },
  triggerFailures: {
    key: 'triggerFailures',
    label: '触发器失败',
    domain: 'runtime',
    severity: 'P1',
    color: '#fb7185',
    enterpriseOnly: true,
    disposal: {
      label: '查看日志',
      route: '/intelligence/triggers?agentCode=:agentCode&showLogs=1',
    },
  },
  traceFailures: {
    key: 'traceFailures',
    label: '运行失败',
    domain: 'runtime',
    severity: 'P1',
    color: '#f97316',
    enterpriseOnly: true,
    disposal: {
      label: '查看运行',
      route: '/agent-runs?agentCode=:agentCode',
      autoLabel: '标记已处置',
    },
  },
  staleMemory: {
    key: 'staleMemory',
    label: '记忆过期',
    domain: 'trust',
    severity: 'P3',
    color: '#c084fc',
    enterpriseOnly: true,
    disposal: {
      label: '归档',
      route: '/intelligence/agent-memory?agentCode=:agentCode&status=STALE',
      autoLabel: '一键清理',
    },
  },
  quarantinedMemory: {
    key: 'quarantinedMemory',
    label: '记忆隔离',
    domain: 'trust',
    severity: 'P2',
    color: '#c084fc',
    enterpriseOnly: true,
    disposal: {
      label: '释放/归档/擦除',
      route: '/intelligence/agent-memory?agentCode=:agentCode&status=QUARANTINED',
    },
  },
  rotationDue: {
    key: 'rotationDue',
    label: '身份轮换到期',
    domain: 'trust',
    severity: 'P2',
    color: '#fbbf24',
    enterpriseOnly: true,
    disposal: {
      label: '立即轮换',
      route: '/trust/identities?agentCode=:agentCode&rotate=1',
      autoLabel: '一键轮换',
    },
  },
  auditAnomalies: {
    key: 'auditAnomalies',
    label: '审计异常',
    domain: 'trust',
    severity: 'P1',
    color: '#fb7185',
    enterpriseOnly: true,
    disposal: {
      label: '查看异常明细',
      route: '/trust/action-audit?agentCode=:agentCode',
    },
  },
  pendingProposals: {
    key: 'pendingProposals',
    label: '进化提案',
    domain: 'trust',
    severity: 'P3',
    color: '#60a5fa',
    enterpriseOnly: true,
    disposal: {
      label: '确认/驳回',
      route: '/intelligence/evolution-proposals?agentCode=:agentCode',
    },
  },
};

/**
 * 双端基础信号键（信号1-3）
 */
export const COMMUNITY_SIGNAL_KEYS: GovernanceSignalKey[] = [
  'driftDetected',
  'slaDegraded',
  'costOverrun',
];

/**
 * 企业专属信号键（信号4-10）
 */
export const ENTERPRISE_SIGNAL_KEYS: GovernanceSignalKey[] = [
  'traceFailures',
  'triggerFailures',
  'staleMemory',
  'quarantinedMemory',
  'rotationDue',
  'auditAnomalies',
  'pendingProposals',
];

/**
 * 蜂巢带左簇（运行域，双端按序渲染）
 */
export const RUNTIME_HEX_ORDER: GovernanceSignalKey[] = [
  'driftDetected',
  'slaDegraded',
  'costOverrun',
  'traceFailures',
  'triggerFailures',
];

/**
 * 蜂巢带右簇（信任与合规域，企业专属整簇渲染）
 */
export const TRUST_HEX_ORDER: GovernanceSignalKey[] = [
  'rotationDue',
  'auditAnomalies',
  'quarantinedMemory',
  'pendingProposals',
  'staleMemory',
];

/**
 * 严重级徽标样式
 */
export const SIGNAL_SEVERITY_LABEL: Record<SignalSeverity, string> = {
  P1: 'P1 严重',
  P2: 'P2 警告',
  P3: 'P3 提示',
};

/**
 * 社区端处置能力企业版锁定提示(置灰入口Tooltip/Title共用)
 */
export const ENTERPRISE_DISPOSAL_TIP = '该处置能力为企业版专属，请升级至企业版使用';

/**
 * 按端返回可见信号键集合
 * @param community 是否社区端
 * @return
 */
export function signalKeysForScope(community: boolean): GovernanceSignalKey[] {
  return community ? [...COMMUNITY_SIGNAL_KEYS] : [...COMMUNITY_SIGNAL_KEYS, ...ENTERPRISE_SIGNAL_KEYS];
}

/**
 * 实例化处置跳转路由
 * @param key 信号类型
 * @param agentCode Agent编码
 * @param community 是否社区端
 * @return
 */
export function resolveDisposalRoute(key: GovernanceSignalKey, agentCode: string | null | undefined,
                                     community: boolean): string | null {
  const meta = SIGNAL_META[key];
  if (!meta) {
    return null;
  }
  const template = community && meta.disposal.communityRoute ? meta.disposal.communityRoute : meta.disposal.route;
  return template.replace(':agentCode', agentCode ?? '');
}

/**
 * 信号是否支持一键处置(后端处置动作注册表已配置)
 * @param key 信号类型
 * @return
 */
export function signalAutoDisposalLabel(key: GovernanceSignalKey): string | null {
  return SIGNAL_META[key]?.disposal.autoLabel ?? null;
}

/**
 * 生成信号事件条目稳定ID(双端Inbox已读忽略标记的localStorage键依据)
 * @param item 信号事件条目
 * @return
 */
export function signalItemId(item: GovernanceRecentItem): string {
  return [item.type, item.agentCode ?? '', item.occurredAt ?? '', item.summary ?? ''].join('|');
}
