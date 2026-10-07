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
import React, { useState } from 'react';
import {
  App,
  Alert,
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate, WEBHOOK_EVENT_OPTIONS } from '@yangqiong/shared';
import type { IntegrationRecord, WebhookEventConfig } from '@yangqiong/shared';

const CONFIG_KEY = 'webhook-configs';
const RECORD_KEY = 'webhook-records';
const PAGE_SIZE = 20;

/**
 * 投递状态展示
 */
const STATUS_META: Record<string, { label: string; color: string }> = {
  SENDING: { label: '投递中', color: 'blue' },
  SUCCESS: { label: '成功', color: 'green' },
  FAILED: { label: '失败', color: 'red' },
  SKIPPED: { label: '已丢弃', color: 'volcano' },
};

/**
 * 事件类型值到中文名的映射
 */
const EVENT_LABEL: Record<string, string> = Object.fromEntries(
  WEBHOOK_EVENT_OPTIONS.map((option) => [option.value, option.label]),
);

interface ConfigFormValues {
  url: string;
  secret?: string;
  eventTypeList?: string[];
  agentFilter?: string;
  retryCount?: number;
  enabled: boolean;
}

/**
 * 事件订阅
 */
export const WebhookPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [configPage, setConfigPage] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<WebhookEventConfig | null>(null);
  const [form] = Form.useForm<ConfigFormValues>();

  const [recordOpen, setRecordOpen] = useState(false);
  const [recordTarget, setRecordTarget] = useState<WebhookEventConfig | null>(null);
  const [recordPage, setRecordPage] = useState(1);

  const configQuery = useQuery({
    queryKey: [CONFIG_KEY, configPage],
    queryFn: () => api.webhook.configs.page({ pageNum: configPage, pageSize: PAGE_SIZE }),
  });

  const recordQuery = useQuery({
    queryKey: [RECORD_KEY, recordTarget?.id, recordPage],
    queryFn: () =>
      api.webhook.configs.records(recordTarget!.id!, {
        pageNum: recordPage,
        pageSize: PAGE_SIZE,
      }),
    enabled: recordOpen && !!recordTarget?.id,
  });

  const saveMutation = useMutation({
    mutationFn: (values: ConfigFormValues) =>
      api.webhook.configs.save({
        ...values,
        id: editing?.id,
        eventTypes: (values.eventTypeList ?? []).join(','),
        enabled: values.enabled ? 1 : 0,
      }),
    onSuccess: () => {
      message.success('订阅已保存');
      closeModal();
      invalidateConfigs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.webhook.configs.remove(id),
    onSuccess: () => {
      message.success('删除成功');
      invalidateConfigs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (record: WebhookEventConfig) =>
      api.webhook.configs.changeEnabled(record.id!, record.enabled === 1 ? 0 : 1),
    onSuccess: () => {
      message.success('状态已更新');
      invalidateConfigs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const testMutation = useMutation({
    mutationFn: (config: WebhookEventConfig) => api.webhook.configs.test(config.id!),
    onSuccess: (ok, config) => {
      if (ok) {
        message.success('测试事件投递成功');
      } else {
        message.error('测试事件投递失败，请检查投递记录');
      }
      if (recordOpen && recordTarget?.id === config.id) {
        recordQuery.refetch();
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '测试投递失败');
    },
  });

  const resendMutation = useMutation({
    mutationFn: (recordId: number) => api.webhook.configs.resend(recordId),
    onSuccess: (ok) => {
      if (ok) {
        message.success('重推成功');
      } else {
        message.error('重推失败，请查看最新记录');
      }
      recordQuery.refetch();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '重推失败');
    },
  });

  const invalidateConfigs = () => {
    queryClient.invalidateQueries({ queryKey: [CONFIG_KEY] });
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: WebhookEventConfig) => {
    setEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      url: record.url,
      secret: undefined,
      eventTypeList: record.eventTypes ? record.eventTypes.split(',') : [],
      agentFilter: record.agentFilter,
      retryCount: record.retryCount,
      enabled: record.enabled === 1,
    });
    setModalOpen(true);
  };

  const openRecords = (record: WebhookEventConfig) => {
    setRecordTarget(record);
    setRecordPage(1);
    setRecordOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    saveMutation.mutate(values);
  };

  const pagination = (current: number, total: number): TablePaginationConfig => ({
    current,
    pageSize: PAGE_SIZE,
    total,
    showSizeChanger: false,
    showTotal: (t) => `共 ${t} 条`,
  });

  const configColumns: ColumnsType<WebhookEventConfig> = [
    {
      title: '回调地址',
      dataIndex: 'url',
      width: 280,
      ellipsis: true,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: '密钥',
      dataIndex: 'secret',
      width: 120,
      render: (v?: string) => v ?? '-',
    },
    {
      title: '订阅事件',
      dataIndex: 'eventTypes',
      width: 260,
      render: (v?: string) => {
        const types = v ? v.split(',') : [];
        if (!types.length) {
          return <Tag color="blue">全部 run 级</Tag>;
        }
        return (
          <Space size={[4, 4]} wrap>
            {types.map((type) => (
              <Tag key={type} color="blue">
                {EVENT_LABEL[type] ?? type}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: 'Agent 过滤',
      dataIndex: 'agentFilter',
      width: 160,
      ellipsis: true,
      render: (v?: string) => v ?? <Tag>全部</Tag>,
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      width: 90,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (v?: number) =>
        v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right',
      render: (_: unknown, record: WebhookEventConfig) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            loading={testMutation.isPending && testMutation.variables?.id === record.id}
            onClick={() => testMutation.mutate(record)}
          >
            测试投递
          </Button>
          <Button type="link" size="small" onClick={() => openRecords(record)}>
            投递记录
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => toggleMutation.mutate(record)}
          >
            {record.enabled === 1 ? '停用' : '启用'}
          </Button>
          <Popconfirm title="确认删除该订阅？" onConfirm={() => deleteMutation.mutate(record.id!)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const recordColumns: ColumnsType<IntegrationRecord> = [
    {
      title: '时间',
      dataIndex: 'sendTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const meta = STATUS_META[v ?? ''] ?? { label: v, color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '响应/原因',
      dataIndex: 'response',
      ellipsis: true,
      render: (v?: string) => (v ? <Typography.Text code>{v}</Typography.Text> : '-'),
    },
    {
      title: '操作',
      width: 90,
      render: (_: unknown, record: IntegrationRecord) => (
        <Button
          type="link"
          size="small"
          disabled={record.status === 'SUCCESS'}
          loading={resendMutation.isPending && resendMutation.variables === record.id}
          onClick={() => resendMutation.mutate(record.id!)}
        >
          重推
        </Button>
      ),
    },
  ];

  return (
    <Card title="事件订阅">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Agent 运行事件将按订阅异步投递到回调地址（POST JSON，头部 X-Ai-Signature 携带 HMAC-SHA256 签名）；重试退避 5s/30s/120s，投递语义 at-least-once"
      />
      <Button
        type="primary"
        icon={<PlusOutlined />}
        style={{ marginBottom: 16 }}
        onClick={openCreate}
      >
        新增订阅
      </Button>
      <Table<WebhookEventConfig>
        rowKey="id"
        columns={configColumns}
        dataSource={configQuery.data?.records ?? []}
        loading={configQuery.isLoading}
        scroll={{ x: 1100 }}
        pagination={pagination(configPage, configQuery.data?.total ?? 0)}
        onChange={(p) => setConfigPage(p.current ?? 1)}
      />

      <Modal
        title={editing ? '编辑订阅' : '新增订阅'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={handleSubmit}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ enabled: false, retryCount: 3 }}>
          <Form.Item
            name="url"
            label="回调地址"
            rules={[{ required: true, message: '请输入回调地址' }]}
          >
            <Input placeholder="https://example.com/hook" />
          </Form.Item>
          <Form.Item
            name="secret"
            label="签名密钥"
            extra={editing ? '留空保留原密钥' : '用于 HMAC-SHA256 请求签名，可留空'}
          >
            <Input.Password placeholder={editing ? '留空保留原密钥' : '请输入密钥'} />
          </Form.Item>
          <Form.Item
            name="eventTypeList"
            label="订阅事件类型"
            extra="留空表示订阅全部 run 级事件"
          >
            <Select
              mode="multiple"
              allowClear
              placeholder="请选择订阅的事件类型"
              options={WEBHOOK_EVENT_OPTIONS.map((option) => ({
                value: option.value,
                label: option.label,
              }))}
            />
          </Form.Item>
          <Form.Item name="agentFilter" label="Agent 过滤" extra="逗号分隔 agentCode，留空表示全部">
            <Input placeholder="例如 agent-a,agent-b" />
          </Form.Item>
          <Form.Item name="retryCount" label="重试次数" extra="失败后按 5s/30s/120s 退避重试">
            <InputNumber min={0} max={5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="enabled" label="是否启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={recordTarget ? `投递记录 - ${recordTarget.url}` : '投递记录'}
        width={860}
        open={recordOpen}
        onClose={() => setRecordOpen(false)}
        destroyOnClose
      >
        <Table<IntegrationRecord>
          rowKey="id"
          columns={recordColumns}
          dataSource={recordQuery.data?.records ?? []}
          loading={recordQuery.isLoading}
          size="small"
          scroll={{ x: 800 }}
          pagination={pagination(recordPage, recordQuery.data?.total ?? 0)}
          onChange={(p) => setRecordPage(p.current ?? 1)}
        />
      </Drawer>
    </Card>
  );
};
