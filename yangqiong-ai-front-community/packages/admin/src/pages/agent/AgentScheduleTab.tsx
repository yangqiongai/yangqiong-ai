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
import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { ScheduleLog, SchedulerJob } from '@yangqiong/shared';

const SCHEDULE_KEY = 'agent-schedules';

const LOG_PAGE_SIZE = 10;

const LOG_PRE_STYLE: React.CSSProperties = {
  margin: '4px 0 0',
  padding: 8,
  background: '#fafafa',
  borderRadius: 6,
  maxHeight: 200,
  overflow: 'auto',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
  fontSize: 12,
};

interface ScheduleFormValues {
  taskName: string;
  cronExpression: string;
  inputText?: string;
  description?: string;
  bodyJson?: string;
}

/**
 * Agent 调度任务
 */
export const AgentScheduleTab: React.FC<{ agentCode: string; userId?: string }> = ({
  agentCode,
  userId,
}) => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  // 后端 /api/schedule 按用户隔离任务，归属用户固定为当前登录人，界面无需选择
  const [nameFilter, setNameFilter] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SchedulerJob | null>(null);
  const [logSchedule, setLogSchedule] = useState<SchedulerJob | null>(null);
  const [logPage, setLogPage] = useState(1);
  const [form] = Form.useForm<ScheduleFormValues>();

  const listQuery = useQuery({
    queryKey: [SCHEDULE_KEY, userId],
    queryFn: () => api.agent.scheduler.list(userId!),
    enabled: !!userId,
  });

  const logsQuery = useQuery({
    queryKey: ['schedule-logs', logSchedule?.scheduleId, logPage],
    queryFn: () => api.agent.scheduler.logs(logSchedule!.scheduleId, logPage, LOG_PAGE_SIZE),
    enabled: !!logSchedule,
  });

  // 过滤出当前 Agent 的任务，并按任务名称过滤（后端仅支持按用户查询）
  const keyword = nameFilter.trim();
  const rows = (listQuery.data ?? []).filter(
    (job) =>
      job.agentCode === agentCode &&
      (!keyword || (job.taskName ?? '').includes(keyword) || job.scheduleId.includes(keyword)),
  );

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: [SCHEDULE_KEY] });
  };

  const saveMutation = useMutation({
    mutationFn: async (values: ScheduleFormValues) => {
      let body: Record<string, unknown> | undefined;
      if (values.bodyJson?.trim()) {
        body = JSON.parse(values.bodyJson);
      }
      if (editing) {
        await api.agent.scheduler.update(editing.scheduleId, {
          taskName: values.taskName,
          cronExpression: values.cronExpression,
          inputText: values.inputText,
          description: values.description,
          body,
        });
      } else {
        await api.agent.scheduler.create({
          userId,
          taskName: values.taskName,
          agentCode,
          cronExpression: values.cronExpression,
          inputText: values.inputText,
          description: values.description,
          body,
        });
      }
    },
    onSuccess: () => {
      message.success(editing ? '调度任务已更新' : '调度任务已创建');
      setModalOpen(false);
      invalidate();
    },
    onError: (err) =>
      message.error(err instanceof Error && err.message ? err.message : '保存失败，请检查 Cron 表达式与 JSON 格式'),
  });

  const deleteMutation = useMutation({
    mutationFn: (scheduleId: string) => api.agent.scheduler.delete(scheduleId),
    onSuccess: () => {
      message.success('调度任务已删除');
      invalidate();
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (job: SchedulerJob) => api.agent.scheduler.toggle(job.scheduleId, job.enabled !== false),
    onSuccess: (_, job) => {
      message.success(job.enabled === false ? '调度任务已恢复' : '调度任务已暂停');
      invalidate();
    },
    onError: () => message.error('操作失败，请重试'),
  });

  const openCreate = () => {
    setEditing(null);
    // 先重置再赋默认值，避免上一条记录的编辑值残留
    form.resetFields();
    form.setFieldsValue({ taskName: '', cronExpression: '0 0/30 * * * ?', inputText: '', description: '', bodyJson: '' });
    setModalOpen(true);
  };

  const openEdit = (job: SchedulerJob) => {
    setEditing(job);
    // 存量 body 可能为非 JSON 内容，解析失败时原样回显避免崩溃
    let bodyText = job.body ?? '';
    if (bodyText.trim()) {
      try {
        bodyText = JSON.stringify(JSON.parse(bodyText), null, 2);
      } catch {
        /* 原样展示 */
      }
    }
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      taskName: job.taskName,
      cronExpression: job.cronExpression,
      inputText: job.inputText,
      description: job.description,
      bodyJson: bodyText,
    });
    setModalOpen(true);
  };

  const openLogs = (job: SchedulerJob) => {
    setLogSchedule(job);
    setLogPage(1);
  };

  const columns: ColumnsType<SchedulerJob> = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 180,
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <Tooltip title={v} placement="topLeft">
            <span style={{ cursor: 'default' }}>{v}</span>
          </Tooltip>
        ) : (
          <Typography.Text type="secondary">未命名</Typography.Text>
        ),
    },
    {
      title: 'Cron',
      dataIndex: 'cronExpression',
      width: 150,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    { title: '执行输入', dataIndex: 'inputText', ellipsis: true },
    {
      title: '任务描述',
      dataIndex: 'description',
      width: 180,
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <Tooltip title={v} placement="topLeft">
            <span style={{ cursor: 'default' }}>{v}</span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v: boolean | undefined) =>
        v === false ? <Tag color="default">已暂停</Tag> : <Tag color="green">运行中</Tag>,
    },
    {
      title: '下次触发',
      dataIndex: 'nextFireTime',
      width: 160,
      render: (v?: string) => formatDate(v),
    },
    {
      title: '上次触发',
      dataIndex: 'lastFireTime',
      width: 160,
      render: (v?: string) => formatDate(v),
    },
    {
      title: '操作',
      key: 'actions',
      width: 205,
      render: (_, job) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => openEdit(job)}>
            编辑
          </Button>
          <Button size="small" type="link" loading={toggleMutation.isPending} onClick={() => toggleMutation.mutate(job)}>
            {job.enabled === false ? '恢复' : '暂停'}
          </Button>
          <Button size="small" type="link" onClick={() => openLogs(job)}>
            历史
          </Button>
          <Popconfirm title="确认删除该调度任务？" onConfirm={() => deleteMutation.mutate(job.scheduleId)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 12 }}
        message="定时任务归属当前登录用户，到期将以该用户身份触发 Agent 执行；连续 3 次执行失败将自动暂停。"
      />
      <Space style={{ marginBottom: 12 }} wrap>
        <Input
          allowClear
          style={{ width: 220 }}
          placeholder="按任务名称过滤"
          value={nameFilter}
          onChange={(e) => setNameFilter(e.target.value)}
        />
        <Button icon={<ReloadOutlined />} onClick={() => invalidate()}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建调度任务
        </Button>
      </Space>
      <Table
        rowKey="scheduleId"
        size="small"
        loading={listQuery.isLoading}
        columns={columns}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: <Empty description="当前用户在此 Agent 下暂无调度任务" /> }}
      />
      <Modal
        title={editing ? '编辑调度任务' : '新建调度任务'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saveMutation.isPending}
        onOk={() => form.submit()}
      >
        <Form<ScheduleFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            name="taskName"
            label="任务名称"
            rules={[{ required: true, message: '请输入任务名称' }]}
          >
            <Input placeholder="如：每日预算数据汇总" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="cronExpression"
            label="Cron 表达式"
            rules={[{ required: true, message: '请输入 Cron 表达式' }]}
            extra=" Quartz 格式，如 0 0/30 * * * ? 表示每 30 分钟"
          >
            <Input placeholder="0 0/30 * * * ?" />
          </Form.Item>
          <Form.Item name="inputText" label="执行输入">
            <Input.TextArea rows={3} placeholder="触发时传给 Agent 的输入文本" />
          </Form.Item>
          <Form.Item name="description" label="任务描述">
            <Input.TextArea rows={2} placeholder="任务的用途说明（选填）" maxLength={512} />
          </Form.Item>
          <Form.Item
            name="bodyJson"
            label="请求体参数（JSON）"
            rules={[
              {
                validator: (_, value: string) => {
                  if (!value?.trim()) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('JSON 格式不正确'));
                  }
                },
              },
            ]}
          >
            <Input.TextArea rows={4} style={{ fontFamily: 'monospace' }} placeholder='{"key": "value"}' />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title={`执行历史 · ${logSchedule?.taskName || logSchedule?.scheduleId || ''}`}
        open={!!logSchedule}
        onCancel={() => setLogSchedule(null)}
        footer={null}
        width={960}
        maskClosable
        keyboard
        destroyOnClose
      >
        <Table<ScheduleLog>
          rowKey="id"
          size="small"
          loading={logsQuery.isFetching}
          columns={[
            {
              title: '触发时间',
              dataIndex: 'fireTime',
              width: 160,
              render: (v?: string) => formatDate(v),
            },
            {
              title: '状态',
              dataIndex: 'success',
              width: 80,
              render: (v?: boolean) =>
                v ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
            },
            {
              title: '耗时',
              dataIndex: 'durationMs',
              width: 90,
              render: (v?: number) =>
                v == null ? '-' : v < 1000 ? `${v}ms` : `${(v / 1000).toFixed(1)}s`,
            },
            {
              title: 'Token 用量',
              dataIndex: 'totalTokens',
              width: 150,
              render: (_, log) =>
                (log.totalTokens ?? 0) > 0 ? (
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {log.inputTokens ?? 0} / {log.outputTokens ?? 0} / {log.totalTokens ?? 0}
                  </Typography.Text>
                ) : (
                  '-'
                ),
            },
            {
              title: '执行输入',
              dataIndex: 'inputText',
              ellipsis: true,
            },
          ]}
          dataSource={logsQuery.data?.list ?? []}
          expandable={{
            expandedRowRender: (log) => (
              <div style={{ display: 'grid', gap: 8 }}>
                <div>
                  <Typography.Text type="secondary">执行输入</Typography.Text>
                  <pre style={LOG_PRE_STYLE}>{log.inputText || '（无）'}</pre>
                </div>
                <div>
                  <Typography.Text type="secondary">执行输出</Typography.Text>
                  <pre style={LOG_PRE_STYLE}>{log.outputText || '（无）'}</pre>
                </div>
                {log.errorMessage ? (
                  <div>
                    <Typography.Text type="danger">失败原因</Typography.Text>
                    <pre style={LOG_PRE_STYLE}>{log.errorMessage}</pre>
                  </div>
                ) : null}
              </div>
            ),
          }}
          pagination={{
            current: logPage,
            pageSize: LOG_PAGE_SIZE,
            total: logsQuery.data?.total ?? 0,
            onChange: (p) => setLogPage(p),
            showSizeChanger: false,
            size: 'small',
            hideOnSinglePage: true,
            showTotal: (t) => `共 ${t} 条`,
          }}
          locale={{ emptyText: <Empty description="暂无执行历史，等待调度触发后自动记录" /> }}
        />
      </Modal>
    </div>
  );
};
