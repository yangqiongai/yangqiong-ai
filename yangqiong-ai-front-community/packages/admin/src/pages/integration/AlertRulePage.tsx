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
import { formatDate, ALERT_LEVEL_OPTIONS } from '@yangqiong/shared';
import type { AlertInstance, AlertRuleConfig } from '@yangqiong/shared';

const RULE_KEY = 'integration-alert-rules';
const INSTANCE_KEY = 'integration-alert-instances';
const PAGE_SIZE = 20;

/**
 * 告警级别到标签颜色的映射
 */
const LEVEL_COLOR: Record<string, string> = {
  INFO: 'blue',
  WARNING: 'gold',
  CRITICAL: 'red',
};

/**
 * 告警实例状态到标签的映射
 */
const INSTANCE_STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待处理', color: 'gold' },
  RESOLVED: { label: '已解决', color: 'green' },
  IGNORED: { label: '已忽略', color: 'default' },
};

interface RuleFormValues {
  ruleName: string;
  eventType: string;
  level: string;
  throttleSeconds?: number;
  actionable: boolean;
  actions?: string;
  enabled: boolean;
}

/**
 * 告警规则
 */
export const AlertRulePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [rulePage, setRulePage] = useState(1);
  const [instancePage, setInstancePage] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AlertRuleConfig | null>(null);
  const [instanceOpen, setInstanceOpen] = useState(false);
  const [form] = Form.useForm<RuleFormValues>();

  const ruleQuery = useQuery({
    queryKey: [RULE_KEY, rulePage],
    queryFn: () => api.integration.alertRules.page({ pageNum: rulePage, pageSize: PAGE_SIZE }),
  });

  const instanceQuery = useQuery({
    queryKey: [INSTANCE_KEY, instancePage],
    queryFn: () => api.integration.alertRules.instances({ pageNum: instancePage, pageSize: PAGE_SIZE }),
    enabled: instanceOpen,
  });

  const saveMutation = useMutation({
    mutationFn: (values: RuleFormValues) =>
      api.integration.alertRules.save({
        id: editing?.id,
        ruleName: values.ruleName,
        eventType: values.eventType,
        level: values.level,
        throttleSeconds: values.throttleSeconds ?? 0,
        actionable: values.actionable ? 1 : 0,
        actions: values.actionable ? values.actions : undefined,
        enabled: values.enabled ? 1 : 0,
      }),
    onSuccess: () => {
      message.success('告警规则已保存');
      closeModal();
      invalidateRules();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.integration.alertRules.remove(id),
    onSuccess: () => {
      message.success('删除成功');
      invalidateRules();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const toggleMutation = useMutation({
    mutationFn: (record: AlertRuleConfig) =>
      api.integration.alertRules.changeEnabled(record.id!, record.enabled === 1 ? 0 : 1),
    onSuccess: () => {
      message.success('状态已更新');
      invalidateRules();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const invalidateRules = () => {
    queryClient.invalidateQueries({ queryKey: [RULE_KEY] });
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

  const openEdit = (record: AlertRuleConfig) => {
    setEditing(record);
    form.setFieldsValue({
      ruleName: record.ruleName,
      eventType: record.eventType,
      level: record.level,
      throttleSeconds: record.throttleSeconds,
      actionable: record.actionable === 1,
      actions: record.actions,
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

  const ruleColumns: ColumnsType<AlertRuleConfig> = [
    {
      title: '规则名称',
      dataIndex: 'ruleName',
      width: 160,
      ellipsis: true,
    },
    {
      title: '事件类型',
      dataIndex: 'eventType',
      width: 160,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: '级别',
      dataIndex: 'level',
      width: 90,
      render: (v?: string) => <Tag color={LEVEL_COLOR[v ?? ''] ?? 'default'}>{v}</Tag>,
    },
    {
      title: '节流(秒)',
      dataIndex: 'throttleSeconds',
      width: 90,
    },
    {
      title: '交互按钮',
      dataIndex: 'actionable',
      width: 90,
      render: (v?: number) => (v === 1 ? <Tag color="blue">有</Tag> : <Tag>无</Tag>),
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
      width: 180,
      fixed: 'right',
      render: (_: unknown, record: AlertRuleConfig) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => toggleMutation.mutate(record)}
          >
            {record.enabled === 1 ? '停用' : '启用'}
          </Button>
          <Popconfirm title="确认删除该告警规则？" onConfirm={() => deleteMutation.mutate(record.id!)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const instanceColumns: ColumnsType<AlertInstance> = [
    {
      title: '时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '标题',
      dataIndex: 'title',
      width: 200,
      ellipsis: true,
    },
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
    },
    {
      title: '级别',
      dataIndex: 'level',
      width: 90,
      render: (v?: string) => <Tag color={LEVEL_COLOR[v ?? ''] ?? 'default'}>{v}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const meta = INSTANCE_STATUS_META[v ?? ''] ?? { label: v, color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '解决时间',
      dataIndex: 'resolveTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
  ];

  return (
    <Card title="告警规则">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="告警规则持久化到数据库，命中事件类型时按级别与节流配置产生告警并推送到启用中的渠道；保存后约 60 秒内生效"
      />
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新增规则
        </Button>
        <Button onClick={() => { setInstancePage(1); setInstanceOpen(true); }}>
          告警实例
        </Button>
      </Space>
      <Table<AlertRuleConfig>
        rowKey="id"
        columns={ruleColumns}
        dataSource={ruleQuery.data?.records ?? []}
        loading={ruleQuery.isLoading}
        scroll={{ x: 1000 }}
        pagination={pagination(rulePage, ruleQuery.data?.total ?? 0)}
        onChange={(p) => setRulePage(p.current ?? 1)}
      />

      <Modal
        title={editing ? '编辑告警规则' : '新增告警规则'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={handleSubmit}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ level: 'WARNING', throttleSeconds: 0, enabled: false }}
        >
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="例如：CPU使用率告警" />
          </Form.Item>
          <Form.Item
            name="eventType"
            label="事件类型"
            rules={[{ required: true, message: '请输入事件类型' }]}
          >
            <Input placeholder="例如：cpu_overload、breaker_open" />
          </Form.Item>
          <Form.Item name="level" label="告警级别" rules={[{ required: true }]}>
            <Select
              options={ALERT_LEVEL_OPTIONS.map((option) => ({
                value: option.value,
                label: option.label,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="throttleSeconds"
            label="节流时间（秒）"
            extra="同一规则在该时间间隔内只产生一次告警，0 表示不节流"
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="actionable" label="是否带交互按钮" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.actionable !== cur.actionable}>
            {({ getFieldValue }) =>
              getFieldValue('actionable') ? (
                <Form.Item
                  name="actions"
                  label="按钮配置"
                  extra="JSON 数组，label/actionCode/actionType(CALLBACK|URL)/value"
                  rules={[{ required: true, message: '请输入按钮配置' }]}
                >
                  <Input.TextArea
                    rows={4}
                    placeholder='[{"label":"重试","actionCode":"RETRY","actionType":"CALLBACK","value":"RETRY"}]'
                  />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="enabled" label="是否启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="告警实例"
        width={900}
        open={instanceOpen}
        onClose={() => setInstanceOpen(false)}
        destroyOnClose
      >
        <Table<AlertInstance>
          rowKey="id"
          columns={instanceColumns}
          dataSource={instanceQuery.data?.records ?? []}
          loading={instanceQuery.isLoading}
          size="small"
          scroll={{ x: 860 }}
          pagination={pagination(instancePage, instanceQuery.data?.total ?? 0)}
          onChange={(p) => setInstancePage(p.current ?? 1)}
        />
      </Drawer>
    </Card>
  );
};
