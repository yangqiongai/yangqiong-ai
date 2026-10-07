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
import { useState } from 'react';
import {
  App,
  Button,
  Empty,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { useAuthStore } from '@/store/auth-store';
import { formatDate, tryPrettyJson } from '@yangqiong/shared';
import type { AgentConfigDrift } from '@yangqiong/shared';

const DRIFT_KEY = 'agent-drift';

const DRIFT_STATUS_META: Record<string, { label: string; color: string }> = {
  DETECTED: { label: '待处理', color: 'orange' },
  REPAIRED: { label: '已修复', color: 'green' },
  IGNORED: { label: '已忽略', color: 'default' },
};

/**
 * Agent 配置漂移
 */
export const AgentDriftTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const operator = useAuthStore((s) => s.user?.name);

  const [statusFilter, setStatusFilter] = useState<string | undefined>();

  const listQuery = useQuery({
    queryKey: [DRIFT_KEY, agentCode, statusFilter],
    queryFn: () => api.registry.drift.list({ agentCode, status: statusFilter }),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: [DRIFT_KEY] });
  };

  const repairMutation = useMutation({
    mutationFn: (id: number) => api.registry.drift.repair(id, operator),
    onSuccess: () => {
      message.success('漂移已修复：运行时配置已按发布快照与环境档重写');
      invalidate();
    },
    onError: () => message.error('修复失败，请稍后重试'),
  });

  const ignoreMutation = useMutation({
    mutationFn: (id: number) => api.registry.drift.ignore(id),
    onSuccess: () => {
      message.success('漂移已加入白名单忽略');
      invalidate();
    },
  });

  const columns: ColumnsType<AgentConfigDrift> = [
    { title: '环境档', dataIndex: 'profileCode', width: 100 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => {
        const meta = DRIFT_STATUS_META[v ?? ''] ?? { label: v ?? '-', color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '字段级差异',
      dataIndex: 'diffJson',
      ellipsis: true,
      render: (v?: string) =>
        v ? (
          <Typography.Paragraph code style={{ fontSize: 12, marginBottom: 0 }} ellipsis={{ rows: 2 }}>
            {tryPrettyJson(v) ?? v}
          </Typography.Paragraph>
        ) : (
          '-'
        ),
    },
    { title: '修复人', dataIndex: 'repairedBy', width: 100, render: (v?: string) => v ?? '-' },
    { title: '修复时间', dataIndex: 'repairedTime', width: 160, render: (v?: string) => formatDate(v) },
    { title: '检出时间', dataIndex: 'createTime', width: 160, render: (v?: string) => formatDate(v) },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      render: (_, record) =>
        record.status === 'DETECTED' ? (
          <Space size={4}>
            <Popconfirm
              title="确认按发布快照修复漂移？"
              onConfirm={() => repairMutation.mutate(record.id!)}
            >
              <Button size="small" type="link" loading={repairMutation.isPending}>
                一键修复
              </Button>
            </Popconfirm>
            <Popconfirm title="确认忽略该漂移（白名单）？" onConfirm={() => ignoreMutation.mutate(record.id!)}>
              <Button size="small" type="link">
                忽略
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            已处理
          </Typography.Text>
        ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select
          allowClear
          style={{ width: 140 }}
          placeholder="状态筛选"
          value={statusFilter}
          onChange={(v) => setStatusFilter(v)}
          options={[
            { label: '待处理', value: 'DETECTED' },
            { label: '已修复', value: 'REPAIRED' },
            { label: '已忽略', value: 'IGNORED' },
          ]}
        />
        <Button icon={<ReloadOutlined />} onClick={invalidate}>
          刷新
        </Button>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          运行时配置与「发布快照 × 环境档」合成期望不一致时产生漂移记录。
        </Typography.Text>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={listQuery.isLoading}
        columns={columns}
        dataSource={listQuery.data ?? []}
        pagination={false}
        locale={{ emptyText: <Empty description="该 Agent 暂无漂移记录" /> }}
      />
    </div>
  );
};
