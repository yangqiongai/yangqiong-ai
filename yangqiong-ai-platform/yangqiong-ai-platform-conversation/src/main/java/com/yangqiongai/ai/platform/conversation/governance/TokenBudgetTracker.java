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
package com.yangqiongai.ai.platform.conversation.governance;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Token 预算追踪器，基于 Redis 按用户维度累计每日 Token 用量与单会话 Token 用量
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class TokenBudgetTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenBudgetTracker.class);

    /**
     * 每用户每日 Token 上限，0 表示不限
     */
    @Value("${ai.conversation.quota.daily-token-limit:100000}")
    private long dailyTokenLimit;

    /**
     * 每会话 Token 上限，0 表示不限
     */
    @Value("${ai.conversation.quota.session-token-limit:8000}")
    private long sessionTokenLimit;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_USER_DAILY = "ai:conversation:token:user-daily:";

    private static final String KEY_SESSION = "ai:conversation:token:session:";

    /**
     * 校验并累加 Token 用量，超限抛 ConversationQuotaExceededException
     * @param userId
     * @param sessionId
     * @param tokenCount
     */
    public void checkAndAccrue(String userId, String sessionId, int tokenCount) {
        if (tokenCount <= 0) {
            return;
        }
        if (stringRedisTemplate == null) {
            log.debug("RedisTemplate 不可用，跳过 Token 预算追踪");
            return;
        }
        checkUserDaily(userId, tokenCount);
        checkSession(sessionId, tokenCount);
        accrue(userId, sessionId, tokenCount);
    }

    /**
     * 获取用户当日已用 Token 数量
     * @param userId
     * @return
     */
    public long getUserDailyUsage(String userId) {
        Long usage = readUsage(buildUserDailyKey(userId));
        return usage == null ? 0 : usage;
    }

    /**
     * 获取会话已用 Token 数量
     * @param sessionId
     * @return
     */
    public long getSessionUsage(String sessionId) {
        Long usage = readUsage(buildSessionKey(sessionId));
        return usage == null ? 0 : usage;
    }

    /**
     * 从 Redis 读取 Token 计数，Redis 不可用时降级返回 null 跳过配额校验，避免阻断消息持久化链路
     * @param key
     * @return
     */
    private Long readUsage(String key) {
        if (stringRedisTemplate == null) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("Redis 不可用，Token 配额校验降级为 0: key={}", key);
            return null;
        }
    }

    /**
     * 校验每日 Token 配额
     * @param userId
     * @param tokenCount
     */
    private void checkUserDaily(String userId, long tokenCount) {
        if (dailyTokenLimit <= 0 || userId == null || userId.isBlank()) {
            return;
        }
        long current = getUserDailyUsage(userId);
        if (current + tokenCount > dailyTokenLimit) {
            log.warn("用户每日 Token 预算超限: userId={}, current={}, request={}, limit={}",
                    userId, current, tokenCount, dailyTokenLimit);
            throw new ConversationQuotaExceededException(
                    AiErrorCode.CONVERSATION_TOKEN_BUDGET_EXCEEDED,
                    "每日Token预算超限，已用=" + current + "，本次请求=" + tokenCount + "，上限=" + dailyTokenLimit);
        }
    }

    /**
     * 校验单会话 Token 配额
     * @param sessionId
     * @param tokenCount
     */
    private void checkSession(String sessionId, long tokenCount) {
        if (sessionTokenLimit <= 0 || sessionId == null || sessionId.isBlank()) {
            return;
        }
        long current = getSessionUsage(sessionId);
        if (current + tokenCount > sessionTokenLimit) {
            log.warn("会话 Token 预算超限: sessionId={}, current={}, request={}, limit={}",
                    sessionId, current, tokenCount, sessionTokenLimit);
            throw new ConversationQuotaExceededException(
                    AiErrorCode.CONVERSATION_TOKEN_BUDGET_EXCEEDED,
                    "会话Token预算超限，已用=" + current + "，本次请求=" + tokenCount + "，上限=" + sessionTokenLimit);
        }
    }

    /**
     * 累加 Token 用量到 Redis
     * @param userId
     * @param sessionId
     * @param tokenCount
     */
    private void accrue(String userId, String sessionId, int tokenCount) {
        try {
            String userKey = buildUserDailyKey(userId);
            stringRedisTemplate.opsForValue().increment(userKey, tokenCount);
            // 设置过期时间到当日剩余秒数，跨天自动重置
            stringRedisTemplate.expire(userKey, Duration.ofSeconds(remainingSecondsToday()));

            String sessionKey = buildSessionKey(sessionId);
            stringRedisTemplate.opsForValue().increment(sessionKey, tokenCount);
            stringRedisTemplate.expire(sessionKey, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Token 用量累加异常，不影响业务: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 计算当日剩余秒数
     * @return
     */
    private long remainingSecondsToday() {
        LocalDate now = LocalDate.now();
        LocalDate tomorrow = now.plusDays(1);
        return ChronoUnit.SECONDS.between(now.atStartOfDay(), tomorrow.atStartOfDay());
    }

    private String buildUserDailyKey(String userId) {
        return KEY_USER_DAILY + LocalDate.now() + ":" + userId;
    }

    private String buildSessionKey(String sessionId) {
        return KEY_SESSION + sessionId;
    }

    public long getDailyTokenLimit() {
        return dailyTokenLimit;
    }

    public long getSessionTokenLimit() {
        return sessionTokenLimit;
    }
}
