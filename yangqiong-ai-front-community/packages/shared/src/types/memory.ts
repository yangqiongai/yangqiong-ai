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
 * 记忆相关类型
 * 字段与后端实体对齐：ChatMemoryRecord / MemoryPageResult (ai-memory/model)
 */

/**
 * 对话记忆记录
 * 对应后端 com.maizi.ai.memory.model.ChatMemoryRecord
 */
export interface ChatMemoryRecord {
  id?: number;
  messageId?: string;
  sessionId?: string;
  userId?: string;
  messageRole?: string;
  messageContent?: string;
  tokenCount?: number;
  inputTokens?: number;
  outputTokens?: number;
  totalTokens?: number;
  executionTime?: number;
  importanceScore?: number;
  body?: string;
  createTime?: string;
  scopeId?: string;
}

/**
 * 记忆分页结果
 * 对应后端 com.maizi.ai.memory.model.MemoryPageResult
 */
export interface MemoryPageResult {
  page: number;
  size: number;
  total: number;
  records: ChatMemoryRecord[];
}
