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
  Drawer,
  Empty,
  Form,
  Input,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Spin,
  Switch,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, FireOutlined, FolderOpenOutlined, FormOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '@/services';
import { AgentIconPicker, AgentIconView } from '@/components/AgentIcon';
import { formatDate } from '@yangqiong/shared';
import type { KnowledgeBase } from '@yangqiong/shared';

const QUERY_KEY = 'kb-list';

/**
 * 次要文字颜色
 */
const SUB_TEXT_COLOR = 'rgba(0, 0, 0, 0.45)';

/**
 * 默认图标名（AgentIcon 预设）
 */
const DEFAULT_KB_ICON = 'BookOutlined';

interface FormValues {
  kbName: string;
  kbDescription?: string;
  kbIcon?: string;
  embeddingModel?: string;
  chunkStrategy?: string;
  chunkConfig?: string;
  kbStatus: number;
  remark?: string;
}

/**
 * 知识库管理
 */
export const KbListPage: React.FC = () => {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(12);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<KnowledgeBase | null>(null);
  const [form] = Form.useForm<FormValues>();

  const { data, isLoading } = useQuery({
    queryKey: [QUERY_KEY],
    queryFn: () => api.knowledge.kb.list(),
  });

  const filteredList = useMemo(() => {
    const list = data ?? [];
    if (!keyword) return list;
    return list.filter(
      (kb) =>
        kb.kbName?.toLowerCase().includes(keyword.toLowerCase()) ||
        kb.kbId?.toLowerCase().includes(keyword.toLowerCase()),
    );
  }, [data, keyword]);

  // 过滤结果变化后页码可能越界，做安全收敛
  const totalPages = Math.max(1, Math.ceil(filteredList.length / pageSize));
  const safePage = Math.min(page, totalPages);
  const pagedList = useMemo(
    () => filteredList.slice((safePage - 1) * pageSize, safePage * pageSize),
    [filteredList, safePage, pageSize],
  );

  const invalidateList = () => {
    queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
  };

  const createMutation = useMutation({
    mutationFn: (values: FormValues) => api.knowledge.kb.create(values),
    onSuccess: () => {
      message.success('创建成功');
      setDrawerOpen(false);
      form.resetFields();
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '创建失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: FormValues) =>
      api.knowledge.kb.update(editing?.kbId as string, values),
    onSuccess: () => {
      message.success('更新成功');
      setDrawerOpen(false);
      setEditing(null);
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (kbId: string) => api.knowledge.kb.delete(kbId),
    onSuccess: () => {
      message.success('删除成功');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const purgeMutation = useMutation({
    mutationFn: (kbId: string) => api.knowledge.kb.purge(kbId),
    onSuccess: () => {
      message.success('知识库已彻底删除');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '彻底删除失败');
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: (record: KnowledgeBase) =>
      api.knowledge.kb.update(record.kbId, { kbStatus: record.kbStatus === 1 ? 0 : 1 }),
    onSuccess: () => {
      message.success('状态已更新');
      invalidateList();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '状态更新失败');
    },
  });

  /**
   * 彻底删除知识库（物理删除文档记录与向量数据，输入名称确认）
   * @param record
   */
  const openPurge = (record: KnowledgeBase) => {
    let inputValue = '';
    modal.confirm({
      title: '彻底删除知识库',
      content: (
        <div>
          <div style={{ marginBottom: 12 }}>
            将物理删除知识库「{record.kbName}」及其所有文档记录、切片和向量数据，
            此操作不可恢复！仅建议在确认不再使用后执行。
          </div>
          <Input
            placeholder={`请输入知识库名称「${record.kbName}」确认`}
            onChange={(e) => {
              inputValue = e.target.value;
            }}
          />
        </div>
      ),
      okText: '彻底删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => {
        if (inputValue !== record.kbName) {
          message.error('输入的名称不匹配，操作已取消');
          return Promise.reject(new Error('名称不匹配'));
        }
        return purgeMutation.mutateAsync(record.kbId);
      },
    });
  };

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ kbIcon: DEFAULT_KB_ICON, kbStatus: 1 });
    setDrawerOpen(true);
  };

  const openEdit = (record: KnowledgeBase) => {
    setEditing(record);
    // 先重置再回填，避免上一条记录的字段值残留
    form.resetFields();
    form.setFieldsValue({
      kbName: record.kbName,
      kbDescription: record.kbDescription,
      kbIcon: record.kbIcon ?? DEFAULT_KB_ICON,
      embeddingModel: record.embeddingModel,
      chunkStrategy: record.chunkStrategy,
      chunkConfig: record.chunkConfig,
      kbStatus: record.kbStatus,
      remark: record.remark,
    });
    setDrawerOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editing) {
      updateMutation.mutate(values);
    } else {
      createMutation.mutate(values);
    }
  };

  /**
   * 渲染单个知识库卡片
   * @param record
   * @return
   */
  const renderCard = (record: KnowledgeBase) => {
    return (
      <Card
        key={record.kbId}
        hoverable
        style={{ height: '100%', cursor: 'pointer' }}
        styles={{ body: { padding: '20px 20px 12px', height: '100%', display: 'flex', flexDirection: 'column' } }}
        onClick={() => navigate(`/knowledge-bases/document?kbId=${record.kbId}`)}
      >
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
        <AgentIconView icon={record.kbIcon ?? DEFAULT_KB_ICON} size={44} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Tooltip title={record.kbName}>
              <Typography.Text strong ellipsis style={{ fontSize: 16, flex: 1 }}>
                {record.kbName || '未命名'}
              </Typography.Text>
            </Tooltip>
            <span onClick={(e) => e.stopPropagation()}>
              <Switch
                size="small"
                checkedChildren="启用"
                unCheckedChildren="停用"
                checked={record.kbStatus === 1}
                loading={toggleStatusMutation.isPending && toggleStatusMutation.variables?.kbId === record.kbId}
                onChange={() => toggleStatusMutation.mutate(record)}
              />
            </span>
          </div>
          <Tooltip title={record.kbId}>
            <Typography.Text code type="secondary" ellipsis style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
              {record.kbId}
            </Typography.Text>
          </Tooltip>
        </div>
      </div>
      <Typography.Paragraph
        type="secondary"
        ellipsis={{ rows: 2 }}
        style={{ marginTop: 12, marginBottom: 12, minHeight: 44 }}
      >
        {record.kbDescription || '暂无描述'}
      </Typography.Paragraph>
      <div
        style={{
          marginTop: 'auto',
          paddingTop: 12,
          borderTop: '1px solid #f0f0f0',
          display: 'flex',
          alignItems: 'center',
        }}
      >
        <div style={{ flexShrink: 0, paddingRight: 16 }}>
          <div style={{ fontSize: 24, fontWeight: 600, lineHeight: '30px', color: '#1677ff' }}>
            {record.documentCount ?? 0}
          </div>
          <div style={{ fontSize: 12, color: SUB_TEXT_COLOR }}>文档数</div>
        </div>
        <div style={{ flex: 1, minWidth: 0, borderLeft: '1px solid #f0f0f0', paddingLeft: 16 }}>
          <div style={{ display: 'flex', gap: 8, fontSize: 12, marginBottom: 4 }}>
            <span style={{ color: SUB_TEXT_COLOR, flexShrink: 0 }}>嵌入模型</span>
            <Tooltip title={record.embeddingModel}>
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  color: 'rgba(0, 0, 0, 0.88)',
                }}
              >
                {record.embeddingModel || '-'}
              </span>
            </Tooltip>
          </div>
          <div style={{ display: 'flex', gap: 8, fontSize: 12 }}>
            <span style={{ color: SUB_TEXT_COLOR, flexShrink: 0 }}>切片策略</span>
            <Tooltip title={record.chunkStrategy}>
              <span
                style={{
                  flex: 1,
                  minWidth: 0,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  color: 'rgba(0, 0, 0, 0.88)',
                }}
              >
                {record.chunkStrategy || '-'}
              </span>
            </Tooltip>
          </div>
        </div>
      </div>
      <div
        style={{
          marginTop: 8,
          fontSize: 12,
          color: SUB_TEXT_COLOR,
          display: 'flex',
          justifyContent: 'space-between',
        }}
      >
        <span>版本 {record.activeVersion ? `v${record.activeVersion}` : '-'}</span>
        <span>创建于 {record.createTime ? formatDate(record.createTime) : '-'}</span>
      </div>
      <div
        style={{
          marginTop: 10,
          paddingTop: 8,
          borderTop: '1px solid #f0f0f0',
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 2,
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <Tooltip title="文档">
          <Button
            type="text"
            size="small"
            icon={<FolderOpenOutlined />}
            onClick={() => navigate(`/knowledge-bases/document?kbId=${record.kbId}`)}
          />
        </Tooltip>
        <Tooltip title="编辑">
          <Button type="text" size="small" icon={<FormOutlined />} onClick={() => openEdit(record)} />
        </Tooltip>
        <Popconfirm
          title="确认删除该知识库？"
          description="删除后知识库不可用且无法检索，文档记录与向量数据会保留；如需彻底清理数据请使用彻底删除"
          okText="删除"
          okButtonProps={{ danger: true }}
          onConfirm={() => deleteMutation.mutate(record.kbId)}
        >
          <Tooltip title="删除">
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Tooltip>
        </Popconfirm>
        <Tooltip title="彻底删除">
          <Button type="text" size="small" danger icon={<FireOutlined />} onClick={() => openPurge(record)} />
        </Tooltip>
      </div>
      </Card>
    );
  };

  return (
    <Card
      title="知识库管理"
      extra={
        <Button type="primary" onClick={openCreate}>
          新增知识库
        </Button>
      }
    >
      <Form
        layout="inline"
        style={{ marginBottom: 16 }}
        onFinish={() => setKeyword((document.getElementById('kb-search') as HTMLInputElement)?.value ?? '')}
      >
        <Form.Item>
          <Input
            id="kb-search"
            placeholder="知识库名称或ID"
            allowClear
            style={{ width: 220 }}
            onPressEnter={(e) => setKeyword(e.currentTarget.value)}
          />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button
              onClick={() => {
                setKeyword('');
                const input = document.getElementById('kb-search') as HTMLInputElement;
                if (input) input.value = '';
              }}
            >
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : pagedList.length === 0 ? (
        <Empty description="暂无知识库" style={{ padding: 48 }} />
      ) : (
        <>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
              gap: 16,
              alignItems: 'stretch',
            }}
          >
            {pagedList.map((record) => renderCard(record))}
          </div>
          <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
            <Pagination
              current={safePage}
              pageSize={pageSize}
              total={filteredList.length}
              showSizeChanger
              showTotal={(t) => `共 ${t} 条`}
              pageSizeOptions={[8, 12, 24, 48]}
              onChange={(p, s) => {
                setPage(p);
                setPageSize(s);
              }}
            />
          </div>
        </>
      )}

      <Drawer
        title={editing ? '编辑知识库' : '新增知识库'}
        width={480}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        destroyOnClose
        extra={
          <Space>
            <Button
              onClick={() => {
                setDrawerOpen(false);
                setEditing(null);
              }}
            >
              取消
            </Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              onClick={handleSubmit}
            >
              保存
            </Button>
          </Space>
        }
      >
        <Form<FormValues> form={form} layout="vertical">
          <Form.Item
            name="kbName"
            label="名称"
            rules={[{ required: true, message: '请输入名称' }]}
          >
            <Input placeholder="请输入知识库名称" />
          </Form.Item>
          <Form.Item name="kbDescription" label="描述">
            <Input.TextArea placeholder="请输入描述" rows={3} />
          </Form.Item>
          <Form.Item name="kbIcon" label="图标" initialValue={DEFAULT_KB_ICON}>
            <AgentIconPicker />
          </Form.Item>
          <Form.Item name="embeddingModel" label="嵌入模型">
            <Input placeholder="如 bge-large-zh" />
          </Form.Item>
          <Form.Item name="chunkStrategy" label="切片策略">
            <Input placeholder="如 fixed_size / semantic" />
          </Form.Item>
          <Form.Item name="chunkConfig" label="切片配置">
            <Input placeholder='JSON 格式，如 {"chunkSize":512}' />
          </Form.Item>
          <Form.Item
            name="kbStatus"
            label="状态"
            initialValue={1}
            rules={[{ required: true, message: '请选择状态' }]}
          >
            <Select
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea placeholder="备注信息" rows={2} />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
};
