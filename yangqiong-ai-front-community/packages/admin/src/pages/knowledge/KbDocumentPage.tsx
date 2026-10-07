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
import React, { useMemo, useRef, useState } from 'react';
import {
  App,
  Button,
  Card,
  Drawer,
  Empty,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadProps } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type { KbDocument, KbSliceRecord, KnowledgeBase } from '@yangqiong/shared';

const { Paragraph, Text } = Typography;

const DOC_QUERY_KEY = 'kb-document-list';

// 上传并发上限
const MAX_CONCURRENT_UPLOADS = 3;

/**
 * 单个文件上传任务
 */
interface UploadTaskState {
  uid: string;

  /**
   * 文件名
   */
  name: string;

  /**
   * 源文件
   */
  file: File;

  /**
   * 目标知识库ID
   */
  kbId: string;

  /**
   * 上传状态
   */
  status: 'uploading' | 'success' | 'failed';

  /**
   * 上传进度（0-100）
   */
  percent: number;

  /**
   * 失败原因
   */
  error?: string;
}

/**
 * 知识库文档管理
 */
export const KbDocumentPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const { data: kbList } = useQuery({
    queryKey: ['kb-list-all'],
    queryFn: () => api.knowledge.kb.list(),
  });

  const kbOptions = useMemo(
    () =>
      (kbList ?? []).map((k: KnowledgeBase) => ({
        label: `${k.kbName}（${k.kbId}）`,
        value: k.kbId,
      })),
    [kbList],
  );

  // 读取 URL 中的 kbId 参数，用于初始化知识库下拉选中值
  const [knowledgeBaseId, setKnowledgeBaseId] = useState<string | undefined>(
    searchParams.get('kbId') ?? undefined,
  );

  const effectiveKbId = knowledgeBaseId ?? kbList?.[0]?.kbId;

  const { data: docList, isLoading } = useQuery({
    queryKey: [DOC_QUERY_KEY, effectiveKbId],
    queryFn: () => api.knowledge.documents.list(effectiveKbId as string),
    enabled: !!effectiveKbId,
  });

  const invalidateDocs = () => {
    queryClient.invalidateQueries({ queryKey: [DOC_QUERY_KEY] });
  };

  const [uploadTasks, setUploadTasks] = useState<UploadTaskState[]>([]);
  const uploadQueueRef = useRef<UploadTaskState[]>([]);
  const activeUploadCountRef = useRef(0);

  const updateUploadTask = (uid: string, patch: Partial<UploadTaskState>) => {
    setUploadTasks((prev) => prev.map((t) => (t.uid === uid ? { ...t, ...patch } : t)));
  };

  // 执行单个文件上传，并回写进度与结果
  const runUploadTask = (task: UploadTaskState): Promise<void> => {
    updateUploadTask(task.uid, { status: 'uploading', percent: 0, error: undefined });
    return api.knowledge.documents
      .upload(task.kbId, task.file, (progress) => {
        const percent = progress.total
          ? Math.round((progress.loaded / progress.total) * 100)
          : 0;
        updateUploadTask(task.uid, { percent });
      })
      .then(() => {
        updateUploadTask(task.uid, { status: 'success', percent: 100 });
        message.success(`${task.name} 上传成功`);
        invalidateDocs();
      })
      .catch((err) => {
        const msg = err instanceof Error ? err.message : '上传失败';
        updateUploadTask(task.uid, { status: 'failed', error: msg });
        message.error(`${task.name} 上传失败：${msg}`);
      });
  };

  // 调度上传队列，控制并发上限
  const pumpUploadQueue = () => {
    while (
      activeUploadCountRef.current < MAX_CONCURRENT_UPLOADS &&
      uploadQueueRef.current.length > 0
    ) {
      const task = uploadQueueRef.current.shift() as UploadTaskState;
      activeUploadCountRef.current += 1;
      runUploadTask(task).finally(() => {
        activeUploadCountRef.current -= 1;
        pumpUploadQueue();
      });
    }
  };

  const enqueueUpload = (task: UploadTaskState) => {
    uploadQueueRef.current.push(task);
    pumpUploadQueue();
  };

  // 重试失败的上传任务
  const retryUpload = (task: UploadTaskState) => {
    enqueueUpload({ ...task });
  };

  const deleteDocMutation = useMutation({
    mutationFn: ({ kbId, docId }: { kbId: string; docId: string }) =>
      api.knowledge.documents.delete(kbId, docId),
    onSuccess: () => {
      message.success('删除成功');
      invalidateDocs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const reprocessMutation = useMutation({
    mutationFn: ({ kbId, docId }: { kbId: string; docId: string }) =>
      api.knowledge.documents.reprocess(kbId, docId),
    onSuccess: () => {
      message.success('已触发重新处理');
      invalidateDocs();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '触发失败');
    },
  });

  // 当前查看切片的文档，非空时打开切片抽屉
  const [sliceDrawerDoc, setSliceDrawerDoc] = useState<KbDocument | null>(null);

  const { data: sliceList, isLoading: slicesLoading } = useQuery({
    queryKey: ['kb-doc-slices', sliceDrawerDoc?.kbId, sliceDrawerDoc?.docId],
    queryFn: () =>
      api.knowledge.documents.slices(
        sliceDrawerDoc!.kbId,
        sliceDrawerDoc!.docId,
      ),
    enabled: !!sliceDrawerDoc,
  });

  const uploadProps: UploadProps = {
    accept: '.txt,.md,.pdf,.doc,.docx,.csv,.html,.json',
    multiple: true,
    showUploadList: false,
    customRequest: ({ file, onSuccess, onError }) => {
      if (!effectiveKbId) {
        message.error('请先选择知识库');
        onError?.(new Error('未选择知识库'));
        return;
      }
      const task: UploadTaskState = {
        uid: (file as { uid?: string }).uid ?? String((file as File).name + Date.now()),
        name: (file as File).name,
        file: file as File,
        kbId: effectiveKbId,
        status: 'uploading',
        percent: 0,
      };
      setUploadTasks((prev) => [...prev, task]);
      enqueueUpload(task);
      // 由队列内部回调决定结果，Upload 组件这边直接置成功以清掉文件选择态
      onSuccess?.({}, file as any);
    },
  };

  const columns: ColumnsType<KbDocument> = [
    { title: '文档ID', dataIndex: 'docId', width: 180, ellipsis: true },
    { title: '名称', dataIndex: 'docName', ellipsis: true },
    { title: '文件类型', dataIndex: 'fileType', width: 100 },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 100,
      render: (v?: number) =>
        v != null ? `${(v / 1024).toFixed(1)} KB` : '-',
    },
    { title: '来源类型', dataIndex: 'sourceType', width: 100 },
    {
      title: '切片数',
      dataIndex: 'chunkCount',
      width: 80,
    },
    {
      title: '状态',
      dataIndex: 'docStatus',
      width: 100,
      render: (v?: string) => {
        if (!v) return '-';
        const colorMap: Record<string, string> = {
          PENDING: 'default',
          PROCESSING: 'processing',
          READY: 'green',
          FAILED: 'red',
        };
        return <Tag color={colorMap[v] ?? 'default'}>{v}</Tag>;
      },
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      ellipsis: true,
      render: (v?: string) => v || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      render: (_: unknown, record: KbDocument) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => setSliceDrawerDoc(record)}
          >
            切片
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() =>
              reprocessMutation.mutate({
                kbId: effectiveKbId as string,
                docId: record.docId,
              })
            }
          >
            重新处理
          </Button>
          <Popconfirm
            title="确认删除该文档？"
            onConfirm={() =>
              deleteDocMutation.mutate({
                kbId: effectiveKbId as string,
                docId: record.docId,
              })
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

  return (
    <Card
      title={
        <Space>
          <Button type="text" size="small" icon={<ArrowLeftOutlined />} onClick={() => navigate('/knowledge-bases')}>
            返回
          </Button>
          知识库文档
        </Space>
      }
    >
      <Space style={{ marginBottom: 16 }}>
        <span>知识库：</span>
        <Select
          style={{ width: 320 }}
          options={kbOptions}
          placeholder="请选择知识库"
          value={effectiveKbId}
          onChange={(v) => {
            setKnowledgeBaseId(v);
          }}
        />
        <Upload {...uploadProps}>
          <Button type="primary" disabled={!effectiveKbId}>
            上传文档
          </Button>
        </Upload>
      </Space>

      {uploadTasks.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 4 }}>
            <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>上传记录</span>
            <Button type="link" size="small" onClick={() => setUploadTasks([])}>
              清空
            </Button>
          </div>
          {uploadTasks.map((task) => (
            <div
              key={task.uid}
              style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '2px 0' }}
            >
              <span
                style={{
                  width: 260,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={task.name}
              >
                {task.name}
              </span>
              {task.status === 'uploading' && (
                <Progress percent={task.percent} size="small" style={{ width: 160 }} />
              )}
              {task.status === 'success' && <Tag color="green">成功</Tag>}
              {task.status === 'failed' && (
                <>
                  <Tag color="red">失败</Tag>
                  <span
                    style={{
                      color: 'rgba(0,0,0,0.45)',
                      fontSize: 12,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                    title={task.error}
                  >
                    {task.error}
                  </span>
                  <Button type="link" size="small" onClick={() => retryUpload(task)}>
                    重试
                  </Button>
                </>
              )}
            </div>
          ))}
        </div>
      )}

      <Table<KbDocument>
        rowKey="docId"
        columns={columns}
        dataSource={docList ?? []}
        loading={isLoading}
        scroll={{ x: 1300 }}
        pagination={{
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
      />

      <Drawer
        title={`切片列表：${sliceDrawerDoc?.docName ?? ''}`}
        width={640}
        open={!!sliceDrawerDoc}
        onClose={() => setSliceDrawerDoc(null)}
      >
        {(sliceList ?? []).length === 0 ? (
          <Empty
            description={slicesLoading ? '加载中...' : '该文档暂无切片'}
          />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Text type="secondary">共 {(sliceList ?? []).length} 条切片</Text>
            {(sliceList ?? []).map((s: KbSliceRecord, idx: number) => (
              <Card
                key={s.sliceId}
                size="small"
                title={
                  <Space wrap>
                    <Tag>#{idx + 1}</Tag>
                    {s.sliceType && (
                      <Tag color={s.sliceType === 'parent' ? 'purple' : 'blue'}>
                        {s.sliceType}
                      </Tag>
                    )}
                    <Text type="secondary" copyable={{ text: s.sliceId }}>
                      {s.sliceId.substring(0, 8)}
                    </Text>
                    {s.parentId && (
                      <Text type="secondary">
                        父块：{s.parentId.substring(0, 8)}
                      </Text>
                    )}
                  </Space>
                }
              >
                <Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {s.content || '-'}
                </Paragraph>
              </Card>
            ))}
          </Space>
        )}
      </Drawer>
    </Card>
  );
};
