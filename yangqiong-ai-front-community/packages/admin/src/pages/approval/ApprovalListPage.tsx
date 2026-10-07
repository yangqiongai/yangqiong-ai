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
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type {
  ApprovalStatus,
  PageQuery,
  PendingRequestInfo,
} from '@yangqiong/shared';

const QUERY_KEY = 'approvals';

/**
 * 发布审批资源类型（与后端 ApprovalPublishGate.RESOURCE_TYPE_AGENT_PUBLISH 对齐）
 */
const RESOURCE_TYPE_PUBLISH = 'AGENT_PUBLISH';

const STATUS_META: Record<ApprovalStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已拒绝', color: 'red' },
  TIMEOUT: { text: '已超时', color: 'default' },
};

const STATUS_OPTIONS = Object.entries(STATUS_META).map(([k, v]) => ({
  label: v.text,
  value: k as ApprovalStatus,
}));

const DECISION_OPTIONS: { label: string; value: boolean }[] = [
  { label: '通过', value: true },
  { label: '拒绝', value: false },
];

interface DecisionFormValues {
  approved: boolean;
  rejectReason?: string;
}

interface ApprovalPageQuery extends PageQuery {
  status?: ApprovalStatus;
  resourceType?: string;
}

/**
 * 审批治理视图
 */
export const ApprovalListPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState('all');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [status, setStatus] = useState<ApprovalStatus | undefined>();
  const [resourceType, setResourceType] = useState<string>('');
  const [searchResourceType, setSearchResourceType] = useState<string>('');

  const [decisionTarget, setDecisionTarget] = useState<PendingRequestInfo | null>(null);
  const [decisionOpen, setDecisionOpen] = useState(false);
  const [form] = Form.useForm<DecisionFormValues>();
  const approvedValue = Form.useWatch('approved', form);

  const [detail, setDetail] = useState<PendingRequestInfo | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const queryParams: ApprovalPageQuery = {
    page,
    size,
    keyword,
    status,
    resourceType: activeTab === 'publish' ? RESOURCE_TYPE_PUBLISH : resourceType || undefined,
  };

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, activeTab, page, size, keyword, status, resourceType],
    queryFn: () => api.approval.list(queryParams),
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const handleMutation = useMutation({
    mutationFn: (values: DecisionFormValues) =>
      api.approval.handle({
        requestId: decisionTarget?.requestId as string,
        approved: values.approved,
        rejectReason: values.rejectReason,
      }),
    onSuccess: () => {
      message.success('审批已处理');
      setDecisionOpen(false);
      setDecisionTarget(null);
      form.resetFields();
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '审批失败');
    },
  });

  const openDecision = (record: PendingRequestInfo) => {
    setDecisionTarget(record);
    form.resetFields();
    form.setFieldsValue({ approved: true });
    setDecisionOpen(true);
  };

  const openDetail = (record: PendingRequestInfo) => {
    setDetail(record);
    setDetailOpen(true);
  };

  const handleDecisionSubmit = async () => {
    const values = await form.validateFields();
    handleMutation.mutate(values);
  };

  const columns: ColumnsType<PendingRequestInfo> = [
    { title: '目标名称', dataIndex: 'targetName', ellipsis: true },
    { title: '资源类型', dataIndex: 'resourceType', width: 140 },
    { title: '申请人', dataIndex: 'userId', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v?: ApprovalStatus) => {
        const key = v ?? 'PENDING';
        return <Tag color={STATUS_META[key].color}>{STATUS_META[key].text}</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '处理时间',
      dataIndex: 'resolvedTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 180,
      fixed: 'right',
      render: (_: unknown, record: PendingRequestInfo) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openDetail(record)}>
            详情
          </Button>
          <Button
            type="link"
            size="small"
            disabled={record.status !== 'PENDING'}
            onClick={() => openDecision(record)}
          >
            审批
          </Button>
        </Space>
      ),
    },
  ];

  const handleSearch = () => {
    setKeyword(searchKeyword);
    setResourceType(searchResourceType);
    setPage(1);
  };

  const tableNode = (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="目标名称/请求 ID"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          allowClear
          style={{ width: 200 }}
        />
        {activeTab === 'all' && (
          <Input
            placeholder="资源类型"
            value={searchResourceType}
            onChange={(e) => setSearchResourceType(e.target.value)}
            allowClear
            style={{ width: 160 }}
          />
        )}
        <Select<ApprovalStatus | undefined>
          allowClear
          placeholder="按状态筛选"
          style={{ width: 160 }}
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
          options={STATUS_OPTIONS}
        />
        <Button type="primary" onClick={handleSearch}>
          查询
        </Button>
        <Button
          onClick={() => {
            setSearchKeyword('');
            setKeyword('');
            setSearchResourceType('');
            setResourceType('');
            setStatus(undefined);
            setPage(1);
          }}
        >
          重置
        </Button>
      </Space>

      <Table<PendingRequestInfo>
        rowKey="requestId"
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
    </>
  );

  return (
    <Card title="变更审批">
      <Tabs
        activeKey={activeTab}
        onChange={(key) => {
          setActiveTab(key);
          setPage(1);
        }}
        items={[
          { key: 'all', label: '全部待办', children: tableNode },
          {
            key: 'publish',
            label: '发布审批',
            children: (
              <>
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="Agent 版本发布审批待办（审批通过后发布门禁自动放行；拒绝或超时将拦截发布）"
                />
                {tableNode}
              </>
            ),
          },
        ]}
      />

      <Drawer
        title="审批任务详情 / 处理记录"
        width={640}
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setDetail(null);
        }}
        destroyOnClose
      >
        {detail && (
          <>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="请求 ID" span={2}>
                {detail.requestId}
              </Descriptions.Item>
              <Descriptions.Item label="目标名称" span={2}>
                {detail.targetName ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="资源类型">
                {detail.resourceType ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_META[detail.status ?? 'PENDING'].color}>
                  {STATUS_META[detail.status ?? 'PENDING'].text}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="申请人">
                {detail.userId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="处理人">
                {detail.resolvedBy ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="会话 ID">
                {detail.sessionId ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="过期时间">
                {detail.expireTime ? formatDate(detail.expireTime) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {detail.createTime ? formatDate(detail.createTime) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="处理时间">
                {detail.resolvedTime ? formatDate(detail.resolvedTime) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="申请原因" span={2}>
                {detail.reason ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="拒绝原因" span={2}>
                {detail.rejectReason ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            {detail.responsePayload && (
              <Card title="业务载荷" size="small" style={{ marginTop: 16 }}>
                <pre style={{ margin: 0, maxHeight: 320, overflow: 'auto' }}>
                  {detail.responsePayload}
                </pre>
              </Card>
            )}
          </>
        )}
      </Drawer>

      <Drawer
        title="审批处理"
        width={480}
        open={decisionOpen}
        onClose={() => {
          setDecisionOpen(false);
          setDecisionTarget(null);
          form.resetFields();
        }}
        destroyOnClose
        extra={
          <Space>
            <Button
              onClick={() => {
                setDecisionOpen(false);
                setDecisionTarget(null);
                form.resetFields();
              }}
            >
              取消
            </Button>
            <Button
              type="primary"
              loading={handleMutation.isPending}
              onClick={handleDecisionSubmit}
            >
              提交
            </Button>
          </Space>
        }
      >
        {decisionTarget && (
          <>
            <Descriptions column={1} size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="目标名称">
                {decisionTarget.targetName ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="资源类型">
                {decisionTarget.resourceType ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="申请人">
                {decisionTarget.userId ?? '-'}
              </Descriptions.Item>
            </Descriptions>
            <Form form={form} layout="vertical">
              <Form.Item
                name="approved"
                label="审批决策"
                rules={[{ required: true, message: '请选择审批决策' }]}
              >
                <Select options={DECISION_OPTIONS} />
              </Form.Item>
              <Form.Item
                name="rejectReason"
                label="拒绝原因 / 审批意见"
                rules={
                  approvedValue === false
                    ? [{ required: true, message: '拒绝时必须填写原因' }]
                    : []
                }
              >
                <Input.TextArea rows={4} placeholder="拒绝时必填原因；通过时可填写审批意见" />
              </Form.Item>
            </Form>
          </>
        )}
      </Drawer>
    </Card>
  );
};
