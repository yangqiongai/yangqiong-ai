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
import React, { useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
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
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { EnterpriseLockIcon, formatDate } from '@yangqiong/shared';
import { mcpEcosystemApi } from '@/services/mcp-ecosystem-api';
import type { AgentTriggerInfo } from '@/services/mcp-ecosystem-api';

const { Title, Text } = Typography;
const { TextArea } = Input;

// WEBHOOK/FILE 为企业增强类型，社区版仅在选项中置灰展示
const TRIGGER_TYPE_OPTIONS = [
  { label: '定时触发（CRON）', value: 'CRON', color: 'blue' },
  { label: '事件触发（EVENT）', value: 'EVENT', color: 'purple' },
  { label: <span>Webhook 回调 <EnterpriseLockIcon style={{ color: '#d48806' }} /></span>, value: 'WEBHOOK', color: 'default', disabled: true },
  { label: <span>文件监听 <EnterpriseLockIcon style={{ color: '#d48806' }} /></span>, value: 'FILE', color: 'default', disabled: true },
];

const TRIGGER_TYPE_COLOR: Record<string, string> = TRIGGER_TYPE_OPTIONS.reduce(
  (acc, item) => {
    acc[item.value] = item.color;
    return acc;
  },
  {} as Record<string, string>
);

const EVENT_SOURCE_OPTIONS = [
  { label: '配置漂移（CONFIG_DRIFT）', value: 'CONFIG_DRIFT' },
  { label: '评测回退（EVAL_REGRESSION）', value: 'EVAL_REGRESSION' },
  { label: '动作异常（ACTION_ANOMALY）', value: 'ACTION_ANOMALY' },
  { label: '预算超限（BUDGET_EXCEEDED）', value: 'BUDGET_EXCEEDED' },
  { label: '输出契约违规（OUTPUT_CONTRACT_VIOLATION）', value: 'OUTPUT_CONTRACT_VIOLATION' },
];

const ENABLED_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
];

interface TriggerQuery {
  triggerType?: string;
  agentCode?: string;
  enabled?: number;
  page: number;
  size: number;
}

interface TriggerFormValues {
  name: string;
  triggerCode?: string;
  triggerType: string;
  agentCode?: string;
  cronExpr?: string;
  eventSource?: string;
  payloadTemplate?: string;
  notifyWebhook?: string;
  dailyQuota?: number;
  dedupWindowSeconds?: number;
  enabled: boolean;
}

/**
 * 生成配置摘要（CRON 展示表达式，EVENT 展示事件源，其余展示占位）
 * @param record
 * @return
 */
const configSummary = (record: AgentTriggerInfo): string => {
  if (record.triggerType === 'CRON') {
    return record.cronExpr ?? '-';
  }
  if (record.triggerType === 'EVENT') {
    return record.eventSource ?? '-';
  }
  return record.watchDir ?? record.notifyWebhook ?? '-';
};

/**
 * Agent 触发器管理（社区裁剪版：仅 CRON/EVENT 可新建编辑）
 */
export const AgentTriggerBasePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<TriggerQuery>({ page: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [current, setCurrent] = useState<AgentTriggerInfo | null>(null);
  const [form] = Form.useForm<TriggerFormValues>();

  const { data: pageData, isLoading, isFetching } = useQuery({
    queryKey: ['agent-trigger-list', query],
    queryFn: () => mcpEcosystemApi.trigger.list(query),
  });

  useEffect(() => {
    if (formOpen) {
      if (current) {
        // 先重置再回填，避免上一条记录的字段值残留
        form.resetFields();
        form.setFieldsValue({
          name: current.name,
          triggerCode: current.triggerCode,
          triggerType: current.triggerType,
          agentCode: current.agentCode,
          cronExpr: current.cronExpr,
          eventSource: current.eventSource,
          payloadTemplate: current.payloadTemplate,
          notifyWebhook: current.notifyWebhook,
          dailyQuota: current.dailyQuota,
          dedupWindowSeconds: current.dedupWindowSeconds,
          enabled: current.enabled === 1,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({ triggerType: 'CRON', enabled: true });
      }
    }
  }, [formOpen, current, form]);

  const createMutation = useMutation({
    mutationFn: (values: TriggerFormValues) =>
      mcpEcosystemApi.trigger.create({
        name: values.name,
        triggerCode: values.triggerCode,
        triggerType: values.triggerType,
        agentCode: values.agentCode,
        cronExpr: values.cronExpr,
        eventSource: values.eventSource,
        payloadTemplate: values.payloadTemplate,
        notifyWebhook: values.notifyWebhook,
        dailyQuota: values.dailyQuota,
        dedupWindowSeconds: values.dedupWindowSeconds,
        enabled: values.enabled ? 1 : 0,
      }),
    onSuccess: () => {
      message.success('触发器创建成功');
      void queryClient.invalidateQueries({ queryKey: ['agent-trigger-list'] });
      setFormOpen(false);
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '触发器创建失败'),
  });

  const updateMutation = useMutation({
    mutationFn: (values: TriggerFormValues) =>
      mcpEcosystemApi.trigger.update({
        id: current?.id,
        name: values.name,
        triggerCode: values.triggerCode,
        triggerType: values.triggerType,
        agentCode: values.agentCode,
        cronExpr: values.cronExpr,
        eventSource: values.eventSource,
        payloadTemplate: values.payloadTemplate,
        notifyWebhook: values.notifyWebhook,
        dailyQuota: values.dailyQuota,
        dedupWindowSeconds: values.dedupWindowSeconds,
        enabled: values.enabled ? 1 : 0,
      }),
    onSuccess: () => {
      message.success('触发器更新成功');
      void queryClient.invalidateQueries({ queryKey: ['agent-trigger-list'] });
      setFormOpen(false);
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '触发器更新失败'),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number | undefined; enabled: boolean }) =>
      mcpEcosystemApi.trigger.toggle(id!, enabled),
    onSuccess: () => {
      message.success('状态切换成功');
      void queryClient.invalidateQueries({ queryKey: ['agent-trigger-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '状态切换失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number | undefined) => mcpEcosystemApi.trigger.remove(id!),
    onSuccess: () => {
      message.success('触发器删除成功');
      void queryClient.invalidateQueries({ queryKey: ['agent-trigger-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '触发器删除失败'),
  });

  const handleFormOk = async () => {
    try {
      const values = await form.validateFields();
      if (current) {
        updateMutation.mutate(values);
      } else {
        createMutation.mutate(values);
      }
    } catch {
      // 校验失败由表单提示
    }
  };

  const submitting = createMutation.isPending || updateMutation.isPending;

  const columns: ColumnsType<AgentTriggerInfo> = [
    {
      title: '类型',
      dataIndex: 'triggerType',
      width: 110,
      render: (v?: string) => (v ? <Tag color={TRIGGER_TYPE_COLOR[v]}>{v}</Tag> : '-'),
    },
    { title: '编码', dataIndex: 'triggerCode', width: 180, ellipsis: true },
    { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
    { title: 'Agent 编码', dataIndex: 'agentCode', width: 150, ellipsis: true },
    {
      title: '配置摘要',
      key: 'config',
      ellipsis: true,
      render: (_, record) => <Text code>{configSummary(record)}</Text>,
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (v: number | undefined, record) => (
        <Switch
          size="small"
          checked={v === 1}
          loading={toggleMutation.isPending}
          onChange={(checked) => toggleMutation.mutate({ id: record.id, enabled: checked })}
        />
      ),
    },
    {
      title: '最近触发',
      dataIndex: 'lastFireTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        // 编辑仅对社区版支持的 CRON/EVENT 类型开放，按钮显隐由后端状态驱动
        <Space size="small">
          {record.triggerType === 'CRON' || record.triggerType === 'EVENT' ? (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setCurrent(record);
                setFormOpen(true);
              }}
            >
              编辑
            </Button>
          ) : null}
          <Popconfirm title="确认删除该触发器？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        触发规则
      </Title>
      <Card styles={{ body: { paddingTop: 0 } }}>
        <Space wrap style={{ marginBottom: 16, paddingTop: 16 }}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setCurrent(null);
              setFormOpen(true);
            }}
          >
            新建触发器
          </Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={() =>
              void queryClient.invalidateQueries({ queryKey: ['agent-trigger-list'] })
            }
          >
            刷新
          </Button>
          <Select
            allowClear
            placeholder="类型筛选"
            style={{ width: 180 }}
            options={TRIGGER_TYPE_OPTIONS}
            onChange={(v?: string) => setQuery((q) => ({ ...q, triggerType: v, page: 1 }))}
          />
          <Input.Search
            allowClear
            placeholder="Agent 编码筛选"
            style={{ width: 200 }}
            onSearch={(v) => setQuery((q) => ({ ...q, agentCode: v || undefined, page: 1 }))}
          />
          <Select
            allowClear
            placeholder="启用状态筛选"
            style={{ width: 140 }}
            options={ENABLED_OPTIONS}
            onChange={(v?: number) => setQuery((q) => ({ ...q, enabled: v, page: 1 }))}
          />
        </Space>
        <Table<AgentTriggerInfo>
          rowKey={(record) => String(record.id ?? record.triggerCode ?? '')}
          columns={columns}
          dataSource={pageData?.records}
          loading={isLoading || isFetching}
          scroll={{ x: 1200 }}
          pagination={{
            current: pageData?.page ?? query.page,
            pageSize: pageData?.size ?? query.size,
            total: pageData?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setQuery((q) => ({ ...q, page, size })),
          }}
        />
      </Card>

      <Modal
        title={current ? '编辑触发器' : '新建触发器'}
        open={formOpen}
        onOk={handleFormOk}
        onCancel={() => setFormOpen(false)}
        confirmLoading={submitting}
        // forceRender 保证打开瞬间 Form 已挂载，setFieldsValue 默认值不会被丢弃
        forceRender
        maskClosable={false}
        width={640}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label="触发器名称"
            rules={[{ required: true, message: '请输入触发器名称' }]}
          >
            <Input placeholder="请输入触发器名称" />
          </Form.Item>
          <Form.Item
            name="triggerCode"
            label="触发器编码"
            rules={[{ required: true, message: '请输入触发器编码' }]}
            extra={current ? undefined : '唯一编码，建议以 trg- 前缀命名'}
          >
            <Input placeholder="请输入触发器编码" disabled={!!current} />
          </Form.Item>
          <Form.Item
            name="triggerType"
            label="触发类型"
            rules={[{ required: true, message: '请选择触发类型' }]}
          >
            <Select options={TRIGGER_TYPE_OPTIONS} placeholder="请选择触发类型" />
          </Form.Item>
          <Form.Item name="agentCode" label="目标 Agent 编码">
            <Input placeholder="请输入目标 Agent 编码" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.triggerType !== next.triggerType}>
            {({ getFieldValue }) =>
              getFieldValue('triggerType') === 'CRON' ? (
                <Form.Item
                  name="cronExpr"
                  label="CRON 表达式"
                  rules={[{ required: true, message: '请输入 CRON 表达式' }]}
                >
                  <Input placeholder="例如 0 0/30 * * * ?" />
                </Form.Item>
              ) : getFieldValue('triggerType') === 'EVENT' ? (
                <Form.Item
                  name="eventSource"
                  label="内置事件源"
                  rules={[{ required: true, message: '请选择内置事件源' }]}
                >
                  <Select options={EVENT_SOURCE_OPTIONS} placeholder="请选择内置事件源" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="payloadTemplate"
            label="输入指令模板"
            extra="支持 {payload} 占位符替换为触发载荷"
          >
            <TextArea placeholder="请输入输入指令模板" autoSize={{ minRows: 2, maxRows: 4 }} />
          </Form.Item>
          <Form.Item name="notifyWebhook" label="结果回投 Webhook 地址">
            <Input placeholder="留空则不回投" />
          </Form.Item>
          <Form.Item name="dailyQuota" label="每日触发配额">
            <InputNumber style={{ width: '100%' }} min={0} placeholder="空或 0 为不限" />
          </Form.Item>
          <Form.Item name="dedupWindowSeconds" label="同源去重窗口（秒）">
            <InputNumber style={{ width: '100%' }} min={0} placeholder="0 为不去重" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AgentTriggerBasePage;
