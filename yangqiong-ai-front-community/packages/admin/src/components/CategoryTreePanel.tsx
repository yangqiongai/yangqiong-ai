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
import { App, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Space, Spin, Tree, TreeSelect } from 'antd';
import { EditOutlined, PlusOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { CategorySaveRequest, CategoryTreeNode } from '@yangqiong/shared';

/**
 * 分类树面板
 * @author yangqiong
 */

/** 未分类的虚拟树节点key（与后端约定一致） */
export const UNGROUPED_KEY = '__ungrouped__';

/**
 * 分类树数据服务（tree/新增/更新/删除）
 */
export interface CategoryTreeService {
  tree: () => Promise<{ nodes?: CategoryTreeNode[]; ungroupedCount?: number }>;
  create: (data: CategorySaveRequest) => Promise<unknown>;
  update: (id: number, data: CategorySaveRequest) => Promise<unknown>;
  remove: (id: number) => Promise<unknown>;
}

interface CategoryFormValues {
  name: string;
  code?: string;
  parentId?: number;
  sortNum?: number;
}

interface CategoryTreePanelProps {
  /** 面板标题（如：能力分类） */
  title: string;

  /**
   * react-query 缓存key（列表页表单可复用同key读取树数据）
   */
  queryKey: string;

  /**
   * 分类数据服务
   */
  service: CategoryTreeService;

  /**
   * 节点计数取值（可选，未传不显示计数）
   */
  countOf?: (node: CategoryTreeNode) => number | undefined;

  /**
   * 分类编码输入框说明文案
   */
  codeExtra?: string;

  /** 当前选中的过滤编码（分类code / __ungrouped__） */
  value?: string;

  /** 选中变化回调（undefined=全部） */
  onChange: (code?: string) => void;

  /**
   * 面板右上角追加操作（如折叠按钮）
   */
  extra?: React.ReactNode;
}

/**
 * 分类树面板（左侧自由分类树，支持增删改与未分类过滤，能力/技能/MCP列表页共用）
 */
export const CategoryTreePanel: React.FC<CategoryTreePanelProps> = ({
  title,
  queryKey,
  service,
  countOf,
  codeExtra,
  extra,
  value,
  onChange,
}) => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<CategoryFormValues>();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingNode, setEditingNode] = useState<CategoryTreeNode | null>(null);
  /** 新增子分类时的父节点 */
  const [parentNode, setParentNode] = useState<CategoryTreeNode | null>(null);

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: [queryKey],
    queryFn: service.tree,
  });

  const invalidateTree = () => {
    queryClient.invalidateQueries({ queryKey: [queryKey] });
  };

  const saveMutation = useMutation({
    mutationFn: (values: CategoryFormValues) => {
      if (editingNode) {
        return service.update(editingNode.id, {
          name: values.name,
          parentId: values.parentId ?? null,
          sortNum: values.sortNum ?? 0,
        });
      }
      return service.create({
        code: values.code,
        name: values.name,
        parentId: values.parentId ?? null,
        sortNum: values.sortNum ?? 0,
      });
    },
    onSuccess: () => {
      message.success(editingNode ? '分类更新成功' : '分类创建成功');
      setModalOpen(false);
      invalidateTree();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => service.remove(id),
    onSuccess: () => {
      message.success('分类删除成功');
      invalidateTree();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  /**
   * 将分类树节点转为antd Tree数据，并在标题右侧挂管理操作
   */
  const toTreeData = (nodes?: CategoryTreeNode[]): any[] =>
    (nodes ?? []).map((node) => ({
      key: node.code,
      title: renderTitle(node),
      children: toTreeData(node.children),
    }));

  const renderTitle = (node: CategoryTreeNode) => {
    const count = countOf?.(node);
    return (
      <Space size={4}>
        <span>{node.name}</span>
        {count != null && <span style={{ color: '#999', fontSize: 12 }}>({count})</span>}
        <Space size={0} style={{ marginLeft: 4 }}>
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            title="新增子分类"
            onClick={(e) => {
              e.stopPropagation();
              openCreate(node);
            }}
          />
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            title="编辑"
            onClick={(e) => {
              e.stopPropagation();
              openEdit(node);
            }}
          />
          <Popconfirm
            title="确认删除该分类？"
            description="存在子分类或已挂载数据时不可删除"
            onConfirm={(e) => {
              e?.stopPropagation();
              deleteMutation.mutate(node.id);
            }}
            onCancel={(e) => e?.stopPropagation()}
          >
            <Button
              type="text"
              size="small"
              danger
              icon={<DeleteOutlined />}
              title="删除"
              onClick={(e) => e.stopPropagation()}
            />
          </Popconfirm>
        </Space>
      </Space>
    );
  };

  const openCreate = (parent?: CategoryTreeNode) => {
    setEditingNode(null);
    setParentNode(parent ?? null);
    form.resetFields();
    form.setFieldsValue({ parentId: parent?.id, sortNum: 0 });
    setModalOpen(true);
  };

  const openEdit = (node: CategoryTreeNode) => {
    setEditingNode(node);
    setParentNode(null);
    form.resetFields();
    form.setFieldsValue({
      name: node.name,
      code: node.code,
      parentId: node.parentId ?? undefined,
      sortNum: node.sortNum ?? 0,
    });
    setModalOpen(true);
  };

  /**
   * 父分类下拉树（编辑时排除自身及子孙，避免成环）
   */
  const parentTreeData = (): any[] => {
    const filterNodes = (nodes?: CategoryTreeNode[]): any[] =>
      (nodes ?? [])
        .filter((node) => !editingNode || node.id !== editingNode.id)
        .map((node) => ({
          title: node.name,
          value: node.id,
          children: filterNodes(node.children),
        }));
    return filterNodes(data?.nodes);
  };

  const treeData = [
    ...toTreeData(data?.nodes),
    {
      key: UNGROUPED_KEY,
      title: (
        <span>
          未分类 <span style={{ color: '#999', fontSize: 12 }}>({data?.ungroupedCount ?? 0})</span>
        </span>
      ),
      isLeaf: true,
    },
  ];

  return (
    <Card
      size="small"
      title={title}
      extra={
        <Space size={4}>
          <Button type="text" size="small" icon={<ReloadOutlined />} loading={isFetching} onClick={() => refetch()} />
          <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => openCreate()}>
            根分类
          </Button>
          {extra}
        </Space>
      }
      styles={{ body: { paddingTop: 8 } }}
    >
      <Spin spinning={isLoading}>
        <Tree
          blockNode
          treeData={treeData}
          selectedKeys={value ? [value] : []}
          onSelect={(keys) => {
            const key = keys[0] as string | undefined;
            onChange(key || undefined);
          }}
        />
      </Spin>
      <Modal
        title={editingNode ? '编辑分类' : parentNode ? '新增子分类' : '新增根分类'}
        open={modalOpen}
        confirmLoading={saveMutation.isPending}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item
            name="name"
            label="分类名称"
            rules={[{ required: true, message: '请输入分类名称' }]}
          >
            <Input placeholder="如：报销管理" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="code"
            label="分类编码"
            extra={editingNode ? '编码创建后不可修改' : codeExtra ?? '创建后不可修改，列表按该编码过滤'}
            rules={[
              { required: !editingNode, message: '请输入分类编码' },
              { pattern: /^[a-zA-Z0-9_-]{1,64}$/, message: '仅支持字母/数字/下划线/中划线，长度不超过64' },
            ]}
          >
            <Input placeholder="如：reimbursement" disabled={!!editingNode} maxLength={64} />
          </Form.Item>
          <Form.Item name="parentId" label="父分类">
            <TreeSelect
              placeholder="不选择则为根分类"
              allowClear
              treeData={parentTreeData()}
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item name="sortNum" label="排序号">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
