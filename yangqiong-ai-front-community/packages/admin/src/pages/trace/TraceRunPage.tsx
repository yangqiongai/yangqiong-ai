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
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Tree,
  Typography,
} from 'antd';
import { ReloadOutlined, CaretRightOutlined, CopyOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { getAppRoutePlugins } from '@/routes/app-plugin';
import { formatDate } from '@yangqiong/shared';
import type {
  ContextMessage,
  SpanNode,
  TaskForkPayload,
  TaskStep,
  TraceRunRow,
} from '@yangqiong/shared';

const QUERY_KEY = 'trace-runs';

/**
 * 运行状态展示
 */
const RUN_STATUS_META: Record<string, { label: string; color: string }> = {
  OK: { label: '成功', color: 'green' },
  ERROR: { label: '失败', color: 'red' },
};

/**
 * 步骤类型展示
 */
const STEP_TYPE_META: Record<string, { label: string; color: string }> = {
  LLM_CALL: { label: 'LLM调用', color: 'blue' },
  TOOL_CALL: { label: '工具调用', color: 'purple' },
  SUBAGENT_CALL: { label: '子Agent调用', color: 'cyan' },
};

/**
 * 上下文消息角色展示
 */
const CONTEXT_ROLE_META: Record<string, { label: string; color: string }> = {
  system: { label: '系统', color: 'purple' },
  user: { label: '用户', color: 'blue' },
  assistant: { label: '助手', color: 'green' },
  tool: { label: '工具', color: 'orange' },
};

/**
 * 上下文消息来源中文标签
 */
const CONTEXT_SOURCE_LABEL: Record<string, string> = {
  system_prompt: '系统提示',
  history: '历史',
  memory_inject: '记忆注入',
  tool_result: '工具结果',
};

/**
 * 非tool消息超过该长度默认折叠
 */
const LONG_CONTENT_THRESHOLD = 600;

interface SpanTreeItem {
  key: string;
  node: SpanNode;
  title: React.ReactNode;
  children: SpanTreeItem[];
}

/**
 * 校验JSON并格式化，非法时返回原文
 * @param text
 * @return
 */
const prettyJson = (text?: string | Record<string, unknown> | null): string => {
  if (!text) return '-';
  if (typeof text === 'object') {
    return JSON.stringify(text, null, 2);
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
};

/**
 * 递归构建Span树数据
 * @param nodes
 * @return
 */
const buildTreeItems = (nodes?: SpanNode[]): SpanTreeItem[] =>
  (nodes ?? []).map((node) => ({
    key: node.spanId,
    node,
    title: (
      <Space size={6}>
        <Tag color={node.status === 'ERROR' ? 'red' : 'green'} style={{ marginRight: 0 }}>
          {node.status === 'ERROR' ? '失败' : '成功'}
        </Tag>
        <Typography.Text style={{ fontSize: 13 }}>{node.operation ?? '-'}</Typography.Text>
        {node.durationMs != null && (
          <Tooltip title="耗时">
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {node.durationMs}ms
            </Typography.Text>
          </Tooltip>
        )}
      </Space>
    ),
    children: buildTreeItems(node.children),
  }));

/**
 * 按key递归查找Span树节点
 * @param items
 * @param key
 * @return
 */
const findTreeItem = (items: SpanTreeItem[], key: string): SpanTreeItem | null => {
  for (const item of items) {
    if (item.key === key) return item;
    const found = findTreeItem(item.children, key);
    if (found) return found;
  }
  return null;
};

interface ContextMessageItemProps {
  index: number;
  item: ContextMessage;
  onCopy: (text: string) => void;
}

/**
 * 上下文快照单条消息（tool与超长消息默认折叠，可展开）
 */
const ContextMessageItem = ({ index, item, onCopy }: ContextMessageItemProps) => {
  const [expanded, setExpanded] = useState(false);
  const roleMeta = CONTEXT_ROLE_META[item.role ?? ''] ?? { label: item.role ?? '-', color: 'default' };
  const sourceLabel = CONTEXT_SOURCE_LABEL[item.source ?? ''];
  const content = item.content ?? '';
  // tool消息与超长消息默认折叠
  const collapsible = item.role === 'tool' || content.length > LONG_CONTENT_THRESHOLD;
  const shown = collapsible && !expanded ? content.slice(0, 300) : content;

  return (
    <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: '8px 12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          #{index + 1}
        </Typography.Text>
        <Tag color={roleMeta.color} style={{ marginRight: 0 }}>
          {roleMeta.label}
        </Tag>
        {sourceLabel && <Tag style={{ marginRight: 0 }}>{sourceLabel}</Tag>}
        {item.truncated && <Tag color="red" style={{ marginRight: 0 }}>已截断</Tag>}
        <span style={{ flex: 1 }} />
        <Button
          type="text"
          size="small"
          icon={<CopyOutlined />}
          onClick={() => onCopy(content)}
        >
          复制原文
        </Button>
      </div>
      <pre
        style={{
          margin: 0,
          maxHeight: collapsible && !expanded ? 120 : 320,
          overflow: 'auto',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          fontSize: 12,
        }}
      >
        {shown || '-'}
      </pre>
      {collapsible && (
        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => setExpanded(!expanded)}>
          {expanded ? '收起' : '展开全部'}
        </Button>
      )}
    </div>
  );
};

/**
 * 运行回放
 */
export const TraceRunPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [agentCode, setAgentCode] = useState('');
  const [taskId, setTaskId] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [timeRange, setTimeRange] = useState<[string, string] | null>(null);

  const [replayOpen, setReplayOpen] = useState(false);
  const [replayTarget, setReplayTarget] = useState<TraceRunRow | null>(null);
  const [replayTab, setReplayTab] = useState('spans');
  const [selectedSpan, setSelectedSpan] = useState<SpanNode | null>(null);

  // 上下文快照抽屉
  const [contextOpen, setContextOpen] = useState(false);
  const [contextTarget, setContextTarget] = useState<{ taskId: string; callSeq: number } | null>(null);
  const [contextTab, setContextTab] = useState('render');

  // 轨迹分叉弹窗
  const [forkOpen, setForkOpen] = useState(false);
  const [forkTarget, setForkTarget] = useState<{ taskId: string; callSeq: number } | null>(null);
  const [forkForm] = Form.useForm<{ remark: string; userInputOverride?: string }>();

  // 用量下钻抽屉（企业版插件注入，社区版无注入则隐藏入口）
  const UsageDrill = useMemo(
    () => getAppRoutePlugins().map((p) => p.traceUsageDrill).find(Boolean) ?? null,
    [],
  );
  const [usageTaskId, setUsageTaskId] = useState<string | null>(null);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: [QUERY_KEY, page, size, agentCode, taskId, status, timeRange],
    queryFn: () =>
      api.trace.runs({
        pageNum: page,
        pageSize: size,
        agentCode: agentCode || undefined,
        taskId: taskId || undefined,
        status,
        start: timeRange?.[0],
        end: timeRange?.[1],
      }),
  });

  const spansQuery = useQuery({
    queryKey: [`${QUERY_KEY}-spans`, replayTarget?.traceId],
    queryFn: () => api.trace.spans(replayTarget!.traceId),
    enabled: !!replayTarget?.traceId,
  });

  const stepsQuery = useQuery({
    queryKey: [`${QUERY_KEY}-steps`, replayTarget?.taskId],
    queryFn: () => api.trace.steps(replayTarget!.taskId!),
    enabled: !!replayTarget?.taskId,
  });

  // 上下文快照摘要列表（含采集时间/字符数）与明细
  const contextListQuery = useQuery({
    queryKey: [`${QUERY_KEY}-contexts`, contextTarget?.taskId],
    queryFn: () => api.trace.fetchContextList(contextTarget!.taskId),
    enabled: !!contextTarget,
  });

  const contextDetailQuery = useQuery({
    queryKey: [`${QUERY_KEY}-context`, contextTarget?.taskId, contextTarget?.callSeq],
    queryFn: () => api.trace.fetchContextDetail(contextTarget!.taskId, contextTarget!.callSeq),
    enabled: !!contextTarget,
  });

  const contextSummary = useMemo(
    () => contextListQuery.data?.find((item) => item.callSeq === contextTarget?.callSeq),
    [contextListQuery.data, contextTarget],
  );

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const replayMutation = useMutation({
    mutationFn: (taskId: string) => api.trace.replay(taskId),
    onSuccess: (res) => {
      message.success(`重放已提交，新任务ID: ${res.newTaskId}`);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '重放失败');
    },
  });

  const forkMutation = useMutation({
    mutationFn: (params: { taskId: string; payload: TaskForkPayload }) =>
      api.trace.forkTask(params.taskId, params.payload),
    onSuccess: (res) => {
      // antd 5 受控 Modal 需手动关闭
      setForkOpen(false);
      setForkTarget(null);
      forkForm.resetFields();
      message.success(`分叉已提交，新任务ID: ${res.newTaskId}`);
      invalidateList();
      // 跳转新任务详情（复用回放抽屉，默认展示步骤时间线）
      if (res.newTaskId) {
        openReplay({ taskId: res.newTaskId } as TraceRunRow, 'steps');
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '分叉失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (traceId: string) => api.trace.deleteRun(traceId),
    onSuccess: () => {
      message.success('已删除');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const spanTreeItems = useMemo(
    () => buildTreeItems(spansQuery.data),
    [spansQuery.data],
  );

  const openReplay = (record: TraceRunRow, tab = 'spans') => {
    setReplayTarget(record);
    setReplayTab(tab);
    setSelectedSpan(null);
    setReplayOpen(true);
  };

  const openContext = (record: TaskStep) => {
    setContextTarget({ taskId: record.taskId!, callSeq: record.callSeq ?? record.stepOrder! });
    setContextTab('render');
    setContextOpen(true);
  };

  const openFork = (record: TaskStep) => {
    setForkTarget({ taskId: record.taskId!, callSeq: record.callSeq ?? record.stepOrder! });
    forkForm.resetFields();
    setForkOpen(true);
  };

  /**
   * 复制消息原文
   * @param text
   * @return
   */
  const handleCopy = (text: string) => {
    navigator.clipboard
      .writeText(text)
      .then(() => message.success('已复制原文'))
      .catch(() => message.error('复制失败'));
  };

  /**
   * 提交分叉（antd 5 受控 Modal onOk 不自动关闭，成功后手动关闭）
   * @param
   * @return
   */
  const handleForkSubmit = () => {
    forkForm
      .validateFields()
      .then((values) => {
        if (!forkTarget) return;
        forkMutation.mutate({
          taskId: forkTarget.taskId,
          payload: {
            callSeq: forkTarget.callSeq,
            remark: values.remark,
            userInputOverride: values.userInputOverride || undefined,
          },
        });
      })
      .catch(() => {
        // 表单校验失败由表单自身提示
      });
  };

  const handleSearch = () => {
    setPage(1);
  };

  const columns: ColumnsType<TraceRunRow> = [
    { title: 'TraceID', dataIndex: 'traceId', width: 160, ellipsis: true },
    { title: '任务ID', dataIndex: 'taskId', width: 160, ellipsis: true },
    { title: 'Agent', dataIndex: 'agentCode', width: 140, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const meta = RUN_STATUS_META[v ?? ''] ?? { label: v ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '耗时(ms)',
      dataIndex: 'durationMs',
      width: 100,
      render: (v?: number) => v ?? '-',
    },
    { title: 'Span数', dataIndex: 'spanCount', width: 80 },
    {
      title: 'Token(输入/输出)',
      width: 130,
      render: (_: unknown, r: TraceRunRow) =>
        r.inputTokens != null || r.outputTokens != null
          ? `${r.inputTokens ?? 0}/${r.outputTokens ?? 0}`
          : '-',
    },
    {
      title: '来源',
      width: 170,
      render: (_: unknown, r: TraceRunRow) =>
        r.parentTaskId ? (
          <Tooltip title={`跳转来源任务 ${r.parentTaskId}`}>
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              onClick={() => openReplay({ taskId: r.parentTaskId } as TraceRunRow, 'steps')}
            >
              分叉自 {r.parentTaskId.slice(0, 8)}
              {r.forkCallSeq != null ? `·第 ${r.forkCallSeq} 轮` : ''}
            </Button>
          </Tooltip>
        ) : null,
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 230,
      fixed: 'right',
      render: (_: unknown, record: TraceRunRow) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<CaretRightOutlined />}
            disabled={!record.traceId}
            onClick={() => openReplay(record)}
          >
            回放
          </Button>
          {UsageDrill && (
            <Button
              type="link"
              size="small"
              disabled={!record.taskId}
              onClick={() => setUsageTaskId(record.taskId!)}
            >
              用量
            </Button>
          )}
          <Popconfirm
            title="确认重放该任务？"
            description="仅重放输入生成新任务，受配额约束"
            onConfirm={() => replayMutation.mutate(record.taskId!)}
            disabled={!record.taskId}
          >
            <Button type="link" size="small" disabled={!record.taskId}>
              重放
            </Button>
          </Popconfirm>
          <Popconfirm
            title="确认删除该运行Trace？"
            description="将删除该追踪ID的全部Span，不可恢复"
            onConfirm={() => deleteMutation.mutate(record.traceId)}
            okButtonProps={{ danger: true }}
            disabled={!record.traceId}
          >
            <Button type="link" size="small" danger disabled={!record.traceId}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const stepColumns: ColumnsType<TaskStep> = [
    { title: '顺序', dataIndex: 'stepOrder', width: 70 },
    {
      title: '类型',
      dataIndex: 'stepType',
      width: 110,
      render: (v?: string) => {
        const meta = STEP_TYPE_META[v ?? ''] ?? { label: v ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '目标',
      width: 160,
      ellipsis: true,
      render: (_: unknown, r: TaskStep) => r.toolName ?? r.agentName ?? '-',
    },
    {
      title: '内容',
      dataIndex: 'stepContent',
      ellipsis: true,
      render: (v?: string) => v ?? '-',
    },
    {
      title: 'Token',
      dataIndex: 'totalTokens',
      width: 90,
      render: (v?: number) => v ?? '-',
    },
    {
      title: '耗时(ms)',
      dataIndex: 'durationMs',
      width: 100,
      render: (v?: number) => v ?? '-',
    },
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      render: (_: unknown, r: TaskStep) => {
        // 仅LLM调用行提供上下文查看与分叉入口，callSeq优先取模型调用序号，回退步骤顺序
        const callSeq = r.callSeq ?? r.stepOrder;
        if (r.stepType !== 'LLM_CALL' || !r.taskId || callSeq == null) {
          return '-';
        }
        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              onClick={() => openContext(r)}
            >
              上下文
            </Button>
            <Button
              type="link"
              size="small"
              style={{ padding: 0 }}
              onClick={() => openFork(r)}
            >
              从此分叉
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title="运行回放"
      extra={
        <Button
          icon={<ReloadOutlined />}
          loading={isFetching}
          onClick={() => invalidateList()}
        >
          刷新
        </Button>
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="Agent 编码"
          value={agentCode}
          onChange={(e) => setAgentCode(e.target.value)}
          allowClear
          style={{ width: 160 }}
          onPressEnter={handleSearch}
        />
        <Input
          placeholder="任务ID"
          value={taskId}
          onChange={(e) => setTaskId(e.target.value)}
          allowClear
          style={{ width: 200 }}
          onPressEnter={handleSearch}
        />
        <Select
          placeholder="状态"
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          allowClear
          style={{ width: 120 }}
          options={[
            { label: '成功', value: 'OK' },
            { label: '失败', value: 'ERROR' },
          ]}
        />
        <DatePicker.RangePicker
          showTime
          format="YYYY-MM-DD HH:mm:ss"
          onChange={(values) => {
            setTimeRange(
              values
                ? [
                    values[0]!.format('YYYY-MM-DD HH:mm:ss'),
                    values[1]!.format('YYYY-MM-DD HH:mm:ss'),
                  ]
                : null,
            );
            setPage(1);
          }}
        />
        <Button type="primary" onClick={handleSearch}>
          查询
        </Button>
      </Space>

      <Table<TraceRunRow>
        rowKey="traceId"
        columns={columns}
        // 后端 okPage 的 data 为数组（total 在 metaData 被拦截器丢弃），按数组消费
        dataSource={Array.isArray(data) ? data : []}
        loading={isLoading}
        scroll={{ x: 1470 }}
        expandable={{
          expandedRowRender: (record) => (
            <Space direction="vertical" size={4} style={{ maxWidth: 720 }}>
              <Typography.Paragraph ellipsis={{ rows: 3 }} style={{ marginBottom: 0 }}>
                <Typography.Text type="secondary">输入摘要：</Typography.Text>
                {record.userInput ?? '-'}
              </Typography.Paragraph>
              <Space size={16}>
                <Typography.Text type="secondary">
                  任务状态：{record.taskStatus ?? '-'}
                </Typography.Text>
                {record.errorMessage && (
                  <Typography.Text type="danger">
                    错误：{record.errorMessage}
                  </Typography.Text>
                )}
              </Space>
            </Space>
          ),
        }}
        pagination={{
          current: page,
          pageSize: size,
          // 后端 total 位于 metaData 未透传，以当前页条数展示
          total: Array.isArray(data) ? data.length : 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <Drawer
        title={`运行回放 - ${replayTarget?.taskId ?? ''}`}
        width={720}
        open={replayOpen}
        onClose={() => {
          setReplayOpen(false);
          setReplayTarget(null);
          setSelectedSpan(null);
        }}
        destroyOnClose
      >
        <Tabs
          activeKey={replayTab}
          onChange={setReplayTab}
          items={[
            {
              key: 'spans',
              label: 'Span 树',
              children: (
                <Space direction="vertical" style={{ width: '100%' }} size={12}>
                  {spansQuery.isLoading && (
                    <div style={{ textAlign: 'center', padding: 24 }}>
                      <Spin />
                    </div>
                  )}
                  {!spansQuery.isLoading && spanTreeItems.length === 0 && (
                    <Empty description="暂无Span数据" />
                  )}
                  {spanTreeItems.length > 0 && (
                    <>
                      <div
                        style={{
                          maxHeight: 380,
                          overflowY: 'auto',
                          border: '1px solid #f0f0f0',
                          borderRadius: 6,
                          padding: '8px 4px',
                        }}
                      >
                        <Tree
                          treeData={spanTreeItems}
                          defaultExpandAll
                          blockNode
                          onSelect={(keys) => {
                            if (keys.length === 0) {
                              setSelectedSpan(null);
                              return;
                            }
                            setSelectedSpan(
                              findTreeItem(spanTreeItems, keys[0] as string)?.node ?? null,
                            );
                          }}
                        />
                      </div>
                      {selectedSpan && (
                        <Card size="small" title={`Span 属性 - ${selectedSpan.operation ?? ''}`}>
                          <Space direction="vertical" size={8} style={{ width: '100%' }}>
                            <Typography.Text type="secondary">
                              spanId: {selectedSpan.spanId}
                              {selectedSpan.parentSpanId
                                ? ` / parent: ${selectedSpan.parentSpanId}`
                                : ''}
                            </Typography.Text>
                            {selectedSpan.errorMessage && (
                              <Typography.Text type="danger">
                                {selectedSpan.errorMessage}
                              </Typography.Text>
                            )}
                            <pre style={{ margin: 0, maxHeight: 300, overflow: 'auto' }}>
                              {prettyJson(selectedSpan.attributes)}
                            </pre>
                          </Space>
                        </Card>
                      )}
                    </>
                  )}
                </Space>
              ),
            },
            {
              key: 'steps',
              label: '步骤时间线',
              children: (
                <Table<TaskStep>
                  rowKey="id"
                  columns={stepColumns}
                  dataSource={stepsQuery.data ?? []}
                  loading={stepsQuery.isLoading}
                  pagination={false}
                  scroll={{ x: 950 }}
                  expandable={{
                    expandedRowRender: (record) => (
                      <Space direction="vertical" size={8} style={{ width: '100%' }}>
                        {record.toolInput && (
                          <div>
                            <Typography.Text type="secondary">工具输入：</Typography.Text>
                            <pre style={{ margin: 0, maxHeight: 160, overflow: 'auto' }}>
                              {prettyJson(record.toolInput)}
                            </pre>
                          </div>
                        )}
                        {record.toolOutput && (
                          <div>
                            <Typography.Text type="secondary">工具输出：</Typography.Text>
                            <pre style={{ margin: 0, maxHeight: 160, overflow: 'auto' }}>
                              {prettyJson(record.toolOutput)}
                            </pre>
                          </div>
                        )}
                      </Space>
                    ),
                    rowExpandable: (record) => !!(record.toolInput || record.toolOutput),
                  }}
                />
              ),
            },
          ]}
        />
      </Drawer>

      <Drawer
        title={contextTarget ? `上下文快照 - 第 ${contextTarget.callSeq} 轮` : '上下文快照'}
        width={640}
        open={contextOpen}
        onClose={() => {
          setContextOpen(false);
          setContextTarget(null);
        }}
        destroyOnClose
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label="调用序号">
              第 {contextTarget?.callSeq ?? '-'} 轮
            </Descriptions.Item>
            <Descriptions.Item label="模型">
              {contextDetailQuery.data?.modelCode ?? contextSummary?.modelCode ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="消息数">
              {contextDetailQuery.data?.messages?.length ?? contextSummary?.msgCount ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="字符数">
              {contextSummary?.totalChars ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="采集时间">
              {contextSummary?.createdAt ? formatDate(contextSummary.createdAt) : '-'}
            </Descriptions.Item>
          </Descriptions>
          <Tabs
            activeKey={contextTab}
            onChange={setContextTab}
            items={[
              {
                key: 'render',
                label: '渲染视图',
                children: (
                  <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    {contextDetailQuery.isLoading && (
                      <div style={{ textAlign: 'center', padding: 24 }}>
                        <Spin />
                      </div>
                    )}
                    {!contextDetailQuery.isLoading &&
                      (contextDetailQuery.data?.messages?.length ?? 0) === 0 && (
                        <Empty description="暂无上下文快照" />
                      )}
                    {contextDetailQuery.data?.messages?.map((item, idx) => (
                      <ContextMessageItem
                        key={`${idx}-${item.role ?? ''}`}
                        index={idx}
                        item={item}
                        onCopy={handleCopy}
                      />
                    ))}
                  </Space>
                ),
              },
              {
                key: 'json',
                label: 'JSON 原文',
                children: (
                  <pre style={{ margin: 0, maxHeight: 480, overflow: 'auto', fontSize: 12 }}>
                    {contextDetailQuery.data
                      ? JSON.stringify(contextDetailQuery.data.messages ?? [], null, 2)
                      : ''}
                  </pre>
                ),
              },
            ]}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            取证数据，请勿外传
          </Typography.Text>
        </Space>
      </Drawer>

      <Modal
        title="从此分叉"
        open={forkOpen}
        confirmLoading={forkMutation.isPending}
        onOk={handleForkSubmit}
        onCancel={() => {
          setForkOpen(false);
          setForkTarget(null);
        }}
        forceRender
        // forceRender 使 Modal 节点先于回放 Drawer 挂载，同为默认 zIndex 1000 时
        // Drawer 遮罩会盖住 Modal 导致无法点击，此处需显式调高
        zIndex={1050}
        okText="确认分叉"
        cancelText="取消"
      >
        {forkTarget && (
          <Descriptions size="small" column={1} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="来源任务">{forkTarget.taskId}</Descriptions.Item>
            <Descriptions.Item label="分叉点">
              第 {forkTarget.callSeq} 轮模型调用前
            </Descriptions.Item>
          </Descriptions>
        )}
        <Form form={forkForm} layout="vertical">
          <Form.Item
            name="remark"
            label="分叉备注"
            rules={[{ required: true, message: '请输入分叉备注' }]}
          >
            <Input.TextArea rows={2} maxLength={200} showCount placeholder="分叉说明（审计用，必填）" />
          </Form.Item>
          <Form.Item
            name="userInputOverride"
            label="追加用户输入（可选）"
            extra="追加到快照消息末尾作为下一步用户输入，留空则按原上下文继续执行"
          >
            <Input.TextArea rows={3} maxLength={2000} placeholder="请输入追加的用户输入" />
          </Form.Item>
        </Form>
      </Modal>

      {UsageDrill && usageTaskId && (
        <UsageDrill taskId={usageTaskId} onClose={() => setUsageTaskId(null)} />
      )}
    </Card>
  );
};
