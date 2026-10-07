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
  Drawer,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { formatDate } from '@yangqiong/shared';
import { mcpEcosystemApi } from '@/services/mcp-ecosystem-api';
import type { AgentMemoryEntryInfo } from '@/services/mcp-ecosystem-api';

const { Title, Text, Paragraph } = Typography;

const MEMORY_TYPE_OPTIONS = [
  { label: '情景记忆（EPISODIC）', value: 'EPISODIC', color: 'blue' },
  { label: '语义记忆（SEMANTIC）', value: 'SEMANTIC', color: 'purple' },
  { label: '程序记忆（PROCEDURAL）', value: 'PROCEDURAL', color: 'cyan' },
];

const MEMORY_TYPE_COLOR: Record<string, string> = MEMORY_TYPE_OPTIONS.reduce(
  (acc, item) => {
    acc[item.value] = item.color;
    return acc;
  },
  {} as Record<string, string>
);

const STATUS_OPTIONS = [
  { label: '生效（ACTIVE）', value: 'ACTIVE', color: 'green' },
  { label: '归档（STALE）', value: 'STALE', color: 'gold' },
  { label: '隔离（QUARANTINED）', value: 'QUARANTINED', color: 'red' },
  { label: '已擦除（ERASED）', value: 'ERASED', color: 'default' },
];

const STATUS_COLOR: Record<string, string> = STATUS_OPTIONS.reduce(
  (acc, item) => {
    acc[item.value] = item.color;
    return acc;
  },
  {} as Record<string, string>
);

const STATUS_LABEL: Record<string, string> = STATUS_OPTIONS.reduce(
  (acc, item) => {
    acc[item.value] = item.label;
    return acc;
  },
  {} as Record<string, string>
);

interface MemoryQuery {
  agentCode?: string;
  memoryType?: string;
  status?: string;
  page: number;
  size: number;
}

/**
 * Agent 运行记忆（社区裁剪版：仅基础查询与归档处置）
 */
export const AgentMemoryBasePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<MemoryQuery>({ page: 1, size: 10 });
  const [detail, setDetail] = useState<AgentMemoryEntryInfo | null>(null);

  const { data: pageData, isLoading, isFetching } = useQuery({
    queryKey: ['agent-memory-list', query],
    queryFn: () =>
      mcpEcosystemApi.agentMemory.list({
        agentCode: query.agentCode,
        memoryType: query.memoryType,
        status: query.status,
        page: query.page,
        size: query.size,
      }),
  });

  // 社区版仅保留归档处置（企业版合规擦除/隔离治理/负反馈不在本页提供）
  const archiveMutation = useMutation({
    mutationFn: (id: number | undefined) => mcpEcosystemApi.agentMemory.archive(id!),
    onSuccess: () => {
      message.success('记忆条目已归档');
      void queryClient.invalidateQueries({ queryKey: ['agent-memory-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '归档失败'),
  });

  const columns: ColumnsType<AgentMemoryEntryInfo> = [
    { title: 'Agent 编码', dataIndex: 'agentCode', width: 160, ellipsis: true },
    { title: '用户锚点', dataIndex: 'userAnchor', width: 140, ellipsis: true },
    {
      title: '记忆类型',
      dataIndex: 'memoryType',
      width: 130,
      render: (v: string | undefined) => (v ? <Tag color={MEMORY_TYPE_COLOR[v]}>{v}</Tag> : '-'),
    },
    {
      title: '内容',
      dataIndex: 'content',
      ellipsis: true,
      render: (v: string | undefined, record) => (
        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => setDetail(record)}>
          {v ?? '-'}
        </Button>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 90,
      render: (v?: number) => (typeof v === 'number' ? v.toFixed(2) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v: string | undefined) =>
        v ? <Tag color={STATUS_COLOR[v]}>{STATUS_LABEL[v] ?? v}</Tag> : '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      fixed: 'right',
      render: (_, record) => (
        // 归档操作仅对生效条目开放，按钮显隐由后端状态驱动
        record.status === 'ACTIVE' ? (
          <Popconfirm
            title="确认归档该记忆条目？"
            onConfirm={() => archiveMutation.mutate(record.id)}
          >
            <Button type="link" size="small">
              归档
            </Button>
          </Popconfirm>
        ) : null
      ),
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        记忆条目
      </Title>
      <Card styles={{ body: { paddingTop: 0 } }}>
        <Space wrap style={{ marginBottom: 16, paddingTop: 16 }}>
          <Input.Search
            allowClear
            placeholder="Agent 编码筛选"
            style={{ width: 220 }}
            onSearch={(v) => setQuery((q) => ({ ...q, agentCode: v || undefined, page: 1 }))}
          />
          <Select
            allowClear
            placeholder="记忆类型筛选"
            style={{ width: 200 }}
            options={MEMORY_TYPE_OPTIONS}
            onChange={(v?: string) => setQuery((q) => ({ ...q, memoryType: v, page: 1 }))}
          />
          <Select
            allowClear
            placeholder="状态筛选"
            style={{ width: 180 }}
            options={STATUS_OPTIONS}
            onChange={(v?: string) => setQuery((q) => ({ ...q, status: v, page: 1 }))}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() =>
              void queryClient.invalidateQueries({ queryKey: ['agent-memory-list'] })
            }
          >
            刷新
          </Button>
        </Space>
        <Table<AgentMemoryEntryInfo>
          rowKey={(record) => String(record.id ?? `${record.agentCode}-${record.memoryType}`)}
          columns={columns}
          dataSource={pageData?.records}
          loading={isLoading || isFetching}
          scroll={{ x: 1100 }}
          pagination={{
            current: pageData?.page ?? query.page,
            pageSize: pageData?.size ?? query.size,
            total: pageData?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setQuery((q) => ({ ...q, page, size })),
          }}
        />
      </Card>

      <Drawer
        title="记忆条目详情"
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
        destroyOnClose
      >
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Paragraph>
              <Text strong>Agent：</Text>
              {detail.agentCode ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>用户锚点：</Text>
              {detail.userAnchor ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>记忆类型：</Text>
              {detail.memoryType ? (
                <Tag color={MEMORY_TYPE_COLOR[detail.memoryType]}>{detail.memoryType}</Tag>
              ) : (
                '-'
              )}
            </Paragraph>
            <Paragraph>
              <Text strong>状态：</Text>
              {detail.status ? (
                <Tag color={STATUS_COLOR[detail.status]}>{STATUS_LABEL[detail.status] ?? detail.status}</Tag>
              ) : (
                '-'
              )}
            </Paragraph>
            <Paragraph>
              <Text strong>置信度：</Text>
              {typeof detail.confidence === 'number' ? detail.confidence.toFixed(2) : '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>来源任务：</Text>
              {detail.sourceTaskId ?? '-'}
            </Paragraph>
            <div>
              <Text strong>记忆内容</Text>
              <pre
                style={{
                  background: '#f5f5f5',
                  padding: 12,
                  borderRadius: 6,
                  maxHeight: 280,
                  overflow: 'auto',
                  fontSize: 12,
                  marginTop: 8,
                  whiteSpace: 'pre-wrap',
                }}
              >
                {detail.content ?? '// 无内容'}
              </pre>
            </div>
            <Paragraph>
              <Text strong>创建时间：</Text>
              {detail.createTime ? formatDate(detail.createTime) : '-'}
            </Paragraph>
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default AgentMemoryBasePage;
