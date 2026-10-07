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
 * AI 对话会话治理模块
 *
 * <p>在 ai-memory 记忆能力之上，提供会话生命周期治理、配额管控、SLA 追踪、
 * 搜索分页、统计聚合、会话导出与标签分类能力。</p>
 *
 * <h2>功能清单</h2>
 * <ul>
 *   <li><b>会话生命周期管理</b>：基于 {@code SessionLifecycleService} 提供
 *       不活跃会话归档、关闭会话并合并长期记忆（{@code closeAndMerge}）、
 *       会话导出（JSON / Markdown）。</li>
 *   <li><b>会话 CRUD API</b>：{@code ConversationController} 暴露创建、查询、
 *       更新、删除、关闭、准备上下文等接口。</li>
 *   <li><b>并发会话配额</b>：{@code SessionQuotaService} 控制每用户活跃会话数
 *       与总会话数，超限抛 {@code ConversationQuotaExceededException}。</li>
 *   <li><b>Token 预算追踪</b>：{@code TokenBudgetTracker} 基于 Redis 按用户维度
 *       累计每日 Token 用量与单会话 Token 用量，超限抛异常。</li>
 *   <li><b>多条件分页搜索</b>：{@code SessionManager.search} 支持关键词、
 *       状态、Agent 类型、时间范围分页查询，{@code ConversationSearchDTO} 封装条件。</li>
 *   <li><b>会话使用统计</b>：{@code ConversationStatsService} 聚合查询
 *       活跃会话数、总会话数、消息数、Token 用量、长期记忆条数。</li>
 *   <li><b>SLA 响应时间追踪</b>：{@code ConversationSlaService} 基于 Redis List
 *       存储最近 1000 条响应时间样本，支持 P95/P99 分位查询。</li>
 *   <li><b>会话导出</b>：{@code SessionLifecycleService.exportSession} 支持
 *       JSON 与 Markdown 两种格式，包含会话元信息、消息列表、摘要文本。</li>
 *   <li><b>会话标签分类</b>：{@code SessionTagService} 支持会话打标签、移除标签、
 *       按标签查询会话、查询用户标签列表（去重）。</li>
 *   <li><b>AgentSessionStore 适配</b>：{@code ConversationAgentSessionStore}
 *       实现 SPI 接口，将 AgentScope 框架的会话/消息/摘要调用适配到记忆层。</li>
 *   <li><b>定时归档调度</b>：{@code SessionCleanupScheduler} 定时归档
 *       不活跃会话，可配置 {@code ai.memory.cleanup.cron} 与
 *       {@code ai.memory.cleanup.delete-messages}。</li>
 * </ul>
 *
 * <h2>核心入口</h2>
 * <ul>
 *   <li><b>会话管理 API</b>：{@link com.yangqiongai.ai.platform.conversation.api.ConversationController}
 *       — CRUD + 关闭 + 搜索 + 导出 + 准备上下文。</li>
 *   <li><b>统计与 SLA API</b>：{@link com.yangqiongai.ai.platform.conversation.api.ConversationStatsController}
 *       — 会话使用统计 + SLA 分位查询。</li>
 *   <li><b>标签管理 API</b>：{@link com.yangqiongai.ai.platform.conversation.api.SessionTagController}
 *       — 标签 CRUD + 按标签查询会话。</li>
 *   <li><b>会话生命周期</b>：{@link com.yangqiongai.ai.platform.conversation.service.SessionLifecycleService}
 *       — 归档 + closeAndMerge + 导出。</li>
 *   <li><b>配额治理</b>：{@link com.yangqiongai.ai.platform.conversation.governance.SessionQuotaService}
 *       与 {@link com.yangqiongai.ai.platform.conversation.governance.TokenBudgetTracker}。</li>
 *   <li><b>SLA 追踪</b>：{@link com.yangqiongai.ai.platform.conversation.service.ConversationSlaService}。</li>
 *   <li><b>标签管理</b>：{@link com.yangqiongai.ai.platform.conversation.service.SessionTagService}。</li>
 *   <li><b>AgentSessionStore 适配</b>：{@link com.yangqiongai.ai.platform.conversation.adapter.ConversationAgentSessionStore}。</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 *
 * <h3>1. 创建会话（带配额校验）</h3>
 * <pre>{@code
 * POST /api/conversation/session
 * {
 *   "sessionId": "sess-001",
 *   "userId": "user-001",
 *   "sessionTitle": "技术问答",
 *   "agentCode": "default"
 * }
 * // 内部调用 SessionQuotaService.checkQuota 校验活跃/总会话数
 * }</pre>
 *
 * <h3>2. 分页搜索会话</h3>
 * <pre>{@code
 * POST /api/conversation/session/search
 * {
 *   "userId": "user-001",
 *   "keyword": "Spring",
 *   "status": 1,
 *   "agentCode": "default",
 *   "startTime": "2026-01-01T00:00:00",
 *   "endTime": "2026-07-01T00:00:00",
 *   "page": 1,
 *   "size": 20
 * }
 * }</pre>
 *
 * <h3>3. 关闭会话并合并长期记忆</h3>
 * <pre>{@code
 * PUT /api/conversation/session/{sessionId}/close
 * // 调用 SessionLifecycleService.closeAndMerge：合并摘要到长期记忆 → 关闭会话
 * }</pre>
 *
 * <h3>4. 导出会话</h3>
 * <pre>{@code
 * GET /api/conversation/session/{sessionId}/export?format=markdown
 * // 返回 Markdown 格式：标题 + 元信息 + 摘要 + 对话记录
 * }</pre>
 *
 * <h3>5. 查询会话使用统计</h3>
 * <pre>{@code
 * GET /api/conversation/session/stats?userId=user-001
 * // 返回 activeSessions/totalSessions/totalMessages/totalTokensUsed/
 * //   todayTokensUsed/dailyTokenLimit/longTermMemoryCount
 * }</pre>
 *
 * <h3>6. 查询 SLA 统计</h3>
 * <pre>{@code
 * GET /api/conversation/session/sla?userId=user-001&days=7
 * // 返回 totalConversations/avgResponseTimeMs/p95ResponseTimeMs/
 * //   p99ResponseTimeMs/maxResponseTimeMs/avgTokenCount
 * }</pre>
 *
 * <h3>7. 会话标签管理</h3>
 * <pre>{@code
 * POST   /api/conversation/session/tag/{sessionId}?userId=user-001&tag=技术问答
 * DELETE /api/conversation/session/tag/{sessionId}?tag=技术问答
 * GET    /api/conversation/session/tag/{sessionId}        // 查询会话所有标签
 * GET    /api/conversation/session/tag?userId=user-001    // 查询用户所有标签（去重）
 * GET    /api/conversation/session/tag/sessions?userId=user-001&tag=技术问答  // 按标签查会话
 * }</pre>
 *
 * <h2>配置项</h2>
 * <ul>
 *   <li>{@code ai.conversation.quota.max-active-sessions}：每用户最大活跃会话数（默认 10，0=不限）</li>
 *   <li>{@code ai.conversation.quota.max-total-sessions}：每用户最大总会话数（默认 100，0=不限）</li>
 *   <li>{@code ai.conversation.quota.daily-token-limit}：每用户每日 Token 上限（默认 100000，0=不限）</li>
 *   <li>{@code ai.conversation.quota.session-token-limit}：每会话 Token 上限（默认 8000，0=不限）</li>
 *   <li>{@code ai.memory.cleanup.cron}：会话清理 cron 表达式（默认每天 02:00）</li>
 *   <li>{@code ai.memory.cleanup.delete-messages}：归档时是否清理详细消息（默认 false）</li>
 * </ul>
 *
 * @author yangqiong
 */
package com.yangqiongai.ai.platform.conversation;
