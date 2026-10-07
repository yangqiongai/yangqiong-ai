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
 * 知识图谱相关类型
 * 字段与后端实体对齐：GraphBuildRequest / GraphBuildRecordInfo / PathQueryRequest / GraphSchema / GraphVersionInfo / GraphHealthStatus / GraphRetrievalResult (ai-graph/model)
 * GraphRetrieveController.RetrieveRequest (ai-platform-api/graph)
 */

/**
 * 图谱构建请求
 * 对应后端 com.maizi.ai.graph.model.GraphBuildRequest
 */
export interface GraphBuildRequest {
  kbId: string;
  docIds?: string[];
  rebuild?: boolean;
  mode?: string;
  buildId?: string;
  resumeFromBuildId?: string;
}

/**
 * 图谱构建记录
 * 对应后端 com.maizi.ai.graph.model.GraphBuildRecordInfo
 */
export interface GraphBuildRecordInfo {
  id?: number;
  buildId?: string;
  kbId?: string;
  docId?: string;
  docIds?: string;
  mode?: string;
  stage?: string;
  status?: string;
  completedStages?: string;
  qualityMetrics?: string;
  quality?: string;
  llmUsage?: string;
  startedAt?: string;
  finishedAt?: string;
  error?: string;
}

/**
 * 路径查询请求
 * 对应后端 com.maizi.ai.graph.model.PathQueryRequest
 */
export interface PathQueryRequest {
  kbId: string;
  query: string;
  maxHops?: number;
  topK?: number;
}

/**
 * 图谱 Schema
 * 对应后端 com.maizi.ai.graph.model.GraphSchema
 */
export interface GraphSchema {
  nodes?: string[];
  relations?: string[];
  attributes?: string[];
  version?: number;
}

/**
 * 图谱版本信息
 * 对应后端 com.maizi.ai.graph.model.GraphVersionInfo
 */
export interface GraphVersionInfo {
  id?: number;
  versionId?: string;
  kbId?: string;
  version?: number;
  status?: string;
  snapshotJson?: string;
}

/**
 * 图谱健康状态
 * 对应后端 com.maizi.ai.graph.model.GraphHealthStatus
 */
export interface GraphHealthStatus {
  healthy: boolean;
  moduleEnabled: boolean;
  neo4jAvailable: boolean;
  qdrantAvailable: boolean;
  kbCount: number;
  totalEntities: number;
  totalTriples: number;
  totalCommunities: number;
  cacheHitRate: number;
  details?: Record<string, unknown>;
}

/**
 * 图谱检索请求
 * 对应后端 GraphRetrieveController.RetrieveRequest
 */
export interface GraphRetrieveRequest {
  kbId: string;
  query: string;
  mode?: string;
  topK?: number;
}
