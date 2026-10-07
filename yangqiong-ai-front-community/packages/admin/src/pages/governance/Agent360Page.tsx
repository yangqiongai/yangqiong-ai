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
import React, { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Button, Progress, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArrowLeftOutlined } from '@ant-design/icons';
import {
  EnterpriseLockIcon,
  ENTERPRISE_DISPOSAL_TIP,
  HEALTH_BAND_COLORS,
  SIGNAL_META,
  computeHealthScore,
  formatDate,
  healthBand,
  healthScoreInputOf,
} from '@yangqiong/shared';
import type { AgentRelease, AgentRuntimeSummary, AgentSummaryDrawerTab, TraceRunRow } from '@yangqiong/shared';
import { api } from '@/services';
import { mcpEcosystemApi } from '@/services/mcp-ecosystem-api';
import type { AgentMemoryEntryInfo, AgentTriggerInfo } from '@/services/mcp-ecosystem-api';
import { governanceDashboardApi } from '@/services/governance-dashboard-api';

/**
 * Tab注册表(社区6 Tab：概要/运行/成本/记忆基础/触发器/配置，企业专属Tab不渲染不留位)
 */
const TABS = [
  { key: 'overview', label: '概要' },
  { key: 'runs', label: '运行' },
  { key: 'cost', label: '成本' },
  { key: 'memory', label: '记忆' },
  { key: 'trigger', label: '触发规则' },
  { key: 'config', label: '配置与版本' },
] as const;

type TabKey = (typeof TABS)[number]['key'];

/**
 * Agent 360°详情(社区裁剪版：头部摘要卡常驻+6 Tab按需加载，数据源全部复用既有端点传agentCode过滤)
 * @return
 */
export const Agent360Page: React.FC = () => {
  const navigate = useNavigate();
  const { agentCode = '' } = useParams<{ agentCode: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const rawTab = searchParams.get('tab') ?? 'overview';
  const activeTab = (TABS.some((tab) => tab.key === rawTab) ? rawTab : 'overview') as TabKey;

  const summaryQuery = useQuery({
    queryKey: ['governance-dashboard-summary'],
    queryFn: governanceDashboardApi.summary,
    staleTime: 30_000,
  });
  const agent = useMemo(
    () => summaryQuery.data?.agents.find((item) => item.agentCode === agentCode) ?? null,
    [summaryQuery.data, agentCode],
  );

  /**
   * 切换Tab并同步URL(处置跳转可携带tab直达)
   * @param tab
   */
  const switchTab = (tab: string): void => {
    const params = new URLSearchParams(searchParams);
    if (tab === 'overview') {
      params.delete('tab');
    } else {
      params.set('tab', tab);
    }
    setSearchParams(params, { replace: true });
  };

  return (
    <div className="flex flex-col gap-4" data-testid="agent-360-page">
      <div className="flex items-center justify-between">
        <div>
          <Typography.Title level={4} className="!mb-0">Agent 360° 详情</Typography.Title>
          <Typography.Text type="secondary" className="text-xs">
            单一Agent的运行治理全景：概要、运行、成本、记忆与触发器
          </Typography.Text>
        </div>
        <Button size="small" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>
          返回
        </Button>
      </div>
      <IdentitySummaryCard
        agentCode={agentCode}
        agent={agent}
        loading={summaryQuery.isLoading}
      />
      <Tabs
        activeKey={activeTab}
        onChange={switchTab}
        items={TABS.map((tab) => ({
          key: tab.key,
          label: tab.label,
          children: activeTab === tab.key ? <TabContent tab={tab.key} agentCode={agentCode} agent={agent} /> : null,
        }))}
      />
    </div>
  );
};

/**
 * 构建360°Tab项(供驾驶舱摘要抽屉内嵌复用，非激活Tab不挂载)
 * @param agentCode
 * @param agent
 * @return
 */
export const buildAgent360Tabs = (
  agentCode: string,
  agent: AgentRuntimeSummary | null,
): AgentSummaryDrawerTab[] =>
  TABS.map((tab) => ({
    key: tab.key,
    label: tab.label,
    children: <TabContent tab={tab.key} agentCode={agentCode} agent={agent} />,
  }));

/**
 * 头部摘要卡(全Tab常驻：名称/编码/状态/健康分/发布版本/信号徽标组，社区无身份凭证区)
 */
const IdentitySummaryCard: React.FC<{
  agentCode: string;

  agent: AgentRuntimeSummary | null;

  loading: boolean;
}> = ({ agentCode, agent, loading }) => {
  if (loading) {
    return (
      <div className="rounded-xl border border-slate-200/70 bg-white px-5 py-4 text-sm text-slate-400">
        摘要加载中…
      </div>
    );
  }
  if (!agent) {
    return (
      <div className="rounded-xl border border-slate-200/70 bg-white px-5 py-4 text-sm text-slate-500">
        未在治理清单中找到 Agent <span className="font-mono">{agentCode || '-'}</span>，可能未纳管或已下线。
      </div>
    );
  }
  const score = computeHealthScore(healthScoreInputOf(agent), { community: true });
  const band = healthBand(score);
  const activeFlags = Object.entries(agent.signalFlags ?? {}).filter(([, count]) => count > 0);
  return (
    <div className="rounded-xl border border-slate-200/70 bg-white px-5 py-4" data-testid="agent360-summary">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="truncate text-base font-semibold text-slate-900">{agent.agentName || agent.agentCode}</span>
            {agent.status === 1 ? <Tag color="green">启用</Tag> : <Tag>禁用</Tag>}
          </div>
          <div className="mt-0.5 font-mono text-xs text-slate-400">{agent.agentCode}</div>
        </div>
        <SummaryItem label="健康分">
          <span className="font-mono text-lg font-semibold" style={{ color: HEALTH_BAND_COLORS[band] }}>{score}</span>
        </SummaryItem>
        <SummaryItem label="当前版本">
          {agent.latestReleaseNo ? (
            <>
              <span className="font-mono text-xs text-slate-700" title={agent.latestReleaseNo}>
                {agent.latestReleaseNo.slice(0, 18)}
              </span>
              <span className="ml-1 text-xs text-slate-400">{formatDate(agent.latestPublishTime, 'MM-DD HH:mm')}</span>
            </>
          ) : (
            <span className="text-xs text-slate-400">未发布</span>
          )}
        </SummaryItem>
      </div>
      {activeFlags.length > 0 ? (
        <div className="mt-3 flex flex-wrap items-center gap-1.5 border-t border-slate-100 pt-3">
          <span className="text-xs text-slate-400">信号徽标：</span>
          {activeFlags.map(([key, count]) => {
            const meta = SIGNAL_META[key as keyof typeof SIGNAL_META];
            return (
              <Tooltip key={key} title={ENTERPRISE_DISPOSAL_TIP}>
                <span
                  className="flex cursor-not-allowed items-center gap-1 rounded-full px-2 py-0.5 text-xs opacity-70"
                  style={{ backgroundColor: `${meta?.color}1a`, color: meta?.color }}
                  data-testid="agent360-signal-flag"
                >
                  {meta?.label ?? key}
                  <span className="font-mono">{count}</span>
                </span>
              </Tooltip>
            );
          })}
          {/* 信号处置已收回企业版，锁图标仅作概览标识 */}
          <Tooltip title={ENTERPRISE_DISPOSAL_TIP}>
            <EnterpriseLockIcon />
          </Tooltip>
        </div>
      ) : (
        <div className="mt-3 border-t border-slate-100 pt-3 text-xs text-emerald-600">无待处置信号</div>
      )}
    </div>
  );
};

/**
 * 摘要项(标签+内容纵向排布)
 */
const SummaryItem: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <div className="flex flex-col gap-0.5">
    <span className="text-xs text-slate-400">{label}</span>
    <span className="flex items-center">{children}</span>
  </div>
);

/**
 * Tab内容分发(仅渲染激活Tab，按需加载)
 */
const TabContent: React.FC<{
  tab: TabKey;

  agentCode: string;

  agent: AgentRuntimeSummary | null;
}> = ({ tab, agentCode, agent }) => {
  switch (tab) {
    case 'runs':
      return <RunsTab agentCode={agentCode} />;
    case 'cost':
      return <CostTab agent={agent} />;
    case 'memory':
      return <MemoryTab agentCode={agentCode} />;
    case 'trigger':
      return <TriggerTab agentCode={agentCode} />;
    case 'config':
      return <ConfigTab agentCode={agentCode} />;
    case 'overview':
    default:
      return <OverviewTab agentCode={agentCode} agent={agent} />;
  }
};

/**
 * 运行表格列(概要/运行Tab共用)
 */
const RUN_COLUMNS: ColumnsType<TraceRunRow> = [
  {
    title: 'TraceID',
    dataIndex: 'traceId',
    render: (value: string) => <Typography.Text code ellipsis={{ tooltip: value }}>{value}</Typography.Text>,
  },
  {
    title: '状态',
    width: 90,
    dataIndex: 'status',
    render: (value: string) => value === 'FAILED' ? <Tag color="red">失败</Tag> : <Tag color="green">{value || '-'}</Tag>,
  },
  {
    title: '耗时',
    width: 100,
    dataIndex: 'durationMs',
    render: (value?: number) => <Typography.Text type="secondary">{value != null ? `${value}ms` : '-'}</Typography.Text>,
  },
  {
    title: 'Tokens',
    width: 100,
    dataIndex: 'totalTokens',
    render: (value?: number) => (
      <Typography.Text type="secondary" className="font-mono tabular-nums">{value ?? '-'}</Typography.Text>
    ),
  },
  {
    title: '开始时间',
    width: 160,
    dataIndex: 'startTime',
    render: (value?: string) => <Typography.Text type="secondary">{formatDate(value)}</Typography.Text>,
  },
];

/**
 * 指标盒(概要/成本摘要共用)
 */
const MetricBox: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div className="rounded-lg border border-slate-100 bg-slate-50/60 px-4 py-3">
    <div className="text-xs text-slate-400">{label}</div>
    <div className="mt-1 font-mono text-base font-semibold text-slate-800 tabular-nums">{value}</div>
  </div>
);

/**
 * 概要Tab：关键指标+最近10条运行
 */
const OverviewTab: React.FC<{ agentCode: string; agent: AgentRuntimeSummary | null }> = ({ agentCode, agent }) => {
  const runsQuery = useQuery({
    queryKey: ['agent360', agentCode, 'overview-runs'],
    queryFn: () => api.trace.runs({ agentCode, pageSize: 10 }),
    enabled: !!agentCode,
  });
  const stats = [
    { label: '近7天运行', value: String(agent?.runs7d ?? 0) },
    { label: '近7天失败', value: String(agent?.failures7d ?? 0) },
    { label: '近30天成本', value: agent?.cost30d != null ? `$${agent.cost30d.toFixed(2)}` : '-' },
    { label: '月度预算', value: agent?.budgetAmount != null ? `$${agent.budgetAmount.toFixed(2)}` : '未配置' },
  ];
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {stats.map((stat) => (
          <MetricBox key={stat.label} label={stat.label} value={stat.value} />
        ))}
      </div>
      <div className="rounded-xl border border-slate-200/70 bg-white">
        <div className="border-b border-slate-100 px-5 py-3 text-sm font-medium text-slate-700">最近 10 条运行</div>
        <Table<TraceRunRow>
          rowKey="traceId"
          columns={RUN_COLUMNS}
          dataSource={runsQuery.data ?? []}
          loading={runsQuery.isLoading}
          size="small"
          pagination={false}
          locale={{ emptyText: '暂无运行记录' }}
        />
      </div>
    </div>
  );
};

/**
 * 运行Tab：运行观测数据源过滤视图
 */
const RunsTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const runsQuery = useQuery({
    queryKey: ['agent360', agentCode, 'runs'],
    queryFn: () => api.trace.runs({ agentCode, pageSize: 20 }),
    enabled: !!agentCode,
  });
  return (
    <div className="rounded-xl border border-slate-200/70 bg-white">
      <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
        <span className="text-sm font-medium text-slate-700">运行记录（最近 20 条）</span>
        <Link to={`/trace-runs?agentCode=${agentCode}`} className="text-xs text-[#1677ff] hover:underline">
          在运行回放中查看 →
        </Link>
      </div>
      <Table<TraceRunRow>
        rowKey="traceId"
        columns={RUN_COLUMNS}
        dataSource={runsQuery.data ?? []}
        loading={runsQuery.isLoading}
        size="small"
        pagination={false}
        locale={{ emptyText: '暂无运行记录' }}
      />
    </div>
  );
};

/**
 * 成本Tab：近30天成本与预算水位(社区无账单明细端点，由summary单请求直出)
 */
const CostTab: React.FC<{ agent: AgentRuntimeSummary | null }> = ({ agent }) => {
  if (!agent) {
    return (
      <div className="rounded-xl border border-slate-200/70 bg-white px-5 py-4 text-sm text-slate-500">
        暂无成本数据。
      </div>
    );
  }
  const cost = agent.cost30d ?? 0;
  const budget = agent.budgetAmount;
  const ratio = budget != null && budget > 0 ? Math.min(cost / budget, 1) : null;
  const overrun = (agent.signalFlags?.costOverrun ?? 0) > 0;
  return (
    <div className="rounded-xl border border-slate-200/70 bg-white px-5 py-4" data-testid="agent360-cost">
      <div className="grid grid-cols-3 gap-3">
        <MetricBox label="近30天成本" value={`$${cost.toFixed(2)}`} />
        <MetricBox label="月度预算" value={budget != null ? `$${budget.toFixed(2)}` : '未配置'} />
        <MetricBox label="超限信号" value={overrun ? '成本超限' : '正常'} />
      </div>
      {ratio != null && budget != null ? (
        <div className="mt-4">
          <div className="mb-1 flex items-center justify-between text-xs text-slate-400">
            <span>预算使用率</span>
            <span className="font-mono tabular-nums">{Math.round((cost / budget) * 100)}%</span>
          </div>
          <Progress
            percent={Math.round(ratio * 100)}
            status={overrun ? 'exception' : ratio > 0.8 ? 'active' : 'normal'}
            showInfo={false}
            data-testid="agent360-budget-progress"
          />
        </div>
      ) : (
        <div className="mt-4 text-xs text-slate-400">未配置预算，不参与成本超限计算。</div>
      )}
    </div>
  );
};

/**
 * 记忆Tab：条目摘要+完整页跳转
 */
const MemoryTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const entriesQuery = useQuery({
    queryKey: ['agent360', agentCode, 'memory-entries'],
    queryFn: () => mcpEcosystemApi.agentMemory.list({ agentCode, page: 1, size: 10 }),
    enabled: !!agentCode,
  });
  const columns: ColumnsType<AgentMemoryEntryInfo> = [
    { title: '类型', width: 110, dataIndex: 'memoryType' },
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
      render: (value: string) => value || '-',
    },
    {
      title: '状态',
      width: 120,
      dataIndex: 'status',
      render: (value: string) => {
        const color = value === 'QUARANTINED' ? 'purple' : value === 'STALE' ? 'gold' : 'green';
        return <Tag color={color}>{value}</Tag>;
      },
    },
    {
      title: '创建时间',
      width: 160,
      dataIndex: 'createTime',
      render: (value?: string) => <Typography.Text type="secondary">{formatDate(value)}</Typography.Text>,
    },
  ];
  return (
    <div className="rounded-xl border border-slate-200/70 bg-white">
      <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
        <span className="text-sm font-medium text-slate-700">最近记忆条目（10 条）</span>
        <Link to={`/agent-memory?agentCode=${agentCode}`} className="text-xs text-[#1677ff] hover:underline">
          在完整页中查看 →
        </Link>
      </div>
      <Table<AgentMemoryEntryInfo>
        rowKey="id"
        columns={columns}
        dataSource={entriesQuery.data?.records ?? []}
        loading={entriesQuery.isLoading}
        size="small"
        tableLayout="fixed"
        pagination={false}
        locale={{ emptyText: '暂无记忆条目' }}
      />
    </div>
  );
};

/**
 * 触发器Tab：规则列表+完整页跳转
 */
const TriggerTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const triggerQuery = useQuery({
    queryKey: ['agent360', agentCode, 'triggers'],
    queryFn: () => mcpEcosystemApi.trigger.list({ agentCode, page: 1, size: 10 }),
    enabled: !!agentCode,
  });
  const columns: ColumnsType<AgentTriggerInfo> = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (value: string, record) => (
        <span className="text-xs text-slate-700">
          {value}
          <span className="ml-1 font-mono text-[10px] text-slate-400">{record.triggerCode}</span>
        </span>
      ),
    },
    { title: '类型', width: 100, dataIndex: 'triggerType' },
    {
      title: '最近触发',
      width: 160,
      dataIndex: 'lastFireTime',
      render: (value?: string) => (
        <Typography.Text type="secondary">{formatDate(value) || '从未触发'}</Typography.Text>
      ),
    },
    {
      title: '状态',
      width: 90,
      dataIndex: 'enabled',
      render: (value?: number) => value === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
  ];
  return (
    <div className="rounded-xl border border-slate-200/70 bg-white">
      <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
        <span className="text-sm font-medium text-slate-700">
          触发规则（前 10 条，共 {triggerQuery.data?.total ?? 0} 条）
        </span>
        <Link to={`/triggers?agentCode=${agentCode}`} className="text-xs text-[#1677ff] hover:underline">
          在完整页中查看 →
        </Link>
      </div>
      <Table<AgentTriggerInfo>
        rowKey="id"
        columns={columns}
        dataSource={triggerQuery.data?.records ?? []}
        loading={triggerQuery.isLoading}
        size="small"
        pagination={false}
        locale={{ emptyText: '暂无触发规则' }}
      />
    </div>
  );
};

/**
 * 配置与版本Tab：发布记录+能力挂载摘要(只读，编辑跳Agent管理)
 */
const ConfigTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const releasesQuery = useQuery({
    queryKey: ['agent360', agentCode, 'releases'],
    queryFn: () => api.registry.releases(agentCode),
    enabled: !!agentCode,
  });
  const typeQuery = useQuery({
    queryKey: ['agent360', agentCode, 'agent-type'],
    queryFn: () => api.agent.type.list({ page: 1, size: 1000 }),
    enabled: !!agentCode,
  });
  const agentTypeInfo = useMemo(
    () => typeQuery.data?.list?.find((item) => item.typeCode === agentCode) ?? null,
    [typeQuery.data, agentCode],
  );
  const configEntries = useMemo(() => {
    if (!agentTypeInfo?.agentConfig) {
      return [];
    }
    try {
      const parsed = JSON.parse(agentTypeInfo.agentConfig) as Record<string, unknown>;
      return Object.entries(parsed).map(([key, value]) => ({
        key,
        value: typeof value === 'string' && value.length > 60 ? `${value.slice(0, 60)}…` : JSON.stringify(value),
      }));
    } catch {
      return [];
    }
  }, [agentTypeInfo]);

  const columns: ColumnsType<AgentRelease> = [
    {
      title: '发布流水号',
      dataIndex: 'releaseNo',
      render: (value: string) => <Typography.Text code ellipsis={{ tooltip: value }}>{value || '-'}</Typography.Text>,
    },
    {
      title: '状态',
      width: 100,
      dataIndex: 'status',
      render: (value?: string) => {
        const map: Record<string, { text: string; color: string }> = {
          SUCCESS: { text: '成功', color: 'green' },
          FAILED: { text: '失败', color: 'red' },
          ROLLBACK: { text: '已回滚', color: 'default' },
        };
        const meta = map[value ?? ''] ?? { text: value ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作人',
      width: 110,
      dataIndex: 'operator',
      render: (value?: string) => <Typography.Text type="secondary">{value || '-'}</Typography.Text>,
    },
    {
      title: '发布时间',
      width: 160,
      dataIndex: 'createTime',
      render: (value?: string) => <Typography.Text type="secondary">{formatDate(value)}</Typography.Text>,
    },
  ];
  return (
    <div className="flex flex-col gap-4">
      <div className="rounded-xl border border-slate-200/70 bg-white">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <span className="text-sm font-medium text-slate-700">能力挂载摘要（只读）</span>
          <Link to={`/agents?agentCode=${agentCode}`} className="text-xs text-[#1677ff] hover:underline">
            在「Agent 管理」中编辑 →
          </Link>
        </div>
        {configEntries.length === 0 ? (
          <div className="py-6 text-center text-xs text-slate-400">未解析到 agentConfig 配置</div>
        ) : (
          <div className="flex flex-col gap-2 px-5 py-4">
            {configEntries.map((entry) => (
              <div key={entry.key} className="flex items-start gap-2 text-xs">
                <span className="w-28 shrink-0 font-mono text-slate-400">{entry.key}</span>
                <span className="min-w-0 break-all font-mono text-slate-700">{entry.value}</span>
              </div>
            ))}
          </div>
        )}
      </div>
      <div className="rounded-xl border border-slate-200/70 bg-white">
        <div className="border-b border-slate-100 px-5 py-3 text-sm font-medium text-slate-700">发布记录</div>
        <Table<AgentRelease>
          rowKey={(record) => String(record.id ?? record.releaseNo)}
          columns={columns}
          dataSource={releasesQuery.data ?? []}
          loading={releasesQuery.isLoading}
          size="small"
          pagination={false}
          locale={{ emptyText: '暂无发布记录' }}
        />
      </div>
    </div>
  );
};
