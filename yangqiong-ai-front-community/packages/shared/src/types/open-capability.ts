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
 * 开放能力相关类型
 * 字段与后端实体对齐：CapabilitySpec / CapabilityRequest / CapabilityResponse / DataContext (ai-open/capability)
 */

/**
 * 能力规格
 * 对应后端 com.maizi.ai.open.capability.spec.CapabilitySpec
 */
export interface CapabilitySpec {
  code: string;
  name?: string;
  description?: string;
  version?: string;
  category?: string;
  agentCode?: string;
  execType?: string;
  workflowCode?: string;
  agentOverrides?: Record<string, unknown>;
  inputSchema?: string;
  outputSchema?: string;
  promptTemplate?: string;
  inputSchemaContent?: string;
  outputSchemaContent?: string;
  promptTemplateContent?: string;
  inputSchemaDescription?: string;
  outputSchemaDescription?: string;
  execution?: Record<string, unknown>;
  contract?: Record<string, unknown>;
  context?: Record<string, unknown>;
  audit?: Record<string, unknown>;
}

/**
 * 能力定义版本历史快照
 */
export interface CapabilityDefinitionHistory {
  id: number;
  capabilityCode: string;
  version?: string;
  name?: string;
  operation?: string;
  definitionJson?: string;
  createTime?: string;
}

/**
 * 能力分类树节点
 * 对应后端 OpenCapabilityAdminController.CategoryNode
 */
export interface CapabilityCategoryNode {
  id: number;
  code: string;
  name: string;
  parentId?: number | null;
  sortNum?: number;
  capabilityCount?: number;
  children?: CapabilityCategoryNode[];
}

/**
 * 能力分类
 * 对应后端 CapabilityCategory
 */
export interface CapabilityCategory {
  id?: number;
  code?: string;
  name?: string;
  parentId?: number | null;
  sortNum?: number;
}

/**
 * 数据上下文
 * 对应后端 com.maizi.ai.open.capability.context.DataContext
 */
export interface DataContext {
  ref?: string;
  type?: string;
  name?: string;
  content?: unknown;
  ttlSeconds?: number;
  createdAt?: string;
}

/**
 * 能力请求
 * 对应后端 com.maizi.ai.open.capability.engine.CapabilityRequest
 */
export interface CapabilityRequest {
  capability: string;
  dataContextRefs?: string[];
  arguments?: Record<string, unknown>;
  dedupKey?: string;
  caller?: string;
  scopeId?: string;
}

/**
 * 能力响应
 * 对应后端 com.maizi.ai.open.capability.engine.CapabilityResponse
 */
export interface CapabilityResponse {
  success: boolean;
  output?: string;
  structuredOutput?: unknown;
  errorMessage?: string;
  callId?: string;
  tokenMetrics?: Record<string, unknown>;
  durationMillis?: number;
}
