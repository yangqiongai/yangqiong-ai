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
package com.yangqiongai.ai.memory.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.memory.config.ConditionalOnCloudMemoryMode;
import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import com.yangqiongai.ai.memory.spi.MemoryEnhancer;
import com.yangqiongai.ai.memory.vector.MemoryVectorStore;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自主记忆整理代理
 * @author yangqiong
 */
@Component
@ConditionalOnCloudMemoryMode
@ConditionalOnProperty(prefix = "ai.memory.agent", name = "enabled", havingValue = "true")
public class MemoryManagerAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryManagerAgent.class);

    private static final int MAX_MEMORIES_PER_USER = 50;

    private static final int MIN_MEMORIES_TO_ORGANIZE = 10;

    private static final int STALE_DAYS_THRESHOLD = 30;

    private static final double LOW_SCORE_THRESHOLD = 10.0;

    private static final String MEMORY_TYPE_DEPRECATED = "DEPRECATED";

    private static final String MEMORY_TYPE_PREFERENCE = "PREFERENCE";

    private static final String MEMORY_TYPE_PROFILE = "PROFILE";

    @Prompt(code = "memory.organize", name = "记忆整理",
            description = "对用户记忆做整理决策（保留/归档/遗忘/合并）", category = PromptCategory.CORE,
            readonly = true, visible = false)
    private static final String ORGANIZE_PROMPT_TEMPLATE = """
            你是记忆整理助手。请对以下用户记忆做整理决策，仅输出JSON数组（不要输出任何其他内容、不要markdown代码块）：
            [{"memoryId":123,"action":"KEEP","reason":"..."}]
            
            动作说明：
            - KEEP: 保留记忆（有价值、仍相关）
            - ARCHIVE: 归档（过时、低价值，标记失效但不删除）
            - FORGET: 遗忘（完全无用、重复冗余，彻底删除）
            - MERGE: 合并相似记忆，需指定 mergeSourceIds（被合并删除的记忆ID列表）和 mergedContent（合并后内容），memoryId 为合并保留的主记忆ID
            
            整理规则：
            1. 重复或高度相似的记忆 → MERGE（合并为一条）
            2. 过时、已失效的记忆 → ARCHIVE
            3. 无价值、冗余的记忆 → FORGET
            4. 仍有价值的记忆 → KEEP
            5. PREFERENCE/PROFILE 类型记忆优先 KEEP
            6. 保守决策，宁可 KEEP 也不要误删
            
            记忆列表：{memories}""";

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired(required = false)
    private UserLongTermMemoryRepository userLongTermMemoryRepository;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private MemoryVectorStore memoryVectorStore;

    @Autowired(required = false)
    private MemoryEnhancer memoryEnhancer;

    @Autowired(required = false)
    private PromptResolver promptResolver;

    @Value("${ai.memory.agent.cron:0 0 4 * * ?}")
    private String cron;

    @Value("${ai.memory.agent.model-code:defaultAgent}")
    private String modelCode;

    /**
     * 每天凌晨4点执行记忆整理
     */
    @Scheduled(cron = "${ai.memory.agent.cron:0 0 4 * * ?}")
    public void run() {
        log.info("开始执行自主记忆整理, cron={}", cron);
        long startedAt = System.currentTimeMillis();
        try {
            List<String> userIds = findAllUserIds();
            if (userIds.isEmpty()) {
                log.info("无用户记忆需要整理");
                return;
            }
            int totalUsers = userIds.size();
            int totalDecisions = 0;
            int totalArchived = 0;
            int totalForgotten = 0;
            int totalMerged = 0;
            for (String userId : userIds) {
                try {
                    int[] stats = organizeUserMemories(userId);
                    totalDecisions += stats[0];
                    totalArchived += stats[1];
                    totalForgotten += stats[2];
                    totalMerged += stats[3];
                } catch (Exception e) {
                    log.warn("用户记忆整理失败: userId={}", userId, e);
                }
            }
            long duration = System.currentTimeMillis() - startedAt;
            log.info("自主记忆整理完成: 用户数={}, 决策数={}, 归档={}, 遗忘={}, 合并={}, 耗时={}ms",
                    totalUsers, totalDecisions, totalArchived, totalForgotten, totalMerged, duration);
        } catch (Exception e) {
            log.error("自主记忆整理执行失败", e);
        }
    }

    /**
     * 整理单个用户的记忆
     * @param userId
     * @return [决策数, 归档数, 遗忘数, 合并数]
     */
    private int[] organizeUserMemories(String userId) {
        List<UserLongTermMemoryInfo> memories = userLongTermMemoryRepository.findUserMemoriesForOrganize(userId, MAX_MEMORIES_PER_USER);
        if (memories.size() < MIN_MEMORIES_TO_ORGANIZE) {
            return new int[]{0, 0, 0, 0};
        }

        List<MemoryManagerDecision> decisions;
        if (languageModelFactory == null) {
            decisions = ruleBasedOrganize(memories);
        } else {
            decisions = llmBasedOrganize(userId, memories);
        }
        if (decisions.isEmpty()) {
            return new int[]{0, 0, 0, 0};
        }

        int archived = 0;
        int forgotten = 0;
        int merged = 0;
        for (MemoryManagerDecision decision : decisions) {
            try {
                switch (decision.getAction()) {
                    case ARCHIVE:
                        archiveMemory(decision.getMemoryId());
                        archived++;
                        break;
                    case FORGET:
                        forgetMemory(decision.getMemoryId());
                        forgotten++;
                        break;
                    case MERGE:
                        mergeMemories(decision);
                        merged++;
                        break;
                    case KEEP:
                    default:
                        break;
                }
            } catch (Exception e) {
                log.warn("执行记忆决策失败: memoryId={}, action={}",
                        decision.getMemoryId(), decision.getAction(), e);
            }
        }
        log.info("用户记忆整理完成: userId={}, 总数={}, 决策={}, 归档={}, 遗忘={}, 合并={}",
                userId, memories.size(), decisions.size(), archived, forgotten, merged);
        return new int[]{decisions.size(), archived, forgotten, merged};
    }

    /**
     * 基于规则的记忆整理（LLM不可用时降级方案）
     * @param memories
     * @return
     */
    private List<MemoryManagerDecision> ruleBasedOrganize(List<UserLongTermMemoryInfo> memories) {
        List<MemoryManagerDecision> decisions = new ArrayList<>(memories.size());
        LocalDateTime now = LocalDateTime.now();
        for (UserLongTermMemoryInfo memory : memories) {
            MemoryManagerDecision decision = ruleBasedDecision(memory, now);
            decisions.add(decision);
        }
        return decisions;
    }

    /**
     * 单条记忆的规则化决策
     * @param memory
     * @param now
     * @return
     */
    private MemoryManagerDecision ruleBasedDecision(UserLongTermMemoryInfo memory, LocalDateTime now) {
        double effectiveScore = calculateEffectiveScore(memory);
        // 低分记忆 → ARCHIVE
        if (effectiveScore < LOW_SCORE_THRESHOLD) {
            return new MemoryManagerDecision(memory.getId(), MemoryManagerAction.ARCHIVE,
                    "低分归档: effectiveScore=" + String.format("%.2f", effectiveScore));
        }
        // 长时间未访问且非偏好/画像 → ARCHIVE
        LocalDateTime lastAccessed = memory.getLastAccessedAt();
        if (lastAccessed != null) {
            long daysSinceAccess = ChronoUnit.DAYS.between(lastAccessed, now);
            if (daysSinceAccess > STALE_DAYS_THRESHOLD) {
                String type = memory.getMemoryType();
                if (!MEMORY_TYPE_PREFERENCE.equals(type) && !MEMORY_TYPE_PROFILE.equals(type)) {
                    return new MemoryManagerDecision(memory.getId(), MemoryManagerAction.ARCHIVE,
                            "过期归档: " + daysSinceAccess + "天未访问");
                }
            }
        }
        return new MemoryManagerDecision(memory.getId(), MemoryManagerAction.KEEP, "保留");
    }

    /**
     * 基于LLM的记忆整理
     * @param userId
     * @param memories
     * @return
     */
    private List<MemoryManagerDecision> llmBasedOrganize(String userId, List<UserLongTermMemoryInfo> memories) {
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            String prompt = buildPrompt(memories);
            String response = languageModelFactory.generateText(modelCode, prompt);
            if (response == null || response.isBlank()) {
                log.debug("LLM 未返回决策, 降级为规则整理: userId={}", userId);
                return ruleBasedOrganize(memories);
            }
            List<MemoryManagerDecision> decisions = parseDecisions(response);
            if (decisions.isEmpty()) {
                log.debug("LLM 决策解析失败, 降级为规则整理: userId={}", userId);
                return ruleBasedOrganize(memories);
            }
            success = true;
            return decisions;
        } catch (Exception e) {
            log.warn("LLM 记忆整理失败, 降级为规则整理: userId={}", userId, e);
            return ruleBasedOrganize(memories);
        } finally {
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("memory.organize", userId, success, System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * 构建LLM整理Prompt
     * @param memories
     * @return
     */
    private String buildPrompt(List<UserLongTermMemoryInfo> memories) {
        JSONArray memoryArray = new JSONArray();
        for (UserLongTermMemoryInfo memory : memories) {
            JSONObject obj = new JSONObject();
            obj.put("id", memory.getId());
            obj.put("type", memory.getMemoryType());
            obj.put("content", truncate(memory.getContent(), 200));
            obj.put("score", memory.getImportanceScore());
            obj.put("accessCount", memory.getAccessCount());
            obj.put("lastAccessed", memory.getLastAccessedAt() != null
                    ? memory.getLastAccessedAt().format(ISO_FORMATTER) : "");
            memoryArray.add(obj);
        }
        String memoriesJson = memoryArray.toJSONString();
        Map<String, Object> variables = new HashMap<>();
        variables.put("memories", memoriesJson);
        String resolved = promptResolver == null
                ? ORGANIZE_PROMPT_TEMPLATE
                : promptResolver.resolve("memory.organize", ORGANIZE_PROMPT_TEMPLATE, variables);
        return resolved.replace("{memories}", memoriesJson);
    }

    /**
     * 解析LLM返回的决策JSON数组
     * @param response
     * @return
     */
    private List<MemoryManagerDecision> parseDecisions(String response) {
        String json = stripMarkdownCodeBlock(response).trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("决策响应非JSON数组格式: {}", truncate(json, 200));
            return Collections.emptyList();
        }
        json = json.substring(start, end + 1);
        try {
            JSONArray array = JSON.parseArray(json);
            List<MemoryManagerDecision> decisions = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                MemoryManagerDecision decision = parseDecision(obj);
                if (decision != null) {
                    decisions.add(decision);
                }
            }
            return decisions;
        } catch (Exception e) {
            log.warn("解析决策JSON失败: {}", truncate(json, 200), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析单个决策JSON对象
     * @param obj
     * @return
     */
    private MemoryManagerDecision parseDecision(JSONObject obj) {
        Long memoryId = obj.getLong("memoryId");
        String actionStr = obj.getString("action");
        if (memoryId == null || actionStr == null || actionStr.isBlank()) {
            return null;
        }
        MemoryManagerAction action;
        try {
            action = MemoryManagerAction.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知决策动作: {}", actionStr);
            return null;
        }
        MemoryManagerDecision decision = new MemoryManagerDecision();
        decision.setMemoryId(memoryId);
        decision.setAction(action);
        decision.setReason(obj.getString("reason"));
        if (action == MemoryManagerAction.MERGE) {
            decision.setMergedContent(obj.getString("mergedContent"));
            JSONArray sourceIds = obj.getJSONArray("mergeSourceIds");
            if (sourceIds != null) {
                List<Long> ids = new ArrayList<>(sourceIds.size());
                for (int i = 0; i < sourceIds.size(); i++) {
                    Long id = sourceIds.getLong(i);
                    if (id != null) {
                        ids.add(id);
                    }
                }
                decision.setMergeSourceIds(ids);
            }
        }
        return decision;
    }

    /**
     * 归档记忆（标记为 DEPRECATED）
     * @param memoryId
     */
    private void archiveMemory(Long memoryId) {
        userLongTermMemoryRepository.archiveAsDeprecated(memoryId);
        if (memoryVectorStore != null) {
            memoryVectorStore.deleteByMemoryId(memoryId);
        }
    }

    /**
     * 遗忘记忆（彻底删除）
     * @param memoryId
     */
    private void forgetMemory(Long memoryId) {
        userLongTermMemoryRepository.deleteById(memoryId);
        if (memoryVectorStore != null) {
            memoryVectorStore.deleteByMemoryId(memoryId);
        }
    }

    /**
     * 合并记忆：更新主记忆内容，删除被合并的源记忆
     * @param decision
     */
    private void mergeMemories(MemoryManagerDecision decision) {
        if (decision.getMergedContent() == null || decision.getMergedContent().isBlank()) {
            return;
        }
        userLongTermMemoryRepository.updateContent(decision.getMemoryId(), decision.getMergedContent());
        if (memoryVectorStore != null) {
            UserLongTermMemoryInfo memory = userLongTermMemoryRepository.selectById(decision.getMemoryId());
            if (memory != null) {
                memoryVectorStore.embedAndIndex(memory.getId(), decision.getMergedContent(),
                        memory.getUserId(), memory.getMemoryType());
            }
        }
        // 删除被合并的源记忆
        if (decision.getMergeSourceIds() != null) {
            for (Long sourceId : decision.getMergeSourceIds()) {
                if (sourceId != null && !sourceId.equals(decision.getMemoryId())) {
                    forgetMemory(sourceId);
                }
            }
        }
    }

    /**
     * 计算记忆的有效分数（衰减服务可用时实时计算，否则降级为 importanceScore）
     * @param memory
     * @return
     */
    private double calculateEffectiveScore(UserLongTermMemoryInfo memory) {
        if (memoryEnhancer != null) {
            try {
                return memoryEnhancer.onRecall(memory);
            } catch (Exception e) {
                // 降级为 importanceScore
            }
        }
        return memory.getImportanceScore() != null ? memory.getImportanceScore() : 0;
    }

    /**
     * 查询所有有长期记忆的用户ID
     * @return
     */
    private List<String> findAllUserIds() {
        return userLongTermMemoryRepository.findAllActiveUserIds();
    }

    /**
     * 去除markdown代码块标记
     * @param text
     * @return
     */
    private String stripMarkdownCodeBlock(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed;
    }

    /**
     * 截断字符串到指定长度
     * @param text
     * @param maxLen
     * @return
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
