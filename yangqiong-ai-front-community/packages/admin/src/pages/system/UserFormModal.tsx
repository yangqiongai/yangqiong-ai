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
import React, { useEffect } from 'react';
import { App, Form, Input, Modal, Select } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { AiUser } from '@yangqiong/shared';

interface UserFormModalProps {
  open: boolean;
  record: AiUser | null;
  onClose: () => void;
}

interface UserFormValues {
  username: string;
  password?: string;
  displayName?: string;
  email?: string;
  phone?: string;
  status?: number;
}

const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/**
 * 用户表单弹窗
 */
export const UserFormModal: React.FC<UserFormModalProps> = ({
  open,
  record,
  onClose,
}) => {
  const [form] = Form.useForm<UserFormValues>();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const isEdit = !!record;

  useEffect(() => {
    if (open) {
      if (record) {
        form.setFieldsValue({
          username: record.username,
          displayName: record.displayName,
          email: record.email,
          phone: record.phone,
          status: record.status,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({ status: 1 });
      }
    }
  }, [open, record, form]);

  const createMutation = useMutation({
    mutationFn: (values: UserFormValues) =>
      api.system.user.create({ ...values, scopeId: 'default' }),
    onSuccess: () => {
      message.success('用户创建成功');
      void queryClient.invalidateQueries({ queryKey: ['system-user-list'] });
      onClose();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '用户创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: UserFormValues) =>
      api.system.user.update(String(record!.id), values),
    onSuccess: () => {
      message.success('用户更新成功');
      void queryClient.invalidateQueries({ queryKey: ['system-user-list'] });
      onClose();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '用户更新失败');
    },
  });

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      if (isEdit) {
        updateMutation.mutate(values);
      } else {
        createMutation.mutate(values);
      }
    } catch {
      // 校验失败由表单自身提示
    }
  };

  const submitting = createMutation.isPending || updateMutation.isPending;

  return (
    <Modal
      title={isEdit ? '编辑用户' : '新增用户'}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={submitting}
      destroyOnClose
      maskClosable={false}
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          name="username"
          label="用户名"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input placeholder="请输入用户名" disabled={isEdit} />
        </Form.Item>
        <Form.Item name="displayName" label="显示名">
          <Input placeholder="请输入显示名" />
        </Form.Item>
        <Form.Item
          name="email"
          label="邮箱"
          rules={[{ type: 'email', message: '邮箱格式不正确' }]}
        >
          <Input placeholder="请输入邮箱" />
        </Form.Item>
        <Form.Item name="phone" label="手机号">
          <Input placeholder="请输入手机号" />
        </Form.Item>
        <Form.Item
          name="status"
          label="状态"
          rules={[{ required: true, message: '请选择状态' }]}
        >
          <Select options={STATUS_OPTIONS} placeholder="请选择状态" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default UserFormModal;
