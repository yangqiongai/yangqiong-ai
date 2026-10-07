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
  Col,
  Divider,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { api } from '@/services';
import type { WorkspaceItem } from '@yangqiong/shared';

const { Text } = Typography;

/**
 * 审批层级展示与下拉配置
 */
export const APPROVAL_MODE_META: Record<WorkspaceItem['approvalMode'], { text: string; color: string }> = {
  MANUAL: { text: '人工审批', color: 'blue' },
  AUTO: { text: 'AI 自动审批', color: 'geekblue' },
  FULL_ACCESS: { text: '完全访问', color: 'orange' },
  CUSTOM: { text: '自定义', color: 'purple' },
};

const APPROVAL_MODE_OPTIONS = Object.entries(APPROVAL_MODE_META).map(([value, meta]) => ({
  value: value as WorkspaceItem['approvalMode'],
  label: meta.text,
}));

interface WorkspaceFormValues {
  name: string;
  rootPath?: string;
  description?: string;
  approvalMode: WorkspaceItem['approvalMode'];
}

interface WorkspaceManageModalProps {
  open: boolean;
  userId?: string;
  workspaces: WorkspaceItem[];
  loading?: boolean;
  onClose: () => void;
  onChanged: () => void;

  /**
   * 打开弹窗时的预置动作（default-create=预填默认目录创建表单）
   */
  initialAction?: 'default-create';
}

/**
 * 工作区管理弹窗
 */
export const WorkspaceManageModal: React.FC<WorkspaceManageModalProps> = ({
  open,
  userId,
  workspaces,
  loading,
  onClose,
  onChanged,
  initialAction,
}) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<WorkspaceFormValues>();
  // 雪花ID超出Number安全范围，编辑/删除目标id保持字符串避免精度丢失
  const [editingId, setEditingId] = useState<string>();
  const [autoCreate, setAutoCreate] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<string>();

  // 弹窗打开时按预置动作初始化表单
  useEffect(() => {
    if (!open) {
      return;
    }
    form.resetFields();
    setEditingId(undefined);
    if (initialAction === 'default-create') {
      setAutoCreate(true);
      form.setFieldsValue({ name: '默认工作区', approvalMode: 'MANUAL' });
    } else {
      setAutoCreate(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initialAction]);

  const resetForm = () => {
    form.resetFields();
    setEditingId(undefined);
    setAutoCreate(false);
  };

  /**
   * 预填默认目录创建表单（路径由用户按本机主目录补全，提交时自动建目录）
   */
  const openDefaultCreate = () => {
    resetForm();
    setAutoCreate(true);
    form.setFieldsValue({ name: '默认工作区', approvalMode: 'MANUAL' });
  };

  const handleEdit = (record: WorkspaceItem) => {
    resetForm();
    setEditingId(record.id);
    form.setFieldsValue({
      name: record.name,
      rootPath: record.rootPath,
      description: record.description,
      approvalMode: record.approvalMode,
    });
  };

  const handleSubmit = async () => {
    if (!userId) {
      message.warning('未获取到当前用户，无法操作工作区');
      return;
    }
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editingId) {
        await api.workspace.update(editingId, {
          userId,
          name: values.name,
          description: values.description,
          approvalMode: values.approvalMode,
        });
        message.success('工作区已更新');
      } else {
        await api.workspace.create({
          userId,
          name: values.name,
          rootPath: values.rootPath ?? '',
          description: values.description,
          approvalMode: values.approvalMode,
          autoCreate,
        });
        message.success('工作区已创建');
      }
      resetForm();
      onChanged();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存工作区失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (record: WorkspaceItem) => {
    if (!userId) {
      return;
    }
    setDeletingId(record.id);
    try {
      await api.workspace.remove(record.id, userId);
      message.success('工作区已删除');
      // 删除的若是当前编辑项则同步退出编辑态
      if (editingId === record.id) {
        resetForm();
      }
      onChanged();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '删除工作区失败');
    } finally {
      setDeletingId(undefined);
    }
  };

  const columns: ColumnsType<WorkspaceItem> = [
    { title: '名称', dataIndex: 'name', width: 130, ellipsis: true },
    { title: '路径', dataIndex: 'rootPath', ellipsis: true },
    {
      title: '审批层级',
      dataIndex: 'approvalMode',
      width: 110,
      render: (v: WorkspaceItem['approvalMode']) => (
        <Tag color={APPROVAL_MODE_META[v]?.color}>{APPROVAL_MODE_META[v]?.text ?? v}</Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 76,
      render: (v: number) =>
        v === 1 ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>,
    },
    {
      title: '健康状态',
      dataIndex: 'valid',
      width: 90,
      render: (v?: boolean) =>
        v === false ? <Tag color="red">失效</Tag> : <Tag color="green">正常</Tag>,
    },
    {
      title: '操作',
      width: 120,
      render: (_: unknown, record: WorkspaceItem) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该工作区？"
            description="仅移除登记，不会删除磁盘目录"
            okText="删除"
            cancelText="取消"
            onConfirm={() => void handleDelete(record)}
          >
            {/* 云端工作区由服务端统一管理，不支持删除 */}
            <Button
              type="link"
              size="small"
              danger
              loading={deletingId === record.id}
              disabled={record.type === 'SERVER'}
              title={record.type === 'SERVER' ? '云端工作区由服务端统一管理，不支持删除' : undefined}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Modal
      title="工作区管理"
      width={720}
      open={open}
      onCancel={onClose}
      footer={null}
      destroyOnClose
    >
      <Table<WorkspaceItem>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={workspaces}
        loading={loading}
        pagination={{ pageSize: 5, size: 'small', hideOnSinglePage: true }}
        scroll={{ y: 240 }}
      />
      <Divider style={{ margin: '16px 0 12px' }}>
        <Space size={4}>
          <PlusOutlined />
          <span>{editingId ? '编辑工作区' : '新增工作区'}</span>
        </Space>
      </Divider>
      <Form<WorkspaceFormValues> form={form} layout="vertical" size="small">
        <Row gutter={12}>
          <Col span={9}>
            <Form.Item
              name="name"
              label="名称"
              rules={[{ required: true, message: '请输入工作区名称' }]}
            >
              <Input placeholder="如：默认工作区" />
            </Form.Item>
          </Col>
          <Col span={15}>
            <Form.Item
              name="rootPath"
              label="路径"
              rules={editingId ? [] : [{ required: true, message: '请输入工作区路径' }]}
              extra={
                autoCreate && !editingId
                  ? '提交后若目录不存在将自动创建，如 C:/Users/你的用户名/yangqiong-workspace'
                  : undefined
              }
            >
              <Input
                disabled={Boolean(editingId)}
                placeholder={editingId ? '路径创建后不可变更' : '如 C:/Users/你的用户名/yangqiong-workspace'}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col span={9}>
            <Form.Item name="approvalMode" label="审批层级" initialValue="MANUAL">
              <Select options={APPROVAL_MODE_OPTIONS} />
            </Form.Item>
          </Col>
          <Col span={15}>
            <Form.Item name="description" label="描述">
              <Input placeholder="选填" />
            </Form.Item>
          </Col>
        </Row>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Button
            icon={<ThunderboltOutlined />}
            onClick={openDefaultCreate}
            disabled={Boolean(editingId)}
            title="预填默认目录创建表单，路径按本机用户主目录填写"
          >
            一键创建默认目录
          </Button>
          <Space>
            {editingId && <Button onClick={resetForm}>取消编辑</Button>}
            <Button type="primary" loading={submitting} onClick={() => void handleSubmit()}>
              {editingId ? '保存' : autoCreate ? '创建并自动建目录' : '新增'}
            </Button>
          </Space>
        </div>
      </Form>
      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        目录需在后端配置的白名单根内；常规新增要求目录已存在，「一键创建默认目录」流程提交后自动创建。
      </Text>
    </Modal>
  );
};
