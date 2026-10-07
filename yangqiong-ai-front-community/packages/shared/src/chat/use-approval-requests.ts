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
import { useCallback, useEffect, useRef, useState } from 'react';
import type { PendingRequestInfo } from '../types';

/**
 * 审批接口最小结构（由各端 api 实例适配）
 */
export interface ApprovalStreamApi {
  approval: {
    list(params?: { keyword?: string; status?: string; resourceType?: string }): Promise<
      { list?: PendingRequestInfo[] } | undefined
    >;

    subscribeStream(
      sessionId: string,
      onMessage: (data: string) => void,
      onError?: (event: Event) => void,
    ): { close(): void };
  };
}

/**
 * 订阅会话维度的待审批事件（进入会话拉取存量待审批 + SSE 实时推送，同 requestId 去重）
 * @param api
 * @param sessionId
 * @return
 */
export const useApprovalRequests = (api: ApprovalStreamApi, sessionId?: string) => {
  const [requests, setRequests] = useState<PendingRequestInfo[]>([]);

  // 回调里读取最新会话，避免旧推送写入新会话
  const sessionIdRef = useRef(sessionId);
  sessionIdRef.current = sessionId;

  // 会话切换时清空并拉取存量待审批
  useEffect(() => {
    setRequests([]);
    if (!sessionId) {
      return;
    }
    let cancelled = false;
    api.approval
      .list()
      .then((page) => {
        if (cancelled) {
          return;
        }
        const mine = (page?.list ?? []).filter(
          (t) => t.sessionId === sessionId && t.status === 'PENDING',
        );
        setRequests(mine);
      })
      .catch(() => {
        // 存量拉取失败不阻塞对话，仅依赖后续 SSE 推送
      });
    return () => {
      cancelled = true;
    };
  }, [api, sessionId]);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const source = api.approval.subscribeStream(
      sessionId,
      (data) => {
        if (sessionIdRef.current !== sessionId) {
          return;
        }
        try {
          const parsed = JSON.parse(data) as PendingRequestInfo | PendingRequestInfo[];
          const incoming = (Array.isArray(parsed) ? parsed : [parsed]).filter(
            (t) =>
              t?.requestId && t.status !== 'APPROVED' && t.status !== 'REJECTED' && t.status !== 'TIMEOUT',
          );
          // 同 requestId 去重，避免流式推送与存量拉取重复渲染
          setRequests((prev) => {
            const known = new Set(prev.map((t) => t.requestId));
            const added = incoming.filter((t) => !known.has(t.requestId));
            return added.length ? [...prev, ...added] : prev;
          });
        } catch {
          // 非JSON数据（心跳等）忽略
        }
      },
      () => {
        // EventSource 断开后浏览器自动重连，无需额外处理
      },
    );
    return () => {
      source.close();
    };
  }, [api, sessionId]);

  const removeRequest = useCallback((requestId: string) => {
    setRequests((prev) => prev.filter((t) => t.requestId !== requestId));
  }, []);

  return { requests, removeRequest };
};
