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
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { CategoryTreeNode, ToolConfigInfo } from '@yangqiong/shared';
import { CategoryTreePanel } from '@/components/CategoryTreePanel';
import type { CategoryTreeService } from '@/components/CategoryTreePanel';

const QUERY_KEY = 'tool-configs';
const CATEGORY_TREE_QUERY_KEY = 'tool-category-tree';

const STATUS_LABEL_NUMBER: Record<number, string> = {
  0: '禁用',
  1: '启用',
};

/**
 * 类型标签：BUILTIN内置 / CUSTOM系统 / USER及空值自定义
 */
const CATEGORY_META: Record<string, { label: string; color: string }> = {
  BUILTIN: { label: '内置', color: 'blue' },
  CUSTOM: { label: '系统', color: 'purple' },
  USER: { label: '自定义', color: 'orange' },
};

/**
 * 内置工具由代码注册，不允许编辑和删除；系统工具不允许删除
 */
const isProtectedCategory = (record: ToolConfigInfo) =>
  record.toolCategory === 'BUILTIN' || record.toolCategory === 'CUSTOM';

/**
 * 工具分类树数据服务
 */
const toolCategoryService: CategoryTreeService = {
  tree: () => api.tool.config.category.tree(),
  create: (data) => api.tool.config.category.create(data),
  update: (id, data) => api.tool.config.category.update(id, data),
  remove: (id) => api.tool.config.category.delete(id),
};

/**
 * 分类树节点转TreeSelect数据（value=分类code，与实体category字段对齐）
 * @param nodes
 * @return
 */
const toCategoryTreeData = (nodes?: CategoryTreeNode[]): any[] =>
  (nodes ?? []).map((node) => ({
    title: node.name,
    value: node.code,
    children: toCategoryTreeData(node.children),
  }));

/**
 * 递归查找分类树中code对应的分类名称
 * @param nodes
 * @param code
 * @return
 */
const findCategoryName = (nodes: CategoryTreeNode[] | undefined, code?: string): string | undefined => {
  if (!code) return undefined;
  for (const node of nodes ?? []) {
    if (node.code === code) return node.name;
    const childName = findCategoryName(node.children, code);
    if (childName) return childName;
  }
  return undefined;
};

export interface ToolConfigFormValues {
  toolCode: string;
  toolName: string;
  toolDesc?: string;
  toolType: string;
  toolStatus?: number;
  toolConfig?: string;
  remark?: string;
  /**
   * 所属分类（分类树节点code，空为未分类）
   */
  category?: string;
}

/**
 * 工具配置列表
 */
export const ToolConfigListPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  /** 左树选中的过滤编码（分类code / __ungrouped__，undefined=全部） */
  const [categoryCode, setCategoryCode] = useState<string | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ToolConfigInfo | null>(null);
  const [form] = Form.useForm<ToolConfigFormValues>();

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, page, size, keyword, categoryCode],
    queryFn: () => api.tool.config.list({ page, size, keyword, categoryCode }),
  });

  // 分类树数据（与左侧分类树面板共享缓存，供表单分类下拉与列表分类列使用）
  const { data: categoryTree } = useQuery({
    queryKey: [CATEGORY_TREE_QUERY_KEY],
    queryFn: api.tool.config.category.tree,
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (values: ToolConfigFormValues) => api.tool.config.create(values),
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
    mutationFn: (values: ToolConfigFormValues) =>
      api.tool.config.update(editing?.toolCode ?? '', values),
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
    mutationFn: (toolCode: string) => api.tool.config.delete(toolCode),
    onSuccess: () => {
      message.success('删除成功');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ toolType: 'function', toolStatus: 1 });
    setModalOpen(true);
  };

  const openEdit = (record: ToolConfigInfo) => {
    setEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      toolCode: record.toolCode,
      toolName: record.toolName,
      toolDesc: record.toolDesc,
      toolType: record.toolType,
      toolStatus: record.toolStatus,
      toolConfig: record.toolConfig,
      remark: record.remark,
      category: record.category,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      updateMutation.mutate(values);
    } else {
      createMutation.mutate(values);
    }
  };

  const columns: ColumnsType<ToolConfigInfo> = [
    { title: '名称', dataIndex: 'toolName', ellipsis: true },
    { title: '编码', dataIndex: 'toolCode', width: 160 },
    {
      title: '类型',
      dataIndex: 'toolCategory',
      width: 90,
      render: (v?: string) => {
        const meta = CATEGORY_META[v ?? ''] ?? CATEGORY_META.USER;
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '分类',
      dataIndex: 'category',
      width: 110,
      ellipsis: true,
      render: (v?: string) => findCategoryName(categoryTree?.nodes, v) ?? v ?? '-',
    },
    { title: '描述', dataIndex: 'toolDesc', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'toolStatus',
      width: 90,
      render: (v?: number) => (
        <Tag color={v === 1 ? 'green' : 'default'}>
          {STATUS_LABEL_NUMBER[v ?? 0]}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '操作',
      width: 140,
      fixed: 'right',
      render: (_: unknown, record: ToolConfigInfo) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            disabled={record.toolCategory === 'BUILTIN'}
            onClick={() => openEdit(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除该工具配置？"
            onConfirm={() => deleteMutation.mutate(record.toolCode)}
            disabled={isProtectedCategory(record)}
          >
            <Button type="link" size="small" danger disabled={isProtectedCategory(record)}>
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
          title="工具分类"
          queryKey={CATEGORY_TREE_QUERY_KEY}
          service={toolCategoryService}
          codeExtra="创建后不可修改，工具列表按该编码过滤"
          value={categoryCode}
          onChange={(code) => {
            setCategoryCode(code);
            setPage(1);
          }}
        />
      </div>
      <Card
        title="工具管理"
        style={{ flex: 1, minWidth: 0 }}
        extra={
          <Button type="primary" onClick={openCreate}>
            新增配置
          </Button>
        }
      >
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="名称/编码"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          allowClear
          style={{ width: 220 }}
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

      <Table<ToolConfigInfo>
        rowKey="toolCode"
        columns={columns}
        dataSource={data?.list ?? []}
        loading={isLoading}
        scroll={{ x: 1100 }}
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

      <Modal
        title={editing ? '编辑工具配置' : '新增工具配置'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="toolName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="请输入名称" />
          </Form.Item>
          <Form.Item
            name="toolCode"
            label="编码"
            rules={[{ required: true, message: '请输入编码' }]}
          >
            <Input placeholder="请输入编码" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="category" label="所属分类" extra="从分类树中选择，可清空为未分类">
            <TreeSelect
              placeholder="不选择则为未分类"
              allowClear
              showSearch
              treeNodeFilterProp="title"
              treeData={toCategoryTreeData(categoryTree?.nodes)}
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item
            name="toolType"
            label="类型"
            rules={[{ required: true, message: '请输入类型' }]}
          >
            <Input placeholder="如 function / api / retrieval" />
          </Form.Item>
          <Form.Item name="toolDesc" label="描述">
            <Input.TextArea rows={2} placeholder="请输入描述" />
          </Form.Item>
          <Form.Item name="toolConfig" label="配置 JSON">
            <Input.TextArea
              rows={4}
              placeholder='{"key":"value"}'
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item name="toolStatus" label="状态">
            <Select
              options={[
                { label: '启用', value: 1 },
                { label: '禁用', value: 0 },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="请输入备注" />
          </Form.Item>
        </Form>
      </Modal>
      </Card>
    </div>
  );
};
