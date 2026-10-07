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
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type {
  EvalCompareResult,
  EvalDataset,
  EvalDatasetCase,
  EvalRun,
  EvalRunCase,
} from '@yangqiong/shared';

const DATASET_KEY = 'eval-datasets';
const RUN_KEY = 'eval-runs';

/**
 * 数据集状态展示
 */
const DATASET_STATUS_META: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  ENABLED: { label: '启用', color: 'green' },
  DISABLED: { label: '停用', color: 'orange' },
};

/**
 * 运行状态展示
 */
const RUN_STATUS_META: Record<string, { label: string; color: string }> = {
  RUNNING: { label: '运行中', color: 'processing' },
  PASSED: { label: '已通过', color: 'green' },
  FAILED: { label: '未通过', color: 'red' },
  ERROR: { label: '异常', color: 'red' },
  CANCELLED: { label: '已取消', color: 'default' },
};

/**
 * 运行是否为终态
 */
const isRunTerminal = (status?: string): boolean =>
  status === 'PASSED' || status === 'FAILED' || status === 'ERROR' || status === 'CANCELLED';

interface DatasetFormValues {
  datasetCode: string;
  name: string;
  description?: string;
  status?: string;
}

interface TriggerFormValues {
  datasetId: number;
  agentCode?: string;
  threshold?: number;
}

interface CaseFormValues {
  caseNo: string;
  title?: string;
  queryText: string;
  expectedOutput?: string;
  scoringCriteria?: string;
  bodyJson?: string;
}

/**
 * 评分策略选项
 */
const SCORING_OPTIONS = [
  { label: 'exact_match（精确匹配）', value: 'exact_match' },
  { label: 'contains_all（包含全部关键词）', value: 'contains_all' },
  { label: 'fuzzy_match（模糊匹配）', value: 'fuzzy_match' },
  { label: 'schema_match（结构化校验）', value: 'schema_match' },
  { label: 'llm_judge（LLM 裁判）', value: 'llm_judge' },
];

/**
 * 校验JSON并格式化，非法时返回原文
 * @param text
 * @return
 */
const prettyJson = (text?: string): string => {
  if (!text) return '-';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
};

/**
 * 数据集管理页签
 */
const DatasetTab: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<EvalDataset | null>(null);
  const [form] = Form.useForm<DatasetFormValues>();

  const [casesTarget, setCasesTarget] = useState<EvalDataset | null>(null);
  const [caseModalOpen, setCaseModalOpen] = useState(false);
  const [caseEditing, setCaseEditing] = useState<EvalDatasetCase | null>(null);
  const [caseForm] = Form.useForm<CaseFormValues>();

  const { data, isLoading } = useQuery({
    queryKey: [DATASET_KEY, page, size, keyword],
    queryFn: () => api.evaluation.dataset.page({ pageNum: page, pageSize: size, keyword }),
  });

  const casesQuery = useQuery({
    queryKey: [`${DATASET_KEY}-cases`, casesTarget?.id],
    queryFn: () => api.evaluation.dataset.cases(casesTarget!.id!),
    enabled: !!casesTarget?.id,
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [DATASET_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (values: DatasetFormValues) => api.evaluation.dataset.create(values),
    onSuccess: () => {
      message.success('创建成功');
      closeDrawer();
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: DatasetFormValues) =>
      api.evaluation.dataset.update(editing!.id!, values),
    onSuccess: () => {
      message.success('更新成功');
      closeDrawer();
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.evaluation.dataset.delete(id),
    onSuccess: () => {
      message.success('删除成功');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditing(null);
  };

  /**
   * 保存用例：合并进现有用例列表后整体替换
   */
  const saveCaseMutation = useMutation({
    mutationFn: async (values: CaseFormValues) => {
      const target = casesTarget!;
      const existing = casesQuery.data ?? [];
      const cases: EvalDatasetCase[] = caseEditing
        ? existing.map((c) => (c.caseNo === caseEditing.caseNo ? { ...c, ...values } : c))
        : [...existing, { ...values }];
      return api.evaluation.dataset.update(target.id!, {
        datasetCode: target.datasetCode,
        name: target.name,
        description: target.description,
        status: target.status,
        cases,
      });
    },
    onSuccess: () => {
      message.success(caseEditing ? '用例已更新' : '用例已新增');
      closeCaseModal();
      queryClient.invalidateQueries({ queryKey: [`${DATASET_KEY}-cases`] });
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存用例失败');
    },
  });

  const closeCaseModal = () => {
    setCaseModalOpen(false);
    setCaseEditing(null);
  };

  const openCaseCreate = () => {
    setCaseEditing(null);
    caseForm.resetFields();
    setCaseModalOpen(true);
  };

  const openCaseEdit = (record: EvalDatasetCase) => {
    setCaseEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    caseForm.resetFields();
    caseForm.setFieldsValue({
      caseNo: record.caseNo,
      title: record.title,
      queryText: record.queryText,
      expectedOutput: record.expectedOutput,
      scoringCriteria: record.scoringCriteria,
      bodyJson: record.bodyJson,
    });
    setCaseModalOpen(true);
  };

  const handleSubmitCase = async () => {
    const values = await caseForm.validateFields();
    // 新增时校验用例号不与现有用例重复
    if (!caseEditing && (casesQuery.data ?? []).some((c) => c.caseNo === values.caseNo)) {
      caseForm.setFields([{ name: 'caseNo', errors: ['用例号已存在'] }]);
      return;
    }
    saveCaseMutation.mutate(values);
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setDrawerOpen(true);
  };

  const openEdit = (record: EvalDataset) => {
    setEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      datasetCode: record.datasetCode,
      name: record.name,
      description: record.description,
      status: record.status,
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

  const handleSearch = () => {
    setKeyword(searchKeyword);
    setPage(1);
  };

  const columns: ColumnsType<EvalDataset> = [
    { title: '编码', dataIndex: 'datasetCode', width: 180, ellipsis: true },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const meta = DATASET_STATUS_META[v ?? ''] ?? { label: v ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '用例数', dataIndex: 'caseCount', width: 90 },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      render: (_: unknown, record: EvalDataset) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => setCasesTarget(record)}>
            用例
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该数据集？"
            onConfirm={() => deleteMutation.mutate(record.id!)}
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
    <>
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
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新增数据集
        </Button>
      </Space>

      <Table<EvalDataset>
        rowKey="id"
        columns={columns}
        dataSource={data?.list ?? []}
        loading={isLoading}
        scroll={{ x: 1000 }}
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
        title={editing ? '编辑数据集' : '新增数据集'}
        width={520}
        open={drawerOpen}
        onClose={closeDrawer}
        destroyOnClose
        extra={
          <Space>
            <Button onClick={closeDrawer}>取消</Button>
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
        <Form form={form} layout="vertical">
          <Form.Item
            name="datasetCode"
            label="编码"
            rules={[{ required: true, message: '请输入数据集编码' }]}
            extra={editing ? '编码不可修改' : 'Scope 内唯一'}
          >
            <Input placeholder="请输入数据集编码" disabled={!!editing} />
          </Form.Item>
          <Form.Item
            name="name"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="请输入数据集名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="数据集描述" />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="DRAFT">
            <Select
              options={[
                { label: '草稿', value: 'DRAFT' },
                { label: '启用', value: 'ENABLED' },
                { label: '停用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        title={`用例列表 - ${casesTarget?.name ?? ''}`}
        width={720}
        open={!!casesTarget}
        onClose={() => setCasesTarget(null)}
        destroyOnClose
        extra={
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCaseCreate}>
            新增用例
          </Button>
        }
      >
        <Table<EvalDatasetCase>
          rowKey="caseNo"
          loading={casesQuery.isLoading}
          dataSource={casesQuery.data ?? []}
          pagination={false}
          scroll={{ x: 760 }}
          columns={[
            { title: '用例号', dataIndex: 'caseNo', width: 110 },
            { title: '标题', dataIndex: 'title', ellipsis: true },
            { title: '输入', dataIndex: 'queryText', ellipsis: true },
            {
              title: '期望输出',
              dataIndex: 'expectedOutput',
              ellipsis: true,
              render: (v?: string) => v ?? '-',
            },
            {
              title: '评分标准',
              dataIndex: 'scoringCriteria',
              ellipsis: true,
              render: (v?: string) => v ?? '-',
            },
            {
              title: '操作',
              width: 80,
              fixed: 'right',
              render: (_: unknown, record: EvalDatasetCase) => (
                <Button type="link" size="small" onClick={() => openCaseEdit(record)}>
                  编辑
                </Button>
              ),
            },
          ]}
          expandable={{
            expandedRowRender: (record) =>
              record.bodyJson ? (
                <pre style={{ margin: 0, maxHeight: 240, overflow: 'auto' }}>
                  {prettyJson(record.bodyJson)}
                </pre>
              ) : (
                <Typography.Text type="secondary">无请求体</Typography.Text>
              ),
            rowExpandable: (record) => !!record.bodyJson,
          }}
        />
      </Drawer>

      <Modal
        title={caseEditing ? `编辑用例 - ${caseEditing.caseNo}` : '新增用例'}
        width={560}
        open={caseModalOpen}
        onCancel={closeCaseModal}
        destroyOnClose
        footer={
          <Space>
            <Button onClick={closeCaseModal}>取消</Button>
            <Button type="primary" loading={saveCaseMutation.isPending} onClick={handleSubmitCase}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={caseForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="caseNo"
              label="用例号"
              rules={[{ required: true, message: '请输入用例号' }]}
              extra={caseEditing ? '用例号不可修改' : '数据集内唯一'}
              style={{ width: 180 }}
            >
              <Input placeholder="如 C001" disabled={!!caseEditing} />
            </Form.Item>
            <Form.Item name="title" label="标题" style={{ flex: 1, minWidth: 280 }}>
              <Input placeholder="用例标题" />
            </Form.Item>
          </Space>
          <Form.Item
            name="queryText"
            label="输入内容"
            rules={[{ required: true, message: '请输入用例输入内容' }]}
          >
            <Input.TextArea rows={4} placeholder="发送给 Agent 的输入" />
          </Form.Item>
          <Form.Item name="expectedOutput" label="期望输出">
            <Input.TextArea rows={3} placeholder="用于评分的期望输出（如逗号分隔的关键词）" />
          </Form.Item>
          <Form.Item name="scoringCriteria" label="评分策略" initialValue="contains_all">
            <Select options={SCORING_OPTIONS} />
          </Form.Item>
          <Form.Item
            name="bodyJson"
            label="请求体(JSON, 可选)"
            rules={[
              {
                validator: (_rule, value?: string) => {
                  if (!value) return Promise.resolve();
                  try {
                    JSON.parse(value);
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(new Error('请输入合法的JSON'));
                  }
                },
              },
            ]}
          >
            <Input.TextArea rows={3} placeholder='{"agentCode": "xxx"}' />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/**
 * 运行记录页签
 */
const RunTab: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [agentCode, setAgentCode] = useState<string | undefined>();
  const [status, setStatus] = useState<string | undefined>();

  const [triggerOpen, setTriggerOpen] = useState(false);
  const [triggerForm] = Form.useForm<TriggerFormValues>();

  const [detailRun, setDetailRun] = useState<EvalRun | null>(null);
  const [casesRunId, setCasesRunId] = useState<number | null>(null);
  const [casesPage, setCasesPage] = useState(1);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: [RUN_KEY, page, size, agentCode, status],
    queryFn: () => api.evaluation.run.page({ pageNum: page, pageSize: size, agentCode, status }),
    refetchInterval: (query) => {
      const rows = query.state.data?.list;
      return rows?.some((r) => r.status === 'RUNNING') ? 5000 : false;
    },
  });

  const datasetsQuery = useQuery({
    queryKey: [`${DATASET_KEY}-all`],
    queryFn: () => api.evaluation.dataset.page({ pageNum: 1, pageSize: 100 }),
    enabled: triggerOpen,
  });

  const agentsQuery = useQuery({
    queryKey: [`${RUN_KEY}-agents`],
    queryFn: () => api.agent.type.list({ page: 1, size: 500 }),
    enabled: triggerOpen,
  });

  const casesQuery = useQuery({
    queryKey: [`${RUN_KEY}-cases`, casesRunId, casesPage],
    queryFn: () =>
      api.evaluation.run.cases(casesRunId!, { pageNum: casesPage, pageSize: 10 }),
    enabled: !!casesRunId,
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [RUN_KEY] });
  };

  const triggerMutation = useMutation({
    mutationFn: (values: TriggerFormValues) => api.evaluation.run.trigger(values),
    onSuccess: (res) => {
      message.success(`已触发评测运行 #${res.runId}`);
      setTriggerOpen(false);
      setPage(1);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '触发失败');
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => api.evaluation.run.cancel(id),
    onSuccess: () => {
      message.success('已取消');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '取消失败');
    },
  });

  const openTrigger = () => {
    triggerForm.resetFields();
    setTriggerOpen(true);
  };

  const openDetail = async (runId: number) => {
    try {
      const run = await api.evaluation.run.get(runId, true);
      setDetailRun(run);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '查询运行详情失败');
    }
  };

  const handleTrigger = async () => {
    const values = await triggerForm.validateFields();
    triggerMutation.mutate(values);
  };

  const columns: ColumnsType<EvalRun> = [
    { title: '运行ID', dataIndex: 'id', width: 90 },
    { title: '数据集', dataIndex: 'datasetCode', width: 160, ellipsis: true },
    { title: 'Agent', dataIndex: 'agentCode', width: 150, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v?: string) => {
        const meta = RUN_STATUS_META[v ?? ''] ?? { label: v ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '通过/总数',
      width: 110,
      render: (_: unknown, r: EvalRun) => `${r.passedCases ?? 0}/${r.totalCases ?? 0}`,
    },
    {
      title: '平均分',
      dataIndex: 'avgScore',
      width: 90,
      render: (v?: number) => (v != null ? Number(v).toFixed(1) : '-'),
    },
    {
      title: '阈值',
      dataIndex: 'threshold',
      width: 80,
      render: (v?: number) => v ?? '-',
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '结束时间',
      dataIndex: 'finishedAt',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 170,
      fixed: 'right',
      render: (_: unknown, record: EvalRun) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openDetail(record.id!)}>
            详情
          </Button>
          <Button type="link" size="small" onClick={() => {
            setCasesRunId(record.id!);
            setCasesPage(1);
          }}>
            用例
          </Button>
          {record.status === 'RUNNING' && (
            <Popconfirm title="确认取消该运行？" onConfirm={() => cancelMutation.mutate(record.id!)}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="Agent 编码"
          value={agentCode}
          onChange={(e) => {
            setAgentCode(e.target.value || undefined);
            setPage(1);
          }}
          allowClear
          style={{ width: 180 }}
        />
        <Select
          placeholder="状态"
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          allowClear
          style={{ width: 140 }}
          options={Object.entries(RUN_STATUS_META).map(([value, meta]) => ({
            label: meta.label,
            value,
          }))}
        />
        <Button icon={<ReloadOutlined />} loading={isFetching} onClick={() => invalidateList()}>
          刷新
        </Button>
        <Button type="primary" onClick={openTrigger}>
          触发评测
        </Button>
      </Space>

      <Table<EvalRun>
        rowKey="id"
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

      <Modal
        title="触发评测"
        open={triggerOpen}
        onCancel={() => setTriggerOpen(false)}
        onOk={handleTrigger}
        confirmLoading={triggerMutation.isPending}
        destroyOnClose
      >
        <Form form={triggerForm} layout="vertical">
          <Form.Item
            name="datasetId"
            label="评测数据集"
            rules={[{ required: true, message: '请选择数据集' }]}
          >
            <Select
              placeholder="请选择数据集"
              loading={datasetsQuery.isLoading}
              showSearch
              optionFilterProp="label"
              options={(datasetsQuery.data?.list ?? []).map((d) => ({
                label: `${d.name} (${d.datasetCode})`,
                value: d.id!,
              }))}
            />
          </Form.Item>
          <Form.Item name="agentCode" label="Agent" extra="留空使用默认 Agent">
            <Select
              placeholder="请选择 Agent"
              allowClear
              loading={agentsQuery.isLoading}
              showSearch
              filterOption={(input, option) =>
                String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
              }
              options={(agentsQuery.data?.list ?? []).map((a) => ({
                label: `${a.typeName} (${a.typeCode})`,
                value: a.typeCode,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="threshold"
            label="通过阈值"
            extra="0-100，留空使用数据集默认阈值"
          >
            <InputNumber min={0} max={100} style={{ width: '100%' }} placeholder="80" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={`运行详情 #${detailRun?.id ?? ''}`}
        width={680}
        open={!!detailRun}
        onClose={() => setDetailRun(null)}
        destroyOnClose
      >
        {detailRun && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="状态">
                {(() => {
                  const meta = RUN_STATUS_META[detailRun.status] ?? {
                    label: detailRun.status,
                    color: 'default',
                  };
                  return <Tag color={meta.color}>{meta.label}</Tag>;
                })()}
              </Descriptions.Item>
              <Descriptions.Item label="Agent">
                {detailRun.agentCode ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="数据集">
                {detailRun.datasetCode ?? detailRun.datasetId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Judge 模型">
                {detailRun.judgeModelCode ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="通过/总数">
                {detailRun.passedCases ?? 0}/{detailRun.totalCases ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label="平均分">
                {detailRun.avgScore != null ? Number(detailRun.avgScore).toFixed(1) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="阈值">
                {detailRun.threshold ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="开始时间">
                {detailRun.startedAt ? formatDate(detailRun.startedAt) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="结束时间">
                {detailRun.finishedAt ? formatDate(detailRun.finishedAt) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">
                {detailRun.startedAt && detailRun.finishedAt
                  ? `${Math.max(
                      0,
                      new Date(detailRun.finishedAt).getTime() -
                        new Date(detailRun.startedAt).getTime(),
                    )}ms`
                  : '-'}
              </Descriptions.Item>
            </Descriptions>
            {detailRun.errorMessage && (
              <Card size="small" title="错误信息" style={{ marginTop: 16 }}>
                <Typography.Text type="danger">{detailRun.errorMessage}</Typography.Text>
              </Card>
            )}
            {detailRun.reportJson && (
              <Card title="评测报告" size="small" style={{ marginTop: 16 }}>
                <pre style={{ margin: 0, maxHeight: 360, overflow: 'auto' }}>
                  {prettyJson(detailRun.reportJson)}
                </pre>
              </Card>
            )}
          </>
        )}
      </Drawer>

      <Drawer
        title={`运行用例明细 #${casesRunId ?? ''}`}
        width={860}
        open={!!casesRunId}
        onClose={() => setCasesRunId(null)}
        destroyOnClose
      >
        <Table<EvalRunCase>
          rowKey="id"
          loading={casesQuery.isLoading}
          dataSource={casesQuery.data?.list ?? []}
          scroll={{ x: 780 }}
          pagination={{
            current: casesPage,
            pageSize: 10,
            total: casesQuery.data?.total ?? 0,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p) => setCasesPage(p),
          }}
          columns={[
            { title: '用例号', dataIndex: 'caseNo', width: 100 },
            {
              title: '得分',
              dataIndex: 'score',
              width: 80,
              render: (v?: number) => (v != null ? Number(v).toFixed(1) : '-'),
            },
            {
              title: '通过',
              dataIndex: 'passed',
              width: 80,
              render: (v?: number) =>
                v === 1 ? <Tag color="green">通过</Tag> : v === 0 ? <Tag color="red">未通过</Tag> : '-',
            },
            {
              title: '评分策略',
              dataIndex: 'strategyName',
              width: 120,
              render: (v?: string) => v ?? '-',
            },
            {
              title: '耗时(ms)',
              dataIndex: 'durationMs',
              width: 100,
              render: (v?: number) => v ?? '-',
            },
            {
              title: '错误',
              dataIndex: 'errorMessage',
              ellipsis: true,
              render: (v?: string) => (v ? <Tooltip title={v}><Typography.Text type="danger">查看</Typography.Text></Tooltip> : '-'),
            },
          ]}
          expandable={{
            expandedRowRender: (record) => (
              <Space direction="vertical" style={{ width: '100%' }} size={8}>
                <div>
                  <Typography.Text type="secondary">期望输出：</Typography.Text>
                  <pre style={{ margin: 0, maxHeight: 160, overflow: 'auto' }}>
                    {record.expectedOutput ?? '-'}
                  </pre>
                </div>
                <div>
                  <Typography.Text type="secondary">实际输出：</Typography.Text>
                  <pre style={{ margin: 0, maxHeight: 160, overflow: 'auto' }}>
                    {record.actualOutput ?? '-'}
                  </pre>
                </div>
              </Space>
            ),
          }}
        />
      </Drawer>
    </>
  );
};

/**
 * 运行对比页签
 */
const CompareTab: React.FC = () => {
  const { message } = App.useApp();

  const [leftId, setLeftId] = useState<number | undefined>();
  const [rightId, setRightId] = useState<number | undefined>();
  const [compareResult, setCompareResult] = useState<EvalCompareResult | null>(null);

  const runsQuery = useQuery({
    queryKey: [`${RUN_KEY}-options`],
    queryFn: () => api.evaluation.run.page({ pageNum: 1, pageSize: 100 }),
  });

  const runOptions = (runsQuery.data?.list ?? [])
    .filter((r) => isRunTerminal(r.status))
    .map((r) => ({
      label: `#${r.id} ${r.datasetCode ?? ''} ${RUN_STATUS_META[r.status]?.label ?? r.status} 均分${r.avgScore != null ? Number(r.avgScore).toFixed(1) : '-'}`,
      value: r.id!,
    }));

  const compareMutation = useMutation({
    mutationFn: () => api.evaluation.run.compare(leftId!, rightId!),
    onSuccess: (res) => {
      setCompareResult(res);
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '对比失败');
    },
  });

  const handleCompare = () => {
    if (!leftId || !rightId) {
      message.warning('请选择两次运行');
      return;
    }
    if (leftId === rightId) {
      message.warning('两次运行不能相同');
      return;
    }
    compareMutation.mutate();
  };

  const renderDelta = (v?: number | null) => {
    if (v == null) return '-';
    const num = Number(v);
    if (num > 0) return <Typography.Text type="success">+{num.toFixed(1)}</Typography.Text>;
    if (num < 0) return <Typography.Text type="danger">{num.toFixed(1)}</Typography.Text>;
    return '0.0';
  };

  const columns: ColumnsType<EvalCompareResult['caseMatrix'][number]> = [
    { title: '用例号', dataIndex: 'caseNo', width: 110 },
    {
      title: '左侧得分',
      dataIndex: 'scoreLeft',
      width: 100,
      render: (v?: number | null) => (v != null ? Number(v).toFixed(1) : '-'),
    },
    {
      title: '右侧得分',
      dataIndex: 'scoreRight',
      width: 100,
      render: (v?: number | null) => (v != null ? Number(v).toFixed(1) : '-'),
    },
    {
      title: '分差',
      dataIndex: 'scoreDelta',
      width: 90,
      render: (v?: number | null) => renderDelta(v),
    },
    {
      title: '左侧通过',
      dataIndex: 'passedLeft',
      width: 100,
      render: (v?: number | null) =>
        v === 1 ? <Tag color="green">通过</Tag> : v === 0 ? <Tag color="red">未通过</Tag> : '-',
    },
    {
      title: '右侧通过',
      dataIndex: 'passedRight',
      width: 100,
      render: (v?: number | null) =>
        v === 1 ? <Tag color="green">通过</Tag> : v === 0 ? <Tag color="red">未通过</Tag> : '-',
    },
    {
      title: '通过状态变化',
      dataIndex: 'passChanged',
      width: 120,
      render: (v?: boolean | null) =>
        v ? <Tag color="orange">有变化</Tag> : <Tag>无变化</Tag>,
    },
  ];

  const summary = compareResult?.summaryDiff;

  return (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          placeholder="左侧运行"
          value={leftId}
          onChange={setLeftId}
          showSearch
          optionFilterProp="label"
          popupMatchSelectWidth={false}
          style={{ width: 320 }}
          loading={runsQuery.isLoading}
          options={runOptions}
        />
        <Select
          placeholder="右侧运行"
          value={rightId}
          onChange={setRightId}
          showSearch
          optionFilterProp="label"
          popupMatchSelectWidth={false}
          style={{ width: 320 }}
          loading={runsQuery.isLoading}
          options={runOptions}
        />
        <Button
          type="primary"
          loading={compareMutation.isPending}
          onClick={handleCompare}
        >
          对比
        </Button>
      </Space>

      {compareResult && summary && (
        <>
          <Card title="整体指标对比" size="small" style={{ marginBottom: 16 }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="平均分">
                {summary.avgScoreLeft != null ? Number(summary.avgScoreLeft).toFixed(1) : '-'} →{' '}
                {summary.avgScoreRight != null ? Number(summary.avgScoreRight).toFixed(1) : '-'}
                （{renderDelta(summary.avgScoreDelta)}）
              </Descriptions.Item>
              <Descriptions.Item label="通过用例">
                {summary.passedCasesLeft ?? '-'} → {summary.passedCasesRight ?? '-'}
                （{renderDelta(summary.passedCasesDelta)}）
              </Descriptions.Item>
              <Descriptions.Item label="失败用例">
                {summary.failedCasesLeft ?? '-'} → {summary.failedCasesRight ?? '-'}
                （{renderDelta(summary.failedCasesDelta)}）
              </Descriptions.Item>
              <Descriptions.Item label="总用例">
                {summary.totalCasesLeft ?? '-'} → {summary.totalCasesRight ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="左侧状态">
                {summary.statusLeft ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="右侧状态">
                {summary.statusRight ?? '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
          <Card title="用例级对比" size="small">
            <Table
              rowKey="caseNo"
              columns={columns}
              dataSource={compareResult.caseMatrix ?? []}
              pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
              scroll={{ x: 700 }}
            />
          </Card>
        </>
      )}
      {!compareResult && (
        <Typography.Text type="secondary">
          选择两次已完成的运行进行对比，右侧减左侧为正表示改善
        </Typography.Text>
      )}
    </>
  );
};

/**
 * 评测管理
 */
export const EvaluationPage: React.FC = () => {
  return (
    <Card title="评测管理">
      <Tabs
        defaultActiveKey="datasets"
        items={[
          { key: 'datasets', label: '数据集', children: <DatasetTab /> },
          { key: 'runs', label: '运行记录', children: <RunTab /> },
          { key: 'compare', label: '运行对比', children: <CompareTab /> },
        ]}
      />
    </Card>
  );
};
