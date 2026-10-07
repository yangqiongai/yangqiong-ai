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
import { useCallback, useState } from 'react';
import type { RetrievalEvidence } from '../types/knowledge';

/**
 * RAG 检索接口最小结构（由各端 api 实例适配）
 */
export interface RagApi {
  retrieve(query: string, kbIds: string[], topK: number): Promise<RetrievalEvidence[] | null | undefined>;
}

/**
 * 检索结果
 */
export interface RagRetrieveOutcome {
  ok: boolean;

  rows: RetrievalEvidence[];

  error: string;
}

/**
 * RAG 检索状态与请求（results/searching/error 状态机多端共用）
 * @param api
 * @return
 */
export const useRagRetrieve = (api: RagApi) => {
  const [results, setResults] = useState<RetrievalEvidence[]>([]);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');

  const retrieve = useCallback(
    async (params: { query: string; kbIds: string[]; topK?: number }): Promise<RagRetrieveOutcome> => {
      // 查询条件校验（知识库与查询问题均必填）
      const query = params.query.trim();
      const kbIds = params.kbIds.filter(Boolean);
      if (!query || kbIds.length === 0) {
        const msg = '请选择知识库并输入查询';
        setError(msg);
        setResults([]);
        return { ok: false, rows: [], error: msg };
      }
      setSearching(true);
      setError('');
      try {
        const data = await api.retrieve(query, kbIds, params.topK ?? 10);
        const rows = data ?? [];
        setResults(rows);
        return { ok: true, rows, error: '' };
      } catch (err) {
        const msg = err instanceof Error ? err.message : '检索失败';
        setError(msg);
        setResults([]);
        return { ok: false, rows: [], error: msg };
      } finally {
        setSearching(false);
      }
    },
    [api],
  );

  const reset = useCallback((): void => {
    setResults([]);
    setError('');
  }, []);

  return { results, setResults, searching, error, retrieve, reset };
};
