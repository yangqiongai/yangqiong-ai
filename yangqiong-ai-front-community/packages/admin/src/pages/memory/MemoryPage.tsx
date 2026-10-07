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
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { ChatMemoryRecord, PageQuery } from '@yangqiong/shared';

const { Title, Text, Paragraph } = Typography;

/**
 * 记忆检索
 */
export const MemoryPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<PageQuery>({ page: 1, size: 10 });
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const [detail, setDetail] = useState<ChatMemoryRecord | null>(null);

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['memory-list', query],
    queryFn: () => api.memory.list(query),
  });

  const { data: searchResults, isLoading: searchLoading } = useQuery({
    queryKey: ['memory-search', searchKeyword],
    queryFn: () =>
      api.memory.search({
        page: 1,
        size: 50,
        keyword: searchKeyword,
      }),
    enabled: searchKeyword.trim().length > 0,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.memory.delete(id),
    onSuccess: () => {
      message.success('记录删除成功');
      void queryClient.invalidateQueries({ queryKey: ['memory-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '删除失败'),
  });

  const batchDeleteMutation = useMutation({
    mutationFn: (ids: string[]) => api.memory.batchDelete(ids),
    onSuccess: () => {
      message.success('批量删除成功');
      setSelectedKeys([]);
      void queryClient.invalidateQueries({ queryKey: ['memory-list'] });
    },
    onError: (err) =>
      message.error(err instanceof Error ? err.message : '批量删除失败'),
  });

  const columns: ColumnsType<ChatMemoryRecord> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '消息 ID', dataIndex: 'messageId', width: 180, ellipsis: true },
    { title: '会话', dataIndex: 'sessionId', width: 160, ellipsis: true },
    {
      title: '角色',
      dataIndex: 'messageRole',
      width: 90,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
    {
      title: '内容',
      dataIndex: 'messageContent',
      ellipsis: true,
      render: (v?: string) => v ?? '-',
    },
    {
      title: '重要性',
      dataIndex: 'importanceScore',
      width: 90,
      render: (v?: number) => (v != null ? v : '-'),
    },
    {
      title: 'Token',
      dataIndex: 'totalTokens',
      width: 90,
      render: (v?: number) => v ?? '-',
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
      width: 130,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => setDetail(record)}>
            详情
          </Button>
          <Popconfirm
            title="确认删除该记录？"
            onConfirm={() =>
              record.id != null && deleteMutation.mutate(String(record.id))
            }
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const searchColumns: ColumnsType<ChatMemoryRecord> = [
    { title: '消息 ID', dataIndex: 'messageId', width: 180, ellipsis: true },
    { title: '会话', dataIndex: 'sessionId', width: 160, ellipsis: true },
    {
      title: '角色',
      dataIndex: 'messageRole',
      width: 90,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
    {
      title: '内容',
      dataIndex: 'messageContent',
      ellipsis: true,
    },
    {
      title: '重要性',
      dataIndex: 'importanceScore',
      width: 90,
      render: (v?: number) => (v != null ? v : '-'),
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
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => setDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        记忆检索
      </Title>

      <Card title="语义检索" style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16 }}>
          <Input
            placeholder="检索关键字"
            style={{ width: 260 }}
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            allowClear
          />
          <Button
            type="primary"
            icon={<SearchOutlined />}
            disabled={!searchKeyword.trim()}
            loading={searchLoading}
            onClick={() =>
              void queryClient.invalidateQueries({
                queryKey: ['memory-search', searchKeyword],
              })
            }
          >
            检索
          </Button>
        </Space>
        {searchKeyword.trim() ? (
          <Table<ChatMemoryRecord>
            rowKey="id"
            columns={searchColumns}
            dataSource={searchResults}
            loading={searchLoading}
            scroll={{ x: 900 }}
            pagination={{ pageSize: 10, showSizeChanger: true }}
          />
        ) : (
          <Text type="secondary">输入关键字后点击检索进行语义查询。</Text>
        )}
      </Card>

      <Card
        title="记忆列表"
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() =>
                void queryClient.invalidateQueries({ queryKey: ['memory-list'] })
              }
            >
              刷新
            </Button>
            <Popconfirm
              title={`确认批量清除选中的 ${selectedKeys.length} 条记录？`}
              disabled={selectedKeys.length === 0}
              onConfirm={() =>
                batchDeleteMutation.mutate(selectedKeys.map((k) => String(k)))
              }
            >
              <Button
                danger
                icon={<DeleteOutlined />}
                disabled={selectedKeys.length === 0}
                loading={batchDeleteMutation.isPending}
              >
                批量清除（{selectedKeys.length}）
              </Button>
            </Popconfirm>
          </Space>
        }
      >
        <Table<ChatMemoryRecord>
          rowKey="id"
          columns={columns}
          dataSource={data?.records}
          loading={isLoading || isFetching}
          scroll={{ x: 1100 }}
          rowSelection={{
            selectedRowKeys: selectedKeys,
            onChange: (keys) => setSelectedKeys(keys),
          }}
          pagination={{
            current: data?.page ?? query.page,
            pageSize: data?.size ?? query.size,
            total: data?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setQuery((q) => ({ ...q, page, size })),
          }}
        />
      </Card>

      <Drawer
        title="记忆详情"
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
        destroyOnClose
      >
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Paragraph>
              <Text strong>ID：</Text>
              {detail.id ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>消息 ID：</Text>
              {detail.messageId ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>会话：</Text>
              {detail.sessionId ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>用户：</Text>
              {detail.userId ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>角色：</Text>
              {detail.messageRole ? <Tag>{detail.messageRole}</Tag> : '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>重要性评分：</Text>
              {detail.importanceScore ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>Token 总量：</Text>
              {detail.totalTokens ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>输入 Token：</Text>
              {detail.inputTokens ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>输出 Token：</Text>
              {detail.outputTokens ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>执行耗时：</Text>
              {detail.executionTime ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>Scope：</Text>
              {detail.scopeId ?? '-'}
            </Paragraph>
            <Paragraph>
              <Text strong>创建时间：</Text>
              {detail.createTime ? formatDate(detail.createTime) : '-'}
            </Paragraph>
            <div>
              <Text strong>消息内容</Text>
              <pre
                style={{
                  background: '#f5f5f5',
                  padding: 12,
                  borderRadius: 6,
                  maxHeight: 240,
                  overflow: 'auto',
                  fontSize: 12,
                  marginTop: 8,
                  whiteSpace: 'pre-wrap',
                }}
              >
                {detail.messageContent ?? '// 无内容'}
              </pre>
            </div>
            {detail.body && (
              <div>
                <Text strong>正文</Text>
                <pre
                  style={{
                    background: '#f5f5f5',
                    padding: 12,
                    borderRadius: 6,
                    maxHeight: 200,
                    overflow: 'auto',
                    fontSize: 12,
                    marginTop: 8,
                    whiteSpace: 'pre-wrap',
                  }}
                >
                  {detail.body}
                </pre>
              </div>
            )}
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default MemoryPage;
