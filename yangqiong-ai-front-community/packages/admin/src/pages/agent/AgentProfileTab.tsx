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
  App,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { AgentConfigProfile } from '@yangqiong/shared';

const PROFILE_KEY = 'agent-profiles';

interface ProfileFormValues {
  profileCode: string;
  displayName?: string;
  overrideJson?: string;
  status: string;
}

const PROFILE_STATUS_META: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '启用', color: 'green' },
  DISABLED: { label: '停用', color: 'default' },
};

/**
 * Agent 环境配置档
 */
export const AgentProfileTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AgentConfigProfile | null>(null);
  const [form] = Form.useForm<ProfileFormValues>();

  const listQuery = useQuery({
    queryKey: [PROFILE_KEY, agentCode],
    queryFn: () => api.registry.profile.list(agentCode),
  });

  // 当前运行环境档（全局编码），命中则展示徽标
  const currentQuery = useQuery({
    queryKey: [PROFILE_KEY, 'current'],
    queryFn: () => api.registry.profile.current(),
  });
  const currentProfileCode = currentQuery.data ?? '';

  const saveMutation = useMutation({
    mutationFn: (values: ProfileFormValues) =>
      api.registry.profile.save({
        id: editing?.id,
        agentCode,
        profileCode: values.profileCode,
        displayName: values.displayName,
        overrideJson: values.overrideJson,
        status: values.status,
      }),
    onSuccess: () => {
      message.success('环境配置档已保存');
      setModalOpen(false);
      queryClient.invalidateQueries({ queryKey: [PROFILE_KEY, agentCode] });
    },
    onError: () => message.error('保存失败，请检查环境档编码与 JSON 格式'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.registry.profile.delete(id),
    onSuccess: () => {
      message.success('环境配置档已删除');
      queryClient.invalidateQueries({ queryKey: [PROFILE_KEY, agentCode] });
    },
  });

  const openCreate = () => {
    setEditing(null);
    // 先重置再赋默认值，避免上一条记录的编辑值残留
    form.resetFields();
    form.setFieldsValue({ profileCode: '', displayName: '', overrideJson: '{}', status: 'ACTIVE' });
    setModalOpen(true);
  };

  const openEdit = (profile: AgentConfigProfile) => {
    setEditing(profile);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      profileCode: profile.profileCode,
      displayName: profile.displayName,
      overrideJson: profile.overrideJson,
      status: profile.status ?? 'ACTIVE',
    });
    setModalOpen(true);
  };

  const columns: ColumnsType<AgentConfigProfile> = [
    {
      title: '环境档编码',
      dataIndex: 'profileCode',
      width: 140,
      render: (v: string) => (
        <Space size={6}>
          <Typography.Text code>{v}</Typography.Text>
          {v === currentProfileCode && <Tag color="blue">当前档</Tag>}
        </Space>
      ),
    },
    { title: '名称', dataIndex: 'displayName', width: 160 },
    {
      title: '差异覆盖',
      dataIndex: 'overrideJson',
      ellipsis: true,
      render: (v?: string) => (
        <Typography.Text code style={{ fontSize: 12 }}>
          {v || '-'}
        </Typography.Text>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const meta = PROFILE_STATUS_META[v ?? ''] ?? { label: v ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 160, render: (v?: string) => formatDate(v) },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Space size={4}>
          <Button size="small" type="link" onClick={() => openEdit(record)}>
            编辑
          </Button>
          {record.id != null && (
            <Popconfirm title="确认删除该环境配置档？" onConfirm={() => deleteMutation.mutate(record.id!)}>
              <Button size="small" type="link" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button icon={<ReloadOutlined />} onClick={() => queryClient.invalidateQueries({ queryKey: [PROFILE_KEY, agentCode] })}>
          刷新
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建环境档
        </Button>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          环境档仅存差异覆盖项（model/temperature/maxIterations/tools 子集/知识库开关），运行时叠加在发布配置之上。
        </Typography.Text>
      </Space>
      <Table
        rowKey={(r) => `${r.profileCode}-${r.id ?? 'new'}`}
        size="small"
        loading={listQuery.isLoading}
        columns={columns}
        dataSource={listQuery.data ?? []}
        pagination={false}
        locale={{ emptyText: <Empty description="暂无环境配置档" /> }}
      />
      <Modal
        title={editing ? '编辑环境配置档' : '新建环境配置档'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saveMutation.isPending}
        onOk={() => form.submit()}
      >
        <Form<ProfileFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            name="profileCode"
            label="环境档编码"
            rules={[{ required: true, message: '请输入环境档编码' }]}
            extra="如 dev / test / prod / default"
          >
            <Input placeholder="dev" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="displayName" label="显示名称">
            <Input placeholder="开发环境" />
          </Form.Item>
          <Form.Item
            name="overrideJson"
            label="差异覆盖 JSON"
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
            <Input.TextArea rows={8} style={{ fontFamily: 'monospace' }} placeholder='{"temperature": 0.2}' />
          </Form.Item>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select
              options={[
                { label: '启用（ACTIVE）', value: 'ACTIVE' },
                { label: '停用（DISABLED）', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
