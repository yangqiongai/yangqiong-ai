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
  Form,
  Input,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { DataSourceIngestLog, KnowledgeBase } from '@yangqiong/shared';

interface TextFormValues {
  kbId: string;
  title: string;
  content: string;
}

interface WebpageFormValues {
  kbId: string;
  url: string;
  title: string;
  content: string;
}

interface ApiFormValues {
  kbId: string;
  title: string;
  content: string;
  sourceUrl: string;
}

interface DatabaseFormValues {
  kbId: string;
  title: string;
  content: string;
}

/**
 * 接入方式中文映射
 */
const INGEST_TYPE_META: Record<string, { label: string; color: string }> = {
  TEXT: { label: '文本导入', color: 'blue' },
  WEBPAGE: { label: '网页导入', color: 'cyan' },
  API: { label: 'API 导入', color: 'purple' },
  DATABASE: { label: '数据库导入', color: 'geekblue' },
};

/**
 * 格式化耗时：1秒内显示毫秒，否则显示秒
 */
const formatLogDuration = (v?: number) => {
  if (v == null) return '-';
  return v < 1000 ? `${v}ms` : `${(v / 1000).toFixed(1)}s`;
};

/**
 * 数据源接入管理
 * 后端为 DataSourceIngestController，仅提供导入接口（文本/网页/API/数据库），无 CRUD
 */
export const DataSourcePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  // 接入记录查询条件（记录 Tab 与导入操作共用，导入成功/失败后自动刷新）
  const [logPage, setLogPage] = useState(1);
  const [logPageSize] = useState(10);
  const [logKbFilter, setLogKbFilter] = useState<string | undefined>();
  const [logTypeFilter, setLogTypeFilter] = useState<string | undefined>();
  const invalidateLogs = () =>
    queryClient.invalidateQueries({ queryKey: ['datasource-ingest-logs'] });

  const { data: kbList } = useQuery({
    queryKey: ['kb-list-all'],
    queryFn: () => api.knowledge.kb.list(),
  });

  const { data: logData, isFetching: logFetching, refetch: refetchLogs } = useQuery({
    queryKey: ['datasource-ingest-logs', logPage, logPageSize, logKbFilter, logTypeFilter],
    queryFn: () =>
      api.knowledge.datasource.logs({
        pageNum: logPage,
        pageSize: logPageSize,
        kbId: logKbFilter,
        ingestType: logTypeFilter,
      }),
  });

  const kbOptions = useMemo(
    () =>
      (kbList ?? []).map((k: KnowledgeBase) => ({
        label: `${k.kbName}（${k.kbId}）`,
        value: k.kbId,
      })),
    [kbList],
  );

  const [textForm] = Form.useForm<TextFormValues>();
  const [webpageForm] = Form.useForm<WebpageFormValues>();
  const [apiForm] = Form.useForm<ApiFormValues>();
  const [dbForm] = Form.useForm<DatabaseFormValues>();

  const ingestTextMutation = useMutation({
    mutationFn: (values: TextFormValues) =>
      api.knowledge.datasource.ingestText(values),
    onSuccess: () => {
      message.success('文本导入成功');
      textForm.resetFields();
      invalidateLogs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '导入失败');
      invalidateLogs();
    },
  });

  const ingestWebpageMutation = useMutation({
    mutationFn: (values: WebpageFormValues) =>
      api.knowledge.datasource.ingestWebpage(values),
    onSuccess: () => {
      message.success('网页导入成功');
      webpageForm.resetFields();
      invalidateLogs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '导入失败');
      invalidateLogs();
    },
  });

  const ingestApiMutation = useMutation({
    mutationFn: (values: ApiFormValues) =>
      api.knowledge.datasource.ingestApi(values),
    onSuccess: () => {
      message.success('API 数据导入成功');
      apiForm.resetFields();
      invalidateLogs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '导入失败');
      invalidateLogs();
    },
  });

  const ingestDbMutation = useMutation({
    mutationFn: (values: DatabaseFormValues) =>
      api.knowledge.datasource.ingestDatabase(values),
    onSuccess: () => {
      message.success('数据库数据导入成功');
      dbForm.resetFields();
      invalidateLogs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '导入失败');
      invalidateLogs();
    },
  });

  const kbSelect = () => (
    <Form.Item
      name="kbId"
      label="知识库"
      rules={[{ required: true, message: '请选择知识库' }]}
    >
      <Select options={kbOptions} placeholder="请选择知识库" />
    </Form.Item>
  );

  // 知识库ID→名称映射（记录列表展示用）
  const kbNameById = useMemo(() => {
    const map = new Map<string, string>();
    (kbList ?? []).forEach((k: KnowledgeBase) => map.set(k.kbId, k.kbName));
    return map;
  }, [kbList]);

  const logColumns: ColumnsType<DataSourceIngestLog> = [
    {
      title: '接入时间',
      dataIndex: 'ingestTime',
      width: 165,
      render: (v: string) => formatDate(v),
    },
    {
      title: '方式',
      dataIndex: 'ingestType',
      width: 105,
      render: (v: string) => {
        const meta = INGEST_TYPE_META[v];
        return meta ? <Tag color={meta.color}>{meta.label}</Tag> : <Tag>{v}</Tag>;
      },
    },
    {
      title: '标题',
      dataIndex: 'title',
      ellipsis: true,
    },
    {
      title: '知识库',
      dataIndex: 'kbId',
      width: 180,
      ellipsis: true,
      render: (v?: string) => (v ? `${kbNameById.get(v) ?? ''}（${v}）` : '-'),
    },
    {
      title: '来源URL',
      dataIndex: 'sourceUrl',
      width: 180,
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'success',
      width: 80,
      render: (v?: boolean) => (v ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 80,
      render: (v?: number) => formatLogDuration(v),
    },
    {
      title: '文档ID',
      dataIndex: 'docId',
      width: 150,
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
  ];

  const logTab = (
    <div>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          placeholder="按知识库筛选"
          options={kbOptions}
          value={logKbFilter}
          onChange={(v) => {
            setLogKbFilter(v);
            setLogPage(1);
          }}
          style={{ width: 240 }}
        />
        <Select
          allowClear
          placeholder="按接入方式筛选"
          options={Object.entries(INGEST_TYPE_META).map(([value, m]) => ({
            value,
            label: m.label,
          }))}
          value={logTypeFilter}
          onChange={(v) => {
            setLogTypeFilter(v);
            setLogPage(1);
          }}
          style={{ width: 150 }}
        />
        <Button icon={<ReloadOutlined />} onClick={() => refetchLogs()}>
          刷新
        </Button>
      </Space>
      <Table<DataSourceIngestLog>
        rowKey="id"
        size="small"
        loading={logFetching}
        columns={logColumns}
        dataSource={logData?.list ?? []}
        pagination={{
          current: logPage,
          pageSize: logPageSize,
          total: logData?.total ?? 0,
          showSizeChanger: false,
          onChange: (page) => setLogPage(page),
        }}
        expandable={{
          rowExpandable: (log) => !!log.errorMessage || log.contentSize != null || !!log.userId,
          expandedRowRender: (log) => (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {log.errorMessage && (
                <div>
                  <Typography.Text type="danger">失败原因：</Typography.Text>
                  <Typography.Text>{log.errorMessage}</Typography.Text>
                </div>
              )}
              <div>
                <Typography.Text type="secondary">内容大小：</Typography.Text>
                <Typography.Text>
                  {log.contentSize != null ? `${log.contentSize} 字节` : '-'}
                </Typography.Text>
              </div>
              <div>
                <Typography.Text type="secondary">归属用户：</Typography.Text>
                <Typography.Text>{log.userId || '-'}</Typography.Text>
              </div>
            </div>
          ),
        }}
      />
    </div>
  );

  return (
    <Card title="数据源接入">
      <Tabs
        items={[
          {
            key: 'text',
            label: '文本导入',
            children: (
              <Form form={textForm} layout="vertical" style={{ maxWidth: 600 }}>
                {kbSelect()}
                <Form.Item
                  name="title"
                  label="标题"
                  rules={[{ required: true, message: '请输入标题' }]}
                >
                  <Input placeholder="文档标题" />
                </Form.Item>
                <Form.Item
                  name="content"
                  label="内容"
                  rules={[{ required: true, message: '请输入内容' }]}
                >
                  <Input.TextArea rows={8} placeholder="文本内容" />
                </Form.Item>
                <Form.Item>
                  <Button
                    type="primary"
                    loading={ingestTextMutation.isPending}
                    onClick={() => textForm.validateFields().then((v) => ingestTextMutation.mutate(v))}
                  >
                    导入
                  </Button>
                </Form.Item>
              </Form>
            ),
          },
          {
            key: 'webpage',
            label: '网页导入',
            children: (
              <Form form={webpageForm} layout="vertical" style={{ maxWidth: 600 }}>
                {kbSelect()}
                <Form.Item
                  name="url"
                  label="网页 URL"
                  rules={[{ required: true, message: '请输入 URL' }]}
                >
                  <Input placeholder="https://example.com/page" />
                </Form.Item>
                <Form.Item
                  name="title"
                  label="标题"
                  rules={[{ required: true, message: '请输入标题' }]}
                >
                  <Input placeholder="文档标题" />
                </Form.Item>
                <Form.Item
                  name="content"
                  label="内容"
                  rules={[{ required: true, message: '请输入内容' }]}
                >
                  <Input.TextArea rows={8} placeholder="网页正文内容" />
                </Form.Item>
                <Form.Item>
                  <Button
                    type="primary"
                    loading={ingestWebpageMutation.isPending}
                    onClick={() => webpageForm.validateFields().then((v) => ingestWebpageMutation.mutate(v))}
                  >
                    导入
                  </Button>
                </Form.Item>
              </Form>
            ),
          },
          {
            key: 'api',
            label: 'API 导入',
            children: (
              <Form form={apiForm} layout="vertical" style={{ maxWidth: 600 }}>
                {kbSelect()}
                <Form.Item
                  name="sourceUrl"
                  label="来源 URL"
                  rules={[{ required: true, message: '请输入来源 URL' }]}
                >
                  <Input placeholder="https://api.example.com/data" />
                </Form.Item>
                <Form.Item
                  name="title"
                  label="标题"
                  rules={[{ required: true, message: '请输入标题' }]}
                >
                  <Input placeholder="文档标题" />
                </Form.Item>
                <Form.Item
                  name="content"
                  label="内容"
                  rules={[{ required: true, message: '请输入内容' }]}
                >
                  <Input.TextArea rows={8} placeholder="接口返回数据内容" />
                </Form.Item>
                <Form.Item>
                  <Button
                    type="primary"
                    loading={ingestApiMutation.isPending}
                    onClick={() => apiForm.validateFields().then((v) => ingestApiMutation.mutate(v))}
                  >
                    导入
                  </Button>
                </Form.Item>
              </Form>
            ),
          },
          {
            key: 'database',
            label: '数据库导入',
            children: (
              <Form form={dbForm} layout="vertical" style={{ maxWidth: 600 }}>
                {kbSelect()}
                <Form.Item
                  name="title"
                  label="标题"
                  rules={[{ required: true, message: '请输入标题' }]}
                >
                  <Input placeholder="文档标题" />
                </Form.Item>
                <Form.Item
                  name="content"
                  label="内容"
                  rules={[{ required: true, message: '请输入内容' }]}
                >
                  <Input.TextArea rows={8} placeholder="数据库查询结果内容" />
                </Form.Item>
                <Form.Item>
                  <Button
                    type="primary"
                    loading={ingestDbMutation.isPending}
                    onClick={() => dbForm.validateFields().then((v) => ingestDbMutation.mutate(v))}
                  >
                    导入
                  </Button>
                </Form.Item>
              </Form>
            ),
          },
          {
            key: 'logs',
            label: '接入记录',
            children: logTab,
          },
        ]}
      />
    </Card>
  );
};
