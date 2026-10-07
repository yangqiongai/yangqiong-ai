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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  ConversationSessionInfo,
  ConversationStatsDTO,
  SessionTag,
} from '../types/conversation';

/**
 * 会话列表查询键前缀
 */
export const CONVERSATIONS_KEY = 'conversations';

/**
 * 会话标签查询键前缀
 */
export const SESSION_TAGS_KEY = 'session-tags';

/**
 * 会话接口最小结构（由各端 api 实例适配）
 */
export interface ConversationApi {
  stats(userId: string): Promise<ConversationStatsDTO>;

  session: {
    list(params: { userId: string; keyword?: string; page: number; size: number }): Promise<{
      list?: ConversationSessionInfo[];
      total?: number;
    }>;
    delete(id: string): Promise<unknown>;
  };

  tag: {
    list(userId: string): Promise<{ list?: SessionTag[] }>;
    add(sessionId: string, userId: string, tag: string): Promise<unknown>;
    removeUserTag(userId: string, tag: string): Promise<unknown>;
  };
}

/**
 * 会话列表查询参数
 */
export interface UseConversationSessionsOptions {
  userId?: string;

  page: number;

  size: number;

  keyword?: string;
}

/**
 * 会话统计/会话列表/会话标签查询与删除会话、创建删除标签的变更（多端共用逻辑，成功后统一失效缓存）
 * @param api
 * @param options
 * @return
 */
export const useConversationSessions = (
  api: ConversationApi,
  { userId, page, size, keyword }: UseConversationSessionsOptions,
) => {
  const queryClient = useQueryClient();
  const enabled = Boolean(userId);

  const statsQuery = useQuery({
    queryKey: [CONVERSATIONS_KEY, 'stats', userId],
    queryFn: () => api.stats(userId ?? ''),
    enabled,
  });

  const sessionsQuery = useQuery({
    queryKey: [CONVERSATIONS_KEY, page, size, keyword, userId],
    queryFn: () => api.session.list({ userId: userId ?? '', keyword, page, size }),
    enabled,
  });

  const tagsQuery = useQuery({
    queryKey: [SESSION_TAGS_KEY, userId],
    queryFn: () => api.tag.list(userId ?? ''),
    enabled,
  });

  const invalidateAll = (): void => {
    queryClient.invalidateQueries({ queryKey: [CONVERSATIONS_KEY] });
    queryClient.invalidateQueries({ queryKey: [SESSION_TAGS_KEY] });
  };

  const deleteSession = useMutation({
    mutationFn: (id: string) => api.session.delete(id),
    onSuccess: invalidateAll,
  });

  const createTag = useMutation({
    mutationFn: (values: { sessionId: string; tag: string }) =>
      api.tag.add(values.sessionId, userId ?? '', values.tag),
    onSuccess: invalidateAll,
  });

  const deleteTag = useMutation({
    mutationFn: (tag: string) => api.tag.removeUserTag(userId ?? '', tag),
    onSuccess: invalidateAll,
  });

  return {
    statsQuery,
    sessionsQuery,
    tagsQuery,
    sessions: sessionsQuery.data?.list ?? [],
    total: sessionsQuery.data?.total ?? 0,
    tags: tagsQuery.data?.list ?? [],
    invalidateAll,
    deleteSession,
    createTag,
    deleteTag,
  };
};
