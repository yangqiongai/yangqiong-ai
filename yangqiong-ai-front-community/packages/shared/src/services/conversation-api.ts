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
import type {
  ConversationSearchDTO,
  ConversationSessionInfo,
  ConversationStatsDTO,
  PageResult,
  SessionTag,
} from '../types';

export const createConversationApi = (http: HttpRequest) => ({
  session: {
    // 后端 POST /api/conversation/session/search 返回当页数据（无总数），满页时总数+1 支持翻页
    list: async (data: ConversationSearchDTO) => {
      const list =
        (await http.post<ConversationSessionInfo[]>(
          '/api/conversation/session/search',
          data,
        )) ?? [];
      const page = data.page ?? 1;
      const size = data.size ?? 10;
      const total =
        list.length === size ? page * size + 1 : (page - 1) * size + list.length;
      return { list, total, page, size } as PageResult<ConversationSessionInfo>;
    },
    get: (sessionId: string) =>
      http.get<ConversationSessionInfo>(`/api/conversation/session/${sessionId}`),
    create: (data: Partial<ConversationSessionInfo>) =>
      http.post<ConversationSessionInfo>('/api/conversation/session', data),
    update: (data: Partial<ConversationSessionInfo>) =>
      http.put<void>('/api/conversation/session', data),
    delete: (sessionId: string) =>
      http.delete<void>(`/api/conversation/session/${sessionId}`),
    close: (sessionId: string) =>
      http.put<void>(`/api/conversation/session/${sessionId}/close`),
    // 导出会话（json 格式含 sessionId/title/agentCode/messages，用于恢复历史对话）
    export: (sessionId: string, format: 'json' | 'markdown' = 'json') =>
      http.get<string>(`/api/conversation/session/${sessionId}/export`, {
        params: { format },
      }),
  },
  stats: (userId: string) =>
    http.get<ConversationStatsDTO>('/api/conversation/session/stats', {
      params: { userId },
    }),
  tag: {
    // 后端返回用户标签名去重列表，包装为 SessionTag 形态（删除按 userId+tag 操作）
    list: async (userId: string) => {
      const tags =
        (await http.get<string[]>('/api/conversation/session/tag', {
          params: { userId },
        })) ?? [];
      return {
        list: tags.map((t) => ({ sessionId: '', tag: t } as SessionTag)),
      };
    },
    listBySession: (sessionId: string) =>
      http.get<SessionTag[]>(`/api/conversation/session/tag/${sessionId}`),
    add: (sessionId: string, userId: string, tag: string) =>
      http.post<SessionTag>(`/api/conversation/session/tag/${sessionId}`, null, {
        params: { userId, tag },
      }),
    remove: (sessionId: string, tag: string) =>
      http.delete<void>(`/api/conversation/session/tag/${sessionId}`, {
        params: { tag },
      }),
    removeUserTag: (userId: string, tag: string) =>
      http.delete<void>('/api/conversation/session/tag', {
        params: { userId, tag },
      }),
    listSessionIds: (userId: string, tag: string) =>
      http.get<string[]>('/api/conversation/session/tag/sessions', {
        params: { userId, tag },
      }),
  },
});
