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

import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.metrics.ConversationMetrics;
import com.yangqiongai.ai.memory.memory.trigger.SummaryTriggerStrategy;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 对话摘要生成
 * @author yangqiong
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    /**
     * 摘要提示模板
     */
    @Prompt(code = "memory.summary", name = "对话摘要",
            description = "压缩对话生成可复用摘要", category = PromptCategory.BUSINESS,
            readonly = true, visible = false)
    private static final String SUMMARY_PROMPT_TEMPLATE = """
            你是对话摘要助手。请压缩对话内容，生成可供后续复用的摘要。
            要求：
            1) 保留事实、约束条件、用户偏好、未解决问题
            2) 去除寒暄、重复内容和无关信息
            3) 不得编造未提及的事实
            4) 仅输出纯文本，不要使用markdown格式

            [历史摘要]
            ${previousSummary}

            [近期对话]
            ${recentDialogues}

            请输出更新后的完整摘要。""";

    /**
     * 摘要刷新锁键前缀
     */
    private static final String SUMMARY_LOCK_KEY_PREFIX = "ai:conversation-summary:lock:";

    /**
     * 摘要刷新锁TTL
     */
    private static final Duration SUMMARY_LOCK_TTL = Duration.ofSeconds(60);

    /**
     * 默认摘要源Token预算
     */
    private static final int DEFAULT_SUMMARY_SOURCE_TOKEN_BUDGET = 2400;

    /**
     * 默认摘要最大字符数
     */
    private static final int DEFAULT_SUMMARY_MAX_CHARS = 2200;

    /**
     * 默认启用的触发策略
     */
    private static final String DEFAULT_ENABLED_STRATEGIES = "message-count";

    /**
     * Redis解锁脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /**
     * 无Redis时的本地锁兜底（按锁键CAS占用，释放时移除）
     */
    private final ConcurrentHashMap<String, AtomicBoolean> localLocks = new ConcurrentHashMap<>();

    /**
     * 本地锁哨兵值（与Redis锁值一样，非null表示持有锁）
     */
    private static final String LOCAL_LOCK_VALUE = "local-lock";

    @Autowired
    private ConversationSessionManager sessionManager;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ConversationMetrics conversationMetrics;

    /**
     * 用户长期记忆服务
     */
    @Autowired(required = false)
    private LongTermMemoryManager longTermMemoryManager;

    @Autowired(required = false)
    private PromptResolver promptResolver;

    @Autowired
    @Qualifier("conversationRetryTemplate")
    private RetryTemplate retryTemplate;

    @Autowired
    private List<SummaryTriggerStrategy> triggerStrategies;

    @Value("${ai.memory.summary.enabled:${ai.conversation.summary.enabled:true}}")
    private boolean summaryEnabled;

    @Value("${ai.memory.summary.trigger.enabled-strategies:${ai.conversation.summary.trigger.enabled-strategies:" + DEFAULT_ENABLED_STRATEGIES + "}}")
    private String enabledStrategies;

    @Value("${ai.memory.summary.source-token-budget:${ai.conversation.summary.source-token-budget:" + DEFAULT_SUMMARY_SOURCE_TOKEN_BUDGET + "}}")
    private int summarySourceTokenBudget;

    @Value("${ai.memory.summary.max-chars:${ai.conversation.summary.max-chars:" + DEFAULT_SUMMARY_MAX_CHARS + "}}")
    private int summaryMaxChars;

    @Value("${ai.memory.summary.model-code:${ai.conversation.summary.model-code:defaultAgent}}")
    private String modelCode;

    /**
     * 异步触发增量摘要生成
     * @param sessionId
     */
    @Async
    public void triggerIncrementalSummaryAsync(String sessionId) {
        if (!summaryEnabled) {
            return;
        }
        try {
            generateIncrementalSummary(sessionId);
        } catch (Exception e) {
            log.warn("增量摘要生成失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 增量摘要生成
     * @param sessionId
     */
    public void generateIncrementalSummary(String sessionId) {
        if (!summaryEnabled) {
            return;
        }
        ConversationSessionInfo session = sessionManager.getSession(sessionId);
        if (session == null) {
            return;
        }

        List<ChatMemoryRecord> messages = sessionManager.loadMessages(sessionId);
        if (!shouldTriggerSummary(session, messages)) {
            return;
        }

        // 分布式锁防止并发摘要生成
        String lockKey = SUMMARY_LOCK_KEY_PREFIX + sessionId;
        String lockValue = acquireLock(lockKey);
        if (lockValue == null) {
            log.debug("摘要锁已被占用, 跳过: sessionId={}", sessionId);
            return;
        }

        try {
            // 双重检查
            ConversationSessionInfo freshSession = sessionManager.getSession(sessionId);
            if (freshSession == null) {
                return;
            }
            List<ChatMemoryRecord> freshMessages = sessionManager.loadMessages(sessionId);
            if (!shouldTriggerSummary(freshSession, freshMessages)) {
                return;
            }
            int freshRound = freshSession.getSummaryRound() != null ? freshSession.getSummaryRound() : 0;

            String dialogueSource = buildSummarySource(freshMessages);
            if (dialogueSource == null || dialogueSource.isBlank()) {
                return;
            }

            String previousSummary = freshSession.getSummaryText() != null ? freshSession.getSummaryText() : "";
            conversationMetrics.incrementSummaryTriggered();
            long startTime = System.currentTimeMillis();
            String summary = callLlmForSummary(sessionId, previousSummary, dialogueSource);
            conversationMetrics.recordSummaryDuration(System.currentTimeMillis() - startTime);
            if (summary == null || summary.isBlank()) {
                conversationMetrics.incrementSummaryFailed();
                return;
            }

            String clampedSummary = clampSummary(summary);
            sessionManager.updateSessionSummary(sessionId, clampedSummary);

            // 更新摘要追踪信息（使用双重检查后的freshRound）
            String summaryId = UUID.randomUUID().toString();
            sessionManager.updateSummaryTracking(sessionId, freshRound + 1, summaryId);

            // 摘要生成成功后合并到用户长期记忆，触发结构化记忆提取（PREFERENCE/FACT等）
            if (longTermMemoryManager != null && freshSession.getUserId() != null) {
                try {
                    longTermMemoryManager.mergeSessionSummary(
                            freshSession.getUserId(), sessionId, clampedSummary);
                } catch (Exception mergeEx) {
                    log.warn("摘要合并到长期记忆失败, 不影响摘要已保存: sessionId={}", sessionId, mergeEx);
                }
            }

            log.info("增量摘要生成完成: sessionId={}, messageCount={}, summaryRound={}", sessionId, freshMessages.size(), freshRound + 1);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 判断是否应触发摘要生成，基于配置启用的策略组合（任一满足即触发）
     * @param session
     * @param messages
     * @return
     */
    private boolean shouldTriggerSummary(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        if (triggerStrategies == null || triggerStrategies.isEmpty()) {
            return false;
        }
        List<String> enabled = Arrays.stream(enabledStrategies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        for (SummaryTriggerStrategy strategy : triggerStrategies) {
            if (enabled.contains(strategy.getName()) && strategy.shouldTrigger(session, messages)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建摘要源文本
     * @param messages
     * @return
     */
    private String buildSummarySource(List<ChatMemoryRecord> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int budget = summarySourceTokenBudget > 0 ? summarySourceTokenBudget : DEFAULT_SUMMARY_SOURCE_TOKEN_BUDGET;
        int usedTokens = 0;

        // 从最新消息开始保留，控制Token预算，先逆序收集再正序拼接，避免O(n^2)
        List<String> chunks = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMemoryRecord msg = messages.get(i);
            String role = msg.getMessageRole();
            String content = msg.getMessageContent();
            if (content == null) {
                content = "";
            }
            String chunk = "[" + role + "]\n" + content;
            int chunkTokens = TokenEstimator.estimateTokens(chunk);
            if (usedTokens + chunkTokens > budget) {
                continue;
            }
            chunks.add(chunk);
            usedTokens += chunkTokens;
        }

        // 逆序遍历收集的chunks是按时间从新到旧，需要反转为从旧到新
        StringBuilder sourceBuilder = new StringBuilder();
        for (int i = chunks.size() - 1; i >= 0; i--) {
            sourceBuilder.append(chunks.get(i)).append("\n\n");
        }
        return sourceBuilder.toString().trim();
    }

    /**
     * 调用LLM生成摘要
     * @param sessionId
     * @param previousSummary
     * @param recentDialogues
     * @return
     */
    private String callLlmForSummary(String sessionId, String previousSummary, String recentDialogues) {
        if (languageModelFactory == null) {
            log.warn("LanguageModelFactory未注入, 无法生成摘要");
            return "";
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("previousSummary", previousSummary.isBlank() ? "(none)" : previousSummary);
        variables.put("recentDialogues", recentDialogues);
        String prompt;
        if (promptResolver == null) {
            prompt = SUMMARY_PROMPT_TEMPLATE
                    .replace("${previousSummary}", previousSummary.isBlank() ? "(none)" : previousSummary)
                    .replace("${recentDialogues}", recentDialogues);
        } else {
            prompt = promptResolver.resolve("memory.summary", SUMMARY_PROMPT_TEMPLATE, variables);
        }
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            String result = retryTemplate.execute(context ->
                    languageModelFactory.generateText(modelCode, prompt));
            if (result != null && !result.trim().isEmpty()) {
                success = true;
            }
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("LLM摘要生成重试耗尽, 降级保留原始对话", e);
            // 降级方案：返回原始对话的截断版本，避免摘要静默丢失
            int maxChars = summaryMaxChars > 0 ? summaryMaxChars : DEFAULT_SUMMARY_MAX_CHARS;
            return recentDialogues.length() > maxChars
                    ? recentDialogues.substring(0, maxChars)
                    : recentDialogues;
        } finally {
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("memory.summary", sessionId, success, System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * 限制摘要长度
     * @param summary
     * @return
     */
    private String clampSummary(String summary) {
        if (summary == null) {
            return "";
        }
        int maxChars = summaryMaxChars > 0 ? summaryMaxChars : DEFAULT_SUMMARY_MAX_CHARS;
        if (summary.length() <= maxChars) {
            return summary;
        }
        return summary.substring(0, maxChars);
    }

    /**
     * 获取分布式锁
     * @param lockKey
     * @return 锁值，获取失败返回null
     */
    private String acquireLock(String lockKey) {
        if (stringRedisTemplate == null) {
            // 无Redis时用本地CAS锁兜底，保证单机并发保护
            AtomicBoolean lock = localLocks.computeIfAbsent(lockKey, k -> new AtomicBoolean(false));
            return lock.compareAndSet(false, true) ? LOCAL_LOCK_VALUE : null;
        }
        try {
            String lockValue = UUID.randomUUID().toString();
            Boolean success = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, SUMMARY_LOCK_TTL);
            return Boolean.TRUE.equals(success) ? lockValue : null;
        } catch (Exception e) {
            log.warn("获取摘要锁异常: lockKey={}", lockKey, e);
            return null;
        }
    }

    /**
     * 释放分布式锁
     * @param lockKey
     * @param lockValue
     */
    private void releaseLock(String lockKey, String lockValue) {
        if (LOCAL_LOCK_VALUE.equals(lockValue)) {
            localLocks.remove(lockKey);
            return;
        }
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT,
                    Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
            log.warn("释放摘要锁异常: lockKey={}", lockKey, e);
        }
    }
}
