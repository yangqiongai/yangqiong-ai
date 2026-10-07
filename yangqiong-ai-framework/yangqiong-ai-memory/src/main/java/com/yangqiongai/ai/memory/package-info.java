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
 * AI 记忆能力模块
 *
 * <p>提供智能体全场景记忆能力，覆盖短期对话、会话摘要、长期记忆与 LLM 主动记忆检索，
 * 兼容 AgentScope 2.0 的 LongTermMemory SPI，并对外暴露可被 LLM 调用的记忆工具。</p>
 *
 * <h2>功能清单</h2>
 * <ul>
 *   <li><b>短期对话记忆</b>：基于 {@code ChatMemory} 实体保存每轮 user/assistant 消息，
 *       支持按会话批量查询与删除。</li>
 *   <li><b>会话管理</b>：基于 {@code ConversationSession} 维护会话元数据，
 *       支持创建、查询、关闭、摘要更新与摘要轮次追踪。</li>
 *   <li><b>对话历史裁剪</b>：{@code HistoryTrimmer} 在 Token 预算内裁剪历史，
 *       支持 time-order（默认时间倒序保留）与 importance-first（重要性优先）两种策略，
 *       并自动将摘要作为系统消息前置。</li>
 *   <li><b>摘要生成</b>：{@code ConversationSummaryService} 异步增量生成会话摘要，
 *       内置 Redis 分布式锁防并发、RetryTemplate 重试、LLM 失败时降级为对话截断，
 *       触发策略通过 {@code SummaryTriggerStrategy} SPI 扩展（内置 message-count、
 *       token-consumption、time-interval 三种）。</li>
 *   <li><b>长期记忆存储</b>：{@code LongTermMemoryManager} 按用户维度持久化
 *       SUMMARY 类型记忆，关闭会话时合并摘要到长期记忆，优先调用 LLM 合并、
 *       失败降级为追加并截断至 {@code ai.memory.cross-session.max-content-chars}。</li>
 *   <li><b>跨会话记忆注入</b>：{@code DialogMemoryAdapter#prepareContext} 在会话准备阶段
 *       可选加载用户长期记忆并前置于会话摘要，使后续裁剪保留。</li>
 *   <li><b>长期记忆主动检索</b>：{@code LongTermMemoryManager#loadByQuery} 基于查询文本
 *       做关键词匹配与相关性评分（中文按字符切分，英文按空白切分，评分=匹配词数/查询总词数），
 *       按相关性降序在 Token 预算内返回。</li>
 *   <li><b>AgentScope LongTermMemory 集成</b>：{@code DbLongTermMemory} 实现
 *       {@code io.agentscope.core.memory.LongTermMemory} 接口，
 *       以响应式 Mono + boundedElastic 调度异步执行 record/retrieve，
 *       异常一律返回空字符串不中断 Agent。</li>
 *   <li><b>LLM 主动记忆工具</b>：{@code LongTermMemoryToolRegistrar} 通过 SPI 接口
 *       {@code LongTermMemoryToolRegistrar} 将 {@code LongTermMemoryTools} 注册到
 *       Agent Toolkit，使 LLM 可主动调用 recordToMemory / retrieveFromMemory。</li>
 *   <li><b>监控指标</b>：{@code ConversationMetrics} 埋点会话创建/关闭、摘要触发/耗时/失败、
 *       裁剪消息数/Token 用量、长期记忆检索/记录计数与耗时，MeterRegistry 不可用时静默跳过。</li>
 *   <li><b>配置命名空间统一</b>：所有配置项主键迁移至 {@code ai.memory.*}，
 *       通过 {@code ${ai.memory.x:${ai.conversation.x:default}}} 形式兼容旧别名。</li>
 *   <li><b>记忆增强 SPI 缝隙</b>：{@code spi} 包定义 {@code MemoryEnhancer} /
 *       {@code MemoryStoreProvider} 商业实现挂载点，社区缺省装配 {@code NoopMemoryEnhancer}
 *       不做去重与衰减；去重/衰减进阶实现位于商业模块 yangqiong-ai-memory-enterprise，
 *       经 {@code MemoryEnhancer.onStore/onRecall} 接入写入与检索链路。</li>
 * </ul>
 *
 * <h2>核心入口</h2>
 * <ul>
 *   <li><b>对外门面</b>：{@link com.yangqiongai.ai.memory.memory.DialogMemoryAdapter}
 *       — 会话准备、消息追加、历史获取、摘要触发、状态持久化。</li>
 *   <li><b>会话 CRUD</b>：{@link com.yangqiongai.ai.memory.SessionManager}
 *       — 创建、查询、更新、关闭、摘要维护。</li>
 *   <li><b>消息 CRUD</b>：{@link com.yangqiongai.ai.memory.ChatMemoryManager}
 *       — 消息保存、按会话查询、按会话删除。</li>
 *   <li><b>长期记忆</b>：{@link com.yangqiongai.ai.memory.LongTermMemoryManager}
 *       — 合并会话摘要、按 Token 预算加载、按查询关键词检索。</li>
 *   <li><b>AgentScope 适配</b>：{@code DbLongTermMemory}（已迁移至 yangqiong-ai-agent-scope 模块）
 *       — 响应式 LongTermMemory 实现，供 AgentScope 框架回调。</li>
 *   <li><b>工具注册</b>：{@code LongTermMemoryToolRegistrar}（已迁移至 yangqiong-ai-agent-scope 模块）
 *       — 通过 SPI 接口将记忆工具注册到 Agent Toolkit。</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 *
 * <h3>1. 准备对话上下文（含跨会话长期记忆）</h3>
 * <pre>{@code
 * @Autowired
 * private DialogMemoryAdapter dialogMemoryAdapter;
 *
 * ConversationSession session = dialogMemoryAdapter.prepareContext(
 *     sessionId, userId, true); // enableCrossSessionMemory=true 加载长期记忆
 * }</pre>
 *
 * <h3>2. 追加消息并自动触发摘要</h3>
 * <pre>{@code
 * dialogMemoryAdapter.appendMessage(sessionId, "user", userInput);
 * dialogMemoryAdapter.appendMessage(sessionId, "assistant", assistantReply);
 * // 内部异步触发 ConversationSummaryService.triggerIncrementalSummaryAsync
 * }</pre>
 *
 * <h3>3. 获取裁剪后的历史消息（AgentMessage 格式）</h3>
 * <pre>{@code
 * List<AgentMessage> history = dialogMemoryAdapter.getHistory(sessionId, 4000);
 * }</pre>
 *
 * <h3>4. 关闭会话并合并长期记忆</h3>
 * <pre>{@code
 * @Autowired
 * private SessionManager sessionManager;
 * @Autowired
 * private LongTermMemoryManager longTermMemoryManager;
 *
 * sessionManager.findBySessionId(sessionId).ifPresent(session -> {
 *     if (session.getSummaryText() != null && !session.getSummaryText().isBlank()) {
 *         longTermMemoryManager.mergeSessionSummary(
 *             session.getUserId(), sessionId, session.getSummaryText());
 *     }
 * });
 * sessionManager.closeSession(sessionId);
 * }</pre>
 *
 * <h3>5. LLM 主动记忆能力（自动启用）</h3>
 * <p>当 {@code ai.memory.long-term.tool-enabled=true}（默认）时，
 * {@code AbstractAgentProcessor} 构建的 Agent 会自动注册 LongTermMemoryTools，
 * LLM 可在 ReAct 循环中主动调用记忆工具；子类覆盖
 * {@code disableLongTermMemoryTools()} 返回 true 可禁用。</p>
 *
 * <h3>6. 按查询文本检索长期记忆</h3>
 * <pre>{@code
 * String memory = longTermMemoryManager.loadByQuery(userId, "用户偏好", 500);
 * }</pre>
 *
 * <h2>配置项（ai.memory.* 命名空间，兼容 ai.conversation.* 旧别名）</h2>
 * <ul>
 *   <li>{@code ai.memory.long-term.tool-enabled}：是否启用 LLM 主动记忆工具（默认 true）</li>
 *   <li>{@code ai.memory.long-term.retrieve-max-tokens}：长期记忆检索 Token 预算（默认 500）</li>
 *   <li>{@code ai.memory.summary.enabled}：是否启用摘要生成（默认 true）</li>
 *   <li>{@code ai.memory.summary.trigger-count}：消息数触发阈值（默认 12）</li>
 *   <li>{@code ai.memory.summary.model-code}：摘要使用的模型编码（默认 default）</li>
 *   <li>{@code ai.memory.trim.strategy}：裁剪策略 time-order / importance-first（默认 time-order）</li>
 *   <li>{@code ai.memory.token.estimator}：Token 估算器 fast / precise（默认 fast）</li>
 *   <li>{@code ai.memory.cross-session.model-code}：长期记忆合并模型编码（默认 default）</li>
 *   <li>{@code ai.memory.cross-session.max-content-chars}：长期记忆最大字符数（默认 2000）</li>
 *   <li>{@code ai.memory.cleanup.cron}：会话清理 cron 表达式（默认每天 02:00）</li>
 * </ul>
 *
 * @author yangqiong
 */
package com.yangqiongai.ai.memory;
