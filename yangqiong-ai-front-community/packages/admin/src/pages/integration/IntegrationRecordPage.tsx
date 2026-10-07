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
  Button,
  Card,
  Drawer,
  Input,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { isModuleNotEnabledError } from '@/utils/error';
import { formatDate } from '@yangqiong/shared';
import type { IntegrationRecord, PageQuery } from '@yangqiong/shared';

const { Title, Text } = Typography;

interface IntegrationQuery extends PageQuery {
  channel?: string;
}

/**
 * 集成记录
 */
export const IntegrationRecordPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('all');
  const [allQuery, setAllQuery] = useState<IntegrationQuery>({ page: 1, size: 10 });
  const [failedQuery, setFailedQuery] = useState<IntegrationQuery>({ page: 1, size: 10 });
  const [keyword, setKeyword] = useState('');
  const [channel, setChannel] = useState<string | undefined>(undefined);
  const [detail, setDetail] = useState<IntegrationRecord | null>(null);

  const { data: allPage, isLoading: allLoading, isFetching: allFetching, error: allError } = useQuery({
    queryKey: ['integration-record-list', allQuery],
    queryFn: () => api.integration.list(allQuery),
    enabled: activeTab === 'all',
  });

  const { data: failedPage, isLoading: failedLoading, isFetching: failedFetching, error: failedError } = useQuery({
    queryKey: ['integration-record-failed', failedQuery],
    queryFn: () => api.integration.failed(failedQuery),
    enabled: activeTab === 'failed',
  });

  const moduleNotEnabled =
    isModuleNotEnabledError(activeTab === 'all' ? allError : failedError);

  const handleSearch = () => {
    if (activeTab === 'all') {
      setAllQuery((q) => ({
        ...q,
        page: 1,
        keyword: keyword || undefined,
        channel,
      }));
    } else {
      setFailedQuery((q) => ({
        ...q,
        page: 1,
        keyword: keyword || undefined,
        channel,
      }));
    }
  };

  const handleReset = () => {
    setKeyword('');
    setChannel(undefined);
    if (activeTab === 'all') {
      setAllQuery({ page: 1, size: 10 });
    } else {
      setFailedQuery({ page: 1, size: 10 });
    }
  };

  const columns: ColumnsType<IntegrationRecord> = [
    { title: 'ID', dataIndex: 'id', width: 100 },
    { title: '消息 ID', dataIndex: 'messageId', width: 180, ellipsis: true },
    {
      title: '渠道',
      dataIndex: 'channel',
      width: 120,
      render: (v?: string) => (v ? <Tag color="blue">{v}</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
    { title: 'Scope', dataIndex: 'scopeId', width: 140, ellipsis: true },
    {
      title: '发送时间',
      dataIndex: 'sendTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
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
        <Button type="link" size="small" onClick={() => setDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  if (moduleNotEnabled) {
    return (
      <div>
        <Title level={4} style={{ marginBottom: 16 }}>
          集成记录
        </Title>
        <Alert
          type="warning"
          showIcon
          message="集成模块未启用"
          description="当前后端未启用 Integration opt-in 模块（接口返回 404）。请联系管理员开启该模块后再使用本页面。"
        />
      </div>
    );
  }

  const handleRefresh = () => {
    if (activeTab === 'all') {
      void queryClient.invalidateQueries({ queryKey: ['integration-record-list'] });
    } else {
      void queryClient.invalidateQueries({ queryKey: ['integration-record-failed'] });
    }
  };

  const renderToolbar = () => (
    <Space wrap style={{ marginBottom: 16, paddingTop: 16 }}>
      <Input
        placeholder="名称/关键字"
        allowClear
        style={{ width: 200 }}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />
      <Input
        placeholder="渠道筛选"
        allowClear
        style={{ width: 140 }}
        value={channel}
        onChange={(e) => setChannel(e.target.value || undefined)}
      />
      <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
        查询
      </Button>
      <Button icon={<ReloadOutlined />} onClick={handleReset}>
        重置
      </Button>
      <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
        刷新
      </Button>
    </Space>
  );

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        集成记录
      </Title>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'all',
            label: '全部记录',
            children: (
              <Card styles={{ body: { paddingTop: 0 } }}>
                {renderToolbar()}
                <Table<IntegrationRecord>
                  rowKey="id"
                  columns={columns}
                  dataSource={allPage?.records ?? []}
                  loading={allLoading || allFetching}
                  scroll={{ x: 1200 }}
                  pagination={{
                    current: allPage?.current ?? allPage?.page ?? allQuery.page,
                    pageSize: allPage?.size ?? allQuery.size,
                    total: allPage?.total ?? 0,
                    showSizeChanger: true,
                    showTotal: (t) => `共 ${t} 条`,
                    onChange: (page, size) =>
                      setAllQuery((q) => ({ ...q, page, size })),
                  }}
                />
              </Card>
            ),
          },
          {
            key: 'failed',
            label: '失败记录',
            children: (
              <Card styles={{ body: { paddingTop: 0 } }}>
                {renderToolbar()}
                <Table<IntegrationRecord>
                  rowKey="id"
                  columns={columns}
                  dataSource={failedPage?.records ?? []}
                  loading={failedLoading || failedFetching}
                  scroll={{ x: 1200 }}
                  pagination={{
                    current: failedPage?.current ?? failedPage?.page ?? failedQuery.page,
                    pageSize: failedPage?.size ?? failedQuery.size,
                    total: failedPage?.total ?? 0,
                    showSizeChanger: true,
                    showTotal: (t) => `共 ${t} 条`,
                    onChange: (page, size) =>
                      setFailedQuery((q) => ({ ...q, page, size })),
                  }}
                />
              </Card>
            ),
          },
        ]}
      />

      <Drawer
        title="集成记录详情"
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
        destroyOnClose
      >
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <div>
              <Text strong>ID</Text>
              <div style={{ marginTop: 4 }}>{detail.id ?? '-'}</div>
            </div>
            <div>
              <Text strong>消息 ID</Text>
              <div style={{ marginTop: 4 }}>{detail.messageId ?? '-'}</div>
            </div>
            <div>
              <Text strong>渠道</Text>
              <div style={{ marginTop: 4 }}>
                {detail.channel ? <Tag color="blue">{detail.channel}</Tag> : '-'}
              </div>
            </div>
            <div>
              <Text strong>状态</Text>
              <div style={{ marginTop: 4 }}>
                {detail.status ? <Tag>{detail.status}</Tag> : '-'}
              </div>
            </div>
            <div>
              <Text strong>Scope</Text>
              <div style={{ marginTop: 4 }}>{detail.scopeId ?? '-'}</div>
            </div>
            <div>
              <Text strong>创建人</Text>
              <div style={{ marginTop: 4 }}>{detail.createUser ?? '-'}</div>
            </div>
            <div>
              <Text strong>发送时间</Text>
              <div style={{ marginTop: 4 }}>
                {detail.sendTime ? formatDate(detail.sendTime) : '-'}
              </div>
            </div>
            <div>
              <Text strong>创建时间</Text>
              <div style={{ marginTop: 4 }}>
                {detail.createTime ? formatDate(detail.createTime) : '-'}
              </div>
            </div>
            <div>
              <Text strong>更新时间</Text>
              <div style={{ marginTop: 4 }}>
                {detail.updateTime ? formatDate(detail.updateTime) : '-'}
              </div>
            </div>
            <div>
              <Text strong>响应</Text>
              <pre
                style={{
                  background: '#f5f5f5',
                  padding: 12,
                  borderRadius: 6,
                  maxHeight: 200,
                  overflow: 'auto',
                  fontSize: 12,
                  marginTop: 4,
                }}
              >
                {detail.response ?? '// 无响应'}
              </pre>
            </div>
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default IntegrationRecordPage;
