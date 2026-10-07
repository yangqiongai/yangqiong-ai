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
import { App, Button, Card, Modal, Space, Switch, Table, Tag, Input } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { SkillUsageInfo } from '@yangqiong/shared';

const QUERY_KEY = 'skill-usage-top';

/**
 * 技能用量排行
 */
export const SkillUsagePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [stateOpen, setStateOpen] = useState(false);
  const [stateSkillId, setStateSkillId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY, size, keyword],
    queryFn: () => api.skill.usage.top({ page: 1, size, keyword }),
  });

  const { data: stateData, isLoading: stateLoading } = useQuery({
    queryKey: ['skill-usage-state', stateSkillId],
    queryFn: () => api.skill.usage.state(stateSkillId as string),
    enabled: !!stateSkillId && stateOpen,
  });

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const pinMutation = useMutation({
    mutationFn: ({ skillId, pinned }: { skillId: string; pinned: boolean }) =>
      api.skill.usage.pinned(skillId, pinned),
    onSuccess: (_data, vars) => {
      message.success(vars.pinned ? '已置顶' : '已取消置顶');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '操作失败');
    },
  });

  const columns: ColumnsType<SkillUsageInfo> = [
    {
      title: '排名',
      width: 70,
      render: (_: unknown, __: unknown, idx: number) => idx + 1,
    },
    { title: '技能 ID', dataIndex: 'skillId', ellipsis: true },
    {
      title: '使用次数',
      dataIndex: 'useCount',
      width: 110,
      sorter: (a, b) => (a.useCount ?? 0) - (b.useCount ?? 0),
    },
    {
      title: '查看次数',
      dataIndex: 'viewCount',
      width: 110,
      render: (v?: number) => (v != null ? v : '-'),
    },
    {
      title: '最近使用',
      dataIndex: 'lastUsedAt',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '置顶',
      width: 100,
      render: (_: unknown, record: SkillUsageInfo) => (
        <Switch
          checked={record.pinned}
          loading={pinMutation.isPending}
          onChange={(checked) =>
            pinMutation.mutate({ skillId: record.skillId, pinned: checked })
          }
        />
      ),
    },
    {
      title: '生命周期',
      width: 120,
      render: (_: unknown, record: SkillUsageInfo) => (
        <Button
          type="link"
          size="small"
          onClick={() => {
            setStateSkillId(record.skillId);
            setStateOpen(true);
          }}
        >
          查看状态
        </Button>
      ),
    },
  ];

  return (
    <Card title="技能用量排行">
      <Space style={{ marginBottom: 16 }}>
        <Input
          placeholder="技能 ID"
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

      <Table<SkillUsageInfo>
        rowKey="skillId"
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        scroll={{ x: 1000 }}
        pagination={{
          pageSize: size,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (_p, s) => setSize(s),
        }}
      />

      <Modal
        title="技能生命周期状态"
        open={stateOpen}
        onCancel={() => {
          setStateOpen(false);
          setStateSkillId(null);
        }}
        footer={null}
        width={560}
      >
        {stateLoading ? (
          '加载中...'
        ) : stateData ? (
          <div>
            <p>
              <strong>技能 ID：</strong>
              {stateSkillId}
            </p>
            <pre
              style={{
                background: '#f5f5f5',
                padding: 12,
                maxHeight: 360,
                overflow: 'auto',
                whiteSpace: 'pre-wrap',
              }}
            >
              {typeof stateData === 'string'
                ? stateData
                : JSON.stringify(stateData, null, 2)}
            </pre>
          </div>
        ) : (
          <Tag>暂无数据</Tag>
        )}
      </Modal>
    </Card>
  );
};
