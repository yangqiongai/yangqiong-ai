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
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Components } from 'react-markdown';
import { App, Button, Input, Tooltip, Typography } from 'antd';
import { AppstoreAddOutlined, ClearOutlined, CodeOutlined, RobotOutlined, SendOutlined, UserOutlined } from '@ant-design/icons';
import { api } from '@/services';
import { JsonViewer } from '@/components/JsonViewer';
import type { WorkflowDefinition } from '@yq/workflow';

const { TextArea } = Input;
const { Text } = Typography;

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  streaming?: boolean;
}

const newId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const mdComponents: Components = {
  h2: (props) => (
    <h2 style={{ fontSize: 14, fontWeight: 600, margin: '10px 0 4px' }} {...props} />
  ),
  h3: (props) => (
    <h3 style={{ fontSize: 13, fontWeight: 600, margin: '8px 0 4px' }} {...props} />
  ),
  p: (props) => <p style={{ margin: '4px 0' }} {...props} />,
  ul: (props) => <ul style={{ margin: '4px 0', paddingLeft: 20 }} {...props} />,
  ol: (props) => <ol style={{ margin: '4px 0', paddingLeft: 20 }} {...props} />,
  li: (props) => <li style={{ margin: '2px 0' }} {...props} />,
  table: (props) => (
    <table
      style={{ borderCollapse: 'collapse', margin: '8px 0', fontSize: 12, width: '100%', display: 'block', overflowX: 'auto' }}
      {...props}
    />
  ),
  th: (props) => (
    <th
      style={{ border: '1px solid #e0e0e0', background: '#fafafa', padding: '4px 10px', textAlign: 'left', whiteSpace: 'nowrap' }}
      {...props}
    />
  ),
  td: (props) => (
    <td style={{ border: '1px solid #e0e0e0', padding: '4px 10px' }} {...props} />
  ),
  code: ({ className, children, ...rest }) => {
    const isBlock = /language-/.test(className ?? '');
    if (isBlock) {
      return (
        <code style={{ fontFamily: 'SFMono-Regular, Consolas, monospace', fontSize: 12 }} className={className} {...rest}>
          {children}
        </code>
      );
    }
    return (
      <code
        style={{ background: '#f5f5f5', borderRadius: 4, padding: '1px 4px', fontFamily: 'SFMono-Regular, Consolas, monospace', fontSize: 12 }}
        {...rest}
      >
        {children}
      </code>
    );
  },
  pre: (props) => (
    <pre
      style={{
        margin: '8px 0',
        overflowX: 'auto',
        background: '#282c34',
        color: '#abb2bf',
        borderRadius: 6,
        padding: 12,
        fontSize: 12,
        lineHeight: 1.6,
      }}
      {...props}
    />
  ),
};

/**
 * 提取回复中最后的工作流定义JSON代码块
 * AI 可能在说明中先给出片段示例，取最后一个完整定义最稳妥
 * @param content
 * @return
 */
function extractWorkflowJson(content: string): WorkflowDefinition | null {
  const jsonBlocks = content.match(/```json\s*([\s\S]*?)```/g);
  if (!jsonBlocks || jsonBlocks.length === 0) return null;
  for (let i = jsonBlocks.length - 1; i >= 0; i--) {
    const block = jsonBlocks[i].replace(/^```json\s*/, '').replace(/```$/, '');
    try {
      const parsed = JSON.parse(block);
      if (parsed && Array.isArray(parsed.nodes) && Array.isArray(parsed.edges)) {
        return parsed as WorkflowDefinition;
      }
    } catch {
      // 解析失败继续尝试前一个代码块
    }
  }
  return null;
}

/**
 * 将AI回复拆分为业务描述与JSON定义代码块
 * JSON定义属于系统内部结构，从展示内容中剥离，默认折叠展示
 * @param content
 * @return
 */
function splitJsonBlocks(content: string): { md: string; blocks: string[] } {
  const blocks: string[] = [];
  const md = content.replace(/```json\s*([\s\S]*?)```/g, (_match, json: string) => {
    blocks.push(json);
    return '';
  });
  return { md: md.trim(), blocks };
}

/**
 * 归一化行首Markdown标记的空格
 * 模型流式输出偶尔丢失标记后的空格（如"##标题"、"-要点"、"1.步骤"），导致无法渲染为标题和列表
 * @param md
 * @return
 */
function normalizeMdMarkers(md: string): string {
  const lines = md.split('\n');
  let inCodeFence = false;
  const result = lines.map((line) => {
    if (/^\s*```/.test(line)) {
      inCodeFence = !inCodeFence;
      return line;
    }
    if (inCodeFence) return line;
    return line
      // 句末标点后紧跟标题标记时补换行，避免标题被并入上一段落
      .replace(/([。！？；!?])(\s*)(#{1,6})\s*(?=\S)/g, '$1\n\n$2 ')
      .replace(/^(#{1,6})(?=[^\s#])/, '$1 ')
      .replace(/^(\s*)[-*](?=[^\s\-*])/, '$1- ')
      .replace(/^(\s*)(\d{1,2})\.(?=[^\s.])/, '$1$2. ');
  });
  return result.join('\n');
}

/**
 * 折叠的JSON定义展示块
 * 默认收起，点击展开后用JSON高亮组件展示格式化内容
 */
const JsonBlock: React.FC<{ raw: string }> = ({ raw }) => {
  const [expanded, setExpanded] = useState(false);
  const formatted = useMemo(() => {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2);
    } catch {
      return raw;
    }
  }, [raw]);
  return (
    <div style={{ marginTop: 8 }}>
      <Button
        size="small"
        type="dashed"
        icon={<CodeOutlined />}
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? '收起流程定义 JSON' : '查看流程定义 JSON（系统内部结构）'}
      </Button>
      {expanded && (
        <div style={{ marginTop: 8 }}>
          <JsonViewer value={formatted} />
        </div>
      )}
    </div>
  );
};

/**
 * 对话式流程设计面板
 * 通过"流程设计助手"Agent 用自然语言生成/修改工作流定义，并可一键应用到画布
 */
export const WorkflowDesignPanel: React.FC<{
  definitionName?: string;
  onApply: (definition: WorkflowDefinition) => void;
}> = ({ definitionName, onApply }) => {
  const { message, modal } = App.useApp();
  const [sessionId, setSessionId] = useState<string>('');
  const [input, setInput] = useState<string>('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sending, setSending] = useState<boolean>(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  /**
   * 清空当前对话并重置会话，让AI忘记之前的上下文
   * @return
   */
  const handleClear = () => {
    if (sending || messages.length === 0) return;
    setSessionId('');
    setMessages([]);
    message.success('已清空对话');
  };

  const handleSend = async () => {
    const content = input.trim();
    if (!content || sending) return;

    let activeSessionId = sessionId;
    if (!activeSessionId) {
      activeSessionId = crypto.randomUUID();
      setSessionId(activeSessionId);
    }

    const userMessage: ChatMessage = { id: newId(), role: 'user', content };
    const assistantMessage: ChatMessage = { id: newId(), role: 'assistant', content: '', streaming: true };
    setMessages((prev) => [...prev, userMessage, assistantMessage]);
    setInput('');
    setSending(true);

    // 携带当前画布流程名，方便 Agent 修改或关联已有流程
    const inputText = definitionName ? `当前流程名称：${definitionName}\n${content}` : content;

    try {
      await api.agent.chatStream(
        { agentCode: 'workflowDesigner', input: [inputText], sessionId: activeSessionId },
        (chunk: string) => {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantMessage.id ? { ...m, content: m.content + chunk } : m)),
          );
        },
      );
      setMessages((prev) => prev.map((m) => (m.id === assistantMessage.id ? { ...m, streaming: false } : m)));
    } catch (e) {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMessage.id
            ? { ...m, content: m.content + `\n\n[请求失败：${e instanceof Error ? e.message : '未知错误'}]`, streaming: false }
            : m,
        ),
      );
    } finally {
      setSending(false);
    }
  };

  const handleApply = (content: string) => {
    const definition = extractWorkflowJson(content);
    if (!definition) {
      message.warning('未从回复中解析到完整的工作流定义 JSON');
      return;
    }
    modal.confirm({
      title: '应用到画布',
      content: '将用 AI 生成的流程替换当前画布内容，未保存的手动修改会丢失，是否继续？',
      okText: '应用',
      cancelText: '取消',
      onOk: () => {
        onApply(definition);
      },
    });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 8px',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <RobotOutlined style={{ fontSize: 18, color: '#1677ff' }} />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 14, fontWeight: 600, lineHeight: '20px' }}>AI 流程设计助手</div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              对话有记忆，可连续追问修改流程
            </Text>
          </div>
        </div>
        <Tooltip title={messages.length === 0 ? '暂无对话' : '清空对话并重置上下文'}>
          <Button size="small" type="text" icon={<ClearOutlined />} disabled={messages.length === 0} onClick={handleClear}>
            清空对话
          </Button>
        </Tooltip>
      </div>
      <div
        ref={scrollRef}
        style={{ flex: 1, overflowY: 'auto', padding: '12px 8px', display: 'flex', flexDirection: 'column', gap: 12 }}
      >
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', color: '#999', padding: '48px 8px' }}>
            <RobotOutlined style={{ fontSize: 28, marginBottom: 8 }} />
            <div>用自然语言描述你想设计的流程，例如：</div>
            <div style={{ marginTop: 4, color: '#bbb', fontSize: 12 }}>
              "创建一个流程：用默认助手分析文本，再通过HTTP发送通知"
            </div>
          </div>
        )}
        {messages.map((m) => {
          // AI回复拆分为业务描述与JSON定义，JSON默认折叠展示，并归一化行首标记空格
          const { md: rawMd, blocks } = m.role === 'assistant' ? splitJsonBlocks(m.content) : { md: m.content, blocks: [] };
          const md = m.role === 'assistant' ? normalizeMdMarkers(rawMd) : rawMd;
          return (
          <div key={m.id}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              {m.role === 'user' ? <UserOutlined /> : <RobotOutlined />}
              <Text type="secondary" style={{ fontSize: 12 }}>
                {m.role === 'user' ? '我' : '流程设计助手'}
              </Text>
            </div>
            <div
              style={{
                padding: '8px 12px',
                borderRadius: 8,
                background: m.role === 'user' ? '#e6f4ff' : '#f6f6f6',
                fontSize: 13,
                lineHeight: 1.7,
                wordBreak: 'break-word',
              }}
            >
              {md && (
                <ReactMarkdown remarkPlugins={[remarkGfm]} components={mdComponents}>
                  {md}
                </ReactMarkdown>
              )}
              {blocks.map((block, i) => (
                <JsonBlock key={`${m.id}-json-${i}`} raw={block} />
              ))}
            </div>
            {m.role === 'assistant' && !m.streaming && extractWorkflowJson(m.content) && (
              <Button type="primary" icon={<AppstoreAddOutlined />} style={{ marginTop: 8 }} onClick={() => handleApply(m.content)}>
                应用到画布
              </Button>
            )}
          </div>
          );
        })}
      </div>
      <div style={{ borderTop: '1px solid #f0f0f0', padding: '12px 8px 8px' }}>
        <div style={{ position: 'relative', border: '1px solid #d9d9d9', borderRadius: 10, background: '#fff', transition: 'border-color 0.2s' }}>
          <TextArea
            variant="borderless"
            style={{ flex: 1, resize: 'none', padding: '10px 12px 44px', fontSize: 13 }}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={(e) => {
              if (!e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="描述要设计的流程，如：创建流程，先分析文本再发送通知"
            autoSize={{ minRows: 4, maxRows: 8 }}
          />
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={sending}
            onClick={handleSend}
            style={{ position: 'absolute', right: 10, bottom: 10, boxShadow: 'none' }}
          >
            发送
          </Button>
        </div>
        <Text type="secondary" style={{ fontSize: 12, display: 'block', textAlign: 'right', marginTop: 4 }}>
          Enter 发送，Shift + Enter 换行
        </Text>
      </div>
    </div>
  );
};
