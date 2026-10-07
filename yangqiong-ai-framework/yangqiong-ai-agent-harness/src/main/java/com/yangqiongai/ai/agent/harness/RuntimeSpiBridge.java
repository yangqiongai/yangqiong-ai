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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.config.CostBudgetPolicy;
import com.yangqiongai.agent.harness.config.TokenBudgetPolicy;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.memory.AgentLongTermMemory;
import com.yangqiongai.agent.harness.core.memory.SessionMemory;
import com.yangqiongai.agent.harness.core.memory.SessionSummary;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.trace.TraceEmitter;
import com.yangqiongai.agent.harness.durable.AgentCheckpoint;
import com.yangqiongai.agent.harness.durable.AgentRunRecord;
import com.yangqiongai.agent.harness.durable.AgentRunState;
import com.yangqiongai.agent.harness.durable.AgentRunStore;
import com.yangqiongai.agent.harness.durable.ApprovalRecord;
import com.yangqiongai.agent.harness.durable.ApprovalStore;
import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.durable.RunLockStore;
import com.yangqiongai.agent.harness.engine.AgentLoop;
import com.yangqiongai.agent.harness.memory.CheckpointManager;
import com.yangqiongai.agent.harness.memory.ForgettingPolicy;
import com.yangqiongai.agent.harness.guardrail.ContentModerationPolicy;
import com.yangqiongai.agent.harness.guardrail.ModerationVerdict;
import com.yangqiongai.agent.harness.permission.ToolPolicyGate;
import com.yangqiongai.agent.harness.rag.DocumentParser;
import com.yangqiongai.agent.harness.rag.RetrievedChunk;
import com.yangqiongai.agent.harness.rag.Retriever;
import com.yangqiongai.agent.harness.tool.ToolExecutionStore;
import com.yangqiongai.agent.harness.web.WebFetcher;
import com.yangqiongai.agent.harness.web.WebSearchProvider;
import com.yangqiongai.agent.harness.web.WebSearchResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 框架SPI桥接转换器
 * <p>
 * 将框架防腐SPI（budget/guardrail/rag/web/tool/event/permission/durable/memory）
 * 包装为引擎对应SPI实现，委托框架接口执行，引擎内部零感知。
 * </p>
 * @author yangqiong
 */
public final class RuntimeSpiBridge {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSpiBridge.class);

    private RuntimeSpiBridge() {
    }

    // ========== 预算与成本 ==========

    /**
     * 桥接Token预算策略
     * @param policy
     * @return
     */
    public static TokenBudgetPolicy toHarness(com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new TokenBudgetPolicy(0, 0, TokenBudgetPolicy.ExceedAction.WARN) {

            @Override
            public boolean isWarnExceeded(long usedTokens) {
                return policy.isWarnExceeded(usedTokens);
            }

            @Override
            public boolean isHardExceeded(long usedTokens) {
                return policy.isHardExceeded(usedTokens);
            }
        };
    }

    /**
     * 桥接成本预算策略
     * @param policy
     * @return
     */
    public static CostBudgetPolicy toHarness(com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new CostBudgetPolicy(0.0, 0.0, CostBudgetPolicy.ExceedAction.WARN) {

            @Override
            public boolean isWarnExceeded(double currentCostUsd) {
                return policy.isWarnExceeded(currentCostUsd);
            }

            @Override
            public boolean isHardExceeded(double currentCostUsd) {
                return policy.isHardExceeded(currentCostUsd);
            }
        };
    }

    /**
     * 桥接模型调用限流器
     * @param limiter
     * @return
     */
    public static com.yangqiongai.agent.harness.ratelimit.RateLimiter toHarness(
            com.yangqiongai.ai.agent.runtime.budget.RateLimiter limiter) {
        if (limiter == null) {
            return null;
        }
        return new com.yangqiongai.agent.harness.ratelimit.RateLimiter() {

            @Override
            public long waitNanos() {
                return limiter.waitNanos();
            }

            @Override
            public double getCurrentRate() {
                return limiter.getCurrentRate();
            }
        };
    }

    // ========== 安全护栏 ==========

    /**
     * 桥接内容审查策略
     * @param policy
     * @return
     */
    public static ContentModerationPolicy toHarness(
            com.yangqiongai.ai.agent.runtime.guardrail.ContentModerationPolicy policy) {
        if (policy == null) {
            return null;
        }
        return message -> {
            com.yangqiongai.ai.agent.runtime.guardrail.ModerationVerdict verdict =
                    policy.moderate(RuntimeTypeConverter.toRuntime(message));
            if (verdict == null) {
                return ModerationVerdict.pass();
            }
            if (verdict.isBlock()) {
                return ModerationVerdict.block(verdict.getReason());
            }
            if (verdict.isMask()) {
                return ModerationVerdict.mask(verdict.getReason());
            }
            return ModerationVerdict.pass();
        };
    }

    // ========== RAG与Web ==========

    /**
     * 桥接命名检索器
     * @param retriever
     * @return
     */
    public static Retriever toHarness(com.yangqiongai.ai.agent.runtime.rag.Retriever retriever) {
        if (retriever == null) {
            return null;
        }
        return (query, topK, filters) -> {
            List<com.yangqiongai.ai.agent.runtime.rag.RetrievedChunk> chunks =
                    retriever.retrieve(query, topK, filters);
            if (chunks == null) {
                return null;
            }
            List<RetrievedChunk> result = new ArrayList<>(chunks.size());
            for (com.yangqiongai.ai.agent.runtime.rag.RetrievedChunk chunk : chunks) {
                if (chunk != null) {
                    result.add(new RetrievedChunk(chunk.content(), chunk.score(),
                            chunk.source(), chunk.metadata()));
                }
            }
            return result;
        };
    }

    /**
     * 桥接Web检索提供方
     * @param provider
     * @return
     */
    public static WebSearchProvider toHarness(com.yangqiongai.ai.agent.runtime.web.WebSearchProvider provider) {
        if (provider == null) {
            return null;
        }
        return (query, topK) -> {
            List<com.yangqiongai.ai.agent.runtime.web.WebSearchResult> results = provider.search(query, topK);
            if (results == null) {
                return null;
            }
            List<WebSearchResult> converted = new ArrayList<>(results.size());
            for (com.yangqiongai.ai.agent.runtime.web.WebSearchResult result : results) {
                if (result != null) {
                    converted.add(new WebSearchResult(result.title(), result.url(),
                            result.snippet(), result.score()));
                }
            }
            return converted;
        };
    }

    /**
     * 桥接网页抓取提供方
     * @param fetcher
     * @return
     */
    public static WebFetcher toHarness(com.yangqiongai.ai.agent.runtime.web.WebFetcher fetcher) {
        if (fetcher == null) {
            return null;
        }
        return fetcher::fetch;
    }

    /**
     * 桥接文档解析器
     * @param parser
     * @return
     */
    public static DocumentParser toHarness(com.yangqiongai.ai.agent.runtime.tool.DocumentParser parser) {
        if (parser == null) {
            return null;
        }
        return parser::parse;
    }

    // ========== 事件与权限 ==========

    /**
     * 桥接事件监听器
     * @param listener
     * @return
     */
    public static com.yangqiongai.agent.harness.event.AgentEventListener toHarness(
            com.yangqiongai.ai.agent.runtime.event.AgentEventListener listener) {
        if (listener == null) {
            return null;
        }
        return new com.yangqiongai.agent.harness.event.AgentEventListener() {

            @Override
            public boolean isInterestedIn(AgentEventType type) {
                try {
                    return listener.isInterestedIn(
                            com.yangqiongai.ai.agent.runtime.event.AgentEventType.valueOf(type.name()));
                } catch (IllegalArgumentException e) {
                    // 框架枚举未跟进的引擎类型视为不感兴趣，避免异常中断事件分发
                    log.warn("事件类型在框架枚举中不存在，监听器跳过: type={}", type.name());
                    return false;
                }
            }

            @Override
            public void onEvent(AgentEvent event) {
                listener.onEvent(SpiConverters.toRuntimeEvent(event));
            }
        };
    }

    /**
     * 桥接统一权限策略门
     * @param gate
     * @return
     */
    public static ToolPolicyGate toHarness(com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate gate) {
        if (gate == null) {
            return null;
        }
        return new ToolPolicyGate(null, null, null, false, null) {

            @Override
            public com.yangqiongai.agent.harness.config.AgentPermissionDecision evaluate(
                    String toolName, String toolCallId, String scopeId, String runId,
                    Map<String, Object> toolInput) {
                com.yangqiongai.ai.agent.runtime.config.AgentPermissionDecision decision =
                        gate.evaluate(toolName, toolCallId, scopeId, runId, toolInput);
                return RuntimeTypeConverter.toHarness(decision);
            }
        };
    }

    // ========== 持久化与分布式 ==========

    /**
     * 桥接运行记录存储
     * @param store
     * @return
     */
    public static AgentRunStore toHarness(com.yangqiongai.ai.agent.runtime.durable.AgentRunStore store) {
        if (store == null) {
            return null;
        }
        return new AgentRunStore() {

            @Override
            public AgentRunRecord create(AgentRunRecord record) {
                return toEngineRunRecord(store.create(toRuntimeRunRecord(record)));
            }

            @Override
            public Optional<AgentRunRecord> findByRunId(String runId) {
                return store.findByRunId(runId).map(RuntimeSpiBridge::toEngineRunRecord);
            }

            @Override
            public Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId) {
                return store.findLatestBySession(scopeId, sessionId).map(RuntimeSpiBridge::toEngineRunRecord);
            }

            @Override
            public List<AgentRunRecord> findBySession(String scopeId, String sessionId) {
                return toEngineRunRecords(store.findBySession(scopeId, sessionId));
            }

            @Override
            public AgentRunRecord saveTransition(AgentRunRecord record) {
                return toEngineRunRecord(store.saveTransition(toRuntimeRunRecord(record)));
            }

            @Override
            public List<AgentRunRecord> findWaitingApproval(String scopeId) {
                return toEngineRunRecords(store.findWaitingApproval(scopeId));
            }
        };
    }

    /**
     * 桥接检查点存储
     * @param store
     * @return
     */
    public static com.yangqiongai.agent.harness.durable.CheckpointStore toHarness(
            com.yangqiongai.ai.agent.runtime.durable.CheckpointStore store) {
        if (store == null) {
            return null;
        }
        return new com.yangqiongai.agent.harness.durable.CheckpointStore() {

            @Override
            public void save(AgentCheckpoint checkpoint) {
                store.save(toRuntimeCheckpoint(checkpoint));
            }

            @Override
            public Optional<AgentCheckpoint> latest(String scopeId, String sessionId) {
                return store.latest(scopeId, sessionId).map(RuntimeSpiBridge::toEngineCheckpoint);
            }

            @Override
            public List<AgentCheckpoint> findByRunId(String runId) {
                List<com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint> checkpoints = store.findByRunId(runId);
                List<AgentCheckpoint> result = new ArrayList<>(checkpoints.size());
                for (com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint checkpoint : checkpoints) {
                    result.add(toEngineCheckpoint(checkpoint));
                }
                return result;
            }

            @Override
            public void clear(String runId) {
                store.clear(runId);
            }
        };
    }

    /**
     * 桥接审批存储
     * @param store
     * @return
     */
    public static ApprovalStore toHarness(com.yangqiongai.ai.agent.runtime.durable.ApprovalStore store) {
        if (store == null) {
            return null;
        }
        return new ApprovalStore() {

            @Override
            public ApprovalRecord create(ApprovalRecord record) {
                return toEngineApprovalRecord(store.create(toRuntimeApprovalRecord(record)));
            }

            @Override
            public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
                return store.findByApprovalId(approvalId).map(RuntimeSpiBridge::toEngineApprovalRecord);
            }

            @Override
            public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
                return store.findByToolCallId(toolCallId).map(RuntimeSpiBridge::toEngineApprovalRecord);
            }

            @Override
            public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
                return toEngineApprovalRecord(store.resolve(approvalId, approved, reason));
            }

            @Override
            public List<ApprovalRecord> findPending(String scopeId) {
                return toEngineApprovalRecords(store.findPending(scopeId));
            }

            @Override
            public List<ApprovalRecord> findByRunId(String runId) {
                return toEngineApprovalRecords(store.findByRunId(runId));
            }
        };
    }

    /**
     * 桥接运行锁存储
     * @param store
     * @return
     */
    public static RunLockStore toHarness(com.yangqiongai.ai.agent.runtime.durable.RunLockStore store) {
        if (store == null) {
            return null;
        }
        return new RunLockStore() {

            @Override
            public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
                return store.tryLock(runId, ownerNodeId, lockTtl);
            }

            @Override
            public void renew(String runId, String ownerNodeId, Duration lockTtl) {
                store.renew(runId, ownerNodeId, lockTtl);
            }

            @Override
            public void unlock(String runId, String ownerNodeId) {
                store.unlock(runId, ownerNodeId);
            }

            @Override
            public Optional<String> owner(String runId) {
                return store.owner(runId);
            }
        };
    }

    /**
     * 桥接工具执行记录存储
     * @param store
     * @return
     */
    public static ToolExecutionStore toHarness(com.yangqiongai.ai.agent.runtime.durable.ToolExecutionStore store) {
        if (store == null) {
            return null;
        }
        return new ToolExecutionStore() {

            @Override
            public void record(String idempotencyKey, AgentToolResultBlock result) {
                store.record(idempotencyKey, RuntimeTypeConverter.toRuntime(result));
            }

            @Override
            public boolean isCompleted(String idempotencyKey) {
                return store.isCompleted(idempotencyKey);
            }

            @Override
            public AgentToolResultBlock getResult(String idempotencyKey) {
                com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock result = store.getResult(idempotencyKey);
                return result != null ? RuntimeTypeConverter.toHarness(result) : null;
            }
        };
    }

    /**
     * 桥接检查点管理器
     * @param manager
     * @return
     */
    public static CheckpointManager toHarness(com.yangqiongai.ai.agent.runtime.durable.CheckpointManager manager) {
        if (manager == null) {
            return null;
        }
        return new CheckpointManager() {

            @Override
            public synchronized void save(String sessionId, List<AgentMessage> messages, int iteration) {
                manager.save(sessionId, toRuntimeMessages(messages), iteration);
            }

            @Override
            public List<AgentMessage> restore(String sessionId) {
                return toEngineMessages(manager.restore(sessionId));
            }

            @Override
            public int restoreIteration(String sessionId) {
                return manager.restoreIteration(sessionId);
            }

            @Override
            public void clear(String sessionId) {
                manager.clear(sessionId);
            }

            @Override
            public boolean hasCheckpoint(String sessionId) {
                return manager.hasCheckpoint(sessionId);
            }
        };
    }

    // ========== 记忆子系统 ==========

    /**
     * 桥接L3长期记忆
     * @param longTermMemory
     * @return
     */
    public static AgentLongTermMemory toHarness(
            com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory longTermMemory) {
        if (longTermMemory == null) {
            return null;
        }
        return new AgentLongTermMemory() {

            @Override
            public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
                longTermMemory.store(userId, sessionId, content, metadata);
            }

            @Override
            public List<String> search(String userId, String query, int limit) {
                return longTermMemory.search(userId, query, limit);
            }

            @Override
            public void delete(String memoryId) {
                longTermMemory.delete(memoryId);
            }
        };
    }

    /**
     * 桥接引擎L3长期记忆为框架SPI，供平台侧自动配置暴露框架类型
     * @param longTermMemory
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory toRuntime(AgentLongTermMemory longTermMemory) {
        if (longTermMemory == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory() {

            @Override
            public void store(String userId, String sessionId, String content, Map<String, Object> metadata) {
                longTermMemory.store(userId, sessionId, content, metadata);
            }

            @Override
            public List<String> search(String userId, String query, int limit) {
                return longTermMemory.search(userId, query, limit);
            }

            @Override
            public void delete(String memoryId) {
                longTermMemory.delete(memoryId);
            }
        };
    }

    /**
     * 桥接记忆遗忘策略
     * @param policy
     * @return
     */
    public static ForgettingPolicy toHarness(com.yangqiongai.ai.agent.runtime.memory.ForgettingPolicy policy) {
        if (policy == null) {
            return null;
        }
        return info -> policy.shouldForget(new com.yangqiongai.ai.agent.runtime.memory.ForgettingPolicy.EntryInfo(
                info.memoryId(), info.content(), info.createdAt(), info.lastAccessedAt(), info.accessCount()));
    }

    /**
     * 桥接L2会话级短期记忆
     * @param sessionMemory
     * @return
     */
    public static SessionMemory toHarness(com.yangqiongai.ai.agent.runtime.memory.AgentSessionMemory sessionMemory) {
        if (sessionMemory == null) {
            return null;
        }
        return new SessionMemory() {

            @Override
            public void saveSummary(String sessionId, String summary, int summarizedMessageCount) {
                sessionMemory.saveSummary(sessionId, summary, summarizedMessageCount);
            }

            @Override
            public SessionSummary loadSummary(String sessionId) {
                com.yangqiongai.ai.agent.runtime.memory.SessionSummary summary = sessionMemory.loadSummary(sessionId);
                return summary != null ? new SessionSummary(summary.summary(), summary.summarizedMessageCount()) : null;
            }

            @Override
            public void saveEpisode(String sessionId, AgentMessage episode) {
                sessionMemory.saveEpisode(sessionId, RuntimeTypeConverter.toRuntime(episode));
            }

            @Override
            public List<AgentMessage> listEpisodes(String sessionId, int limit) {
                return toEngineMessages(sessionMemory.listEpisodes(sessionId, limit));
            }

            @Override
            public void clear(String sessionId) {
                sessionMemory.clear(sessionId);
            }
        };
    }

    /**
     * 桥接分布式共享存储集合
     * @param stores
     * @return
     */
    public static DistributedStores toHarness(com.yangqiongai.ai.agent.runtime.durable.DistributedStores stores) {
        if (stores == null) {
            return null;
        }
        return new DistributedStores() {

            @Override
            public AgentRunStore runStore() {
                return toHarness(stores.runStore());
            }

            @Override
            public com.yangqiongai.agent.harness.durable.CheckpointStore checkpointStore() {
                return toHarness(stores.checkpointStore());
            }

            @Override
            public ApprovalStore approvalStore() {
                return toHarness(stores.approvalStore());
            }

            @Override
            public SessionMemory sessionMemory() {
                return toHarness(stores.sessionMemory());
            }

            @Override
            public AgentLongTermMemory longTermMemory() {
                return toHarness(stores.longTermMemory());
            }

            @Override
            public ToolExecutionStore toolExecutionStore() {
                return toHarness(stores.toolExecutionStore());
            }

            @Override
            public RunLockStore runLockStore() {
                return toHarness(stores.runLockStore());
            }
        };
    }

    // ========== 可观测性与扩展点 ==========

    /**
     * 桥接追踪导出器
     * @param emitter
     * @return
     */
    public static TraceEmitter toHarness(com.yangqiongai.ai.agent.runtime.trace.TraceEmitter emitter) {
        if (emitter == null) {
            return null;
        }
        return spanInfo -> emitter.onSpan(new com.yangqiongai.ai.agent.runtime.trace.SpanInfo(
                spanInfo.getTraceId(), spanInfo.getSpanId(), spanInfo.getParentSpanId(),
                spanInfo.getOperation(), spanInfo.getDurationMs(), spanInfo.getAttributes(),
                spanInfo.getStatus(), spanInfo.getErrorMessage(), spanInfo.getStartTimeMs()));
    }

    /**
     * 桥接上下文快照监听器
     * @param listener
     * @return
     */
    public static com.yangqiongai.agent.harness.core.trace.ContextSnapshotListener toHarness(
            com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener listener) {
        if (listener == null) {
            return null;
        }
        return snapshot -> listener.onSnapshot(new com.yangqiongai.ai.agent.runtime.trace.ContextSnapshot(
                snapshot.getTaskId(), snapshot.getTraceId(), snapshot.getSessionId(), snapshot.getAgentCode(),
                snapshot.getScopeId(), snapshot.getModelCode(), snapshot.getCallSeq(),
                toRuntimeContextMessages(snapshot.getMessages())));
    }

    /**
     * 快照消息引擎转运行时
     * @param messages
     * @return
     */
    private static java.util.List<com.yangqiongai.ai.agent.runtime.trace.ContextMessage> toRuntimeContextMessages(
            java.util.List<com.yangqiongai.agent.harness.model.ContextMessage> messages) {
        if (messages == null) {
            return java.util.List.of();
        }
        java.util.List<com.yangqiongai.ai.agent.runtime.trace.ContextMessage> result =
                new java.util.ArrayList<>(messages.size());
        for (com.yangqiongai.agent.harness.model.ContextMessage message : messages) {
            result.add(new com.yangqiongai.ai.agent.runtime.trace.ContextMessage(
                    message.getRole(), message.getContent(), message.getSource(), message.isTruncated()));
        }
        return result;
    }

    /**
     * 桥接自定义执行循环，上下文经引擎内置AgentRuntimeContext转换
     * @param loop
     * @return
     */
    public static AgentLoop toHarness(com.yangqiongai.ai.agent.runtime.spi.AgentLoop loop) {
        if (loop == null) {
            return null;
        }
        return (inputs, context) -> loop.run(
                        SpiConverters.toRuntimeMessages(inputs),
                        SpiConverters.toRuntimeContext(context.getRuntimeContext()))
                .mapNotNull(SpiConverters::toHarnessEvent);
    }

    // ========== 引擎转框架（多节点共享存储装配用） ==========

    /**
     * 桥接引擎运行记录存储为框架SPI
     * @param store
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.durable.AgentRunStore toRuntime(AgentRunStore store) {
        if (store == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.AgentRunStore() {

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord create(
                    com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord record) {
                return toRuntimeRunRecord(store.create(toEngineRunRecord(record)));
            }

            @Override
            public Optional<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> findByRunId(String runId) {
                return store.findByRunId(runId).map(RuntimeSpiBridge::toRuntimeRunRecord);
            }

            @Override
            public Optional<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> findLatestBySession(
                    String scopeId, String sessionId) {
                return store.findLatestBySession(scopeId, sessionId).map(RuntimeSpiBridge::toRuntimeRunRecord);
            }

            @Override
            public List<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> findBySession(
                    String scopeId, String sessionId) {
                List<AgentRunRecord> records = store.findBySession(scopeId, sessionId);
                List<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> result =
                        new ArrayList<>(records.size());
                for (AgentRunRecord record : records) {
                    result.add(toRuntimeRunRecord(record));
                }
                return result;
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord saveTransition(
                    com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord record) {
                return toRuntimeRunRecord(store.saveTransition(toEngineRunRecord(record)));
            }

            @Override
            public List<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> findWaitingApproval(String scopeId) {
                List<AgentRunRecord> records = store.findWaitingApproval(scopeId);
                List<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> result =
                        new ArrayList<>(records.size());
                for (AgentRunRecord record : records) {
                    result.add(toRuntimeRunRecord(record));
                }
                return result;
            }
        };
    }

    /**
     * 桥接引擎检查点存储为框架SPI
     * @param store
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.durable.CheckpointStore toRuntime(
            com.yangqiongai.agent.harness.durable.CheckpointStore store) {
        if (store == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.CheckpointStore() {

            @Override
            public void save(com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint checkpoint) {
                store.save(toEngineCheckpoint(checkpoint));
            }

            @Override
            public Optional<com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint> latest(
                    String scopeId, String sessionId) {
                return store.latest(scopeId, sessionId).map(RuntimeSpiBridge::toRuntimeCheckpoint);
            }

            @Override
            public List<com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint> findByRunId(String runId) {
                List<AgentCheckpoint> checkpoints = store.findByRunId(runId);
                List<com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint> result =
                        new ArrayList<>(checkpoints.size());
                for (AgentCheckpoint checkpoint : checkpoints) {
                    result.add(toRuntimeCheckpoint(checkpoint));
                }
                return result;
            }

            @Override
            public void clear(String runId) {
                store.clear(runId);
            }
        };
    }

    /**
     * 桥接引擎审批存储为框架SPI
     * @param store
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.durable.ApprovalStore toRuntime(ApprovalStore store) {
        if (store == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.ApprovalStore() {

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord create(
                    com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord record) {
                return toRuntimeApprovalRecord(store.create(toEngineApprovalRecord(record)));
            }

            @Override
            public Optional<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> findByApprovalId(
                    String approvalId) {
                return store.findByApprovalId(approvalId).map(RuntimeSpiBridge::toRuntimeApprovalRecord);
            }

            @Override
            public Optional<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> findByToolCallId(
                    String toolCallId) {
                return store.findByToolCallId(toolCallId).map(RuntimeSpiBridge::toRuntimeApprovalRecord);
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord resolve(String approvalId,
                    boolean approved, String reason) {
                return toRuntimeApprovalRecord(store.resolve(approvalId, approved, reason));
            }

            @Override
            public List<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> findPending(String scopeId) {
                return toRuntimeApprovalRecords(store.findPending(scopeId));
            }

            @Override
            public List<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> findByRunId(String runId) {
                return toRuntimeApprovalRecords(store.findByRunId(runId));
            }
        };
    }

    /**
     * 桥接引擎运行锁存储为框架SPI
     * @param store
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.durable.RunLockStore toRuntime(RunLockStore store) {
        if (store == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.RunLockStore() {

            @Override
            public boolean tryLock(String runId, String ownerNodeId, Duration lockTtl) {
                return store.tryLock(runId, ownerNodeId, lockTtl);
            }

            @Override
            public void renew(String runId, String ownerNodeId, Duration lockTtl) {
                store.renew(runId, ownerNodeId, lockTtl);
            }

            @Override
            public void unlock(String runId, String ownerNodeId) {
                store.unlock(runId, ownerNodeId);
            }

            @Override
            public Optional<String> owner(String runId) {
                return store.owner(runId);
            }
        };
    }

    /**
     * 桥接引擎分布式共享存储集合为框架SPI（仅桥接持久执行四项，记忆类由框架侧自有装配提供）
     * @param stores
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.durable.DistributedStores toRuntime(DistributedStores stores) {
        if (stores == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.DistributedStores() {

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.AgentRunStore runStore() {
                return toRuntime(stores.runStore());
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.CheckpointStore checkpointStore() {
                return toRuntime(stores.checkpointStore());
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.ApprovalStore approvalStore() {
                return toRuntime(stores.approvalStore());
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.memory.AgentSessionMemory sessionMemory() {
                return null;
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory longTermMemory() {
                return null;
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.ToolExecutionStore toolExecutionStore() {
                return null;
            }

            @Override
            public com.yangqiongai.ai.agent.runtime.durable.RunLockStore runLockStore() {
                return toRuntime(stores.runLockStore());
            }
        };
    }

    /**
     * 审批记录列表引擎转Runtime
     * @param records
     * @return
     */
    private static List<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> toRuntimeApprovalRecords(
            List<ApprovalRecord> records) {
        if (records == null) {
            return null;
        }
        List<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> result = new ArrayList<>(records.size());
        for (ApprovalRecord record : records) {
            result.add(toRuntimeApprovalRecord(record));
        }
        return result;
    }

    // ========== 私有转换方法 ==========

    /**
     * 运行记录Runtime转引擎
     * @param record
     * @return
     */
    private static AgentRunRecord toEngineRunRecord(com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord record) {
        if (record == null) {
            return null;
        }
        AgentRunRecord converted = new AgentRunRecord(record.getRunId(), record.getScopeId(),
                record.getSessionId(), record.getUserId(), record.getAgentName());
        // 持久化快照恢复不走状态机校验：轨迹首条CREATED与初始状态同态，逐条transitionTo重放会误判非法迁移且污染version
        List<AgentRunRecord.StateTransition> transitions = new ArrayList<>();
        if (record.getTransitions() != null) {
            for (com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord.StateTransition transition : record.getTransitions()) {
                transitions.add(new AgentRunRecord.StateTransition(
                        AgentRunState.valueOf(transition.state().name()), transition.timestamp(), transition.reason()));
            }
        }
        converted.restore(AgentRunState.valueOf(record.getState().name()), record.getVersion(),
                record.getUpdatedAt(), record.getError(), transitions);
        return converted;
    }

    /**
     * 运行记录引擎转Runtime
     * @param record
     * @return
     */
    private static com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord toRuntimeRunRecord(AgentRunRecord record) {
        if (record == null) {
            return null;
        }
        com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord converted =
                new com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord(
                        record.getRunId(), record.getScopeId(), record.getSessionId(),
                        record.getUserId(), record.getAgentName(),
                        com.yangqiongai.ai.agent.runtime.durable.AgentRunState.valueOf(record.getState().name()),
                        record.getCreatedAt());
        // 持久化快照恢复不走状态机校验：轨迹首条CREATED与初始状态同态，逐条transitionTo重放会误判非法迁移且污染version
        List<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord.StateTransition> transitions = new ArrayList<>();
        if (record.getTransitions() != null) {
            for (AgentRunRecord.StateTransition transition : record.getTransitions()) {
                transitions.add(new com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord.StateTransition(
                        com.yangqiongai.ai.agent.runtime.durable.AgentRunState.valueOf(transition.getState().name()),
                        transition.getTimestamp(), transition.getReason()));
            }
        }
        converted.restore(
                com.yangqiongai.ai.agent.runtime.durable.AgentRunState.valueOf(record.getState().name()),
                record.getVersion(), record.getUpdatedAt(), record.getError(), transitions);
        return converted;
    }

    /**
     * 运行记录列表Runtime转引擎
     * @param records
     * @return
     */
    private static List<AgentRunRecord> toEngineRunRecords(
            List<com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord> records) {
        if (records == null) {
            return null;
        }
        List<AgentRunRecord> result = new ArrayList<>(records.size());
        for (com.yangqiongai.ai.agent.runtime.durable.AgentRunRecord record : records) {
            result.add(toEngineRunRecord(record));
        }
        return result;
    }

    /**
     * 检查点引擎转Runtime
     * @param checkpoint
     * @return
     */
    private static com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint toRuntimeCheckpoint(AgentCheckpoint checkpoint) {
        if (checkpoint == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint(
                checkpoint.getRunId(), checkpoint.getScopeId(), checkpoint.getSessionId(),
                checkpoint.getIteration(), toRuntimeMessages(checkpoint.getMessages()),
                toRuntimeToolUseBlocks(checkpoint.getPendingToolCalls()),
                checkpoint.getCompletedToolUseIds(), checkpoint.getTimestamp(), checkpoint.getVersion());
    }

    /**
     * 检查点Runtime转引擎
     * @param checkpoint
     * @return
     */
    private static AgentCheckpoint toEngineCheckpoint(
            com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint checkpoint) {
        if (checkpoint == null) {
            return null;
        }
        return new AgentCheckpoint(checkpoint.getRunId(), checkpoint.getScopeId(), checkpoint.getSessionId(),
                checkpoint.getIteration(), toEngineMessages(checkpoint.getMessages()),
                toEngineToolUseBlocks(checkpoint.getPendingToolCalls()),
                checkpoint.getCompletedToolUseIds(), checkpoint.getTimestamp(), checkpoint.getVersion());
    }

    /**
     * 审批记录Runtime转引擎
     * @param record
     * @return
     */
    private static ApprovalRecord toEngineApprovalRecord(
            com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord record) {
        if (record == null) {
            return null;
        }
        return new ApprovalRecord(record.getApprovalId(), record.getRunId(), record.getToolCallId(),
                record.getToolName(), record.getScopeId(), record.getApproverId(),
                ApprovalRecord.ApprovalState.valueOf(record.getState().name()),
                record.getReason(), record.getCreatedAt());
    }

    /**
     * 审批记录引擎转Runtime
     * @param record
     * @return
     */
    private static com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord toRuntimeApprovalRecord(ApprovalRecord record) {
        if (record == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord(
                record.getApprovalId(), record.getRunId(), record.getToolCallId(), record.getToolName(),
                record.getScopeId(), record.getApproverId(),
                com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord.ApprovalState.valueOf(record.getState().name()),
                record.getReason(), record.getCreatedAt());
    }

    /**
     * 审批记录列表Runtime转引擎
     * @param records
     * @return
     */
    private static List<ApprovalRecord> toEngineApprovalRecords(
            List<com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord> records) {
        if (records == null) {
            return null;
        }
        List<ApprovalRecord> result = new ArrayList<>(records.size());
        for (com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord record : records) {
            result.add(toEngineApprovalRecord(record));
        }
        return result;
    }

    /**
     * 消息列表Runtime转引擎
     * @param messages
     * @return
     */
    private static List<AgentMessage> toEngineMessages(
            List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> messages) {
        if (messages == null) {
            return null;
        }
        List<AgentMessage> result = new ArrayList<>(messages.size());
        for (com.yangqiongai.ai.agent.runtime.message.AgentMessage message : messages) {
            result.add(RuntimeTypeConverter.toHarness(message));
        }
        return result;
    }

    /**
     * 消息列表引擎转Runtime
     * @param messages
     * @return
     */
    private static List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> toRuntimeMessages(
            List<AgentMessage> messages) {
        if (messages == null) {
            return null;
        }
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> result =
                new ArrayList<>(messages.size());
        for (AgentMessage message : messages) {
            result.add(RuntimeTypeConverter.toRuntime(message));
        }
        return result;
    }

    /**
     * 工具调用块列表Runtime转引擎
     * @param blocks
     * @return
     */
    private static List<AgentToolUseBlock> toEngineToolUseBlocks(
            List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> blocks) {
        if (blocks == null) {
            return null;
        }
        List<AgentToolUseBlock> result = new ArrayList<>(blocks.size());
        for (com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock block : blocks) {
            result.add(RuntimeTypeConverter.toHarness(block));
        }
        return result;
    }

    /**
     * 工具调用块列表引擎转Runtime
     * @param blocks
     * @return
     */
    private static List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> toRuntimeToolUseBlocks(
            List<AgentToolUseBlock> blocks) {
        if (blocks == null) {
            return null;
        }
        List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> result =
                new ArrayList<>(blocks.size());
        for (AgentToolUseBlock block : blocks) {
            result.add(RuntimeTypeConverter.toRuntime(block));
        }
        return result;
    }
}
