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
import { formatDate } from '@yangqiong/shared';
import type { CategoryTreeNode, McpServerConfigInfo } from '@yangqiong/shared';
import { CategoryTreePanel } from '@/components/CategoryTreePanel';
import type { CategoryTreeService } from '@/components/CategoryTreePanel';

const QUERY_KEY = 'mcp-servers';
const CATEGORY_TREE_QUERY_KEY = 'mcp-category-tree';

const STATUS_LABEL_NUMBER: Record<number, string> = {
  0: '禁用',
  1: '启用',
};

/**
 * MCP分类树数据服务
 */
const mcpCategoryService: CategoryTreeService = {
  tree: () => api.tool.mcp.category.tree(),
  create: (data) => api.tool.mcp.category.create(data),
  update: (id, data) => api.tool.mcp.category.update(id, data),
  remove: (id) => api.tool.mcp.category.delete(id),
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

export interface McpServerFormValues {
  serverCode: string;
  serverName: string;
  transportType: 'stdio' | 'sse' | 'websocket';
  url?: string;
  command?: string;
  args?: string;
  env?: string;
  enabledTools?: string;
  serverStatus?: number;
  remark?: string;
  /**
   * 所属分类（分类树节点code，空为未分类）
   */
  category?: string;
}

/**
 * 将表单中的连接信息（url/command/args/env）编码为 connectionConfig JSON 字符串
 */
const buildConnectionConfig = (
  values: McpServerFormValues,
): string | undefined => {
  const config: Record<string, unknown> = {};
  if (values.url) config.url = values.url;
  if (values.command) config.command = values.command;
  if (values.args) {
    config.args = values.args
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
  }
  if (values.env) {
    try {
      config.env = JSON.parse(values.env);
    } catch {
      // 忽略非法 JSON
    }
  }
  return Object.keys(config).length ? JSON.stringify(config, null, 2) : undefined;
};

/**
 * 将 connectionConfig JSON 字符串解析回表单可编辑的连接信息字段
 */
const parseConnectionConfig = (raw?: string) => {
  const empty = {
    url: undefined,
    command: undefined,
    args: undefined,
    env: undefined,
  };
  if (!raw) return empty;
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>;
    return {
      url: typeof obj.url === 'string' ? obj.url : undefined,
      command: typeof obj.command === 'string' ? obj.command : undefined,
      args: Array.isArray(obj.args)
        ? (obj.args as string[]).join('\n')
        : undefined,
      env: obj.env ? JSON.stringify(obj.env, null, 2) : undefined,
    };
  } catch {
    return empty;
  }
};

/**
 * MCP 服务端列表
 */
export const McpServerListPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  /** 左树选中的过滤编码（分类code / __ungrouped__，undefined=全部） */
  const [categoryCode, setCategoryCode] = useState<string | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<McpServerConfigInfo | null>(null);
  const [form] = Form.useForm<McpServerFormValues>();

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, page, size, keyword, categoryCode],
    queryFn: () => api.tool.mcp.list({ page, size, keyword, categoryCode }),
  });

  // 分类树数据（与左侧分类树面板共享缓存，供表单分类下拉使用）
  const { data: categoryTree } = useQuery({
    queryKey: [CATEGORY_TREE_QUERY_KEY],
    queryFn: api.tool.mcp.category.tree,
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const buildPayload = (values: McpServerFormValues) => {
    const connectionConfig = buildConnectionConfig(values);
    return {
      serverCode: values.serverCode,
      serverName: values.serverName,
      transportType: values.transportType,
      connectionConfig,
      enabledTools: values.enabledTools,
      serverStatus: values.serverStatus,
      remark: values.remark,
      category: values.category,
    };
  };

  const createMutation = useMutation({
    mutationFn: (values: McpServerFormValues) =>
      api.tool.mcp.create(buildPayload(values)),
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
    mutationFn: (values: McpServerFormValues) =>
      api.tool.mcp.update(editing?.serverCode ?? '', buildPayload(values)),
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
    mutationFn: (serverCode: string) => api.tool.mcp.delete(serverCode),
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
    form.setFieldsValue({ transportType: 'sse', serverStatus: 1 });
    setModalOpen(true);
  };

  const openEdit = (record: McpServerConfigInfo) => {
    setEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    const conn = parseConnectionConfig(record.connectionConfig);
    form.setFieldsValue({
      serverCode: record.serverCode,
      serverName: record.serverName,
      transportType: record.transportType as McpServerFormValues['transportType'],
      url: conn.url,
      command: conn.command,
      args: conn.args,
      env: conn.env,
      enabledTools: record.enabledTools,
      serverStatus: record.serverStatus,
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

  const columns: ColumnsType<McpServerConfigInfo> = [
    { title: '名称', dataIndex: 'serverName', ellipsis: true },
    { title: '编码', dataIndex: 'serverCode', width: 160 },
    { title: '分类', dataIndex: 'category', width: 110, ellipsis: true },
    {
      title: '传输方式',
      dataIndex: 'transportType',
      width: 110,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
    { title: '启用工具', dataIndex: 'enabledTools', width: 180, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'serverStatus',
      width: 90,
      render: (v?: number) => (
        <Tag color={v === 1 ? 'green' : 'default'}>
          {STATUS_LABEL_NUMBER[v ?? 0]}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', ellipsis: true },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 140,
      fixed: 'right',
      render: (_: unknown, record: McpServerConfigInfo) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该 MCP 服务端？"
            onConfirm={() => deleteMutation.mutate(record.serverCode)}
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
          title="MCP分类"
          queryKey={CATEGORY_TREE_QUERY_KEY}
          service={mcpCategoryService}
          codeExtra="创建后不可修改，MCP列表按该编码过滤"
          value={categoryCode}
          onChange={(code) => {
            setCategoryCode(code);
            setPage(1);
          }}
        />
      </div>
      <Card
        title="MCP 服务端管理"
        style={{ flex: 1, minWidth: 0 }}
        extra={
          <Button type="primary" onClick={openCreate}>
            新增服务端
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

      <Table<McpServerConfigInfo>
        rowKey="serverCode"
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

      <Modal
        title={editing ? '编辑 MCP 服务端' : '新增 MCP 服务端'}
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
            name="serverName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="请输入名称" />
          </Form.Item>
          <Form.Item
            name="serverCode"
            label="编码"
            rules={[{ required: true, message: '请输入编码' }]}
          >
            <Input placeholder="请输入编码" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="category" label="分类" extra="从分类树中选择，可清空为未分类">
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
            name="transportType"
            label="传输方式"
            rules={[{ required: true, message: '请选择传输方式' }]}
          >
            <Select
              options={[
                { label: 'stdio', value: 'stdio' },
                { label: 'sse', value: 'sse' },
                { label: 'websocket', value: 'websocket' },
              ]}
            />
          </Form.Item>
          <Form.Item name="url" label="URL（写入连接配置）">
            <Input placeholder="http://host:port 或 stdio 标识" />
          </Form.Item>
          <Form.Item name="command" label="启动命令（stdio，写入连接配置）">
            <Input placeholder="如 npx" />
          </Form.Item>
          <Form.Item name="args" label="启动参数（每行一个，写入连接配置）">
            <Input.TextArea
              rows={3}
              placeholder={'-y\n@modelcontextprotocol/server'}
            />
          </Form.Item>
          <Form.Item name="env" label="环境变量 JSON（写入连接配置）">
            <Input.TextArea
              rows={3}
              placeholder='{"API_KEY":"xxx"}'
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Form.Item name="enabledTools" label="启用工具（逗号分隔）">
            <Input placeholder="如 toolA,toolB" />
          </Form.Item>
          <Form.Item name="serverStatus" label="状态">
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
