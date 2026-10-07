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
import { App, Button, Card, Input, Popconfirm, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { CapabilityCategoryNode, CapabilitySpec } from '@yangqiong/shared';
import { CategoryTreePanel } from '@/components/CategoryTreePanel';
import type { CategoryTreeService } from '@/components/CategoryTreePanel';
import { CapabilityFormDrawer } from './CapabilityFormDrawer';

const QUERY_KEY = 'open-capabilities';

/**
 * 能力分类树数据服务
 */
const capabilityCategoryService: CategoryTreeService = {
  tree: () => api.openCapability.categoryTree(),
  create: (data) => api.openCapability.createCategory(data),
  update: (id, data) => api.openCapability.updateCategory(id, data),
  remove: (id) => api.openCapability.deleteCategory(id),
};

/**
 * 开放能力管理（左分类树 + 右能力列表）
 */
export const OpenCapabilityPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  /** 左树选中的过滤编码（分类code / __ungrouped__，undefined=全部） */
  const [categoryCode, setCategoryCode] = useState<string | undefined>(undefined);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<CapabilitySpec | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, page, size, keyword, categoryCode],
    queryFn: () => api.openCapability.list({ page, size, keyword, categoryCode }),
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (spec: CapabilitySpec) => api.openCapability.create(spec),
    onSuccess: () => {
      message.success('创建成功');
      setDrawerOpen(false);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (spec: CapabilitySpec) =>
      api.openCapability.update(editing?.code as string, spec),
    onSuccess: () => {
      message.success('更新成功');
      setDrawerOpen(false);
      setEditing(null);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (code: string) => api.openCapability.delete(code),
    onSuccess: () => {
      message.success('删除成功');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const columns: ColumnsType<CapabilitySpec> = [
    { title: '编码', dataIndex: 'code', width: 160 },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    { title: 'Agent', dataIndex: 'agentCode', width: 110, ellipsis: true },
    { title: '版本', dataIndex: 'version', width: 90 },
    { title: '分类', dataIndex: 'category', width: 110, ellipsis: true },
    {
      title: '操作',
      width: 160,
      fixed: 'right',
      render: (_: unknown, record: CapabilitySpec) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditing(record);
              setDrawerOpen(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除该开放能力？"
            onConfirm={() => deleteMutation.mutate(record.code)}
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
    <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
      <div style={{ width: 260, flexShrink: 0 }}>
        <CategoryTreePanel
          title="能力分类"
          queryKey="open-capability-category-tree"
          service={capabilityCategoryService}
          countOf={(node) => (node as CapabilityCategoryNode).capabilityCount}
          codeExtra="创建后不可修改，能力列表按该编码过滤"
          value={categoryCode}
          onChange={(code) => {
            setCategoryCode(code);
            setPage(1);
          }}
        />
      </div>
      <Card
        title="开放能力管理"
        style={{ flex: 1, minWidth: 0 }}
        extra={
          <Button
            type="primary"
            onClick={() => {
              setEditing(null);
              setDrawerOpen(true);
            }}
          >
            新增能力
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }}>
          <Input
            placeholder="编码/名称/分类"
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

        <Table<CapabilitySpec>
          rowKey="code"
          columns={columns}
          dataSource={data?.list ?? []}
          loading={isLoading}
          scroll={{ x: 900 }}
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
      </Card>

      <CapabilityFormDrawer
        open={drawerOpen}
        editing={editing}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        onSubmit={(spec) =>
          editing ? updateMutation.mutate(spec) : createMutation.mutate(spec)
        }
        onRolledBack={() => queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })}
      />
    </div>
  );
};
