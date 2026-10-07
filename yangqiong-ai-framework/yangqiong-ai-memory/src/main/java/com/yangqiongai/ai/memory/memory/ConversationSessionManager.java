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
package com.yangqiongai.ai.memory.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.metrics.ConversationMetrics;
import com.yangqiongai.ai.memory.ChatMemoryManager;
import com.yangqiongai.ai.memory.SessionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 对话会话管理器
 * @author yangqiong
 */
@Service
public class ConversationSessionManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationSessionManager.class);

    /**
     * 会话状态缓存，使用Caffeine支持自动过期与容量限制，防止内存泄漏
     */
    private final Cache<String, Object> sessionStateCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * Redis L2缓存键前缀
     */
    private static final String SESSION_REDIS_KEY_PREFIX = "ai:memory:session-state:";

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ChatMemoryManager chatMemoryManager;

    @Autowired
    private ConversationMetrics conversationMetrics;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${ai.memory.session.cache.redis.enabled:false}")
    private boolean redisCacheEnabled;

    @Value("${ai.memory.session.cache.redis.ttl-minutes:120}")
    private long redisCacheTtlMinutes;

    /**
     * 构造器，绑定Caffeine缓存指标到Micrometer
     * <p>tag keys与Spring Boot缓存自动绑定对齐（cache/cache_manager/name），避免Prometheus同名meter标签冲突</p>
     * @param meterRegistry
     */
    public ConversationSessionManager(@Autowired(required = false) MeterRegistry meterRegistry) {
        if (meterRegistry != null) {
            new CaffeineCacheMetrics(sessionStateCache, "conversation.session.state",
                    Tags.of("cache_manager", "memory", "name", "conversation.session.state"))
                    .bindTo(meterRegistry);
        }
    }

    /**
     * 确保会话存在，不存在则创建
     * @param sessionId
     * @param userId
     * @return
     */
    public ConversationSessionInfo ensureSession(String sessionId, String userId) {
        Optional<ConversationSessionInfo> existing = sessionManager.findBySessionId(sessionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        sessionManager.create(session);
        conversationMetrics.incrementSessionCreated();
        return session;
    }

    /**
     * 创建新会话
     * @param userId
     * @return
     */
    public ConversationSessionInfo createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        sessionManager.create(session);
        conversationMetrics.incrementSessionCreated();
        return session;
    }

    /**
     * 保存用户消息到记忆
     * @param sessionId
     * @param content
     */
    public void saveUserMessage(String sessionId, String content) {
        ChatMemoryRecord memory = new ChatMemoryRecord();
        memory.setSessionId(sessionId);
        memory.setScopeId(ScopeContext.getScopeId());
        memory.setMessageRole("user");
        memory.setMessageContent(content);
        chatMemoryManager.saveMessage(memory);
    }

    /**
     * 保存助手消息到记忆
     * @param sessionId
     * @param content
     */
    public void saveAssistantMessage(String sessionId, String content) {
        ChatMemoryRecord memory = new ChatMemoryRecord();
        memory.setSessionId(sessionId);
        memory.setScopeId(ScopeContext.getScopeId());
        memory.setMessageRole("assistant");
        memory.setMessageContent(content);
        chatMemoryManager.saveMessage(memory);
    }

    /**
     * 获取会话信息
     * @param sessionId
     * @return
     */
    public ConversationSessionInfo getSession(String sessionId) {
        Optional<ConversationSessionInfo> optional = sessionManager.findBySessionId(sessionId);
        return optional.orElse(null);
    }

    /**
     * 更新会话摘要
     * @param sessionId
     * @param summaryText
     */
    public void updateSessionSummary(String sessionId, String summaryText) {
        sessionManager.updateSummaryText(sessionId, summaryText);
    }

    /**
     * 更新摘要追踪信息
     * @param sessionId
     * @param summaryRound
     * @param latestSummaryId
     */
    public void updateSummaryTracking(String sessionId, int summaryRound, String latestSummaryId) {
        sessionManager.updateSummaryTracking(sessionId, summaryRound, latestSummaryId);
    }

    /**
     * 追加消息到会话
     * @param sessionId
     * @param role
     * @param content
     */
    public void appendMessage(String sessionId, String role, String content) {
        if ("user".equals(role)) {
            saveUserMessage(sessionId, content);
        } else if ("assistant".equals(role)) {
            saveAssistantMessage(sessionId, content);
        } else {
            ChatMemoryRecord memory = new ChatMemoryRecord();
            memory.setSessionId(sessionId);
            memory.setScopeId(ScopeContext.getScopeId());
            memory.setMessageRole(role);
            memory.setMessageContent(content);
            chatMemoryManager.saveMessage(memory);
        }
    }

    /**
     * 加载会话历史消息
     * @param sessionId
     * @return
     */
    public List<ChatMemoryRecord> loadMessages(String sessionId) {
        return chatMemoryManager.findBySessionId(sessionId);
    }

    /**
     * 持久化会话状态到缓存（L1 Caffeine + L2 Redis），Redis不可用时仅写Caffeine
     * @param sessionId
     * @param result
     */
    public void persistSession(String sessionId, Object result) {
        try {
            sessionStateCache.put("conversation:" + sessionId, result);
            if (redisCacheEnabled && stringRedisTemplate != null) {
                String key = SESSION_REDIS_KEY_PREFIX + sessionId;
                stringRedisTemplate.opsForValue().set(key, "1",
                        Duration.ofMinutes(redisCacheTtlMinutes > 0 ? redisCacheTtlMinutes : 120));
            }
            log.info("会话状态持久化完成: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("会话状态持久化失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 恢复会话状态，L1未命中时查询L2 Redis并回填L1
     * @param sessionId
     * @return
     */
    public boolean restoreSession(String sessionId) {
        try {
            Object cached = sessionStateCache.getIfPresent("conversation:" + sessionId);
            if (cached != null) {
                log.info("会话状态恢复(L1命中): sessionId={}", sessionId);
                return true;
            }
            // L1未命中，查询L2 Redis
            if (redisCacheEnabled && stringRedisTemplate != null) {
                String key = SESSION_REDIS_KEY_PREFIX + sessionId;
                Boolean exists = stringRedisTemplate.hasKey(key);
                if (Boolean.TRUE.equals(exists)) {
                    // L2命中，回填L1（用哨兵值占位，restoreSession只关心存在性）
                    sessionStateCache.put("conversation:" + sessionId, "restored-from-redis");
                    // 刷新Redis TTL，延长生命周期
                    stringRedisTemplate.expire(key, Duration.ofMinutes(redisCacheTtlMinutes > 0 ? redisCacheTtlMinutes : 120));
                    log.info("会话状态恢复(L2命中): sessionId={}", sessionId);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("会话状态恢复失败: sessionId={}", sessionId, e);
            return false;
        }
    }

    /**
     * 清空会话历史（L1 + L2 + DB）
     * @param sessionId
     */
    public void clearSession(String sessionId) {
        chatMemoryManager.deleteBySessionId(sessionId);
        sessionManager.updateSummaryText(sessionId, null);
        sessionStateCache.invalidate("conversation:" + sessionId);
        if (redisCacheEnabled && stringRedisTemplate != null) {
            try {
                stringRedisTemplate.delete(SESSION_REDIS_KEY_PREFIX + sessionId);
            } catch (Exception e) {
                log.warn("清空Redis会话缓存失败: sessionId={}", sessionId, e);
            }
        }
        conversationMetrics.incrementSessionClosed();
        log.info("会话历史已清空: sessionId={}", sessionId);
    }
}
