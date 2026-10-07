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
  ApiIngestRequest,
  DataSourceIngestLogPage,
  DatabaseIngestRequest,
  IngestDocument,
  KbDocument,
  KbSliceRecord,
  KnowledgeBase,
  RecallRequest,
  RecallResult,
  RetrievalEvidence,
  TextIngestRequest,
  VectorCollectionInfo,
  WebpageIngestRequest,
} from '../types';

export const createKnowledgeApi = (http: HttpRequest) => ({
  kb: {
    list: (params?: { userId?: string }) =>
      http.get<KnowledgeBase[]>('/api/knowledge-base', { params }),
    get: (kbId: string) => http.get<KnowledgeBase>(`/api/knowledge-base/${kbId}`),
    create: (data: Partial<KnowledgeBase>) =>
      http.post<KnowledgeBase>('/api/knowledge-base', data),
    update: (kbId: string, data: Partial<KnowledgeBase>) =>
      http.put<KnowledgeBase>(`/api/knowledge-base/${kbId}`, data),
    delete: (kbId: string) => http.delete<void>(`/api/knowledge-base/${kbId}`),
    purge: (kbId: string) =>
      http.delete<void>(`/api/knowledge-base/${kbId}/purge`, {
        params: { confirm: true },
      }),
  },
  documents: {
    list: (kbId: string, params?: { userId?: string }) =>
      http.get<KbDocument[]>(`/api/knowledge-base/${kbId}/documents`, {
        params,
      }),
    get: (kbId: string, docId: string) =>
      http.get<KbDocument>(`/api/knowledge-base/${kbId}/documents/${docId}`),
    upload: (
      kbId: string,
      file: File,
      onProgress?: (progress: { loaded: number; total?: number }) => void,
    ) => {
      const formData = new FormData();
      formData.append('file', file);
      return http.post<KbDocument>(
        `/api/knowledge-base/${kbId}/documents/upload`,
        formData,
        {
          headers: { 'Content-Type': 'multipart/form-data' },
          onUploadProgress: (e) => onProgress?.({ loaded: e.loaded, total: e.total }),
        },
      );
    },
    reprocess: (kbId: string, docId: string) =>
      http.post<void>(`/api/knowledge-base/${kbId}/documents/${docId}/reprocess`),
    delete: (kbId: string, docId: string) =>
      http.delete<void>(`/api/knowledge-base/${kbId}/documents/${docId}`),
    slices: (kbId: string, docId: string) =>
      http.get<KbSliceRecord[]>(
        `/api/knowledge-base/${kbId}/documents/${docId}/slices`,
      ),
  },
  datasource: {
    ingestText: (data: TextIngestRequest) =>
      http.post<IngestDocument>('/api/datasource/text', data),
    ingestWebpage: (data: WebpageIngestRequest) =>
      http.post<IngestDocument>('/api/datasource/webpage', data),
    ingestApi: (data: ApiIngestRequest) =>
      http.post<IngestDocument>('/api/datasource/controller', data),
    ingestDatabase: (data: DatabaseIngestRequest) =>
      http.post<IngestDocument>('/api/datasource/database', data),
    logs: (params: {
      pageNum: number;
      pageSize: number;
      kbId?: string;
      ingestType?: string;
    }) => http.get<DataSourceIngestLogPage>('/api/datasource/ingest/logs', { params }),
  },
  rag: {
    retrieve: (query: string, kbIds: string[], topK?: number) =>
      http.get<RetrievalEvidence[]>('/api/rag/retrieve', {
        params: { query, kbIds: kbIds.join(','), topK: topK ?? 5 },
        timeout: 60000,
      }),
    verifyRecall: (request: RecallRequest) =>
      http.post<RecallResult[]>('/api/rag/recall/verify', request, {
        timeout: 120000,
      }),
    expandContext: (docId: string, query: string, maxChars?: number) =>
      http.get<string>('/api/rag/context/expand', {
        params: { docId, query, maxChars: maxChars ?? 5000 },
        timeout: 60000,
      }),
  },
  vector: {
    listCollections: () =>
      http.get<VectorCollectionInfo[]>('/api/admin/vector/collections'),
    getCollectionDetail: (collectionName: string) =>
      http.get<unknown>(`/api/admin/vector/collections/${collectionName}`),
    deleteCollection: (collectionName: string) =>
      http.delete<void>(`/api/admin/vector/collections/${collectionName}`, {
        params: { confirm: true },
      }),
  },
});
