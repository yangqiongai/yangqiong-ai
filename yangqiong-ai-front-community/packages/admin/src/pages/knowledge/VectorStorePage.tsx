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
import { App, Button, Card, Collapse, Descriptions, Drawer, Popconfirm, Space, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { VectorCollectionInfo } from '@yangqiong/shared';

const { Text } = Typography;

const QUERY_KEY = 'vector-collections';

/**
 * 从列表行提取集合名称（后端返回 collectionName，兼容其他命名形态）
 * @param record
 * @return
 */
const pickCollectionName = (record: VectorCollectionInfo) =>
  (record as Record<string, unknown>)?.name ??
  (record as Record<string, unknown>)?.collectionName ??
  (record as Record<string, unknown>)?.['collection_name'];

/**
 * 从集合详情中提取常用展示字段（protobuf序列化结构稳定，防御式取值）
 * @param detail
 * @return
 */
const pickDetail = (detail: Record<string, unknown>) => {
  const pick = (keys: string[]) => {
    for (const k of keys) {
      if (detail[k] !== undefined && detail[k] !== null) return detail[k];
    }
    return undefined;
  };
  return {
    status: pick(['status', 'Status']),
    pointsCount: pick(['pointsCount', 'points_count']),
    vectorsCount: pick(['vectorsCount', 'vectors_count', 'vectors_count_']),
    segmentsCount: pick(['segmentsCount', 'segments_count']),
  };
};

/**
 * 向量存储管理
 * 后端为 VectorStoreAdminController，仅提供集合查询和删除（Qdrant 管理）
 */
export const VectorStorePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [detailName, setDetailName] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY],
    queryFn: () => api.knowledge.vector.listCollections(),
  });

  const detailQuery = useQuery({
    queryKey: ['vector-collection-detail', detailName],
    queryFn: () => api.knowledge.vector.getCollectionDetail(detailName as string),
    enabled: !!detailName,
  });

  const deleteMutation = useMutation({
    mutationFn: (collectionName: string) =>
      api.knowledge.vector.deleteCollection(collectionName),
    onSuccess: () => {
      message.success('集合已删除');
      setDetailName(null);
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const renderDetail = () => {
    if (detailQuery.isLoading) {
      return <Spin />;
    }
    const detail = (detailQuery.data ?? {}) as Record<string, unknown>;
    const info = pickDetail(detail);
    return (
      <>
        <Descriptions
          column={1}
          size="small"
          bordered
          items={[
            { key: 'name', label: '集合名称', children: detailName ?? '-' },
            { key: 'status', label: '状态', children: info.status ? String(info.status) : '-' },
            { key: 'points', label: '点位数', children: info.pointsCount != null ? String(info.pointsCount) : '-' },
            { key: 'vectors', label: '向量数', children: info.vectorsCount != null ? String(info.vectorsCount) : '-' },
            { key: 'segments', label: '分段数', children: info.segmentsCount != null ? String(info.segmentsCount) : '-' },
          ]}
        />
        <Collapse
          style={{ marginTop: 16 }}
          items={[{
            key: 'raw',
            label: '原始详情（JSON）',
            children: (
              <pre style={{ maxHeight: 320, overflow: 'auto', fontSize: 12 }}>
                {JSON.stringify(detail, null, 2)}
              </pre>
            ),
          }]}
        />
      </>
    );
  };

  const columns: ColumnsType<VectorCollectionInfo> = [
    {
      title: '集合名称',
      dataIndex: 'collectionName',
      ellipsis: true,
      render: (_: unknown, record) => {
        const name = pickCollectionName(record);
        return name ? (
          <Button type="link" size="small" style={{ paddingLeft: 0 }} onClick={() => setDetailName(String(name))}>
            {String(name)}
          </Button>
        ) : '-';
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 120,
      render: (v?: string) => (v ? <Tag color="green">{v}</Tag> : '-'),
    },
    {
      title: '向量数',
      dataIndex: 'vectorCount',
      width: 100,
      render: (v?: number, record?: VectorCollectionInfo) =>
        (v ??
          (record as Record<string, unknown> | undefined)?.vectorsCount as number | undefined) ?? '-',
    },
    {
      title: '维度',
      dataIndex: 'dimension',
      width: 80,
      render: (v?: number) => v ?? '-',
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, record: VectorCollectionInfo) => {
        const name = pickCollectionName(record) as string | undefined;
        return (
          <Space size={0}>
            <Button type="link" size="small" disabled={!name} onClick={() => name && setDetailName(name)}>
              详情
            </Button>
            <Popconfirm
              title="确认删除该集合？"
              description="此操作不可恢复，将删除该集合下的所有向量数据"
              okText="删除"
              okButtonProps={{ danger: true }}
              onConfirm={() => name && deleteMutation.mutate(name)}
            >
              <Button type="link" size="small" danger disabled={!name}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title="向量存储管理"
      extra={
        <Space>
          <Text type="secondary">Qdrant 向量数据库</Text>
          <Button
            onClick={() => queryClient.invalidateQueries({ queryKey: [QUERY_KEY] })}
          >
            刷新
          </Button>
        </Space>
      }
    >
      <Table<VectorCollectionInfo>
        rowKey={(record) => String(pickCollectionName(record) ?? '')}
        columns={columns}
        dataSource={data ?? []}
        loading={isLoading}
        scroll={{ x: 800 }}
        pagination={false}
      />

      <Drawer
        title={`集合详情：${detailName ?? ''}`}
        width={520}
        open={!!detailName}
        onClose={() => setDetailName(null)}
        destroyOnClose
      >
        {renderDetail()}
      </Drawer>
    </Card>
  );
};
