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
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { ToolConfigInfo } from '@yangqiong/shared';

const QUERY_KEY = 'ai-tool-configs';

const STATUS_LABEL_NUMBER: Record<number, string> = {
  0: '禁用',
  1: '启用',
};

export interface AiToolFormValues {
  toolCode: string;
  toolName: string;
  toolDesc?: string;
  toolClass?: string;
  toolType: string;
  toolCategory?: string;
  toolOrder?: number;
  toolConfig?: string;
  toolStatus?: number;
  remark?: string;
}

/**
 * AI 工具配置列表
 */
export const AiToolConfigPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [searchKeyword, setSearchKeyword] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ToolConfigInfo | null>(null);
  const [form] = Form.useForm<AiToolFormValues>();

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, searchKeyword],
    queryFn: () => api.tool.config.list({ page: 1, size: 200, keyword: searchKeyword }),
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (values: AiToolFormValues) => api.tool.config.create(values),
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
    mutationFn: (values: AiToolFormValues) =>
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
      toolClass: record.toolClass,
      toolType: record.toolType,
      toolCategory: record.toolCategory,
      toolOrder: record.toolOrder,
      toolConfig: record.toolConfig,
      toolStatus: record.toolStatus,
      remark: record.remark,
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
      title: '工具类型',
      dataIndex: 'toolType',
      width: 130,
      render: (v?: string) => (v ? <Tag color="blue">{v}</Tag> : '-'),
    },
    { title: '分类', dataIndex: 'toolCategory', width: 120 },
    { title: '描述', dataIndex: 'toolDesc', ellipsis: true },
    { title: '排序', dataIndex: 'toolOrder', width: 80 },
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
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该 AI 工具配置？"
            onConfirm={() => deleteMutation.mutate(record.toolCode)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="AI 工具配置"
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
          onPressEnter={() => invalidateList()}
        />
        <Button type="primary" onClick={() => invalidateList()}>
          查询
        </Button>
        <Button onClick={() => setSearchKeyword('')}>重置</Button>
      </Space>

      <Table<ToolConfigInfo>
        rowKey="toolCode"
        columns={columns}
        dataSource={data?.list ?? []}
        loading={isLoading}
        scroll={{ x: 1300 }}
        pagination={{
          showSizeChanger: true,
          pageSize: 10,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Modal
        title={editing ? '编辑 AI 工具配置' : '新增 AI 工具配置'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        destroyOnClose
        width={620}
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
          <Form.Item
            name="toolType"
            label="工具类型"
            rules={[{ required: true, message: '请选择工具类型' }]}
          >
            <Select
              options={[
                { label: 'function', value: 'function' },
                { label: 'api', value: 'api' },
                { label: 'retrieval', value: 'retrieval' },
                { label: 'code_interpreter', value: 'code_interpreter' },
              ]}
            />
          </Form.Item>
          <Form.Item name="toolClass" label="工具类">
            <Input placeholder="如 com.example.MyTool" />
          </Form.Item>
          <Form.Item name="toolCategory" label="工具分类">
            <Input placeholder="如 数据查询 / 文件操作" />
          </Form.Item>
          <Form.Item name="toolOrder" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
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
  );
};
