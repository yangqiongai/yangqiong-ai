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
 * 模型相关类型
 * 字段与后端实体对齐：ModelInfo (ai-data/llm/entity，继承 ScopeEntity)
 */

/**
 * 模型信息
 * 对应后端 com.maizi.ai.data.llm.entity.ModelInfo
 * 继承 ScopeEntity（含 scopeId, createUser, createTime, updateUser, updateTime）
 */
export interface ModelInfo {
  id?: number;
  modelCode: string;
  modelName: string;
  provider?: string;
  modelType?: string;
  apiEndpoint?: string;
  apiKey?: string;
  modelConfig?: string;
  isDefault?: number;
  modelStatus?: number;
  remark?: string;
  supportReasoning?: number;
  supportImage?: number;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 模型连通性测试结果
 */
export interface ModelTestResult {
  success: boolean;
  latencyMs?: number;
  reply?: string;
  error?: string;
}
