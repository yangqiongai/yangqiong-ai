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
package com.yangqiongai.ai.memory;

import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.common.event.UserPreferenceChangedEvent;
import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;
import com.yangqiongai.ai.memory.extract.ExtractedMemory;
import com.yangqiongai.ai.memory.extract.MemoryExtractor;
import com.yangqiongai.ai.memory.spi.DedupDecision;
import com.yangqiongai.ai.memory.spi.MemoryEnhancer;
import com.yangqiongai.ai.memory.graph.KnowledgeTriple;
import com.yangqiongai.ai.memory.graph.MemoryGraphManager;
import com.yangqiongai.ai.memory.repository.UserLongTermMemoryRepository;
import com.yangqiongai.ai.memory.vector.MemoryVectorStore;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.hankcs.hanlp.HanLP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户长期记忆管理
 * @author yangqiong
 */
@Service
public class LongTermMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryManager.class);

    private static final String MEMORY_TYPE_SUMMARY = "SUMMARY";

    private static final String MEMORY_TYPE_DEPRECATED = "DEPRECATED";

    private static final int DEFAULT_IMPORTANCE_SCORE = 50;

    private static final int DEFAULT_MAX_CONTENT_CHARS = 2000;

    private static final int VECTOR_SEARCH_TOP_K = 20;

    @Prompt(code = "memory.merge", name = "记忆整合",
            description = "将已有长期记忆与新会话摘要合并", category = PromptCategory.BUSINESS,
            readonly = true, visible = false)
    private static final String MERGE_PROMPT_TEMPLATE = """
            你是记忆整合助手。请将已有长期记忆与新的会话摘要进行合并。
            要求：
            1) 保留事实、约束条件、用户偏好、未解决问题
            2) 去除重复内容和无关信息
            3) 不得编造未提及的事实
            4) 仅输出纯文本，不要使用markdown格式

            [已有记忆]
            ${existingMemory}

            [新会话摘要]
            ${sessionSummary}

            请输出合并后的完整记忆。""";

    @Autowired(required = false)
    private UserLongTermMemoryRepository userLongTermMemoryRepository;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private MemoryVectorStore memoryVectorStore;

    @Autowired(required = false)
    private MemoryExtractor memoryExtractor;

    @Autowired(required = false)
    private MemoryEnhancer memoryEnhancer;

    @Autowired(required = false)
    private MemoryGraphManager memoryGraphManager;

    @Autowired(required = false)
    private SessionManager sessionManager;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private PromptResolver promptResolver;

    @Value("${ai.memory.cross-session.model-code:${ai.conversation.cross-session.model-code:defaultAgent}}")
    private String modelCode;

    @Value("${ai.memory.cross-session.max-content-chars:${ai.conversation.cross-session.max-content-chars:" + DEFAULT_MAX_CONTENT_CHARS + "}}")
    private int maxContentChars;

    @Value("${ai.memory.vector.enabled:true}")
    private boolean vectorEnabled;

    @Value("${ai.memory.graph.retrieval.enabled:true}")
    private boolean graphRetrievalEnabled;

    @Value("${ai.memory.graph.retrieval.max-entities:3}")
    private int graphMaxEntities;

    @Value("${ai.memory.graph.retrieval.hops:1}")
    private int graphHops;

    @Value("${ai.memory.graph.entity-recognizer.type:hanlp}")
    private String entityRecognizerType;

    /**
     * 合并会话摘要到用户长期记忆
     * @param userId
     * @param sessionId
     * @param sessionSummary
     */
    @Transactional(rollbackFor = Exception.class)
    public void mergeSessionSummary(String userId, String sessionId, String sessionSummary) {
        if (sessionSummary == null || sessionSummary.isBlank()) {
            return;
        }

        UserLongTermMemoryInfo existing = findLatestSummaryMemory(userId);
        String mergedContent = mergeContent(userId, existing, sessionSummary);

        if (existing == null) {
            UserLongTermMemoryInfo memory = new UserLongTermMemoryInfo();
            memory.setUserId(userId);
            memory.setMemoryType(MEMORY_TYPE_SUMMARY);
            memory.setContent(mergedContent);
            memory.setRelatedSessionIds(sessionId);
            memory.setImportanceScore(DEFAULT_IMPORTANCE_SCORE);
            memory.setBaseScore(DEFAULT_IMPORTANCE_SCORE);
            memory.setAccessCount(0);
            memory.setLastAccessedAt(LocalDateTime.now());
            memory.setCreateTime(LocalDateTime.now());
            memory.setUpdateTime(LocalDateTime.now());
            userLongTermMemoryRepository.insert(memory);
            indexMemoryVector(memory, mergedContent);
        } else {
            String mergedRelatedIds = appendSessionId(existing.getRelatedSessionIds(), sessionId);
            userLongTermMemoryRepository.updateContentAndRelatedIds(existing.getId(), mergedContent, mergedRelatedIds);
            existing.setContent(mergedContent);
            indexMemoryVector(existing, mergedContent);
        }
        log.info("用户长期记忆合并完成: userId={}, sessionId={}", userId, sessionId);
        // 对会话摘要做结构化记忆提取（FACT/PREFERENCE/CONSTRAINT/PROFILE/EVENT）
        extractAndStoreStructuredMemories(userId, sessionId, sessionSummary);
    }

    /**
     * 对会话摘要做结构化记忆提取，将提取的条目按类型分别写入长期记忆（写入前做去重检查）
     * @param userId
     * @param sessionId
     * @param sessionSummary
     */
    private void extractAndStoreStructuredMemories(String userId, String sessionId, String sessionSummary) {
        if (memoryExtractor == null) {
            return;
        }
        List<ExtractedMemory> extracted = memoryExtractor.extract(sessionSummary, userId);
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        int added = 0;
        int skipped = 0;
        int updated = 0;
        for (ExtractedMemory item : extracted) {
            try {
                DedupDecision decision = checkDuplicateBeforeWrite(userId, item);
                if (decision == null) {
                    decision = DedupDecision.add();
                }
                switch (decision.getAction()) {
                    case SKIP:
                        skipped++;
                        break;
                    case ADD:
                        insertStructuredMemory(userId, sessionId, item);
                        added++;
                        break;
                    case UPDATE:
                        updateExistingMemory(decision.getExistingMemoryId(), item);
                        updated++;
                        break;
                    case CONFLICT:
                        // 旧记忆标记失效，创建新记忆
                        markMemoryExpired(decision.getExistingMemoryId());
                        insertStructuredMemory(userId, sessionId, item);
                        added++;
                        break;
                    case DELETE:
                        // 严重冲突：删除旧记忆，创建新记忆
                        deleteMemoryById(decision.getExistingMemoryId());
                        insertStructuredMemory(userId, sessionId, item);
                        added++;
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                log.warn("结构化记忆写入失败: userId={}, type={}", userId, item.getMemoryType(), e);
            }
        }
        log.info("结构化记忆提取完成: userId={}, 提取={}, 新增={}, 更新={}, 跳过={}",
                userId, extracted.size(), added, updated, skipped);
    }

    /**
     * 写入前调用去重检查
     * @param userId
     * @param item
     * @return
     */
    private DedupDecision checkDuplicateBeforeWrite(String userId, ExtractedMemory item) {
        if (memoryEnhancer == null) {
            return DedupDecision.add();
        }
        try {
            return memoryEnhancer.onStore(userId, item.getContent(), item.getKey(), item.getMemoryType());
        } catch (Exception e) {
            log.warn("去重检查异常, 降级为ADD: userId={}", userId, e);
            return DedupDecision.add();
        }
    }

    /**
     * 新增结构化记忆
     * @param userId
     * @param sessionId
     * @param item
     */
    private void insertStructuredMemory(String userId, String sessionId, ExtractedMemory item) {
        UserLongTermMemoryInfo memory = buildStructuredMemory(userId, sessionId, item);
        userLongTermMemoryRepository.insert(memory);
        indexMemoryVector(memory, memory.getContent());
    }

    /**
     * 更新已有记忆内容，importanceScore 取新旧较大值
     * @param existingId
     * @param item
     */
    private void updateExistingMemory(Long existingId, ExtractedMemory item) {
        UserLongTermMemoryInfo existing = userLongTermMemoryRepository.selectById(existingId);
        if (existing == null) {
            return;
        }
        int newScore = mapConfidenceToScore(item.getConfidence());
        int mergedScore = Math.max(
                existing.getImportanceScore() != null ? existing.getImportanceScore() : 0,
                newScore);
        userLongTermMemoryRepository.updateContentAndScore(existingId, item.getContent(), mergedScore, mergedScore);
        existing.setContent(item.getContent());
        indexMemoryVector(existing, item.getContent());
    }

    /**
     * 标记记忆失效（validUntil=now）
     * @param memoryId
     */
    private void markMemoryExpired(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        userLongTermMemoryRepository.markExpired(memoryId);
        if (memoryVectorStore != null) {
            memoryVectorStore.deleteByMemoryId(memoryId);
        }
    }

    /**
     * 删除记忆（含向量索引）
     * @param memoryId
     */
    private void deleteMemoryById(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        userLongTermMemoryRepository.deleteById(memoryId);
        if (memoryVectorStore != null) {
            memoryVectorStore.deleteByMemoryId(memoryId);
        }
    }

    /**
     * 根据提取结果构建长期记忆实体
     * @param userId
     * @param sessionId
     * @param item
     * @return
     */
    private UserLongTermMemoryInfo buildStructuredMemory(String userId, String sessionId, ExtractedMemory item) {
        int score = mapConfidenceToScore(item.getConfidence());
        UserLongTermMemoryInfo memory = new UserLongTermMemoryInfo();
        memory.setUserId(userId);
        memory.setMemoryType(item.getMemoryType());
        memory.setContent(item.getContent());
        memory.setRelatedSessionIds(sessionId);
        memory.setImportanceScore(score);
        memory.setBaseScore(score);
        memory.setAccessCount(0);
        // 技术债：实体无 key 列，暂将 key 存入 tags 列以保持 dedup 精确匹配兼容
        // 后续如增加 key 列需同步调整 DefaultMemoryDeduper.checkKeyConflict 与 MemoryScheduler.filterByTags
        memory.setTags(item.getKey());
        memory.setValidFrom(item.getValidFrom());
        memory.setValidUntil(item.getValidUntil());
        memory.setLastAccessedAt(LocalDateTime.now());
        memory.setCreateTime(LocalDateTime.now());
        memory.setUpdateTime(LocalDateTime.now());
        return memory;
    }

    /**
     * 将置信度0-1映射为评分0-100
     * @param confidence
     * @return
     */
    private int mapConfidenceToScore(Double confidence) {
        if (confidence == null) {
            return DEFAULT_IMPORTANCE_SCORE;
        }
        int score = (int) Math.round(confidence * 100);
        return Math.max(1, Math.min(100, score));
    }

    /**
     * 加载用户长期记忆，控制Token预算
     * @param userId
     * @param maxTokens
     * @return
     */
    public String loadUserLongTermMemory(String userId, int maxTokens) {
        List<UserLongTermMemoryInfo> memories = userLongTermMemoryRepository.findEffectiveMemories(userId);
        if (memories.isEmpty()) {
            return "";
        }

        // 按类型优先级升序（PREFERENCE在前） + effectiveScore 降序（高分在前）
        memories.sort(Comparator
                .comparingInt(this::memoryTypePriority)
                .thenComparing(Comparator.comparingDouble(this::effectiveScoreForSort).reversed()));
        return buildContextWithinBudget(memories, maxTokens, true);
    }

    /**
     * 获取记忆的 effectiveScore 用于排序（衰减服务可用时实时计算，否则降级为 importanceScore）
     * @param memory
     * @return
     */
    private double effectiveScoreForSort(UserLongTermMemoryInfo memory) {
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
     * 记忆类型优先级（数值越小优先级越高）
     * @param memory
     * @return
     */
    private int memoryTypePriority(UserLongTermMemoryInfo memory) {
        String type = memory.getMemoryType();
        if (type == null) {
            return 99;
        }
        switch (type) {
            case "PREFERENCE":
                return 1;
            case "FACT":
                return 2;
            case "CONSTRAINT":
                return 3;
            case "PROFILE":
                return 4;
            case "EVENT":
                return 5;
            case MEMORY_TYPE_SUMMARY:
                return 6;
            default:
                return 99;
        }
    }

    /**
     * 按查询文本加载相关长期记忆，基于关键词匹配做相关性排序
     * @param userId
     * @param queryText
     * @param maxTokens
     * @return
     */
    public String loadByQuery(String userId, String queryText, int maxTokens) {
        if (userId == null || userId.isBlank() || queryText == null || queryText.isBlank()) {
            return "";
        }

        // 优先向量语义检索
        String memoryContext = "";
        if (vectorEnabled && memoryVectorStore != null) {
            memoryContext = loadByVector(userId, queryText, maxTokens);
            if (memoryContext.isBlank()) {
                log.debug("向量检索无结果, 降级为关键词匹配: userId={}", userId);
                memoryContext = loadByKeyword(userId, queryText, maxTokens);
            }
        } else {
            memoryContext = loadByKeyword(userId, queryText, maxTokens);
        }

        // 图谱联动检索：从查询文本提取候选实体，补充图谱三元组
        String graphContext = retrieveFromGraph(userId, queryText, maxTokens);
        if (!graphContext.isBlank()) {
            memoryContext = memoryContext.isBlank() ? graphContext : memoryContext + "\n\n" + graphContext;
        }
        return memoryContext;
    }

    /**
     * 从知识图谱补充检索相关三元组
     * @param userId
     * @param queryText
     * @param maxTokens
     * @return
     */
    private String retrieveFromGraph(String userId, String queryText, int maxTokens) {
        if (!graphRetrievalEnabled || memoryGraphManager == null) {
            return "";
        }
        try {
            // 根据配置选择实体识别方式：llm=大模型识别, hanlp=HanLP分词
            List<String> entityCandidates;
            if ("llm".equalsIgnoreCase(entityRecognizerType)) {
                entityCandidates = memoryGraphManager.recognizeEntitiesByLlm(queryText, graphMaxEntities);
            } else {
                entityCandidates = memoryGraphManager.recognizeEntitiesByHanlp(queryText, graphMaxEntities);
            }
            if (entityCandidates.isEmpty()) {
                return "";
            }

            int hops = graphHops > 0 ? graphHops : 1;
            Set<String> seen = new HashSet<>();
            StringBuilder builder = new StringBuilder();
            int usedTokens = 0;
            for (String entity : entityCandidates) {
                List<KnowledgeTriple> triples = memoryGraphManager.searchByEntity(entity, userId, hops);
                if (triples == null || triples.isEmpty()) {
                    continue;
                }
                for (KnowledgeTriple triple : triples) {
                    String line = triple.toText();
                    if (seen.contains(line)) {
                        continue;
                    }
                    seen.add(line);
                    int lineTokens = TokenEstimator.estimateTokens(line);
                    if (usedTokens + lineTokens > maxTokens) {
                        break;
                    }
                    builder.append("[图谱] ").append(line).append("\n");
                    usedTokens += lineTokens;
                }
            }
            return builder.toString().trim();
        } catch (Exception e) {
            log.warn("图谱联动检索失败, 降级为空: userId={}", userId, e);
            return "";
        }
    }

    /**
     * 清空用户所有长期记忆，按DB→向量库→图谱→会话顺序清理
     * @param userId
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryCleanupStats clearUserMemory(String userId) {
        if (userId == null || userId.isBlank()) {
            return new MemoryCleanupStats(0, -1, -1, 0);
        }

        // 删除DB所有记忆（含DEPRECATED）
        int dbDeleted = userLongTermMemoryRepository.deleteByUserId(userId);

        // 删除向量索引
        int vectorDeleted = -1;
        if (memoryVectorStore != null) {
            vectorDeleted = memoryVectorStore.deleteByUser(userId);
        }

        // 删除知识图谱
        int graphDeleted = -1;
        if (memoryGraphManager != null) {
            graphDeleted = memoryGraphManager.deleteByUser(userId);
        }

        // 删除会话
        int sessionDeleted = 0;
        if (sessionManager != null) {
            List<ConversationSessionInfo> sessions =
                    sessionManager.findByUserId(userId);
            if (sessions != null) {
                for (ConversationSessionInfo session : sessions) {
                    try {
                        sessionManager.deleteBySessionId(session.getSessionId());
                        sessionDeleted++;
                    } catch (Exception e) {
                        log.warn("删除会话失败: sessionId={}", session.getSessionId(), e);
                    }
                }
            }
        }

        log.info("用户记忆清理完成: userId={}, db={}, vector={}, graph={}, session={}",
                userId, dbDeleted, vectorDeleted, graphDeleted, sessionDeleted);
        return new MemoryCleanupStats(dbDeleted, vectorDeleted, graphDeleted, sessionDeleted);
    }

    private static final String MEMORY_TYPE_PREFERENCE = "PREFERENCE";

    private static final String SOURCE_LLM_EXTRACTED = "LLM_EXTRACTED";

    private static final String SOURCE_USER_DECLARED = "USER_DECLARED";

    private static final int USER_DECLARED_SCORE = 100;

    /**
     * 记录用户偏好
     * @param userId
     * @param key
     * @param content
     * @param source
     * @param sessionId
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Long recordPreference(String userId, String key, String content, String source, String sessionId) {
        if (userId == null || userId.isBlank() || content == null || content.isBlank()) {
            return null;
        }
        String effectiveSource = (source == null || source.isBlank()) ? SOURCE_LLM_EXTRACTED : source.toUpperCase();
        boolean isUserDeclared = SOURCE_USER_DECLARED.equals(effectiveSource);

        // USER_DECLARED 直接覆盖同key旧值；LLM_EXTRACTED 走标准去重
        if (isUserDeclared && key != null && !key.isBlank()) {
            return overwritePreferenceByKey(userId, key, content, effectiveSource, sessionId);
        }

        // 走标准去重检查
        DedupDecision decision = checkDuplicateBeforeWrite(userId,
                new ExtractedMemory() {{
                    setMemoryType(MEMORY_TYPE_PREFERENCE);
                    setContent(content);
                    setKey(key);
                }});
        if (decision == null) {
            decision = DedupDecision.add();
        }
        switch (decision.getAction()) {
            case SKIP:
                log.debug("偏好记录被去重跳过: userId={}, key={}", userId, key);
                return null;
            case UPDATE:
            case CONFLICT:
            case DELETE:
                // USER_DECLARED 覆盖旧记忆；LLM_EXTRACTED 仅在旧记忆非USER_DECLARED时更新
                UserLongTermMemoryInfo existing = userLongTermMemoryRepository.selectById(decision.getExistingMemoryId());
                if (existing != null && SOURCE_USER_DECLARED.equals(existing.getSource()) && !isUserDeclared) {
                    log.debug("LLM提取偏好不覆盖用户显式偏好: userId={}, key={}", userId, key);
                    return null;
                }
                return upsertPreference(existing, userId, key, content, effectiveSource, sessionId);
            case ADD:
            default:
                return insertPreference(userId, key, content, effectiveSource, sessionId);
        }
    }

    /**
     * USER_DECLARED按key覆盖：先标记同key旧记忆失效，再插入新记忆
     * @param userId
     * @param key
     * @param content
     * @param source
     * @param sessionId
     * @return
     */
    private Long overwritePreferenceByKey(String userId, String key, String content, String source, String sessionId) {
        List<UserLongTermMemoryInfo> existingList = userLongTermMemoryRepository.findActiveMemoriesByTypeAndTags(userId, MEMORY_TYPE_PREFERENCE, key);
        for (UserLongTermMemoryInfo existing : existingList) {
            markMemoryExpired(existing.getId());
        }
        return insertPreference(userId, key, content, source, sessionId);
    }

    /**
     * 插入新偏好记忆
     * @param userId
     * @param key
     * @param content
     * @param source
     * @param sessionId
     * @return
     */
    private Long insertPreference(String userId, String key, String content, String source, String sessionId) {
        int score = SOURCE_USER_DECLARED.equals(source) ? USER_DECLARED_SCORE : DEFAULT_IMPORTANCE_SCORE;
        UserLongTermMemoryInfo memory = new UserLongTermMemoryInfo();
        memory.setUserId(userId);
        memory.setMemoryType(MEMORY_TYPE_PREFERENCE);
        memory.setContent(content);
        memory.setRelatedSessionIds(sessionId);
        memory.setImportanceScore(score);
        memory.setBaseScore(score);
        memory.setAccessCount(0);
        memory.setTags(key);
        memory.setSource(source);
        memory.setLastAccessedAt(LocalDateTime.now());
        memory.setCreateTime(LocalDateTime.now());
        memory.setUpdateTime(LocalDateTime.now());
        userLongTermMemoryRepository.insert(memory);
        indexMemoryVector(memory, content);
        log.info("用户偏好记录写入: userId={}, key={}, source={}, id={}", userId, key, source, memory.getId());
        publishPreferenceChanged(userId, key, content, "ADD");
        return memory.getId();
    }

    /**
     * 更新已有偏好记忆
     * @param existing
     * @param userId
     * @param key
     * @param content
     * @param source
     * @param sessionId
     * @return
     */
    private Long upsertPreference(UserLongTermMemoryInfo existing, String userId, String key, String content,
                                   String source, String sessionId) {
        int newScore = SOURCE_USER_DECLARED.equals(source) ? USER_DECLARED_SCORE : DEFAULT_IMPORTANCE_SCORE;
        int mergedScore = Math.max(
                existing.getImportanceScore() != null ? existing.getImportanceScore() : 0,
                newScore);
        userLongTermMemoryRepository.updateContentScoreAndSource(existing.getId(), content, mergedScore, mergedScore, source);
        existing.setContent(content);
        indexMemoryVector(existing, content);
        log.info("用户偏好记录更新: userId={}, key={}, source={}, id={}", userId, key, source, existing.getId());
        publishPreferenceChanged(userId, key, content, "UPDATE");
        return existing.getId();
    }

    /**
     * 发布用户偏好变化事件，供下游模块（如自进化）监听
     * @param userId
     * @param key
     * @param content
     * @param changeType
     */
    private void publishPreferenceChanged(String userId, String key, String content, String changeType) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publishEvent(new UserPreferenceChangedEvent(userId, key, content, changeType));
    }

    /**
     * 向量语义检索路径
     * @param userId
     * @param queryText
     * @param maxTokens
     * @return
     */
    private String loadByVector(String userId, String queryText, int maxTokens) {
        try {
            List<MemoryVectorStore.ScoredMemoryId> scored = memoryVectorStore.searchScored(
                    queryText, userId, null, VECTOR_SEARCH_TOP_K);
            if (scored.isEmpty()) {
                return "";
            }
            List<Long> ids = scored.stream()
                    .map(MemoryVectorStore.ScoredMemoryId::getMemoryId)
                    .collect(Collectors.toList());
            // 按 ID 批量查询记忆，并排除 DEPRECATED 和过期记忆
            List<UserLongTermMemoryInfo> memories = userLongTermMemoryRepository.findByIdsEffective(ids);
            if (memories.isEmpty()) {
                return "";
            }
            // 按向量相似度排序
            memories.sort(Comparator.comparingDouble(m -> -scoredSimilarity(scored, m.getId())));
            return buildContextWithinBudget(memories, maxTokens, true);
        } catch (Exception e) {
            log.warn("向量检索异常, 降级为关键词匹配: userId={}", userId, e);
            return "";
        }
    }

    /**
     * 关键词匹配路径（降级方案）
     * @param userId
     * @param queryText
     * @param maxTokens
     * @return
     */
    private String loadByKeyword(String userId, String queryText, int maxTokens) {
        List<UserLongTermMemoryInfo> memories = userLongTermMemoryRepository.findMemoriesForKeywordMatch(userId);
        if (memories.isEmpty()) {
            return "";
        }

        // 计算相关性评分并过滤得分为 0 的记忆
        List<ScoredMemory> scoredMemories = new ArrayList<>();
        for (UserLongTermMemoryInfo memory : memories) {
            String content = memory.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            double score = scoreRelevance(content, queryText);
            if (score > 0) {
                scoredMemories.add(new ScoredMemory(memory, score));
            }
        }
        if (scoredMemories.isEmpty()) {
            log.warn("长期记忆按查询无匹配: userId={}, queryText={}", userId, truncate(queryText));
            return "";
        }

        scoredMemories.sort(Comparator.comparingDouble(ScoredMemory::score).reversed());
        List<UserLongTermMemoryInfo> ordered = scoredMemories.stream()
                .map(sm -> sm.memory)
                .collect(Collectors.toList());
        return buildContextWithinBudget(ordered, maxTokens, true);
    }

    /**
     * 按Token预算构建上下文，并更新命中记忆的访问信息
     * @param memories
     * @param maxTokens
     * @param updateAccess
     * @return
     */
    private String buildContextWithinBudget(List<UserLongTermMemoryInfo> memories, int maxTokens, boolean updateAccess) {
        StringBuilder builder = new StringBuilder();
        int usedTokens = 0;
        List<Long> hitIds = new ArrayList<>();
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
            hitIds.add(memory.getId());
        }
        if (updateAccess && !hitIds.isEmpty()) {
            batchUpdateAccessInfo(hitIds);
        }
        return builder.toString().trim();
    }

    /**
     * 将记忆内容嵌入向量并写入Qdrant
     * @param memory
     * @param content
     */
    private void indexMemoryVector(UserLongTermMemoryInfo memory, String content) {
        if (!vectorEnabled || memoryVectorStore == null || memory.getId() == null) {
            return;
        }
        memoryVectorStore.embedAndIndex(memory.getId(), content, memory.getUserId(), memory.getMemoryType());
    }

    /**
     * 从向量检索评分中查找对应memoryId的相似度
     * @param scored
     * @param memoryId
     * @return
     */
    private double scoredSimilarity(List<MemoryVectorStore.ScoredMemoryId> scored, Long memoryId) {
        for (MemoryVectorStore.ScoredMemoryId s : scored) {
            if (memoryId.equals(s.getMemoryId())) {
                return s.getSimilarity();
            }
        }
        return 0.0;
    }

    /**
     * 批量更新访问次数和最后访问时间
     * @param ids
     */
    private void batchUpdateAccessInfo(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            userLongTermMemoryRepository.batchUpdateAccessInfo(ids);
        } catch (Exception e) {
            log.warn("批量更新访问信息失败", e);
        }
    }

    /**
     * 查询用户最新的SUMMARY类型长期记忆
     * @param userId
     * @return
     */
    private UserLongTermMemoryInfo findLatestSummaryMemory(String userId) {
        return userLongTermMemoryRepository.findLatestSummaryMemory(userId);
    }

    /**
     * 合并现有记忆与会话摘要，优先调用LLM，失败时降级为追加截断
     * @param userId
     * @param existing
     * @param sessionSummary
     * @return
     */
    private String mergeContent(String userId, UserLongTermMemoryInfo existing, String sessionSummary) {
        String existingContent = existing != null ? existing.getContent() : "";
        if (existingContent == null || existingContent.isBlank()) {
            return clampContent(sessionSummary);
        }
        if (languageModelFactory == null) {
            // 无LLM时降级为追加并截断
            String appended = existingContent + "\n\n" + sessionSummary;
            return clampContent(appended);
        }
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("existingMemory", existingContent);
            variables.put("sessionSummary", sessionSummary);
            String prompt;
            if (promptResolver == null) {
                prompt = MERGE_PROMPT_TEMPLATE
                        .replace("${existingMemory}", existingContent)
                        .replace("${sessionSummary}", sessionSummary);
            } else {
                prompt = promptResolver.resolve("memory.merge", MERGE_PROMPT_TEMPLATE, variables);
            }
            String result = languageModelFactory.generateText(modelCode, prompt);
            if (result != null && !result.isBlank()) {
                success = true;
            }
            return result != null ? clampContent(result.trim()) : clampContent(sessionSummary);
        } catch (Exception e) {
            log.warn("LLM合并长期记忆失败, 降级为追加", e);
            String appended = existingContent + "\n\n" + sessionSummary;
            return clampContent(appended);
        } finally {
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("memory.merge", userId, success, System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * 限制内容长度
     * @param content
     * @return
     */
    private String clampContent(String content) {
        if (content == null) {
            return "";
        }
        int maxChars = maxContentChars > 0 ? maxContentChars : DEFAULT_MAX_CONTENT_CHARS;
        return content.length() > maxChars ? content.substring(0, maxChars) : content;
    }

    /**
     * 追加会话ID到关联列表（逗号分隔）
     * @param existingIds
     * @param sessionId
     * @return
     */
    private String appendSessionId(String existingIds, String sessionId) {
        if (existingIds == null || existingIds.isBlank()) {
            return sessionId;
        }
        List<String> ids = Arrays.stream(existingIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (!ids.contains(sessionId)) {
            ids.add(sessionId);
        }
        return String.join(",", ids);
    }

    /**
     * 计算记忆内容与查询文本的相关性评分
     * @param content
     * @param queryText
     * @return
     */
    private double scoreRelevance(String content, String queryText) {
        if (content == null || content.isBlank() || queryText == null || queryText.isBlank()) {
            return 0.0;
        }
        List<String> queryTokens = tokenize(queryText);
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> uniqueQueryTokens = new HashSet<>(queryTokens);
        String lowerContent = content.toLowerCase();
        int matched = 0;
        for (String token : uniqueQueryTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (lowerContent.contains(token.toLowerCase())) {
                matched++;
            }
        }
        return (double) matched / uniqueQueryTokens.size();
    }

    /**
     * 文本分词：使用HanLP标准分词
     * @param text
     * @return
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<com.hankcs.hanlp.seg.common.Term> terms = HanLP.segment(text);
        List<String> tokens = new ArrayList<>(terms.size());
        for (com.hankcs.hanlp.seg.common.Term term : terms) {
            String word = term.word.trim();
            if (!word.isEmpty()) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * 截断文本用于日志输出
     * @param text
     * @return
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }

    /**
     * 记忆清理统计信息
     */
    public static final class MemoryCleanupStats {

        /**
         * 删除的DB记忆条数
         */
        private final int dbDeleted;

        /**
         * 删除的向量条数（-1表示不可用）
         */
        private final int vectorDeleted;

        /**
         * 删除的图谱三元组条数（-1表示不可用）
         */
        private final int graphDeleted;

        /**
         * 删除的会话条数
         */
        private final int sessionDeleted;

        public MemoryCleanupStats(int dbDeleted, int vectorDeleted, int graphDeleted, int sessionDeleted) {
            this.dbDeleted = dbDeleted;
            this.vectorDeleted = vectorDeleted;
            this.graphDeleted = graphDeleted;
            this.sessionDeleted = sessionDeleted;
        }

        public int getDbDeleted() {
            return dbDeleted;
        }

        public int getVectorDeleted() {
            return vectorDeleted;
        }

        public int getGraphDeleted() {
            return graphDeleted;
        }

        public int getSessionDeleted() {
            return sessionDeleted;
        }
    }

    /**
     * 带评分的记忆
     */
    private static final class ScoredMemory {

        /**
         * 原始记忆实体
         */
        private final UserLongTermMemoryInfo memory;

        /**
         * 相关性评分
         */
        private final double score;

        ScoredMemory(UserLongTermMemoryInfo memory, double score) {
            this.memory = memory;
            this.score = score;
        }

        double score() {
            return score;
        }
    }
}
