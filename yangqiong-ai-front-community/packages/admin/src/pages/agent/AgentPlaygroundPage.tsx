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
import React, { useMemo, useRef, useState, useEffect } from 'react';
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '@/services';
import type {
  AgentChatRequest,
  AgentChatResponse,
  AgentTypeInfo,
} from '@yangqiong/shared';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  createTime?: string;
  streaming?: boolean;
}

/**
 * 将 Agent 响应输出转为可展示文本
 */
const extractContent = (res: AgentChatResponse): string => {
  if (!res.success) {
    return res.errorMessage ?? '对话失败';
  }
  if (Array.isArray(res.output) && res.output.length > 0) {
    return res.output
      .map((item) => (typeof item === 'string' ? item : JSON.stringify(item)))
      .join('');
  }
  return res.errorMessage ?? '';
};

/**
 * Agent 测试
 */
export const AgentPlaygroundPage: React.FC = () => {
  const { message } = App.useApp();
  const [agentType, setAgentType] = useState<string>('');
  const [input, setInput] = useState('');
  const [streamMode, setStreamMode] = useState(true);
  const [sessionId, setSessionId] = useState<string | undefined>(undefined);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [goal, setGoal] = useState('');
  const [orchestrationAgents, setOrchestrationAgents] = useState<string[]>([]);
  const [orchestrationResult, setOrchestrationResult] = useState<unknown>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // 加载 Agent 类型列表用于下拉选择
  const { data: agentTypePage, isLoading: agentTypeLoading } = useQuery({
    queryKey: ['agent-type-list', 'playground'],
    queryFn: () => api.agent.type.list({ page: 1, size: 200 }),
  });

  const agentTypeOptions = useMemo<{ label: string; value: string }[]>(() => {
    const list = agentTypePage?.list ?? [];
    return list.map((item: AgentTypeInfo) => ({
      label: item.typeName
        ? `${item.typeName}（${item.typeCode}）`
        : item.typeCode,
      value: item.typeCode,
    }));
  }, [agentTypePage]);

  useEffect(() => {
    if (!agentType && agentTypeOptions.length > 0) {
      setAgentType(agentTypeOptions[0].value);
    }
  }, [agentTypeOptions, agentType]);

  // 自动滚动到底部
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages]);

  const chatMutation = useMutation({
    mutationFn: async (req: AgentChatRequest) => api.agent.chat(req),
  });

  const sendDisabled = !agentType || !input.trim() || chatMutation.isPending;

  const handleSend = async () => {
    if (sendDisabled) {
      return;
    }
    const userText = input.trim();
    const userMessage: ChatMessage = {
      role: 'user',
      content: userText,
      createTime: new Date().toISOString(),
    };
    const assistantPlaceholder: ChatMessage = {
      role: 'assistant',
      content: '',
      streaming: true,
    };
    setMessages((prev) => [...prev, userMessage, assistantPlaceholder]);
    setInput('');

    // 本地维护会话 ID，首次发送时生成
    const currentSessionId = sessionId ?? crypto.randomUUID();
    if (!sessionId) {
      setSessionId(currentSessionId);
    }

    const req: AgentChatRequest = {
      agentCode: agentType,
      sessionId: currentSessionId,
      input: [userText],
    };

    try {
      if (streamMode) {
        await api.agent.chatStream(req, (chunk: string) => {
          setMessages((prev) => {
            const next = [...prev];
            const last = next[next.length - 1];
            if (last && last.role === 'assistant') {
              next[next.length - 1] = {
                ...last,
                content: last.content + chunk,
              };
            }
            return next;
          });
        });
        setMessages((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          if (last && last.role === 'assistant') {
            next[next.length - 1] = { ...last, streaming: false };
          }
          return next;
        });
      } else {
        const res: AgentChatResponse = await chatMutation.mutateAsync(req);
        const content = extractContent(res);
        setMessages((prev) => {
          const next = [...prev];
          const last = next[next.length - 1];
          if (last && last.role === 'assistant') {
            next[next.length - 1] = {
              ...last,
              content,
              streaming: false,
            };
          }
          return next;
        });
      }
    } catch (err) {
      const errMsg = err instanceof Error ? err.message : '对话失败';
      setMessages((prev) => {
        const next = [...prev];
        const last = next[next.length - 1];
        if (last && last.role === 'assistant') {
          next[next.length - 1] = {
            ...last,
            content: `请求失败：${errMsg}`,
            streaming: false,
          };
        }
        return next;
      });
      message.error(errMsg);
    }
  };

  const handleClear = () => {
    setMessages([]);
    setSessionId(undefined);
  };

  const orchestrationMutation = useMutation({
    mutationFn: async (req: Record<string, unknown>) =>
      api.agent.orchestration.run(req),
    onSuccess: (data) => {
      setOrchestrationResult(data);
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '编排执行失败');
    },
  });

  const handleRunOrchestration = () => {
    if (!goal.trim() || orchestrationAgents.length === 0) {
      message.warning('请填写编排目标并选择至少一个 Agent 类型');
      return;
    }
    const req: Record<string, unknown> = {
      // 后端 AgentRequest 从 input 提取任务描述，agentTypes 供后端扩展使用
      input: goal.trim(),
      agentTypes: orchestrationAgents,
    };
    orchestrationMutation.mutate(req);
  };

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        Agent 测试
      </Title>
      <Row gutter={16}>
        <Col xs={24} lg={14}>
          <Card
            title={
              <Space>
                <SendOutlined />
                <span>对话区</span>
                {sessionId && <Tag color="blue">会话 {sessionId.slice(0, 8)}</Tag>}
              </Space>
            }
            extra={
              <Space>
                <Text type="secondary">流式</Text>
                <Switch checked={streamMode} onChange={setStreamMode} size="small" />
                <Button size="small" onClick={handleClear}>
                  清空
                </Button>
              </Space>
            }
            styles={{ body: { padding: 0 } }}
          >
            <div style={{ padding: '12px 16px 0' }}>
              <Space wrap>
                <Text type="secondary">Agent 类型：</Text>
                <Select
                  loading={agentTypeLoading}
                  value={agentType || undefined}
                  onChange={setAgentType}
                  options={agentTypeOptions}
                  style={{ minWidth: 260 }}
                  placeholder="请选择 Agent 类型"
                  showSearch
                  optionFilterProp="label"
                />
              </Space>
            </div>
            <div
              ref={listRef}
              style={{
                height: 420,
                overflowY: 'auto',
                padding: 16,
                background: '#fafafa',
                margin: '12px 0',
                borderRadius: 6,
              }}
            >
              {messages.length === 0 ? (
                <Empty description="开始与 Agent 对话" style={{ marginTop: 120 }} />
              ) : (
                messages.map((msg, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'flex',
                      justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
                      marginBottom: 12,
                    }}
                  >
                    <div
                      style={{
                        maxWidth: '80%',
                        padding: '8px 12px',
                        borderRadius: 8,
                        background: msg.role === 'user' ? '#1677ff' : '#fff',
                        color: msg.role === 'user' ? '#fff' : 'inherit',
                        border: msg.role === 'user' ? 'none' : '1px solid #f0f0f0',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                      }}
                    >
                      {msg.content || (msg.streaming ? '思考中…' : '')}
                      {msg.streaming && msg.content && (
                        <Spin size="small" style={{ marginLeft: 6 }} />
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>
            <div style={{ padding: '0 16px 16px' }}>
              <Space.Compact style={{ width: '100%' }}>
                <TextArea
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="输入消息，回车发送，Shift+回车换行"
                  autoSize={{ minRows: 1, maxRows: 4 }}
                  onPressEnter={(e) => {
                    if (!e.shiftKey) {
                      e.preventDefault();
                      handleSend();
                    }
                  }}
                />
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  onClick={handleSend}
                  loading={chatMutation.isPending}
                  disabled={sendDisabled}
                  style={{ height: 'auto' }}
                >
                  发送
                </Button>
              </Space.Compact>
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={10}>
          <Card
            title={
              <Space>
                <ThunderboltOutlined />
                <span>编排方案预览</span>
              </Space>
            }
          >
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <div>
                <Text type="secondary">编排目标</Text>
                <TextArea
                  value={goal}
                  onChange={(e) => setGoal(e.target.value)}
                  placeholder="描述需要编排完成的目标"
                  autoSize={{ minRows: 2, maxRows: 4 }}
                  style={{ marginTop: 4 }}
                />
              </div>
              <div>
                <Text type="secondary">参与 Agent</Text>
                <Select
                  mode="multiple"
                  value={orchestrationAgents}
                  onChange={setOrchestrationAgents}
                  options={agentTypeOptions}
                  placeholder="选择参与编排的 Agent 类型"
                  style={{ width: '100%', marginTop: 4 }}
                  optionFilterProp="label"
                />
              </div>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                onClick={handleRunOrchestration}
                loading={orchestrationMutation.isPending}
              >
                执行编排
              </Button>
              <div>
                <Text type="secondary">编排结果</Text>
                <pre
                  style={{
                    background: '#f5f5f5',
                    padding: 12,
                    borderRadius: 6,
                    maxHeight: 360,
                    overflow: 'auto',
                    fontSize: 12,
                    margin: '4px 0 0',
                  }}
                >
                  {orchestrationResult
                    ? JSON.stringify(orchestrationResult, null, 2)
                    : '// 暂无编排结果，点击「执行编排」生成方案'}
                </pre>
              </div>
            </Space>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default AgentPlaygroundPage;
