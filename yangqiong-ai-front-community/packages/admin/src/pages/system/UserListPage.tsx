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
  Button,
  Card,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { AiUser, PageQuery } from '@yangqiong/shared';
import { UserFormModal } from './UserFormModal';

const { Title } = Typography;

interface UserListQuery extends PageQuery {
  username?: string;
  status?: number;
}

const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/**
 * 用户管理
 */
interface UserListPageProps {
  /**
   * 是否隐藏页面标题（外层已展示标题时传 true）
   */
  hideTitle?: boolean;
}

export const UserListPage: React.FC<UserListPageProps> = ({ hideTitle }) => {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<UserListQuery>({ page: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [current, setCurrent] = useState<AiUser | null>(null);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['system-user-list', query],
    queryFn: () => api.system.user.list(query),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: number }) =>
      api.system.user.status(id, status),
    onSuccess: () => {
      message.success('状态更新成功');
      void queryClient.invalidateQueries({ queryKey: ['system-user-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '状态更新失败'),
  });

  const passwordMutation = useMutation({
    mutationFn: ({ id, password }: { id: string; password: string }) =>
      api.system.user.password(id, password),
    onSuccess: () => message.success('密码重置成功'),
    onError: (err) => message.error(err instanceof Error ? err.message : '密码重置失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.system.user.delete(id),
    onSuccess: () => {
      message.success('用户删除成功');
      void queryClient.invalidateQueries({ queryKey: ['system-user-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '用户删除失败'),
  });

  const columns: ColumnsType<AiUser> = [
    { title: '用户名', dataIndex: 'username', width: 140 },
    { title: '显示名', dataIndex: 'displayName', width: 140 },
    { title: '邮箱', dataIndex: 'email', width: 200 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status?: number) => (
        <Tag color={status === 1 ? 'green' : 'default'}>
          {status === 1 ? '启用' : '禁用'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 280,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small" wrap>
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
          <Button
            type="link"
            size="small"
            onClick={() =>
              statusMutation.mutate({
                id: String(record.id),
                status: record.status === 1 ? 0 : 1,
              })
            }
          >
            {record.status === 1 ? '禁用' : '启用'}
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => handleResetPassword(record)}
          >
            重置密码
          </Button>
          <Popconfirm
            title="确认删除该用户？"
            onConfirm={() => deleteMutation.mutate(String(record.id))}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleResetPassword = (record: AiUser) => {
    let value = '';
    modal.confirm({
      title: `重置密码：${record.username}`,
      content: (
        <Input.Password
          placeholder="请输入新密码"
          onChange={(e) => {
            value = e.target.value;
          }}
          autoFocus
        />
      ),
      onOk: () => {
        if (!value || value.length < 6) {
          message.warning('密码长度至少 6 位');
          return Promise.reject(new Error('invalid'));
        }
        passwordMutation.mutate({ id: String(record.id), password: value });
      },
    });
  };

  return (
    <div>
      {!hideTitle && (
        <Title level={4} style={{ marginBottom: 16 }}>
          用户管理
        </Title>
      )}
      <Card styles={{ body: { paddingBottom: 0 } }} style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input.Search
            placeholder="用户名/邮箱关键字"
            allowClear
            style={{ width: 240 }}
            onSearch={(v) => setQuery((q) => ({ ...q, username: v || undefined, page: 1 }))}
          />
          <Select
            allowClear
            placeholder="状态筛选"
            style={{ width: 140 }}
            options={STATUS_OPTIONS}
            onChange={(v?: number) =>
              setQuery((q) => ({ ...q, status: v, page: 1 }))
            }
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void queryClient.invalidateQueries({ queryKey: ['system-user-list'] })}
          >
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setCurrent(null);
              setFormOpen(true);
            }}
          >
            新增用户
          </Button>
        </Space>
      </Card>
      <Card styles={{ body: { paddingTop: 0 } }}>
        <Table<AiUser>
          rowKey="id"
          columns={columns}
          dataSource={data?.list}
          loading={isLoading || isFetching}
          scroll={{ x: 1040 }}
          pagination={{
            current: data?.page ?? query.page,
            pageSize: data?.size ?? query.size,
            total: data?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setQuery((q) => ({ ...q, page, size })),
          }}
        />
      </Card>

      <UserFormModal
        open={formOpen}
        record={current}
        onClose={() => setFormOpen(false)}
      />
    </div>
  );
};

export default UserListPage;
