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
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  HistoryOutlined,
  MessageOutlined,
  PlusOutlined,
  SearchOutlined,
  TagOutlined,
} from '@ant-design/icons';
import { api } from '@/services';
import { useAuthStore } from '@/store/auth-store';
import { formatDate } from '@yangqiong/shared';
import type { ConversationSessionInfo } from '@yangqiong/shared';
import { useConversationSessions } from '@yangqiong/shared/conversation';

const { Text } = Typography;

/**
 * 会话历史
 */
export const ConversationListPage: React.FC = () => {
  const { message } = App.useApp();
  const user = useAuthStore((s) => s.user);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [tagModalOpen, setTagModalOpen] = useState(false);
  const [tagForm] = Form.useForm<{ sessionId: string; tag: string }>();

  const {
    statsQuery,
    sessionsQuery,
    sessions,
    total,
    tags,
    createTag,
    deleteSession,
    deleteTag,
  } = useConversationSessions(api.conversation, { userId: user?.id, page, size, keyword });

  /**
   * 删除会话（成功后提示并失效缓存）
   * @param id
   * @return
   */
  const handleDeleteSession = (id: string) => {
    deleteSession.mutate(id, {
      onSuccess: () => message.success('会话已删除'),
      onError: (err) => message.error(err instanceof Error ? err.message : '删除失败'),
    });
  };

  /**
   * 创建会话标签（成功后提示、关弹窗并失效缓存）
   * @param values
   * @return
   */
  const handleCreateTag = (values: { sessionId: string; tag: string }) => {
    createTag.mutate(values, {
      onSuccess: () => {
        message.success('标签已创建');
        setTagModalOpen(false);
        tagForm.resetFields();
      },
      onError: (err) => message.error(err instanceof Error ? err.message : '创建失败'),
    });
  };

  /**
   * 删除会话标签（成功后提示并失效缓存）
   * @param tag
   * @return
   */
  const handleDeleteTag = (tag: string) => {
    deleteTag.mutate(tag, {
      onSuccess: () => message.success('标签已删除'),
      onError: (err) => message.error(err instanceof Error ? err.message : '删除失败'),
    });
  };

  const columns = [
    {
      title: '标题',
      dataIndex: 'sessionTitle',
      render: (_: unknown, record: ConversationSessionInfo) => (
        <>
          {/* 标题单行省略，点击行首箭头展开查看详细内容 */}
          <Text
            strong
            style={{
              display: 'block',
              maxWidth: 520,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {record.sessionTitle || record.sessionId}
          </Text>
          {record.summaryText && (
            <Text
              type="secondary"
              style={{
                fontSize: 12,
                display: 'block',
                maxWidth: 520,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {record.summaryText}
            </Text>
          )}
        </>
      ),
    },
    {
      title: 'Agent',
      dataIndex: 'agentCode',
      width: 160,
      render: (v: string) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'sessionStatus',
      width: 100,
      render: (v: number) => (
        <Tag color={v === 1 ? 'success' : 'warning'}>{v === 1 ? '活跃' : '停用'}</Tag>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 180,
      render: (_: unknown, record: ConversationSessionInfo) => (
        <Text type="secondary">{formatDate(record.updateTime ?? record.archivedAt ?? '')}</Text>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_: unknown, record: ConversationSessionInfo) => (
        <Popconfirm
          title="确认删除该会话？"
          onConfirm={() => handleDeleteSession(record.sessionId)}
        >
          <Button type="text" danger size="small" icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  const statsData = statsQuery.data;

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={statsQuery.isLoading}>
            <Statistic title="总会话数" value={statsData?.totalSessions ?? 0} prefix={<MessageOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={statsQuery.isLoading}>
            <Statistic title="活跃会话" value={statsData?.activeSessions ?? 0} prefix={<HistoryOutlined />} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={statsQuery.isLoading}>
            <Statistic title="消息总数" value={statsData?.totalMessages ?? 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={statsQuery.isLoading}>
            <Statistic title="已用 Token" value={statsData?.totalTokensUsed ?? 0} />
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <TagOutlined />
            <span>会话标签</span>
          </Space>
        }
        extra={
          <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => setTagModalOpen(true)}>
            新建标签
          </Button>
        }
        style={{ marginTop: 16 }}
      >
        {tags.length === 0 ? (
          <Text type="secondary">暂无标签</Text>
        ) : (
          <Space wrap>
            {tags.map((tag) => (
              <Tag
                key={tag.tag}
                closable
                onClose={(e) => {
                  e.preventDefault();
                  handleDeleteTag(tag.tag);
                }}
              >
                {tag.tag}
              </Tag>
            ))}
          </Space>
        )}
      </Card>

      <Card
        title={
          <Space>
            <MessageOutlined />
            <span>会话列表</span>
          </Space>
        }
        extra={
          <Space>
            <Input.Search
              placeholder="按会话标题搜索"
              allowClear
              onSearch={(v) => {
                setKeyword(v);
                setPage(1);
              }}
              style={{ width: 240 }}
              prefix={<SearchOutlined />}
            />
          </Space>
        }
        style={{ marginTop: 16 }}
      >
        <Table
          rowKey="sessionId"
          columns={columns}
          dataSource={sessions}
          loading={sessionsQuery.isLoading}
          expandable={{
            // 展开行展示会话详细内容（限高滚动，避免超长摘要撑爆页面）
            expandedRowRender: (record) => (
              <div
                style={{
                  maxHeight: 320,
                  overflowY: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  fontSize: 12,
                  lineHeight: 1.7,
                  color: '#595959',
                  background: '#fafafa',
                  borderRadius: 6,
                  padding: 12,
                }}
              >
                {record.summaryText || record.sessionTitle || '无详细内容'}
              </div>
            ),
            rowExpandable: (record) => Boolean(record.summaryText || record.sessionTitle),
          }}
          pagination={{
            current: page,
            pageSize: size,
            total,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p, s) => {
              setPage(p);
              setSize(s);
            },
          }}
        />
      </Card>

      <Modal
        title="新建会话标签"
        open={tagModalOpen}
        onCancel={() => setTagModalOpen(false)}
        onOk={() => tagForm.submit()}
        confirmLoading={createTag.isPending}
        destroyOnClose
      >
        <Form
          form={tagForm}
          layout="vertical"
          onFinish={(values) => handleCreateTag(values)}
        >
          <Form.Item
            name="sessionId"
            label="会话 ID"
            rules={[{ required: true, message: '请输入会话 ID' }]}
          >
            <Input placeholder="请输入关联的会话 ID" />
          </Form.Item>
          <Form.Item
            name="tag"
            label="标签名称"
            rules={[{ required: true, message: '请输入标签名称' }]}
          >
            <Input placeholder="请输入标签名称" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default ConversationListPage;
