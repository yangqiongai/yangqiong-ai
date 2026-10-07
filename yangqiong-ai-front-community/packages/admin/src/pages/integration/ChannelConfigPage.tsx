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
  Form,
  Input,
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
import { formatDate, INTEGRATION_CHANNEL_OPTIONS } from '@yangqiong/shared';
import type { IntegrationChannelConfig } from '@yangqiong/shared';

const CONFIG_KEY = 'integration-channels';
const PAGE_SIZE = 20;

/**
 * 渠道类型到中文名的映射
 */
const CHANNEL_LABEL: Record<string, string> = Object.fromEntries(
  INTEGRATION_CHANNEL_OPTIONS.map((option) => [option.value, option.label]),
);

interface ConfigFormValues {
  channelType: string;
  webhookUrl?: string;
  secret?: string;
  extra?: string;
  enabled: boolean;
}

/**
 * 渠道接入配置
 */
export const ChannelConfigPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [configPage, setConfigPage] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<IntegrationChannelConfig | null>(null);
  const [form] = Form.useForm<ConfigFormValues>();

  const configQuery = useQuery({
    queryKey: [CONFIG_KEY, configPage],
    queryFn: () => api.integration.channels.page({ pageNum: configPage, pageSize: PAGE_SIZE }),
  });

  const saveMutation = useMutation({
    mutationFn: (values: ConfigFormValues) =>
      api.integration.channels.save({
        id: editing?.id,
        channelType: values.channelType,
        webhookUrl: values.webhookUrl,
        secret: values.secret,
        extra: values.extra,
        enabled: values.enabled ? 1 : 0,
      }),
    onSuccess: () => {
      message.success('渠道配置已保存');
      closeModal();
      invalidateConfigs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.integration.channels.remove(id),
    onSuccess: () => {
      message.success('删除成功');
      invalidateConfigs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (record: IntegrationChannelConfig) =>
      api.integration.channels.changeEnabled(record.id!, record.enabled === 1 ? 0 : 1),
    onSuccess: () => {
      message.success('状态已更新');
      invalidateConfigs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const testMutation = useMutation({
    mutationFn: (id: string) => api.integration.channels.test(id),
    onSuccess: (ok) => {
      if (ok) {
        message.success('测试消息发送成功');
      } else {
        message.error('测试消息发送失败，请查看集成记录');
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '测试发送失败');
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

  const openEdit = (record: IntegrationChannelConfig) => {
    setEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      channelType: record.channelType,
      webhookUrl: record.webhookUrl,
      secret: undefined,
      extra: record.extra,
      enabled: record.enabled === 1,
    });
    setModalOpen(true);
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

  const configColumns: ColumnsType<IntegrationChannelConfig> = [
    {
      title: '渠道类型',
      dataIndex: 'channelType',
      width: 120,
      render: (v: string) => <Tag color="blue">{CHANNEL_LABEL[v] ?? v}</Tag>,
    },
    {
      title: 'Webhook地址',
      dataIndex: 'webhookUrl',
      width: 300,
      ellipsis: true,
      render: (v?: string) => (v ? <Typography.Text code>{v}</Typography.Text> : '-'),
    },
    {
      title: '密钥',
      dataIndex: 'secret',
      width: 120,
      render: (v?: string) => v ?? '-',
    },
    {
      title: '扩展配置',
      dataIndex: 'extra',
      width: 200,
      ellipsis: true,
      render: (v?: string) => (v ? <Typography.Text code>{v}</Typography.Text> : '-'),
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
      render: (_: unknown, record: IntegrationChannelConfig) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            loading={testMutation.isPending && testMutation.variables === record.id}
            onClick={() => testMutation.mutate(record.id!)}
          >
            测试
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => toggleMutation.mutate(record)}
          >
            {record.enabled === 1 ? '停用' : '启用'}
          </Button>
          <Popconfirm title="确认删除该渠道配置？" onConfirm={() => deleteMutation.mutate(record.id!)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title="渠道接入配置">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="渠道接入配置持久化到数据库，发送时数据库配置优先、yml 兜底；保存后约 60 秒内生效；密钥仅显示尾号，更新时留空保留原密钥"
      />
      <Button
        type="primary"
        icon={<PlusOutlined />}
        style={{ marginBottom: 16 }}
        onClick={openCreate}
      >
        新增渠道
      </Button>
      <Table<IntegrationChannelConfig>
        rowKey="id"
        columns={configColumns}
        dataSource={configQuery.data?.records ?? []}
        loading={configQuery.isLoading}
        scroll={{ x: 1100 }}
        pagination={pagination(configPage, configQuery.data?.total ?? 0)}
        onChange={(p) => setConfigPage(p.current ?? 1)}
      />

      <Modal
        title={editing ? '编辑渠道配置' : '新增渠道配置'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={handleSubmit}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ enabled: false }}>
          <Form.Item
            name="channelType"
            label="渠道类型"
            rules={[{ required: true, message: '请选择渠道类型' }]}
          >
            <Select
              placeholder="请选择渠道类型"
              disabled={!!editing}
              options={INTEGRATION_CHANNEL_OPTIONS.map((option) => ({
                value: option.value,
                label: option.label,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="webhookUrl"
            label="Webhook地址"
            rules={[{ required: true, message: '请输入Webhook地址' }]}
          >
            <Input placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
          </Form.Item>
          <Form.Item
            name="secret"
            label="签名密钥"
            extra={editing ? '留空保留原密钥' : '渠道签名密钥，可留空'}
          >
            <Input.Password placeholder={editing ? '留空保留原密钥' : '请输入密钥'} />
          </Form.Item>
          <Form.Item
            name="extra"
            label="扩展配置"
            extra="JSON 格式，例如钉钉的 atMobiles、atUserIds 等"
          >
            <Input.TextArea rows={3} placeholder='{"atMobiles":["13800000000"]}' />
          </Form.Item>
          <Form.Item name="enabled" label="是否启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
