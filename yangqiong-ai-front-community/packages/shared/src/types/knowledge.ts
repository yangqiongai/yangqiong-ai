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
 * 知识库相关类型
 * 字段与后端实体对齐：KnowledgeBase / KbDocument (ai-platform-knowledge)
 * RAG 类型对齐：RetrievalEvidence / RecallRequest / RecallResult (ai-rag)
 */

/**
 * 知识库
 * 对应后端 com.maizi.ai.platform.knowledge.entity.KnowledgeBase
 */
export interface KnowledgeBase {
  kbId: string;
  userId?: string;
  kbName: string;
  kbDescription?: string;
  embeddingModel?: string;
  chunkStrategy?: string;
  chunkConfig?: string;
  activeVersion?: string;
  kbStatus?: number;
  kbIcon?: string;
  documentCount?: number;
  remark?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 知识库文档
 * 对应后端 com.maizi.ai.platform.knowledge.entity.KbDocument
 */
export interface KbDocument {
  docId: string;
  userId?: string;
  kbId: string;
  docName: string;
  fileType?: string;
  fileSize?: number;
  contentHash?: string;
  fileBucket?: string;
  filePath?: string;
  sourceType?: string;
  content?: string;
  versionTag?: string;
  docStatus?: string;
  errorMessage?: string;
  chunkCount?: number;
  summary?: string;
  regions?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 知识库文档切片
 * 对应后端 com.yangqiongai.ai.rag.model.SliceRecord
 */
export interface KbSliceRecord {
  id?: number;
  sliceId: string;
  docId: string;
  kbId: string;
  content?: string;
  chunkKey?: string;
  parentId?: string;
  sliceType?: string;
  tokenCount?: number;
  version?: string;
  sortNum?: number;
  isActive?: boolean;
  metadata?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * RAG 检索证据
 * 对应后端 com.maizi.ai.rag.model.RetrievalEvidence
 */
export interface RetrievalEvidence {
  content: string;
  sourceDocId: string;
  sourceDocName: string;
  sliceId: string;
  score: number;
  body?: Record<string, unknown>;
}

/**
 * RAG 召回验证请求
 * 对应后端 com.maizi.ai.rag.model.RecallRequest
 */
export interface RecallRequest {
  queries: string[];
  kbIds: string[];
  topK: number;
  minScore: number;
  expectedRelevantDocIds?: string[];
}

/**
 * RAG 召回验证结果
 * 对应后端 com.maizi.ai.rag.model.RecallResult
 */
export interface RecallResult {
  query: string;
  hitCount: number;
  hitSlices?: unknown[];
  recallRate: number;
  coverageRate: number;
  mrr: number;
}

/**
 * 数据源文本导入请求
 * 对应后端 DataSourceIngestController.TextIngestRequest
 */
export interface TextIngestRequest {
  kbId: string;
  title: string;
  content: string;
}

/**
 * 数据源网页导入请求
 * 对应后端 DataSourceIngestController.WebpageIngestRequest
 */
export interface WebpageIngestRequest {
  kbId: string;
  url: string;
  title: string;
  content: string;
}

/**
 * 数据源 API 导入请求
 * 对应后端 DataSourceIngestController.ApiIngestRequest
 */
export interface ApiIngestRequest {
  kbId: string;
  title: string;
  content: string;
  sourceUrl: string;
}

/**
 * 数据源数据库导入请求
 * 对应后端 DataSourceIngestController.DatabaseIngestRequest
 */
export interface DatabaseIngestRequest {
  kbId: string;
  title: string;
  content: string;
}

/**
 * 导入文档结果
 */
export interface IngestDocument {
  docId?: string;
  kbId?: string;
  docName?: string;
  [key: string]: unknown;
}

/**
 * 数据源接入记录
 */
export interface DataSourceIngestLog {
  id: string;

  /**
   * 接入方式(TEXT/WEBPAGE/API/DATABASE)
   */
  ingestType: string;

  /**
   * 文档标题
   */
  title?: string;

  /**
   * 知识库ID
   */
  kbId?: string;

  /**
   * 来源URL(网页/API接入)
   */
  sourceUrl?: string;

  /**
   * 生成的文档ID(失败时为空)
   */
  docId?: string;

  /**
   * 内容大小(字节)
   */
  contentSize?: number;

  /**
   * 归属用户(知识库归属人)
   */
  userId?: string;

  /**
   * 是否成功
   */
  success: boolean;

  /**
   * 失败原因
   */
  errorMessage?: string;

  /**
   * 耗时毫秒
   */
  durationMs?: number;

  /**
   * 接入时间
   */
  ingestTime: string;
}

/**
 * 数据源接入记录分页
 */
export interface DataSourceIngestLogPage {
  list: DataSourceIngestLog[];
  total: number;
}

/**
 * 向量存储集合信息
 * 对应后端 VectorStoreAdminController 返回的 Map
 */
export interface VectorCollectionInfo {
  name?: string;
  status?: string;
  vectorsCount?: number;
  dimension?: number;
  [key: string]: unknown;
}
