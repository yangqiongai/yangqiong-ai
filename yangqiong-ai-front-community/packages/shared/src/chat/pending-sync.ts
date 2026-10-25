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
/**
 * 对话页挂起任务同步事件工具
 * <p>
 * 对话页内嵌框与全局通知卡片共同呈现同一挂起任务（澄清提问/引擎确认），
 * 任一处提交成功后通过 window 事件广播 handled，其余呈现处自动移除对应条目；
 * 对话页挂载期间广播视图激活信号，全局通知卡片在对话界面不再弹出（内嵌框负责呈现）；
 * "去处理"跳转时写入交接数据，对话页恢复会话后据此还原内嵌提问框完成作答。
 * </p>
 * @author yangqiong
 */

/**
 * 挂起处理完成事件名
 */
const HANDLED_EVENT = 'yangqiong:agent-pending-handled';

/**
 * 对话视图激活事件名
 */
const CHAT_VIEW_EVENT = 'yangqiong:agent-chat-view-active';

/**
 * "去处理"交接数据存储键
 */
const HANDOFF_KEY = 'yangqiong:agent-pending-handoff';

/**
 * 挂起处理完成事件明细
 */
export interface AgentPendingHandledDetail {
  /**
   * 会话ID
   */
  sessionId: string;

  /**
   * 任务类型（澄清提问/引擎确认）
   */
  taskType: 'CLARIFICATION' | 'CONFIRM';

  /**
   * 工具调用ID（澄清提问标识）
   */
  toolCallId?: string;

  /**
   * 请求ID（引擎确认标识）
   */
  requestId?: string;
}

/**
 * "去处理"交接的澄清提问条目
 */
export interface AgentPendingHandoffItem {
  /**
   * 会话ID
   */
  sessionId: string;

  /**
   * 工具调用ID
   */
  toolCallId: string;

  /**
   * AI 的问题
   */
  question: string;

  /**
   * 候选选项列表
   */
  options?: string[];
}

/**
 * 广播挂起任务已在某处处理完成
 * @param detail
 */
export const emitAgentPendingHandled = (detail: AgentPendingHandledDetail): void => {
  window.dispatchEvent(new CustomEvent<AgentPendingHandledDetail>(HANDLED_EVENT, { detail }));
};

/**
 * 订阅挂起处理完成广播
 * @param handler
 * @return 取消订阅函数
 */
export const onAgentPendingHandled = (
  handler: (detail: AgentPendingHandledDetail) => void,
): (() => void) => {
  const listener = (event: Event) => handler((event as CustomEvent<AgentPendingHandledDetail>).detail);
  window.addEventListener(HANDLED_EVENT, listener);
  return () => window.removeEventListener(HANDLED_EVENT, listener);
};

/**
 * 广播对话视图激活状态变化（对话页挂载为激活，卸载为解除）
 * @param active
 */
export const emitAgentChatViewActive = (active: boolean): void => {
  window.dispatchEvent(new CustomEvent<boolean>(CHAT_VIEW_EVENT, { detail: active }));
};

/**
 * 订阅对话视图激活广播
 * @param handler
 * @return 取消订阅函数
 */
export const onAgentChatViewActive = (handler: (active: boolean) => void): (() => void) => {
  const listener = (event: Event) => handler(Boolean((event as CustomEvent<boolean>).detail));
  window.addEventListener(CHAT_VIEW_EVENT, listener);
  return () => window.removeEventListener(CHAT_VIEW_EVENT, listener);
};

/**
 * 写入"去处理"交接的澄清提问条目（同会话多条依次累积）
 * @param item
 */
export const writeAgentPendingHandoff = (item: AgentPendingHandoffItem): void => {
  let items: AgentPendingHandoffItem[] = [];
  try {
    const raw = sessionStorage.getItem(HANDOFF_KEY);
    if (raw) {
      items = JSON.parse(raw) as AgentPendingHandoffItem[];
    }
  } catch {
    items = [];
  }
  items.push(item);
  sessionStorage.setItem(HANDOFF_KEY, JSON.stringify(items));
};

/**
 * 消费指定会话的"去处理"交接条目（取后即清，恢复会话时还原内嵌提问框用）
 * @param sessionId
 * @return 该会话待还原的澄清提问条目
 */
export const consumeAgentPendingHandoff = (sessionId: string): AgentPendingHandoffItem[] => {
  let items: AgentPendingHandoffItem[] = [];
  try {
    const raw = sessionStorage.getItem(HANDOFF_KEY);
    if (raw) {
      items = JSON.parse(raw) as AgentPendingHandoffItem[];
    }
  } catch {
    return [];
  }
  const matched = items.filter((item) => item.sessionId === sessionId);
  if (matched.length === 0) {
    return [];
  }
  const rest = items.filter((item) => item.sessionId !== sessionId);
  if (rest.length === 0) {
    sessionStorage.removeItem(HANDOFF_KEY);
  } else {
    sessionStorage.setItem(HANDOFF_KEY, JSON.stringify(rest));
  }
  return matched;
};
