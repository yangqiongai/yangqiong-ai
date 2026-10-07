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
import { App, Button, Card, Input, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ApiOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { ModelInfo } from '@yangqiong/shared';
import { ModelFormModal, type ModelFormValues } from './ModelFormModal';

const QUERY_KEY = 'models';

const { Text } = Typography;

const MODEL_STATUS_LABEL: Record<number, string> = {
  1: '启用',
  0: '禁用',
};

/**
 * 模型列表
 */
export const ModelListPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ModelInfo | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, page, size, keyword],
    queryFn: () => api.model.list({ page, size, keyword }),
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (values: ModelFormValues) => api.model.create(values),
    onSuccess: () => {
      message.success('创建成功');
      setModalOpen(false);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: ModelFormValues) => api.model.update(values),
    onSuccess: () => {
      message.success('更新成功');
      setModalOpen(false);
      setEditing(null);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (modelCode: string) => api.model.delete(modelCode),
    onSuccess: () => {
      message.success('删除成功');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const testMutation = useMutation({
    mutationFn: (record: ModelInfo) => api.model.testConnectivity(record),
    onSuccess: (res) => {
      if (res.success) {
        message.success(`连接成功，耗时 ${res.latencyMs ?? 0}ms${res.reply ? `，回复：${res.reply}` : ''}`);
      } else {
        message.error(`连接失败：${res.error ?? '未知错误'}`);
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '测试请求失败');
    },
  });

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (record: ModelInfo) => {
    setEditing(record);
    setModalOpen(true);
  };

  const handleSubmit = (values: ModelFormValues) => {
    if (editing) {
      updateMutation.mutate(values);
    } else {
      createMutation.mutate(values);
    }
  };

  const columns: ColumnsType<ModelInfo> = [
    { title: '供应方', dataIndex: 'provider', width: 110 },
    { title: '模型编码', dataIndex: 'modelCode', width: 140 },
    { title: '模型名称', dataIndex: 'modelName', width: 140, ellipsis: true },
    {
      title: '描述',
      dataIndex: 'remark',
      width: 180,
      render: (v?: string) =>
        v ? (
          <Text style={{ maxWidth: 160 }} ellipsis={{ tooltip: v }}>
            {v}
          </Text>
        ) : (
          '-'
        ),
    },
    { title: '模型类型', dataIndex: 'modelType', width: 100 },
    {
      title: '默认',
      dataIndex: 'isDefault',
      width: 60,
      render: (v?: number) => (v ? <Tag color="blue">默认</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'modelStatus',
      width: 80,
      render: (v: ModelInfo['modelStatus']) => (
        <Tag color={v === 1 ? 'green' : 'default'}>
          {v != null ? MODEL_STATUS_LABEL[v] ?? v : '-'}
        </Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 160,
      render: (v: string) => formatDate(v),
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right',
      render: (_: unknown, record: ModelInfo) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            icon={<ApiOutlined />}
            loading={testMutation.isPending && testMutation.variables?.modelCode === record.modelCode}
            onClick={() => testMutation.mutate(record)}
          >
            测试
          </Button>
          <Popconfirm
            title="确认删除该模型？"
            onConfirm={() => {
              if (record.modelCode) deleteMutation.mutate(record.modelCode);
            }}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const handleSearch = () => {
    setKeyword(searchKeyword);
    setPage(1);
  };

  return (
    <Card
      title="模型管理"
      extra={
        <Button type="primary" onClick={openCreate}>
          新增模型
        </Button>
      }
    >
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="供应方/模型编码/名称"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          allowClear
          style={{ width: 240 }}
          onPressEnter={handleSearch}
        />
        <Button type="primary" onClick={handleSearch}>
          查询
        </Button>
        <Button
          onClick={() => {
            setSearchKeyword('');
            setKeyword('');
            setPage(1);
          }}
        >
          重置
        </Button>
      </Space>

      <Table<ModelInfo>
        rowKey="id"
        columns={columns}
        dataSource={data?.list ?? []}
        loading={isLoading}
        scroll={{ x: 1200 }}
        pagination={{
          current: page,
          pageSize: size,
          total: data?.total ?? 0,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
          },
        }}
      />

      <ModelFormModal
        open={modalOpen}
        editing={editing}
        loading={createMutation.isPending || updateMutation.isPending}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
      />
    </Card>
  );
};
