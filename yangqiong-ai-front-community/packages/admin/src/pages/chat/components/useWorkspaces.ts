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
import { useQuery } from '@tanstack/react-query';
import { normalizeWorkspaceList } from '@yangqiong/shared';
import { api } from '@/services';
import type { WorkspaceItem } from '@yangqiong/shared';

/**
 * 会话与工作区绑定的 localStorage 键前缀
 */
export const WS_BINDING_PREFIX = 'ws-binding-';

/**
 * 读取会话绑定的workspaceId（无绑定返回undefined；雪花ID全程保持字符串避免精度丢失）
 * @param sessionId
 * @return
 */
export const readWorkspaceBinding = (sessionId: string): string | undefined => {
  const raw = localStorage.getItem(`${WS_BINDING_PREFIX}${sessionId}`);
  return raw && /^\d+$/.test(raw) ? raw : undefined;
};

/**
 * 写入会话与工作区的绑定关系
 * @param sessionId
 * @param workspaceId
 */
export const writeWorkspaceBinding = (sessionId: string, workspaceId: string): void => {
  localStorage.setItem(`${WS_BINDING_PREFIX}${sessionId}`, workspaceId);
};

/**
 * 拉取当前用户工作区列表（附目录健康状态）
 * @param userId
 * @return
 */
export const useWorkspaces = (userId?: string) => {
  const query = useQuery({
    queryKey: ['workspaces', userId],
    queryFn: () => api.workspace.list(userId as string).then(normalizeWorkspaceList),
    enabled: Boolean(userId),
  });

  const workspaces = query.data?.workspaces ?? [];

  return {
    workspaces: workspaces as WorkspaceItem[],
    loading: query.isLoading,
    refresh: query.refetch,
  };
};
