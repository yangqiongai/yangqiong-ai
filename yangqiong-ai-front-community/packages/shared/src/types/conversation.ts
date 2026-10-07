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
 * 会话相关类型
 * 字段与后端实体对齐：ConversationSessionInfo (ai-memory/model)
 * SessionTag (ai-platform-conversation/entity，继承 ScopeEntity)
 * ConversationRequestDTO / ConversationSearchDTO / ConversationStatsDTO / ConversationSlaDTO (ai-platform-conversation/dto)
 */

/**
 * 会话标签
 * 对应后端 com.maizi.ai.platform.conversation.entity.SessionTag（继承 ScopeEntity）
 */
export interface SessionTag {
  id?: number;
  sessionId: string;
  userId?: string;
  tag: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 会话信息
 * 对应后端 com.maizi.ai.memory.model.ConversationSessionInfo
 */
export interface ConversationSessionInfo {
  id?: number;
  sessionId: string;
  userId?: string;
  agentCode?: string;
  sessionTitle?: string;
  summaryText?: string;
  sessionType?: string;
  body?: string;
  summaryRound?: number;
  latestSummaryId?: string;
  lastSummarizedAt?: string;
  sessionStatus?: number;
  archivedAt?: string;
  updateTime?: string;
}

/**
 * 会话请求
 * 对应后端 com.maizi.ai.platform.conversation.dto.ConversationRequestDTO
 */
export interface ConversationRequestDTO {
  sessionId?: string;
  userId?: string;
  message: string;
  enableCrossSessionMemory?: boolean;
  maxTokens?: number;
}

/**
 * 会话搜索
 * 对应后端 com.maizi.ai.platform.conversation.dto.ConversationSearchDTO
 */
export interface ConversationSearchDTO {
  userId?: string;
  keyword?: string;
  status?: number;
  agentCode?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

/**
 * 会话统计
 * 对应后端 com.maizi.ai.platform.conversation.dto.ConversationStatsDTO
 */
export interface ConversationStatsDTO {
  activeSessions: number;
  totalSessions: number;
  totalMessages: number;
  totalTokensUsed: number;
  todayTokensUsed: number;
  dailyTokenLimit: number;
  longTermMemoryCount: number;
}

/**
 * 会话 SLA
 * 对应后端 com.maizi.ai.platform.conversation.dto.ConversationSlaDTO
 */
export interface ConversationSlaDTO {
  totalConversations: number;
  avgResponseTimeMs: number;
  p95ResponseTimeMs: number;
  p99ResponseTimeMs: number;
  maxResponseTimeMs: number;
  avgTokenCount: number;
}
