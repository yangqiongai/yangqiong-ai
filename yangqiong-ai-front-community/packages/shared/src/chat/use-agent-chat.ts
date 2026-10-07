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
import { extractMcpAppsUiDeclaration } from '../components/McpAppRenderer';
import type {
  AgentChatRequest,
  AgentChatResponse,
  ClarificationRequestInfo,
  ConfirmRequestInfo,
  QuotaWarningInfo,
} from '../types/agent';
import { newId, type ChatMessage } from './chat-message';

/**
 * 对话接口最小结构（由各端 api 实例适配）
 */
export interface AgentChatApi {
  chat(req: AgentChatRequest): Promise<AgentChatResponse>;

  chatStream(
    req: AgentChatRequest,
    onContent: (chunk: string) => void,
    onThinking: (chunk: string) => void,
    onClarification?: (info: ClarificationRequestInfo) => void,
    onConfirm?: (info: ConfirmRequestInfo) => void,
    onQuotaWarning?: (info: QuotaWarningInfo) => void,
  ): Promise<void>;
}

/**
 * hook 选项
 */
export interface UseAgentChatOptions {
  /**
   * 发送失败时的旁路通知（如toast），错误信息已写入error与消息内容
   */
  onError?: (message: string) => void;

  /**
   * 失败时写入消息内容的错误前缀
   */
  errorPrefix?: string;

  /**
   * 失败时错误文案是否写入消息气泡（页面已用常驻红条展示error时置false，避免同一错误双重展示）
   */
  bubbleError?: boolean;
}

/**
 * 发送对话参数
 */
export interface SendAgentChatOptions {
  req: AgentChatRequest;

  /**
   * 用户消息展示内容（含引用文件注记等，可与req.input不同）
   */
  userContent: string;

  /**
   * 随消息发送的图片（dataURL，转换为后端image输入块）
   */
  images?: string[];

  stream?: boolean;

  showThinking?: boolean;

  /**
   * 发送前是否清空待处理的澄清与确认（对话页续聊为true，无卡片场景传false）
   */
  clearPending?: boolean;
}

/**
 * Agent对话发送流程（消息状态、会话ID、流式回调去重、MCP UI提取、失败兜底）
 * @param api
 * @param options
 * @return
 */
export const useAgentChat = (api: AgentChatApi, options?: UseAgentChatOptions) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [sessionId, setSessionId] = useState<string>('');
  const [sending, setSending] = useState<boolean>(false);
  const [error, setError] = useState<string>('');
  const [clarifications, setClarifications] = useState<ClarificationRequestInfo[]>([]);
  const [confirms, setConfirms] = useState<ConfirmRequestInfo[]>([]);
  const [quotaWarning, setQuotaWarning] = useState<string>('');

  const errorPrefix = options?.errorPrefix ?? '[出错] ';

  const notifyError = (message: string): void => {
    setError(message);
    options?.onError?.(message);
  };

  /**
   * 取当前会话ID，首次调用时本地生成（后端响应不回传sessionId）
   * @return
   */
  const ensureSessionId = (): string => {
    if (sessionId) {
      return sessionId;
    }
    const generated = crypto.randomUUID();
    setSessionId(generated);
    return generated;
  };

  /**
   * 流式回调组：内容与思考增量追加到指定消息，澄清与确认按标识去重入列
   * @param assistantId
   * @param showThinking
   * @return
   */
  const streamCallbacks = (assistantId: string, showThinking: boolean) => ({
    onContent: (chunk: string) => {
      const payload = chunk || '';
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, content: m.content + payload }
            : m,
        ),
      );
    },
    onThinking: (chunk: string) => {
      if (!showThinking) {
        return;
      }
      const payload = chunk || '';
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, thinking: (m.thinking ?? '') + payload }
            : m,
        ),
      );
    },
    onClarification: (info: ClarificationRequestInfo) => {
      // AI在对话流中向用户提问，同toolCallId去重后加入澄清卡片
      setClarifications((prev) =>
        prev.some((c) => c.toolCallId === info.toolCallId) ? prev : [...prev, info],
      );
    },
    onConfirm: (info: ConfirmRequestInfo) => {
      // 引擎审批暂停要求人工确认工具调用，同requestId去重后加入确认卡片
      setConfirms((prev) =>
        prev.some((c) => c.requestId === info.requestId) ? prev : [...prev, info],
      );
    },
    onQuotaWarning: (info: QuotaWarningInfo) => {
      // 配额/预算超限告警（流头部一次性推送），展示为输入区上方警示条
      setQuotaWarning(info.message ?? '配额超限告警');
    },
  });

  /**
   * 结束指定助手消息的流式状态（提取MCP Apps UI声明，无声明或render_allowed=0时不渲染）
   * @param assistantId
   */
  const finalizeAssistant = (assistantId: string): void => {
    setMessages((prev) =>
      prev.map((m) => {
        if (m.id !== assistantId) {
          return m;
        }
        const mcpApp = extractMcpAppsUiDeclaration(m.content) ?? undefined;
        return { ...m, streaming: false, mcpApp };
      }),
    );
  };

  /**
   * 流失败兜底：落定流式状态并写入错误内容与error
   * @param assistantId
   * @param message
   */
  const failAssistant = (assistantId: string, message: string): void => {
    if (options?.bubbleError === false) {
      // 错误由页面常驻红条统一展示：移除无内容的占位气泡避免双重展示，已有内容（流中断）保留原样
      setMessages((prev) => prev.filter((m) => m.id !== assistantId || m.content !== ''));
    } else {
      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantId
            ? { ...m, streaming: false, content: `${errorPrefix}${message}` }
            : m,
        ),
      );
    }
    notifyError(message);
  };

  /**
   * 以流式恢复方式追加一条assistant消息并承接回调（澄清回答/确认决策等续流场景）
   * @param showThinking
   * @param streamCall
   * @return
   */
  const runAssistantStream = async (
    showThinking: boolean,
    streamCall: (assistantId: string) => Promise<void>,
  ): Promise<void> => {
    const assistantId = newId();
    setMessages((prev) => [
      ...prev,
      { id: assistantId, role: 'assistant', content: '', streaming: true },
    ]);
    try {
      await streamCall(assistantId);
      finalizeAssistant(assistantId);
    } catch (err) {
      failAssistant(assistantId, err instanceof Error ? err.message : '对话失败');
    }
  };

  /**
   * 发送对话消息（自动补齐sessionId、构造双方消息、流式/非流式调度）
   * @param opts
   * @return
   */
  const sendMessage = async (opts: SendAgentChatOptions): Promise<string> => {
    const {
      req,
      userContent,
      images = [],
      stream = true,
      showThinking = true,
      clearPending = false,
    } = opts;
    const hasText = userContent.trim().length > 0;
    if ((!hasText && images.length === 0) || sending) {
      return '';
    }
    const activeSessionId = req.sessionId || ensureSessionId();
    // 输入块：文本+图片合并，纯图片无文本时追加兜底提示词
    const baseInput = (req.input ?? []).filter(
      (item) => !(typeof item === 'string' && item.trim() === ''),
    );
    const imageItems = images.map((dataUrl) => ({ type: 'image', dataUrl }) as const);
    const input: unknown[] = [...baseInput, ...imageItems];
    if (images.length > 0 && !baseInput.some((item) => typeof item === 'string' && item.trim() !== '')) {
      input.push('请分析图片内容');
    }
    const request: AgentChatRequest = { ...req, sessionId: activeSessionId, input };
    const userMessage: ChatMessage = {
      id: newId(),
      role: 'user',
      content: userContent,
      ...(images.length > 0 ? { images } : {}),
      createTime: new Date().toISOString(),
    };
    const assistantMessage: ChatMessage = {
      id: newId(),
      role: 'assistant',
      content: '',
      streaming: true,
      createTime: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMessage, assistantMessage]);
    // 发送新消息时清空上一轮残留的澄清提问、确认请求与配额警示
    if (clearPending) {
      setClarifications([]);
      setConfirms([]);
      setQuotaWarning('');
    }
    setSending(true);
    try {
      // 本次回复的展示行为按发送时刻的Agent配置与用户开关决定
      if (stream) {
        const callbacks = streamCallbacks(assistantMessage.id, showThinking);
        await api.chatStream(
          request,
          callbacks.onContent,
          callbacks.onThinking,
          callbacks.onClarification,
          callbacks.onConfirm,
          callbacks.onQuotaWarning,
        );
        finalizeAssistant(assistantMessage.id);
      } else {
        const resp: AgentChatResponse = await api.chat(request);
        const content = resp.success
          ? (resp.output ?? []).map((o) => String(o)).join('')
          : resp.errorMessage || '对话失败';
        // 同步路径配额告警随结果体返回，与流式budget_warning事件一致展示为警示条
        if (resp.quotaWarning) {
          setQuotaWarning(resp.quotaWarning);
        }
        const mcpApp = extractMcpAppsUiDeclaration(content) ?? undefined;
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantMessage.id
              ? {
                  ...m,
                  content,
                  streaming: false,
                  mcpApp,
                  createTime: new Date().toISOString(),
                }
              : m,
          ),
        );
      }
    } catch (err) {
      failAssistant(assistantMessage.id, err instanceof Error ? err.message : '对话失败');
    } finally {
      setSending(false);
    }
    return assistantMessage.id;
  };

  /**
   * 清空会话回到初始状态
   */
  const reset = (): void => {
    setMessages([]);
    setSessionId('');
    setError('');
    setClarifications([]);
    setConfirms([]);
    setQuotaWarning('');
  };

  return {
    messages,
    setMessages,
    sessionId,
    setSessionId,
    sending,
    error,
    setError,
    clarifications,
    setClarifications,
    confirms,
    setConfirms,
    quotaWarning,
    setQuotaWarning,
    ensureSessionId,
    streamCallbacks,
    finalizeAssistant,
    runAssistantStream,
    sendMessage,
    reset,
  };
};
