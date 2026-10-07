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
import { Button, Card, Input, Space, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { ToolUsageInfo } from '@yangqiong/shared';

const QUERY_KEY = 'tool-usage-top';

/**
 * 工具用量排行
 */
export const ToolUsagePage: React.FC = () => {
  const queryClient = useQueryClient();

  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, size, keyword],
    queryFn: () => api.tool.usage.top({ page: 1, size, keyword }),
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const columns: ColumnsType<ToolUsageInfo> = [
    {
      title: '排名',
      width: 70,
      render: (_: unknown, __: unknown, idx: number) => idx + 1,
    },
    {
      title: '工具编码',
      dataIndex: 'toolCode',
      ellipsis: true,
    },
    {
      title: '工具名称',
      dataIndex: 'toolName',
      width: 140,
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: '工具描述',
      dataIndex: 'toolDesc',
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: '调用次数',
      dataIndex: 'callCount',
      width: 110,
      sorter: (a, b) => (a.callCount ?? 0) - (b.callCount ?? 0),
    },
    {
      title: '成功次数',
      dataIndex: 'successCount',
      width: 110,
      render: (v?: number) => (v != null ? v : '-'),
    },
    {
      title: '失败次数',
      dataIndex: 'failCount',
      width: 110,
      render: (v?: number) => (v != null ? v : '-'),
    },
    {
      title: '最近调用',
      dataIndex: 'lastCalledAt',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
  ];

  return (
    <Card title="工具用量排行">
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="工具编码"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
          style={{ width: 220 }}
        />
        <Button type="primary" onClick={invalidateList}>
          查询
        </Button>
        <Button
          onClick={() => {
            setKeyword('');
            setSize(20);
            invalidateList();
          }}
        >
          重置
        </Button>
      </Space>

      <Table<ToolUsageInfo>
        rowKey="toolCode"
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        scroll={{ x: 900 }}
        pagination={{
          pageSize: size,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (_p, s) => setSize(s),
        }}
      />
    </Card>
  );
};
