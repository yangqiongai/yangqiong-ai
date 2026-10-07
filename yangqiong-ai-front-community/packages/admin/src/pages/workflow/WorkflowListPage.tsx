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
import React, { useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Drawer,
  Dropdown,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { MoreOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '@/services';
import { EnterpriseLockIcon, formatDate } from '@yangqiong/shared';
import {
  formatDuration,
  workflowExecutionStatusLabel,
  workflowStatusGroup,
} from '@yangqiong/shared/workflow';
import type { WorkflowStatusGroup } from '@yangqiong/shared/workflow';
import { JsonViewer } from '@/components/JsonViewer';
import type {
  WorkflowDefinitionEntity,
  WorkflowExecutionHistoryEntity,
  WorkflowNodeTraceEntity,
} from '@yangqiong/shared';

const { Text } = Typography;

const safeParseObject = (v?: string): Record<string, unknown> | undefined => {
  if (!v) return undefined;
  try {
    const parsed = JSON.parse(v);
    return typeof parsed === 'object' && parsed !== null ? parsed : undefined;
  } catch {
    return undefined;
  }
};

const renderDefinitionStatus = (v?: number) => {
  if (v === undefined || v === null) return '-';
  const enabled = v === 1;
  return <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '禁用'}</Tag>;
};

/**
 * 执行状态语义分组对应 antd Tag 颜色
 */
const EXECUTION_STATUS_TAG_COLOR: Record<WorkflowStatusGroup, string> = {
  success: 'success',
  failed: 'error',
  running: 'processing',
  paused: 'warning',
  pending: 'default',
  cancelled: 'default',
  skipped: 'default',
  other: 'default',
};

const renderExecutionStatus = (v?: string) => {
  if (!v) return '-';
  return <Tag color={EXECUTION_STATUS_TAG_COLOR[workflowStatusGroup(v)]}>{workflowExecutionStatusLabel(v)}</Tag>;
};

/**
 * 节点类型中文映射
 */
const NODE_TYPE_META: Record<string, string> = {
  AGENT: '智能体',
  CONDITION: '条件',
  PARALLEL: '并行',
  LOOP: '循环',
  SUBGRAPH: '子流程',
  TRANSFORM: '转换',
  SCRIPT: '脚本',
  HTTP: 'HTTP请求',
  ASSIGN: '赋值',
  APPROVAL: '审批',
  START: '开始',
  END: '结束',
};

const renderNodeType = (v?: string) => {
  if (!v) return '-';
  return NODE_TYPE_META[v.toUpperCase()] ?? v;
};

interface DefinitionFormValues {
  definitionName: string;
  displayName?: string;
  description?: string;
  category?: string;
  remark?: string;
  definitionJson?: string;
}

/**
 * 工作流管理
 */
export const WorkflowListPage: React.FC = () => {
  return (
    <Card title="工作流管理">
      <Tabs
        items={[
          { key: 'definition', label: '流程定义', children: <DefinitionTab /> },
          { key: 'instance', label: '流程实例', children: <InstanceTab /> },
        ]}
      />
    </Card>
  );
};

/**
 * 流程定义 Tab
 */
const DefinitionTab: React.FC = () => {
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<WorkflowDefinitionEntity | null>(null);
  const [form] = Form.useForm<DefinitionFormValues>();
  const [historyRecord, setHistoryRecord] = useState<WorkflowDefinitionEntity | null>(null);
  const [historyPage, setHistoryPage] = useState(1);
  const [historySize, setHistorySize] = useState(10);

  const { data, isLoading } = useQuery({
    queryKey: ['workflow-definition-list', page, size],
    queryFn: () => api.workflow.definition.list({ page, size }),
  });

  const { data: historyData, isLoading: historyLoading } = useQuery({
    queryKey: [
      'workflow-definition-history',
      historyRecord?.definitionName,
      historyPage,
      historySize,
    ],
    queryFn: () =>
      api.workflow.listWorkflowHistory({
        pageNum: historyPage,
        pageSize: historySize,
        definitionName: historyRecord?.definitionName,
      }),
    enabled: !!historyRecord,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: ['workflow-definition-list'] });

  const createMutation = useMutation({
    mutationFn: (values: DefinitionFormValues) =>
      api.workflow.definition.create({
        definitionName: values.definitionName,
        displayName: values.displayName,
        description: values.description,
        category: values.category,
        remark: values.remark,
        definition: safeParseObject(values.definitionJson),
      }),
    onSuccess: () => {
      message.success('创建成功');
      setDrawerOpen(false);
      form.resetFields();
      invalidate();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '创建失败'),
  });

  const updateMutation = useMutation({
    mutationFn: (values: DefinitionFormValues) =>
      api.workflow.definition.update(editing?.definitionName ?? '', {
        displayName: values.displayName,
        description: values.description,
        category: values.category,
        remark: values.remark,
        definition: safeParseObject(values.definitionJson),
      }),
    onSuccess: () => {
      message.success('更新成功');
      setDrawerOpen(false);
      setEditing(null);
      invalidate();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '更新失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (definitionName: string) =>
      api.workflow.definition.delete(definitionName),
    onSuccess: () => {
      message.success('删除成功');
      invalidate();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '删除失败'),
  });

  const toggleMutation = useMutation({
    mutationFn: (definitionName: string) =>
      api.workflow.definition.toggle(definitionName),
    onSuccess: () => {
      message.success('状态已更新');
      invalidate();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '状态更新失败'),
  });

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  };

  const openEdit = (record: WorkflowDefinitionEntity) => {
    setEditing(record);
    form.setFieldsValue({
      definitionName: record.definitionName,
      displayName: record.displayName,
      description: record.description,
      category: record.category,
      remark: record.remark,
      definitionJson: record.definitionJson ?? '',
    });
    setDrawerOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      updateMutation.mutate(values);
    } else {
      createMutation.mutate(values);
    }
  };

  const confirmDelete = (record: WorkflowDefinitionEntity) => {
    modal.confirm({
      title: '确认删除该流程？',
      content: `删除后不可恢复：${record.displayName ?? record.definitionName}`,
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: () => deleteMutation.mutate(record.definitionName),
    });
  };

  const confirmToggle = (record: WorkflowDefinitionEntity) => {
    const enabled = record.status === 1;
    modal.confirm({
      title: `确认${enabled ? '禁用' : '启用'}该流程？`,
      content: `${record.displayName ?? record.definitionName}${enabled ? '禁用后无法再发起执行' : '启用后可正常发起执行'}`,
      onOk: () => toggleMutation.mutate(record.definitionName),
    });
  };

  const columns: ColumnsType<WorkflowDefinitionEntity> = [
    { title: '编码', dataIndex: 'definitionName', width: 160 },
    { title: '名称', dataIndex: 'displayName', ellipsis: true },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    { title: '分类', dataIndex: 'category', width: 120 },
    { title: '版本', dataIndex: 'version', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: number) => renderDefinitionStatus(v),
    },
    { title: '作者', dataIndex: 'createUser', width: 120 },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v: string) => formatDate(v),
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right',
      render: (_: unknown, record: WorkflowDefinitionEntity) => {
        const moreItems: MenuProps['items'] = [
          { key: 'edit', label: '编辑', onClick: () => openEdit(record) },
          {
            // 定时任务为企业版专属：社区版入口禁用置灰，企业版经fork列表页承载启用态
            key: 'schedule',
            label: <span>定时设置 <EnterpriseLockIcon style={{ color: '#d48806' }} /></span>,
            disabled: true,
          },
          {
            key: 'history',
            label: '历史',
            onClick: () => {
              setHistoryRecord(record);
              setHistoryPage(1);
            },
          },
          {
            key: 'toggle',
            label: record.status === 1 ? '禁用' : '启用',
            onClick: () => confirmToggle(record),
          },
          { type: 'divider' },
          { key: 'delete', label: '删除', danger: true, onClick: () => confirmDelete(record) },
        ];
        return (
          <Space size="small">
            <Button
              type="link"
              size="small"
              onClick={() =>
                navigate(
                  `/workflow/${record.definitionName}/edit?displayName=${encodeURIComponent(
                    record.displayName ?? '',
                  )}`,
                )
              }
            >
              设计
            </Button>
            <Dropdown menu={{ items: moreItems }} trigger={['click']}>
              <Button type="link" size="small">
                更多 <MoreOutlined />
              </Button>
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  const historyColumns: ColumnsType<WorkflowExecutionHistoryEntity> = [
    { title: '实例ID', dataIndex: 'instanceId', width: 200, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v?: string) => renderExecutionStatus(v),
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true },
  ];

  return (
    <>
      <div style={{ marginBottom: 16 }}>
        <Button type="primary" onClick={openCreate}>
          新增流程
        </Button>
      </div>
      <Table<WorkflowDefinitionEntity>
        rowKey="definitionName"
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
      <Drawer
        title={editing ? '编辑流程' : '新增流程'}
        width={560}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              onClick={handleSubmit}
            >
              保存
            </Button>
          </Space>
        }
      >
        <Form<DefinitionFormValues> form={form} layout="vertical">
          <Form.Item
            name="definitionName"
            label="编码"
            rules={[{ required: true, message: '请输入编码' }]}
          >
            <Input placeholder="请输入编码" />
          </Form.Item>
          <Form.Item name="displayName" label="名称">
            <Input placeholder="请输入名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Select
              allowClear
              placeholder="请选择分类"
              options={[
                { value: 'ORCHESTRATION', label: '编排' },
                { value: 'REPORT', label: '报表' },
                { value: 'DATA_PIPELINE', label: '数据管道' },
                { value: 'CUSTOM', label: '自定义' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="definitionJson" label="定义 JSON">
            <Input.TextArea
              rows={6}
              placeholder='{"nodes":[],"edges":[]}'
            />
          </Form.Item>
        </Form>
      </Drawer>
      <Drawer
        title={`执行历史：${historyRecord?.displayName ?? historyRecord?.definitionName ?? ''}`}
        width={960}
        open={!!historyRecord}
        onClose={() => setHistoryRecord(null)}
        destroyOnClose
      >
        <Table<WorkflowExecutionHistoryEntity>
          rowKey="id"
          columns={historyColumns}
          dataSource={historyData?.records ?? []}
          loading={historyLoading}
          scroll={{ x: 900 }}
          pagination={{
            current: historyPage,
            pageSize: historySize,
            total: historyData?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setHistoryPage(p);
              setHistorySize(s);
            },
          }}
        />
      </Drawer>
    </>
  );
};

/**
 * 流程实例 Tab
 */
const InstanceTab: React.FC = () => {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [traceRecord, setTraceRecord] = useState<WorkflowExecutionHistoryEntity | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['workflow-instance-list', page, size],
    queryFn: () => api.workflow.history.list({ page, size }),
  });

  const { data: defData } = useQuery({
    queryKey: ['workflow-definition-all'],
    queryFn: () => api.workflow.definition.list({ page: 1, size: 200 }),
  });

  const displayNameMap = useMemo(() => {
    const map = new Map<string, string>();
    (defData?.list ?? []).forEach((d) => {
      if (d.definitionName) {
        map.set(d.definitionName, d.displayName ?? d.definitionName);
      }
    });
    return map;
  }, [defData]);

  const { data: traceData, isLoading: traceLoading } = useQuery({
    queryKey: ['workflow-instance-trace', traceRecord?.instanceId],
    queryFn: () => api.workflow.history.nodes(traceRecord!.instanceId),
    enabled: !!traceRecord,
  });

  const columns: ColumnsType<WorkflowExecutionHistoryEntity> = [
    {
      title: '名称',
      width: 200,
      ellipsis: true,
      render: (_: unknown, record: WorkflowExecutionHistoryEntity) =>
        record.definitionName ? displayNameMap.get(record.definitionName) ?? record.definitionName : '-',
    },
    { title: '流程编码', dataIndex: 'definitionName', width: 160 },
    { title: '实例ID', dataIndex: 'instanceId', width: 200, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v?: string) => renderExecutionStatus(v),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 110,
      render: (v?: number) => formatDuration(v),
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: WorkflowExecutionHistoryEntity) => (
        <Button type="link" size="small" onClick={() => setTraceRecord(record)}>
          查询执行详细
        </Button>
      ),
    },
  ];

  const traceColumns: ColumnsType<WorkflowNodeTraceEntity> = [
    {
      title: '节点',
      dataIndex: 'nodeName',
      ellipsis: true,
      render: (v?: string, r?: WorkflowNodeTraceEntity) => v ?? r?.nodeId ?? '-',
    },
    {
      title: '类型',
      dataIndex: 'nodeType',
      width: 110,
      render: (v?: string) => renderNodeType(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v?: string) => renderExecutionStatus(v),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 110,
      render: (v?: number) => formatDuration(v),
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true },
  ];

  return (
    <>
      <Table<WorkflowExecutionHistoryEntity>
        rowKey="instanceId"
        columns={columns}
        dataSource={data?.list ?? []}
        loading={isLoading}
        scroll={{ x: 1300 }}
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
      <Drawer
        title={`执行详细：${traceRecord ? displayNameMap.get(traceRecord.definitionName ?? '') ?? traceRecord.definitionName ?? '' : ''}`}
        width={960}
        open={!!traceRecord}
        onClose={() => setTraceRecord(null)}
        destroyOnClose
      >
        <Table<WorkflowNodeTraceEntity>
          rowKey={(r) => String(r.id ?? `${r.instanceId}-${r.nodeId}-${r.executionOrder ?? ''}`)}
          columns={traceColumns}
          dataSource={traceData ?? []}
          loading={traceLoading}
          scroll={{ x: 900 }}
          pagination={false}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" style={{ width: '100%' }}>
                <Text type="secondary">输入</Text>
                <JsonViewer value={record.inputData} />
                <Text type="secondary">输出</Text>
                <JsonViewer value={record.outputData} />
              </Space>
            ),
          }}
        />
      </Drawer>
    </>
  );
};
