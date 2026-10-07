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
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  App,
  Button,
  Card,
  Empty,
  Image,
  Input,
  Pagination,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  CopyOutlined,
  HistoryOutlined,
  MessageOutlined,
  ReloadOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { McpAppRenderer, formatDate } from '@yangqiong/shared';
import type { ConversationSessionInfo } from '@yangqiong/shared';
import {
  ImagePreviewStrip,
  collapseFileRefs,
  copyToClipboard,
  parseAgentConfig,
  restoreMessagesFromExport,
  useAgentChat,
  useApprovalRequests,
  useImagePaste,
} from '@yangqiong/shared/chat';
import type { ChatMessage } from '@yangqiong/shared/chat';
import { api } from '@/services';
import { AgentIconView } from '@/components/AgentIcon';
import { useAuthStore } from '@/store/auth-store';
import type { AgentTypeInfo } from '@yangqiong/shared';
import { ApprovalCards } from '@/pages/chat/components/ApprovalCards';
import { ClarificationCards } from '@/pages/chat/components/ClarificationCards';
import { ConfirmCards } from '@/pages/chat/components/ConfirmCards';
import { MarkdownContent } from '@/pages/chat/components/ChatMarkdown';
import {
  readWorkspaceBinding,
  useWorkspaces,
  writeWorkspaceBinding,
} from '@/pages/chat/components/useWorkspaces';
import { WorkspaceSelector } from '@/pages/chat/components/WorkspaceSelector';
import { WorkspaceManageModal } from '@/pages/chat/components/WorkspaceManageModal';

const { TextArea } = Input;
const { Text } = Typography;

/**
 * 左侧历史话题每页条数
 */
const TOPIC_PAGE_SIZE = 12;

/**
 * Agent 对话
 */
export const AgentChatPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const [agentType, setAgentType] = useState<string>('');
  const [streamMode, setStreamMode] = useState<boolean>(true);
  const [input, setInput] = useState<string>('');

  /**
   * 澄清回答提交中
   */
  const [clarificationSubmitting, setClarificationSubmitting] = useState<boolean>(false);

  /**
   * 引擎确认提交中
   */
  const [confirmSubmitting, setConfirmSubmitting] = useState<boolean>(false);

  // 对话消息状态与会话调度（共享hook：消息、会话ID、流式回调去重、MCP UI提取、失败兜底）
  const {
    messages,
    setMessages,
    sessionId,
    setSessionId,
    sending,
    clarifications,
    setClarifications,
    confirms,
    setConfirms,
    ensureSessionId,
    streamCallbacks,
    runAssistantStream,
    sendMessage,
    reset: resetChat,
  } = useAgentChat(api.agent, {
    onError: (msg) => message.error(msg),
    errorPrefix: '请求失败：',
  });

  const [topicPage, setTopicPage] = useState<number>(1);
  const [workspaceId, setWorkspaceId] = useState<string>();
  const [workspaceManageOpen, setWorkspaceManageOpen] = useState(false);

  // 输入框图片粘贴：类型与大小校验（单图5MB），dataURL预览与发送
  const { images, handlePaste, removeImage, clearImages } = useImagePaste((msg) =>
    message.warning(msg),
  );

  const scrollRef = useRef<HTMLDivElement>(null);

  const { workspaces, loading: workspacesLoading, refresh: refreshWorkspaces } = useWorkspaces(user?.id);

  // 会话维度的待审批事件订阅，卡片渲染在输入框上方
  const { requests: approvalRequests, removeRequest: removeApprovalRequest } =
    useApprovalRequests(api, sessionId || undefined);

  // 页面打开后是否已执行过自动恢复（仅首次加载后执行一次）
  const autoRestoredRef = useRef(false);

  const { data: agentTypeData, isLoading: agentTypesLoading } = useQuery({
    queryKey: ['agent-types', 'chat'],
    queryFn: () => api.agent.type.list({ page: 1, size: 200 }),
  });

  // 左侧话题列表：按当前用户查询会话，取最近50条
  const { data: sessionData, isLoading: topicsLoading } = useQuery({
    queryKey: ['chat-sessions', user?.id],
    queryFn: () => api.conversation.session.list({ userId: user?.id ?? '', page: 1, size: 50 }),
    enabled: Boolean(user?.id),
  });

  const topics = useMemo<ConversationSessionInfo[]>(
    () =>
      (sessionData?.list ?? [])
        .slice()
        .sort((a, b) => (b.updateTime ?? '').localeCompare(a.updateTime ?? '')),
    [sessionData],
  );

  const agentTypes = useMemo<AgentTypeInfo[]>(
    () => agentTypeData?.list ?? [],
    [agentTypeData],
  );

  // 话题总数变化时收拢页码，避免停留在超出范围的空页
  useEffect(() => {
    const totalPages = Math.max(1, Math.ceil(topics.length / TOPIC_PAGE_SIZE));
    if (topicPage > totalPages) {
      setTopicPage(totalPages);
    }
  }, [topics.length, topicPage]);

  // 当前页的话题切片
  const pagedTopics = useMemo(
    () => topics.slice((topicPage - 1) * TOPIC_PAGE_SIZE, topicPage * TOPIC_PAGE_SIZE),
    [topics, topicPage],
  );

  const currentAgent = agentTypes.find((t) => t.typeCode === agentType);

  // Agent配置的输出行为：showThinking 控制思维链展示、streamOutput 控制流式返回（默认均开启）
  const agentCfg = useMemo(
    () => parseAgentConfig(currentAgent?.agentConfig),
    [currentAgent?.agentConfig],
  );
  const showThinking = agentCfg.reasoning?.showThinking !== false;
  const agentStreamDisabled = agentCfg.streamOutput === false;

  const agentOptions = useMemo(
    () =>
      agentTypes.map((item) => ({
        label: (
          <Space size={8}>
            <AgentIconView icon={item.typeIcon} size={20} />
            <span>{item.typeName ? `${item.typeName}（${item.typeCode}）` : item.typeCode}</span>
          </Space>
        ),
        value: item.typeCode,
      })),
    [agentTypes],
  );

  useEffect(() => {
    if (!agentType && agentOptions.length > 0) {
      setAgentType(agentOptions[0].value);
    }
  }, [agentType, agentOptions]);

  // 新消息到达时滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  /**
   * 恢复历史话题：拉取会话消息并还原到当前对话区
   * @param session
   * @return
   */
  const restoreSession = async (session: ConversationSessionInfo) => {
    if (sending) {
      message.warning('正在对话中，请等待回复完成后再切换话题');
      return;
    }
    try {
      const json = await api.conversation.session.export(session.sessionId, 'json');
      setMessages(restoreMessagesFromExport(json));
      setSessionId(session.sessionId);
      if (session.agentCode) {
        // 历史会话可能存有编码统一前的defaultAgent，恢复为等价的default
        setAgentType(session.agentCode === 'defaultAgent' ? 'default' : session.agentCode);
      }
      // 本地Agent会话还原绑定的workspaceId
      if (session.agentCode === 'localAgent') {
        const bound = readWorkspaceBinding(session.sessionId);
        if (bound && workspaces.some((w) => w.id === bound)) {
          setWorkspaceId(bound);
        }
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : '恢复会话失败');
    }
  };

  // 页面打开后默认恢复最近一个话题（仅首次数据加载后执行一次，且不打断正在进行的对话）
  useEffect(() => {
    if (autoRestoredRef.current || !sessionData) {
      return;
    }
    autoRestoredRef.current = true;
    const latest = topics[0];
    if (latest && !sessionId && messages.length === 0) {
      void restoreSession(latest);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionData]);

  const handleSend = async () => {
    const content = input.trim();
    if ((!content && images.length === 0) || sending) {
      return;
    }
    if (!agentType) {
      message.warning('请选择 Agent 类型');
      return;
    }
    // 当前Agent绑定模型未开启图片支持时拒绝发送
    if (images.length > 0 && currentAgent?.supportImage !== 1) {
      message.warning('当前模型不支持图片输入');
      return;
    }

    const activeSessionId = ensureSessionId();

    // 本地Agent携带工作区（置于body映射后端AgentRequest.body），其余Agent不带
    const req = {
      agentCode: agentType,
      input: content ? [content] : [],
      sessionId: activeSessionId,
      userId: user?.id,
      ...(agentType === 'localAgent' && workspaceId ? { body: { _workspaceId: workspaceId } } : {}),
    };

    // 会话维度记住工作区绑定，续聊自动携带
    if (agentType === 'localAgent' && workspaceId) {
      writeWorkspaceBinding(activeSessionId, workspaceId);
    }

    setInput('');
    // 本次回复的展示行为按发送时刻的Agent配置与用户流式开关决定
    await sendMessage({
      req,
      userContent: content,
      images,
      stream: streamMode && !agentStreamDisabled,
      showThinking,
      clearPending: true,
    });
    clearImages();
    // 会话记录在服务端对话完成后落库，发送结束后刷新左侧话题列表
    queryClient.invalidateQueries({ queryKey: ['chat-sessions'] });
  };

  /**
   * 提交澄清回答：恢复流增量追加为新的assistant消息，流中可再次收到澄清提问（多轮澄清）
   * @param toolCallId
   * @param answer
   * @return
   */
  const handleSubmitClarification = async (toolCallId: string, answer: string) => {
    if (!sessionId || clarificationSubmitting) {
      return;
    }
    setClarificationSubmitting(true);
    await runAssistantStream(showThinking, (assistantId) => {
      const callbacks = streamCallbacks(assistantId, showThinking);
      return api.agent.chatClarificationStream(
        { sessionId, toolCallId, answer },
        callbacks.onContent,
        callbacks.onThinking,
        callbacks.onClarification,
        callbacks.onConfirm,
      );
    });
    setClarificationSubmitting(false);
    // 已回答的提问从卡片列表移除
    setClarifications((prev) => prev.filter((c) => c.toolCallId !== toolCallId));
    queryClient.invalidateQueries({ queryKey: ['chat-sessions'] });
  };

  /**
   * 提交引擎确认决策：恢复流增量追加为新的assistant消息，流中可再次收到确认请求（连续审批）
   * @param requestId
   * @param approved
   * @return
   */
  const handleConfirmDecide = async (requestId: string, approved: boolean) => {
    if (!sessionId || confirmSubmitting) {
      return;
    }
    setConfirmSubmitting(true);
    await runAssistantStream(showThinking, (assistantId) => {
      const callbacks = streamCallbacks(assistantId, showThinking);
      return api.agent.chatConfirmStream(
        { sessionId, approved, operator: user?.id ?? undefined },
        callbacks.onContent,
        callbacks.onThinking,
        callbacks.onClarification,
        callbacks.onConfirm,
      );
    });
    setConfirmSubmitting(false);
    // 已处理的确认从卡片列表移除
    setConfirms((prev) => prev.filter((c) => c.requestId !== requestId));
    queryClient.invalidateQueries({ queryKey: ['chat-sessions'] });
  };

  const handleReset = () => {
    resetChat();
    setInput('');
    clearImages();
  };

  /**
   * 复制消息原始内容（markdown回复复制markdown源码，纯文本复制纯文本）
   * @param msg
   */
  const handleCopy = async (msg: ChatMessage) => {
    try {
      await copyToClipboard(msg.content);
      message.success('已复制');
    } catch {
      message.error('复制失败，请检查浏览器剪贴板权限');
    }
  };

  return (
    <div style={{ height: '100%' }}>
      <Card
        style={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
        title={
          <Space>
            <MessageOutlined />
            <span>Agent 对话</span>
            {sessionId && <Tag color="blue">会话 {sessionId.slice(0, 8)}</Tag>}
          </Space>
        }
        extra={
          <Space>
            <Text type="secondary">Agent 类型</Text>
            <Select
              loading={agentTypesLoading}
              value={agentType || undefined}
              onChange={setAgentType}
              options={agentOptions}
              style={{ minWidth: 240 }}
              placeholder="请选择 Agent"
              showSearch
              optionFilterProp="label"
            />
            {agentType === 'localAgent' && (
              <WorkspaceSelector
                value={workspaceId}
                onChange={setWorkspaceId}
                workspaces={workspaces}
                loading={workspacesLoading}
                onManage={() => setWorkspaceManageOpen(true)}
              />
            )}
            <Text type="secondary">流式</Text>
            {agentStreamDisabled ? (
              <Tooltip title="该 Agent 已配置为非流式输出">
                <Switch checked={false} disabled size="small" />
              </Tooltip>
            ) : (
              <Switch checked={streamMode} onChange={setStreamMode} size="small" />
            )}
            <Button size="small" icon={<ReloadOutlined />} onClick={handleReset}>
              新对话
            </Button>
          </Space>
        }
        styles={{ body: { padding: 0, flex: 1, minHeight: 0, display: 'flex', flexDirection: 'row', overflow: 'hidden' } }}
      >
        <div
          style={{
            width: 232,
            flexShrink: 0,
            borderRight: '1px solid #f0f0f0',
            display: 'flex',
            flexDirection: 'column',
            background: '#fff',
          }}
        >
          <div style={{ padding: '10px 12px', borderBottom: '1px solid #f0f0f0', flexShrink: 0 }}>
            <Space size={6}>
              <HistoryOutlined style={{ color: '#1677ff' }} />
              <Text strong style={{ fontSize: 13 }}>历史话题</Text>
            </Space>
          </div>
          <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: 6 }}>
            {topicsLoading ? (
              <Text type="secondary" style={{ fontSize: 12, padding: '8px 10px', display: 'block' }}>
                加载中…
              </Text>
            ) : topics.length === 0 ? (
              <Empty
                description="暂无历史话题"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ marginTop: 48 }}
              />
            ) : (
              pagedTopics.map((t) => {
                const active = t.sessionId === sessionId;
                return (
                  <div
                    key={t.sessionId}
                    className="chat-topic-item"
                    onClick={() => void restoreSession(t)}
                    style={{
                      padding: '8px 10px',
                      borderRadius: 6,
                      marginBottom: 2,
                      background: active ? '#e6f4ff' : 'transparent',
                    }}
                  >
                    <div
                      title={t.sessionTitle || t.sessionId}
                      style={{
                        fontSize: 13,
                        color: active ? '#1677ff' : 'inherit',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {t.sessionTitle || `会话 ${t.sessionId.slice(0, 8)}`}
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {formatDate(t.updateTime ?? '')}
                    </Text>
                  </div>
                );
              })
            )}
          </div>
          {topics.length > TOPIC_PAGE_SIZE && (
            <div
              style={{
                flexShrink: 0,
                borderTop: '1px solid #f0f0f0',
                padding: '6px 8px',
                display: 'flex',
                justifyContent: 'center',
              }}
            >
              <Pagination
                simple
                size="small"
                current={topicPage}
                pageSize={TOPIC_PAGE_SIZE}
                total={topics.length}
                onChange={setTopicPage}
              />
            </div>
          )}
        </div>
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <div
          ref={scrollRef}
          style={{
            flex: 1,
            minHeight: 0,
            overflowY: 'auto',
            padding: 16,
            background: '#fafafa',
          }}
        >
          {/* 用户消息悬浮显示复制按钮（绝对定位到气泡下方，不遮文字不占布局） */}
          <style>{`
            .user-msg-row { position: relative; }
            .user-msg-row .user-copy-btn { opacity: 0; transition: opacity 0.15s; }
            .user-msg-row:hover .user-copy-btn { opacity: 1; }
          `}</style>
          {messages.length === 0 ? (
            <Empty
              description="选择 Agent 开始对话"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              style={{ marginTop: 140 }}
            />
          ) : (
            messages.map((m) => (
              <div
                key={m.id}
                className={m.role === 'user' ? 'user-msg-row' : undefined}
                style={{
                  display: 'flex',
                  justifyContent: m.role === 'user' ? 'flex-end' : 'flex-start',
                  marginBottom: 12,
                  gap: 8,
                }}
              >
                {m.role === 'assistant' && (
                  <AgentIconView icon={currentAgent?.typeIcon} size={28} />
                )}
                <div
                  style={{
                    maxWidth: '75%',
                    padding: '8px 12px',
                    borderRadius: 8,
                    background: m.role === 'user' ? '#1677ff' : '#fff',
                    color: m.role === 'user' ? '#fff' : 'inherit',
                    border: m.role === 'user' ? 'none' : '1px solid #f0f0f0',
                    wordBreak: 'break-word',
                  }}
                >
                  {showThinking && m.role === 'assistant' && m.thinking && (
                    <details style={{ marginBottom: 4 }}>
                      <summary
                        style={{
                          cursor: 'pointer',
                          userSelect: 'none',
                          fontSize: 12,
                          color: '#8c8c8c',
                        }}
                      >
                        思考过程
                      </summary>
                      <div
                        style={{
                          marginTop: 4,
                          maxHeight: 240,
                          overflowY: 'auto',
                          whiteSpace: 'pre-wrap',
                          background: '#fafafa',
                          borderRadius: 6,
                          padding: 8,
                          fontSize: 12,
                          color: '#8c8c8c',
                        }}
                      >
                        {m.thinking}
                      </div>
                    </details>
                  )}
                  {m.role === 'user' && m.images && m.images.length > 0 && (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: m.content ? 6 : 0 }}>
                      <Image.PreviewGroup>
                        {m.images.map((img, idx) => (
                          <Image
                            key={`${idx}-${img.slice(-12)}`}
                            src={img}
                            alt={`图片${idx + 1}`}
                            width={96}
                            style={{ borderRadius: 6, objectFit: 'cover' }}
                          />
                        ))}
                      </Image.PreviewGroup>
                    </div>
                  )}
                  {m.content ? (
                    m.role === 'assistant' ? (
                      <MarkdownContent content={m.content} />
                    ) : (
                      <div style={{ whiteSpace: 'pre-wrap' }}>{collapseFileRefs(m.content)}</div>
                    )
                  ) : (
                    m.streaming && '思考中…'
                  )}
                  {m.role === 'assistant' && m.content && !m.streaming && (
                    <>
                      <Button
                        type="text"
                        size="small"
                        icon={<CopyOutlined />}
                        onClick={() => void handleCopy(m)}
                        style={{ marginTop: 2, marginLeft: -8, fontSize: 12, color: '#8c8c8c' }}
                      >
                        复制
                      </Button>
                      {/* MCP Apps UI 沙箱渲染：按工具结果声明分发 form/chart/card 模板 */}
                      {m.mcpApp && (
                        <McpAppRenderer
                          declaration={m.mcpApp.declaration}
                          payload={m.mcpApp.payload}
                        />
                      )}
                    </>
                  )}
                </div>
                {m.role === 'user' && m.content && (
                  <Button
                    type="text"
                    size="small"
                    className="user-copy-btn"
                    icon={<CopyOutlined />}
                    onClick={() => void handleCopy(m)}
                    title="复制"
                    style={{
                      position: 'absolute',
                      top: '100%',
                      right: 0,
                      marginTop: 2,
                      fontSize: 12,
                      color: '#8c8c8c',
                      background: '#fafafa',
                      zIndex: 1,
                    }}
                  />
                )}
                {m.role === 'user' && (
                  <UserOutlined style={{ color: '#8c8c8c', fontSize: 18, marginTop: 4 }} />
                )}
              </div>
            ))
          )}
        </div>
        <div style={{ padding: '12px 16px 16px', flexShrink: 0 }}>
          <ApprovalCards
            requests={approvalRequests}
            onHandled={removeApprovalRequest}
            approvedBy={user?.id}
          />
          <ClarificationCards
            questions={clarifications}
            onSubmit={handleSubmitClarification}
            submitting={clarificationSubmitting}
          />
          <ConfirmCards
            confirms={confirms}
            onDecide={handleConfirmDecide}
            submitting={confirmSubmitting}
          />
          <style>{`
            .chat-input-box { transition: border-color 0.2s, box-shadow 0.2s; }
            .chat-input-box:focus-within { border-color: #1677ff; box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.08); }
            .chat-topic-item { cursor: pointer; transition: background-color 0.2s; }
            .chat-topic-item:hover { background-color: #f5f5f5; }
          `}</style>
          <div
            className="chat-input-box"
            style={{
              border: '1px solid #e5e7eb',
              borderRadius: 16,
              background: '#fff',
              padding: '12px 16px 8px',
              boxShadow: '0 2px 12px rgba(15, 23, 42, 0.05)',
            }}
          >
            <ImagePreviewStrip images={images} onRemove={removeImage} />
            <TextArea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPaste={handlePaste}
              placeholder="输入消息，Enter 发送，Shift + Enter 换行，可粘贴图片"
              autoSize={{ minRows: 1, maxRows: 6 }}
              variant="borderless"
              disabled={sending}
              style={{ fontSize: 14, padding: 0, resize: 'none' }}
              onPressEnter={(e) => {
                if (!e.shiftKey) {
                  e.preventDefault();
                  void handleSend();
                }
              }}
            />
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginTop: 6,
              }}
            >
              <Space size={6}>
                <AgentIconView icon={currentAgent?.typeIcon} size={18} />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  当前 Agent：{currentAgent?.typeName ?? (agentType || '未选择')}
                </Text>
                {currentAgent?.supportImage === 1 && <Tag color="green">支持图片</Tag>}
              </Space>
              <Button
                type="primary"
                shape="circle"
                icon={<SendOutlined />}
                onClick={handleSend}
                loading={sending}
                disabled={!input.trim() && images.length === 0}
              />
            </div>
          </div>
        </div>
        </div>
      </Card>
      <WorkspaceManageModal
        open={workspaceManageOpen}
        userId={user?.id}
        workspaces={workspaces}
        loading={workspacesLoading}
        onClose={() => setWorkspaceManageOpen(false)}
        onChanged={() => refreshWorkspaces()}
      />
    </div>
  );
};

export default AgentChatPage;
