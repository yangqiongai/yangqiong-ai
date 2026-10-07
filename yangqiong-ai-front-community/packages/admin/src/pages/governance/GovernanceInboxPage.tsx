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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Checkbox, Input, Modal, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { HistoryOutlined, InboxOutlined, SearchOutlined, ThunderboltOutlined } from '@ant-design/icons';
import {
  EnterpriseLockIcon,
  ENTERPRISE_DISPOSAL_TIP,
  SIGNAL_META,
  SIGNAL_SEVERITY_LABEL,
  formatDate,
  resolveDisposalRoute,
  signalAutoDisposalLabel,
  signalItemId,
  signalKeysForScope,
} from '@yangqiong/shared';
import type { GovernanceRecentItem, GovernanceSignalKey, SignalSeverity } from '@yangqiong/shared';
import {
  collectAckedItemIds,
  disposalFeedbackTone,
  filterInboxRows,
  mergeIgnoredIds,
  pickAutoDisposalItems,
  readLegacyIgnoredIds,
} from '@yangqiong/shared/governance';
import { governanceDashboardApi } from '@/services/governance-dashboard-api';
import { governanceDisposalApi } from '@/services/governance-disposal-api';
import type { DisposalRecord } from '@/services/governance-disposal-api';

/**
 * 已读确认记录查询上限(后端单页上限100)
 */
const ACK_PAGE_SIZE = 100;

/**
 * 严重级徽标颜色
 */
const SEVERITY_COLOR: Record<SignalSeverity, string> = {
  P1: 'red',
  P2: 'gold',
  P3: 'default',
};

/**
 * 处置结果徽标颜色
 */
const RESULT_COLOR: Record<string, string> = {
  SUCCESS: 'green',
  PARTIAL: 'gold',
  FAILED: 'red',
};

/**
 * 处置反馈条类型
 */
const FEEDBACK_TYPE: Record<string, 'success' | 'warning' | 'error'> = {
  success: 'success',
  partial: 'warning',
  failed: 'error',
};

/**
 * 动作类型中文名
 */
const ACTION_LABEL: Record<string, string> = {
  AUTO_REPAIR: '自动修复',
  RUN_REGRESSION: '回归评测',
  BUDGET_OVERRIDE: '预算收紧',
  BUDGET_OVERRIDE_REVOKE: '撤销预算覆盖',
  ACK: '已读确认',
  EXECUTE: '处置',
};

/**
 * 治理待处置Inbox(社区裁剪版：3类运行信号聚合视图，严重级优先，信号源为事实源不新建后端表)
 * 处置能力已收回企业版，处置入口置灰仅作升级引导；已读状态经ACK记录只读恢复，支持处置历史查看
 * @return
 */
export const GovernanceInboxPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [agentKeyword, setAgentKeyword] = useState('');
  const [showIgnored, setShowIgnored] = useState(false);
  const [checkedIds, setCheckedIds] = useState<React.Key[]>([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'partial' | 'failed'; text: string } | null>(null);
  const legacyIgnoredIds = useMemo(readLegacyIgnoredIds, []);

  const scopeKeys = useMemo(() => signalKeysForScope(true), []);
  const selectedTypes = useMemo(() => {
    const raw = (searchParams.get('type') ?? '').split(',');
    return scopeKeys.filter((key) => raw.includes(key));
  }, [searchParams, scopeKeys]);

  const summaryQuery = useQuery({
    queryKey: ['governance-dashboard-summary'],
    queryFn: governanceDashboardApi.summary,
    staleTime: 30_000,
  });
  const summary = summaryQuery.data;

  const acksQuery = useQuery({
    queryKey: ['governance-disposal-acks'],
    queryFn: () => governanceDisposalApi.records({ action: 'ACK', pageSize: ACK_PAGE_SIZE }),
    staleTime: 15_000,
  });

  // 已读集合=后端ACK记录中的条目ID+localStorage遗留ID
  const ackedItemIds = useMemo(() => collectAckedItemIds(acksQuery.data), [acksQuery.data]);
  const ignoredIds = useMemo(
    () => mergeIgnoredIds(ackedItemIds, legacyIgnoredIds),
    [ackedItemIds, legacyIgnoredIds],
  );

  const rows = useMemo(
    () =>
      // 社区端防御性裁剪：企业专属信号条目即使混入数据源也不渲染
      filterInboxRows(
        (summary?.recent ?? []).filter((item) => scopeKeys.includes(item.type)),
        { selectedTypes, agentKeyword, showIgnored, ignoredIds },
      ),
    [summary, scopeKeys, selectedTypes, agentKeyword, showIgnored, ignoredIds],
  );

  const checkedItems = useMemo(
    () => rows.filter((item) => checkedIds.includes(signalItemId(item))),
    [rows, checkedIds],
  );
  const autoDisposalItems = useMemo(
    () => pickAutoDisposalItems(checkedItems),
    [checkedItems],
  );

  const invalidateAcks = (): void => {
    queryClient.invalidateQueries({ queryKey: ['governance-disposal-acks'] });
  };

  const acknowledgeMutation = useMutation({
    mutationFn: async (items: GovernanceRecentItem[]) => {
      await Promise.allSettled(items.map((item) =>
        governanceDisposalApi.acknowledge(item.type, item.agentCode, signalItemId(item))));
    },
    onSuccess: () => {
      invalidateAcks();
      setCheckedIds([]);
    },
  });

  const executeMutation = useMutation({
    mutationFn: async (item: GovernanceRecentItem) => {
      const result = await governanceDisposalApi.execute(item.type, item.agentCode);
      // 处置生效后自动已读, 失败项保留在待处置列表
      if (result.status !== 'FAILED') {
        await governanceDisposalApi.acknowledge(item.type, item.agentCode, signalItemId(item));
      }
      return result;
    },
    onSuccess: (result, item) => {
      invalidateAcks();
      setFeedback({
        tone: disposalFeedbackTone(result.status),
        text: `${SIGNAL_META[item.type]?.label ?? item.type}一键处置：${result.detail}`,
      });
    },
    onError: (error, item) => {
      setFeedback({
        tone: 'failed',
        text: `${SIGNAL_META[item.type]?.label ?? item.type}一键处置失败：${error instanceof Error ? error.message : '请稍后重试'}`,
      });
    },
  });

  const batchDisposalMutation = useMutation({
    mutationFn: async (items: GovernanceRecentItem[]) => {
      const details: string[] = [];
      let successCount = 0;
      for (const item of items) {
        try {
          const result = await governanceDisposalApi.execute(item.type, item.agentCode);
          if (result.status !== 'FAILED') {
            await governanceDisposalApi.acknowledge(item.type, item.agentCode, signalItemId(item));
          }
          if (result.status === 'SUCCESS') {
            successCount++;
          }
          details.push(`${SIGNAL_META[item.type]?.label ?? item.type}(${item.agentCode})：${result.detail}`);
        } catch {
          details.push(`${SIGNAL_META[item.type]?.label ?? item.type}(${item.agentCode})：处置失败`);
        }
      }
      invalidateAcks();
      return { successCount, total: items.length, details };
    },
    onSuccess: ({ successCount, total, details }) => {
      setCheckedIds([]);
      setFeedback({
        tone: successCount === total ? 'success' : successCount === 0 ? 'failed' : 'partial',
        text: `批量处置完成：成功${successCount}/${total}。${details.join('；')}`,
      });
    },
  });

  const historyQuery = useQuery({
    queryKey: ['governance-disposal-records'],
    queryFn: () => governanceDisposalApi.records({ pageSize: ACK_PAGE_SIZE }),
    enabled: historyOpen,
  });

  /**
   * 切换信号类型过滤(多选，同步到URL保持可分享)
   * @param key
   */
  const toggleType = (key: GovernanceSignalKey): void => {
    const next = selectedTypes.includes(key)
      ? selectedTypes.filter((item) => item !== key)
      : [...selectedTypes, key];
    const params = new URLSearchParams(searchParams);
    if (next.length > 0) {
      params.set('type', next.join(','));
    } else {
      params.delete('type');
    }
    setSearchParams(params, { replace: true });
    setCheckedIds([]);
  };

  const columns: ColumnsType<GovernanceRecentItem> = [
    {
      title: '信号类型',
      width: 140,
      render: (_, item) => {
        const meta = SIGNAL_META[item.type];
        return (
          <span className="flex items-center gap-1.5 text-xs font-medium">
            <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: meta?.color }} />
            {meta?.label ?? item.type}
            {ignoredIds.has(signalItemId(item)) ? (
              <span className="rounded bg-slate-100 px-1 text-[10px] text-slate-400">已读</span>
            ) : null}
          </span>
        );
      },
    },
    {
      title: 'Agent',
      width: 180,
      render: (_, item) => item.agentCode ? (
        <Button
          type="link"
          size="small"
          className="!px-0"
          onClick={() => navigate(`/governance-agents/${item.agentCode}`)}
        >
          {item.agentName || item.agentCode}
        </Button>
      ) : (
        <Typography.Text type="secondary">-</Typography.Text>
      ),
    },
    {
      title: '摘要',
      render: (_, item) => (
        <Typography.Text ellipsis={{ tooltip: item.summary ?? undefined }} className="max-w-md">
          {item.summary || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '严重级',
      width: 100,
      render: (_, item) => {
        const severity = SIGNAL_META[item.type]?.severity ?? 'P3';
        return <Tag color={SEVERITY_COLOR[severity]}>{SIGNAL_SEVERITY_LABEL[severity]}</Tag>;
      },
    },
    {
      title: '发生时间',
      width: 130,
      render: (_, item) => (
        <Typography.Text type="secondary" className="text-xs">
          {formatDate(item.occurredAt, 'MM-DD HH:mm')}
        </Typography.Text>
      ),
    },
    {
      title: '处置操作',
      width: 220,
      render: (_, item) => {
        const meta = SIGNAL_META[item.type];
        const autoLabel = signalAutoDisposalLabel(item.type);
        const autoReady = Boolean(autoLabel && item.agentCode);
        // 漂移处置跳转目标(Agent管理漂移Tab)已下线, 社区端不再渲染该导航
        const disposeRoute = item.type === 'driftDetected'
          ? null
          : resolveDisposalRoute(item.type, item.agentCode, true);
        return (
          <div className="flex flex-wrap items-center gap-1">
            {autoReady && (
              <Tooltip title={ENTERPRISE_DISPOSAL_TIP}>
                <span>
                  <Button
                    type="primary"
                    size="small"
                    icon={<ThunderboltOutlined />}
                    disabled
                    data-testid={`inbox-auto-dispose-${item.type}`}
                    onClick={() => executeMutation.mutate(item)}
                  >
                    {autoLabel}
                  </Button>
                </span>
              </Tooltip>
            )}
            {disposeRoute ? (
              <Tooltip title={ENTERPRISE_DISPOSAL_TIP}>
                <span>
                  <Button
                    type="link"
                    size="small"
                    className="!px-0"
                    disabled
                    data-testid={`inbox-dispose-${item.type}`}
                    onClick={() => navigate(disposeRoute)}
                  >
                    {meta?.disposal.label}
                  </Button>
                </span>
              </Tooltip>
            ) : null}
            {(autoReady || disposeRoute) ? <Tooltip title={ENTERPRISE_DISPOSAL_TIP}><EnterpriseLockIcon /></Tooltip> : null}
          </div>
        );
      },
    },
  ];

  if (summaryQuery.isLoading) {
    return (
      <div className="flex items-center gap-2 py-10 text-sm text-slate-400">
        待处置信号加载中…
      </div>
    );
  }

  if (summaryQuery.isError) {
    return (
      <div className="flex items-center gap-2 py-10 text-sm text-slate-400">
        <InboxOutlined />
        待处置信号加载失败，请稍后重试
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4" data-testid="governance-inbox">
      {/* 处置能力已收回企业版，社区版仅保留信号概览与已读标记引导 */}
      <Alert type="info" showIcon message="治理信号处置为企业版能力，社区版仅展示信号概览" />
      <div className="flex items-center justify-between">
        <div>
          <Typography.Title level={4} className="!mb-0">治理收件箱</Typography.Title>
          <Typography.Text type="secondary" className="text-xs">
            三类治理信号聚合视图：严重级优先，处置入口已升级至企业版
          </Typography.Text>
        </div>
        <div className="flex items-center gap-2">
          {autoDisposalItems.length > 0 && (
            <Tooltip title={ENTERPRISE_DISPOSAL_TIP}>
              <span className="inline-flex items-center gap-1">
                <Button
                  type="primary"
                  size="small"
                  disabled
                  data-testid="inbox-batch-dispose"
                  onClick={() => batchDisposalMutation.mutate(autoDisposalItems)}
                >
                  批量处置（{autoDisposalItems.length}）
                </Button>
                <EnterpriseLockIcon />
              </span>
            </Tooltip>
          )}
          {checkedIds.length > 0 ? (
            <Tooltip title={ENTERPRISE_DISPOSAL_TIP}>
              <span className="inline-flex items-center gap-1">
                <Button
                  size="small"
                  disabled
                  onClick={() => acknowledgeMutation.mutate(checkedItems)}
                  data-testid="inbox-mark-read"
                >
                  标记已读（{checkedIds.length}）
                </Button>
                <EnterpriseLockIcon />
              </span>
            </Tooltip>
          ) : null}
          <Button
            size="small"
            icon={<HistoryOutlined />}
            data-testid="inbox-history"
            onClick={() => setHistoryOpen(true)}
          >
            处置记录
          </Button>
        </div>
      </div>
      {feedback && (
        <Alert
          type={FEEDBACK_TYPE[feedback.tone]}
          message={feedback.text}
          closable
          showIcon
          data-testid="inbox-disposal-feedback"
          onClose={() => setFeedback(null)}
        />
      )}
      <div className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200/70 bg-white px-4 py-3">
        {scopeKeys.map((key) => {
          const meta = SIGNAL_META[key];
          const active = selectedTypes.includes(key);
          const count = summary?.signals[key] ?? 0;
          return (
            <button
              key={key}
              type="button"
              aria-pressed={active}
              data-testid={`inbox-type-chip-${key}`}
              onClick={() => toggleType(key)}
              className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs transition-colors ${
                active
                  ? 'border-[#1677ff]/40 bg-[#1677ff]/10 text-[#1677ff]'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
              }`}
            >
              <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: meta?.color }} />
              {meta?.label ?? key}
              <span className="font-mono tabular-nums text-slate-400">{count}</span>
            </button>
          );
        })}
        <div className="ml-auto flex items-center gap-3">
          <Input
            value={agentKeyword}
            onChange={(event) => setAgentKeyword(event.target.value)}
            placeholder="搜索 Agent 名称/编码"
            prefix={<SearchOutlined className="text-slate-400" />}
            className="w-56"
            size="small"
            allowClear
            data-testid="inbox-agent-search"
          />
          <Checkbox
            checked={showIgnored}
            data-testid="inbox-show-ignored"
            onChange={(event) => setShowIgnored(event.target.checked)}
          >
            显示已忽略
          </Checkbox>
        </div>
      </div>
      <div className="rounded-xl border border-slate-200/70 bg-white">
        <Table<GovernanceRecentItem>
          rowKey={(item) => signalItemId(item)}
          columns={columns}
          dataSource={rows}
          size="middle"
          pagination={false}
          locale={{ emptyText: showIgnored ? '没有已忽略的信号' : '暂无待处置信号' }}
          rowSelection={{
            selectedRowKeys: checkedIds,
            onChange: (keys) => setCheckedIds(keys),
          }}
          onRow={(item) => ({
            'data-testid': 'inbox-row',
            className: ignoredIds.has(signalItemId(item)) ? 'opacity-50' : undefined,
          })}
        />
      </div>
      <Modal
        title="处置记录（最近100条）"
        open={historyOpen}
        footer={null}
        width={860}
        onCancel={() => setHistoryOpen(false)}
      >
        <div data-testid="inbox-history-dialog">
          {historyQuery.isLoading ? (
            <div className="py-8 text-center text-sm text-slate-400">处置记录加载中…</div>
          ) : (historyQuery.data ?? []).length === 0 ? (
            <div className="py-8 text-center text-sm text-slate-400">暂无处置记录</div>
          ) : (
            <Table<DisposalRecord>
              rowKey={(record) => String(record.id)}
              dataSource={historyQuery.data ?? []}
              size="small"
              pagination={false}
              scroll={{ y: 420 }}
            >
              <Table.Column<DisposalRecord> title="时间" width={130} render={(_, record) => (
                <Typography.Text type="secondary" className="text-xs">
                  {record.createTime ? formatDate(record.createTime, 'MM-DD HH:mm') : '-'}
                </Typography.Text>
              )} />
              <Table.Column<DisposalRecord> title="信号" width={100} render={(_, record) =>
                SIGNAL_META[record.signalType as GovernanceSignalKey]?.label ?? record.signalType
              } />
              <Table.Column<DisposalRecord> title="Agent" width={130} ellipsis render={(_, record) =>
                record.agentCode || '-'
              } />
              <Table.Column<DisposalRecord> title="动作" width={110} render={(_, record) =>
                ACTION_LABEL[record.action] ?? record.action
              } />
              <Table.Column<DisposalRecord> title="结果" width={100} render={(_, record) => (
                <Tag color={RESULT_COLOR[record.resultStatus] ?? 'default'}>{record.resultStatus}</Tag>
              )} />
              <Table.Column<DisposalRecord> title="明细" ellipsis render={(_, record) =>
                record.resultDetail || '-'
              } />
            </Table>
          )}
        </div>
      </Modal>
    </div>
  );
};
