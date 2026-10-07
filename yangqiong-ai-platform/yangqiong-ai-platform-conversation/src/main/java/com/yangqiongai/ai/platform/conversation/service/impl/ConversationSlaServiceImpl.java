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
package com.yangqiongai.ai.platform.conversation.service.impl;

import com.yangqiongai.ai.platform.conversation.dto.ConversationSlaDTO;
import com.yangqiongai.ai.platform.conversation.service.ConversationSlaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的会话 SLA 统计服务
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class ConversationSlaServiceImpl implements ConversationSlaService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSlaServiceImpl.class);

    /**
     * 每用户保留的最近响应时间样本数
     */
    private static final int MAX_SAMPLES = 1000;

    /**
     * Token 累计 hash 字段前缀
     */
    private static final String KEY_RESPONSE_TIMES = "ai:conversation:sla:response:";

    private static final String KEY_TOKEN_TOTAL = "ai:conversation:sla:token-total:";

    private static final String KEY_COUNT = "ai:conversation:sla:count:";

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void recordResponse(String userId, String sessionId, long responseTimeMs, int tokenCount) {
        if (stringRedisTemplate == null) {
            log.debug("RedisTemplate 不可用，跳过 SLA 记录");
            return;
        }
        try {
            // 使用 List 存最近 N 条响应时间
            String key = buildResponseKey(userId);
            stringRedisTemplate.opsForList().rightPush(key, String.valueOf(responseTimeMs));
            // 修剪保留最近 MAX_SAMPLES 条
            stringRedisTemplate.opsForList().trim(key, -MAX_SAMPLES, -1);
            stringRedisTemplate.expire(key, Duration.ofDays(30));

            // 累计 token 数和次数
            String tokenKey = buildTokenKey(userId);
            stringRedisTemplate.opsForValue().increment(tokenKey, tokenCount);
            stringRedisTemplate.expire(tokenKey, Duration.ofDays(30));

            String countKey = buildCountKey(userId);
            stringRedisTemplate.opsForValue().increment(countKey);
            stringRedisTemplate.expire(countKey, Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("SLA 记录异常，不影响业务: userId={}", userId, e);
        }
    }

    @Override
    public ConversationSlaDTO getSlaStats(String userId, int days) {
        ConversationSlaDTO dto = new ConversationSlaDTO();
        if (stringRedisTemplate == null) {
            return dto;
        }
        try {
            String responseKey = buildResponseKey(userId);
            List<String> samples = stringRedisTemplate.opsForList().range(responseKey, 0, -1);
            if (samples == null || samples.isEmpty()) {
                return dto;
            }
            List<Long> times = samples.stream().map(Long::parseLong).sorted().collect(Collectors.toList());
            int n = times.size();
            dto.setTotalConversations(n);
            dto.setAvgResponseTimeMs(times.stream().mapToLong(l -> l).average().orElse(0));
            dto.setMaxResponseTimeMs(times.get(n - 1));
            dto.setP95ResponseTimeMs(percentile(times, 0.95));
            dto.setP99ResponseTimeMs(percentile(times, 0.99));

            String tokenKey = buildTokenKey(userId);
            String tokenValue = stringRedisTemplate.opsForValue().get(tokenKey);
            long totalTokens = tokenValue == null ? 0 : Long.parseLong(tokenValue);
            dto.setAvgTokenCount(n == 0 ? 0 : (double) totalTokens / n);
        } catch (Exception e) {
            log.warn("SLA 统计查询异常: userId={}", userId, e);
        }
        return dto;
    }

    /**
     * 计算分位值
     * @param sorted 已排序的样本
     * @param percentile 分位（0-1）
     * @return
     */
    private double percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private String buildResponseKey(String userId) {
        return KEY_RESPONSE_TIMES + userId;
    }

    private String buildTokenKey(String userId) {
        return KEY_TOKEN_TOTAL + userId;
    }

    private String buildCountKey(String userId) {
        return KEY_COUNT + userId;
    }
}
