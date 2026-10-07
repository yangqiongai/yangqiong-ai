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
import type { HttpRequest } from './http';
import { getAuthHeaders } from '../utils/helpers';
import type {
  AgentChatRequest,
  AgentChatResponse,
  AgentDirectoryNode,
  AgentDirectorySaveRequest,
  AgentDirectoryTree,
  AgentTypeInfo,
  ClarificationRequestInfo,
  ConfirmRequestInfo,
  CreateScheduleRequest,
  PageQuery,
  PageResult,
  QuotaWarningInfo,
  ScheduleLogPage,
  SchedulerJob,
  UpdateScheduleRequest,
} from '../types';

export const createAgentApi = (http: HttpRequest) => ({
  processors: async () => {
    const list = await http.get<Array<{ code: string; name: string }>>('/api/agent/processors');
    return { list: list ?? [] };
  },
  type: {
    // 后端为 /api/agent/list 裸数组，字段与 AgentTypeInfo 前缀不同，此处映射并包装分页
    list: async (params?: PageQuery) => {
      const list = await http.get<Record<string, unknown>[]>('/api/agent/list');
      const mapped = (list ?? []).map(toAgentTypeInfo);
      const keyword = (params?.keyword ?? '').trim().toLowerCase();
      const filtered = keyword
        ? mapped.filter((t) =>
            [t.typeCode, t.typeName].some(
              (v) => v != null && v.toLowerCase().includes(keyword),
            ),
          )
        : mapped;
      const page = params?.page ?? 1;
      const size = params?.size ?? 10;
      const start = (page - 1) * size;
      return {
        list: filtered.slice(start, start + size),
        total: filtered.length,
        page,
        size,
      } as PageResult<AgentTypeInfo>;
    },
    get: (typeCode: string) => http.get<AgentTypeInfo>(`/api/agent/${typeCode}`),
    create: (data: Omit<AgentTypeInfo, 'id' | 'updateTime'>) =>
      http.post<AgentTypeInfo>('/api/agent', toAgentPayload(data)),
    update: (typeCode: string, data: Partial<AgentTypeInfo>) =>
      http.put<AgentTypeInfo>(`/api/agent/${typeCode}`, toAgentPayload(data)),
    // 切换启用/禁用（后端为 toggle 语义，返回最新 status）
    toggleStatus: (typeCode: string) =>
      http.put<{ success: boolean; agentCode: string; status: number }>(
        `/api/agent/${typeCode}/status`,
      ),
    // 后端接收数组，键为 agentCode/sortOrder
    sort: (items: { agentCode: string; sortOrder: number }[]) =>
      http.put<void>('/api/agent/sort', items),
    sync: () => http.post<void>('/api/agent/sync'),
  },
  // 智能体目录树管理（与工具/技能分类同款交互）
  directory: {
    tree: () => http.get<AgentDirectoryTree>('/api/agent/directory/tree'),
    create: (data: AgentDirectorySaveRequest) =>
      http.post<AgentDirectoryNode>('/api/agent/directory', data),
    update: (id: number, data: AgentDirectorySaveRequest) =>
      http.put<AgentDirectoryNode>(`/api/agent/directory/${id}`, data),
    remove: (id: number) => http.delete<void>(`/api/agent/directory/${id}`),
  },
  // 后端同步对话端点为 /api/agent/chat/execute，input 需转为文本块结构
  chat: async (req: AgentChatRequest) => {
    const result = await http.post<AgentChatResponse>('/api/agent/chat/execute', {
      ...req,
      input: toInputBlocks(req.input),
    });
    return normalizeOutput(result);
  },
  // 流式对话走后端 /api/agent/chat/stream SSE 端点，message 为正文增量、thinking 为思考过程增量、clarification_required 为AI向用户提问、confirm_required 为引擎工具确认请求、budget_warning 为预算/配额超限告警（流头部一次性推送）
  chatStream: async (
    req: AgentChatRequest,
    onMessage: (chunk: string) => void,
    onThinking?: (chunk: string) => void,
    onClarification?: (info: ClarificationRequestInfo) => void,
    onConfirm?: (info: ConfirmRequestInfo) => void,
    onQuotaWarning?: (info: QuotaWarningInfo) => void,
  ): Promise<void> => {
    const response = await fetch('/api/agent/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ ...req, input: toInputBlocks(req.input) }),
      credentials: 'include',
    });
    await ensureSseResponse(response);
    if (!response.body) {
      throw new Error('响应无数据流');
    }
    await consumeSseStream(response.body, onMessage, onThinking, onClarification, onConfirm, onQuotaWarning);
  },
  // 澄清回答恢复对话走后端 /api/agent/chat/clarification SSE 端点，事件契约与对话流一致（message/thinking/clarification_required/confirm_required/error/done）
  chatClarificationStream: async (
    req: { sessionId: string; toolCallId: string; answer: string },
    onMessage: (chunk: string) => void,
    onThinking?: (chunk: string) => void,
    onClarification?: (info: ClarificationRequestInfo) => void,
    onConfirm?: (info: ConfirmRequestInfo) => void,
  ): Promise<void> => {
    const response = await fetch('/api/agent/chat/clarification', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify(req),
      credentials: 'include',
    });
    await ensureSseResponse(response);
    if (!response.body) {
      throw new Error('响应无数据流');
    }
    await consumeSseStream(response.body, onMessage, onThinking, onClarification, onConfirm);
  },
  // 引擎确认恢复对话走后端 /api/agent/chat/confirm SSE 端点，事件契约与对话流一致
  chatConfirmStream: async (
    req: { sessionId: string; approved: boolean; operator?: string; reason?: string },
    onMessage: (chunk: string) => void,
    onThinking?: (chunk: string) => void,
    onClarification?: (info: ClarificationRequestInfo) => void,
    onConfirm?: (info: ConfirmRequestInfo) => void,
  ): Promise<void> => {
    const response = await fetch('/api/agent/chat/confirm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify(req),
      credentials: 'include',
    });
    await ensureSseResponse(response);
    if (!response.body) {
      throw new Error('响应无数据流');
    }
    await consumeSseStream(response.body, onMessage, onThinking, onClarification, onConfirm);
  },
  orchestration: {
    run: (req: Record<string, unknown>) =>
      http.post<unknown>('/api/agent/orchestration/plan', req),
  },
  scheduler: {
    // 后端 /api/schedule 按用户ID查询，返回裸数组，拦截器对裸对象直返
    list: (userId: string) =>
      http.get<SchedulerJob[]>('/api/schedule', { params: { userId } }),
    create: (data: CreateScheduleRequest) =>
      http.post<string>('/api/schedule', data),
    update: (scheduleId: string, data: UpdateScheduleRequest) =>
      http.put<void>(`/api/schedule/${scheduleId}`, data),
    delete: (scheduleId: string) => http.delete<void>(`/api/schedule/${scheduleId}`),
    // 启停走 pause/resume 端点：enabled 为当前状态，操作取反（运行中→暂停，已暂停→恢复）
    toggle: (scheduleId: string, enabled: boolean) =>
      enabled
        ? http.post<void>(`/api/schedule/${scheduleId}/pause`)
        : http.post<void>(`/api/schedule/${scheduleId}/resume`),
    // 执行历史分页，返回裸对象{total,list}，拦截器对裸对象直返
    logs: (scheduleId: string, pageNum: number, pageSize: number) =>
      http.get<ScheduleLogPage>(`/api/schedule/${scheduleId}/logs`, {
        params: { pageNum, pageSize },
      }),
  },
});

/**
 * 后端 Agent 实体字段映射为前端 AgentTypeInfo
 * @param agent
 * @return
 */
const toAgentTypeInfo = (agent: Record<string, unknown>): AgentTypeInfo => ({
  id: agent.id as number | undefined,
  typeCode: String(agent.agentCode ?? ''),
  typeName: String(agent.agentName ?? ''),
  typeDescription: agent.description as string | undefined,
  typeIcon: agent.icon as string | undefined,
  typeCategory: agent.category as string | undefined,
  directoryCode: agent.directoryCode as string | undefined,
  typeStatus: agent.status as number | undefined,
  typeOrder: agent.sortOrder as number | undefined,
  sessionType: agent.sessionType as string | undefined,
  agentConfig: agent.agentConfig as string | undefined,
  scopeId: agent.scopeId as string | undefined,
  shared: agent.shared as number | undefined,
  originAgentCode: agent.originAgentCode as string | undefined,
  supportImage: agent.supportImage as number | undefined,
  remark: agent.remark as string | undefined,
  updateTime: agent.updateTime as string | undefined,
});

/**
 * 前端 AgentTypeInfo 映射为后端 Agent 实体字段（undefined 字段序列化时丢弃，后端按 null 忽略不更新）
 * @param info
 * @return
 */
const toAgentPayload = (info: Partial<AgentTypeInfo>): Record<string, unknown> => ({
  agentCode: info.typeCode,
  agentName: info.typeName,
  description: info.typeDescription,
  icon: info.typeIcon,
  category: info.typeCategory,
  // 空串保留序列化（表示移至未分类），undefined 丢弃表示不修改
  directoryCode: info.directoryCode ?? undefined,
  status: info.typeStatus,
  sortOrder: info.typeOrder,
  sessionType: info.sessionType,
  agentConfig: info.agentConfig,
  remark: info.remark,
});

/**
 * 聊天图片输入项（dataURL 形式，发送时转为后端 image 输入块）
 */
export interface ChatImageItem {
  type: 'image';

  dataUrl: string;
}

/**
 * 输入消息转为后端 InputBlock 结构：字符串转文本块，图片项转image块（base64来源），其余透传
 * @param input
 * @return
 */
const toInputBlocks = (input?: unknown[]): unknown[] =>
  (input ?? []).map((item) => {
    if (typeof item === 'string') {
      return { type: 'text', text: item };
    }
    if (item != null && typeof item === 'object' && (item as ChatImageItem).type === 'image') {
      return toImageBlock((item as ChatImageItem).dataUrl);
    }
    return item;
  });

/**
 * dataURL 转为后端 image 输入块（base64来源契约：source.type=base64 + mediaType + 裸编码数据）
 * @param dataUrl
 * @return
 */
const toImageBlock = (dataUrl: string): unknown => {
  const match = /^data:([^;,]+);base64,(.*)$/s.exec(dataUrl);
  const mediaType = match?.[1] ?? 'image/png';
  const data = match?.[2] ?? dataUrl;
  return {
    type: 'image',
    source: { type: 'base64', mediaType, data },
  };
};

/**
 * 校验流式响应：全局异常处理器对AiException返回HTTP 200+JSON错误体，需转为异常提示避免流被静默吞掉
 * @param response
 */
const ensureSseResponse = async (response: Response): Promise<void> => {
  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败: ${response.status}`);
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    const err = await response.json().catch(() => null);
    throw new Error((err as { message?: string })?.message || '对话失败');
  }
};

/**
 * 规范化响应输出：后端 output 为块对象数组，提取文本块内容为字符串便于展示
 * @param result
 * @return
 */
const normalizeOutput = (result: AgentChatResponse): AgentChatResponse => ({
  ...result,
  output: (result.output ?? []).map((item) =>
    item != null && typeof item === 'object' && typeof (item as { text?: unknown }).text === 'string'
      ? (item as { text: string }).text
      : item,
  ),
});

/**
 * SSE 流式响应消费与事件分发（对话流、澄清恢复流与确认恢复流共用）
 * 事件契约：message=正文增量、thinking=思考增量、clarification_required=AI向用户提问（payload为JSON字符串）、confirm_required=引擎工具确认请求（payload为JSON字符串）、budget_warning=预算/配额告警（payload为JSON字符串）、error=失败原因
 * @param body
 * @param onMessage
 * @param onThinking
 * @param onClarification
 * @param onConfirm
 * @param onQuotaWarning
 */
const consumeSseStream = async (
  body: ReadableStream<Uint8Array>,
  onMessage: (chunk: string) => void,
  onThinking?: (chunk: string) => void,
  onClarification?: (info: ClarificationRequestInfo) => void,
  onConfirm?: (info: ConfirmRequestInfo) => void,
  onQuotaWarning?: (info: QuotaWarningInfo) => void,
): Promise<void> => {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventName = 'message';
  let dataLines: string[] = [];
  let errorMessage = '';
  let streamDone = false;

  // 按SSE规范聚合多行data后分发：后端markdown文本含换行会被拆成多个data:行，需用\n拼接还原
  const dispatch = () => {
    if (dataLines.length === 0) return;
    const data = dataLines.join('\n');
    dataLines = [];
    // 过滤心跳保活信号（后端以data形式发送的 ": heartbeat"）
    if (!data || data.startsWith(':')) return;
    if (eventName === 'message') {
      onMessage(data);
    } else if (eventName === 'thinking' && onThinking) {
      onThinking(data);
    } else if (eventName === 'clarification_required' && onClarification) {
      // 澄清提问payload为JSON字符串，解析失败静默忽略
      try {
        onClarification(JSON.parse(data) as ClarificationRequestInfo);
      } catch {
        // 忽略非法payload
      }
    } else if (eventName === 'confirm_required' && onConfirm) {
      // 引擎确认payload为JSON字符串，解析失败静默忽略
      try {
        onConfirm(JSON.parse(data) as ConfirmRequestInfo);
      } catch {
        // 忽略非法payload
      }
    } else if (eventName === 'budget_warning' && onQuotaWarning) {
      // 预算/配额告警payload为JSON字符串，解析失败退回原始文本展示
      try {
        onQuotaWarning(JSON.parse(data) as QuotaWarningInfo);
      } catch {
        onQuotaWarning({ message: data });
      }
    } else if (eventName === 'error') {
      // 后端以 error 事件推送执行失败原因，收集后在流结束时抛出
      errorMessage = data;
    } else if (eventName === 'done') {
      // done为流结束标记：立即落定发送状态，不等连接关闭（部分代理/异常收尾下连接可能长时间不关导致前端一直转圈）
      streamDone = true;
    }
  };

  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';
    for (const line of lines) {
      const trimmed = line.replace(/\r$/, '');
      if (trimmed === '') {
        // 空行是SSE事件边界
        dispatch();
        eventName = 'message';
        continue;
      }
      // SSE注释行（心跳保活）
      if (trimmed.startsWith(':')) continue;
      if (trimmed.startsWith('event:')) {
        eventName = trimmed.slice(6).trim();
        continue;
      }
      if (trimmed.startsWith('data:')) {
        // Spring SseEmitter 直接拼接payload不加分隔空格，空格token（如"## "的空格）是内容的一部分，不能剥离
        dataLines.push(trimmed.slice(5));
      }
    }
    // 收到done结束标记后主动取消读取并跳出，不再等待连接关闭
    if (streamDone) {
      break;
    }
  }
  if (streamDone) {
    try {
      await reader.cancel();
    } catch {
      // 连接可能已关闭，忽略取消失败
    }
  }
  dispatch();
  if (errorMessage) {
    throw new Error(errorMessage);
  }
};
