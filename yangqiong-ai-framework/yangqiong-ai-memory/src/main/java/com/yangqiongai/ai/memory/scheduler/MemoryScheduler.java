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
package com.yangqiongai.ai.memory.scheduler;

import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import com.yangqiongai.ai.memory.vector.MemoryVectorStore;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆调度服务
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(prefix = "ai.memory.schedule", name = "enabled", havingValue = "true")
public class MemoryScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemoryScheduler.class);

    private static final String INTENT_PROMPT_TEMPLATE =
            "分析以下用户消息的意图，输出3-5个最相关的记忆类别标签（用逗号分隔，中文），"
                    + "例如：技术,项目,饮食,偏好,事实,约束,日程,社交,职业,技能\n"
                    + "仅输出标签，不要输出其他内容。\n"
                    + "用户消息：{message}";

    private static final String ACTIVATED_KEY_PREFIX = "memory:activated:";

    private static final int VECTOR_SEARCH_TOP_K = 20;

    @Autowired(required = false)
    private UserLongTermMemoryRepository userLongTermMemoryRepository;

    @Autowired(required = false)
    private MemoryVectorStore memoryVectorStore;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${ai.memory.schedule.model-code:defaultAgent}")
    private String modelCode;

    @Value("${ai.memory.schedule.activation-ttl-minutes:30}")
    private int activationTtlMinutes;

    /**
     * 根据对话上下文预测需要的记忆类别并预加载，返回的记忆标记为"已激活"状态
     * @param userId
     * @param currentMessage
     * @param maxTokens
     * @return
     */
    public String preloadByContext(String userId, String currentMessage, int maxTokens) {
        if (userId == null || userId.isBlank() || currentMessage == null || currentMessage.isBlank()) {
            return "";
        }
        // 意图分类
        List<String> intentTags = classifyIntent(currentMessage);
        log.debug("意图分类结果: userId={}, tags={}", userId, intentTags);

        // 向量检索相关记忆
        List<Long> candidateIds = retrieveCandidates(userId, currentMessage, intentTags);
        if (candidateIds.isEmpty()) {
            return "";
        }
        // 查询记忆内容，排除 DEPRECATED 和过期记忆
        List<UserLongTermMemoryInfo> memories = userLongTermMemoryRepository.findByIdsEffective(candidateIds);
        if (memories.isEmpty()) {
            return "";
        }

        // 按相似度排序（候选 ID 顺序即相似度顺序）
        memories.sort(Comparator.comparingInt(m -> indexOf(candidateIds, m.getId())));

        // Token 预算内构建上下文
        String context = buildContextWithinBudget(memories, maxTokens);

        // 标记为"已激活"状态
        markAsActivated(userId, memories);
        return context;
    }

    /**
     * 调用 LLM 对用户消息做意图分类
     * @param currentMessage
     * @return
     */
    private List<String> classifyIntent(String currentMessage) {
        if (languageModelFactory == null) {
            return Collections.emptyList();
        }
        try {
            String prompt = INTENT_PROMPT_TEMPLATE.replace("{message}", currentMessage);
            String response = languageModelFactory.generateText(modelCode, prompt);
            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }
            String[] tags = response.split("[,，\\s]+");
            List<String> result = new ArrayList<>();
            for (String tag : tags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty() && result.size() < 5) {
                    result.add(trimmed);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("意图分类失败, 降级为空标签", e);
            return Collections.emptyList();
        }
    }

    /**
     * 向量检索相关记忆候选
     * @param userId
     * @param currentMessage
     * @param intentTags
     * @return
     */
    private List<Long> retrieveCandidates(String userId, String currentMessage, List<String> intentTags) {
        if (memoryVectorStore == null) {
            return Collections.emptyList();
        }
        try {
            List<MemoryVectorStore.ScoredMemoryId> scored = memoryVectorStore.searchScored(
                    currentMessage, userId, null, VECTOR_SEARCH_TOP_K);
            List<Long> ids = scored.stream()
                    .map(MemoryVectorStore.ScoredMemoryId::getMemoryId)
                    .collect(Collectors.toList());
            // 如果意图标签非空，按标签过滤候选（tags 字段包含任一标签）
            if (!intentTags.isEmpty() && !ids.isEmpty()) {
                ids = filterByTags(userId, ids, intentTags);
            }
            return ids;
        } catch (Exception e) {
            log.warn("向量检索候选失败, userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 按标签过滤候选记忆
     * @param userId
     * @param ids
     * @param intentTags
     * @return
     */
    private List<Long> filterByTags(String userId, List<Long> ids, List<String> intentTags) {
        List<UserLongTermMemoryInfo> filtered = userLongTermMemoryRepository.findByIdsAndTags(ids, intentTags);
        return filtered.stream()
                .map(UserLongTermMemoryInfo::getId)
                .collect(Collectors.toList());
    }

    /**
     * 按Token预算构建上下文
     * @param memories
     * @param maxTokens
     * @return
     */
    private String buildContextWithinBudget(List<UserLongTermMemoryInfo> memories, int maxTokens) {
        StringBuilder builder = new StringBuilder();
        int usedTokens = 0;
        for (UserLongTermMemoryInfo memory : memories) {
            String content = memory.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            int contentTokens = TokenEstimator.estimateTokens(content);
            if (usedTokens + contentTokens > maxTokens) {
                continue;
            }
            builder.append(content).append("\n\n");
            usedTokens += contentTokens;
        }
        return builder.toString().trim();
    }

    /**
     * 将预加载的记忆标记为"已激活"状态（存Redis，TTL 30分钟）
     * @param userId
     * @param memories
     */
    private void markAsActivated(String userId, List<UserLongTermMemoryInfo> memories) {
        if (stringRedisTemplate == null || memories.isEmpty()) {
            return;
        }
        try {
            String key = ACTIVATED_KEY_PREFIX + userId;
            String[] memoryIds = memories.stream()
                    .map(m -> String.valueOf(m.getId()))
                    .toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(key, memoryIds);
            stringRedisTemplate.expire(key, Duration.ofMinutes(activationTtlMinutes));
        } catch (Exception e) {
            log.warn("标记激活态失败, userId={}", userId, e);
        }
    }

    private int indexOf(List<Long> ids, Long target) {
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(target)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
