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
package com.yangqiongai.ai.rag.agent;

import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.grader.ContextGradeResult;
import com.yangqiongai.ai.rag.grader.ContextGraderService;
import com.yangqiongai.ai.rag.metrics.RagMetrics;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.model.RecallRequest;
import com.yangqiongai.ai.rag.model.RecallResult;
import com.yangqiongai.ai.rag.route.RetrievalRoute;
import com.yangqiongai.ai.rag.RagRetrieveService;
import com.yangqiongai.ai.rag.websearch.WebSearchProvider;
import com.yangqiongai.ai.rag.websearch.WebSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索代理（反思-迭代闭环协调器）
 * @author yangqiong
 */
@Service
@Primary
@ConditionalOnProperty(name = "ai.rag.retrieval-agent.enabled", havingValue = "true")
public class RetrievalAgent implements RagRetrieveService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalAgent.class);

    @Autowired
    @Qualifier("defaultRagRetrieve")
    private RagRetrieveService delegate;

    @Autowired
    private RagProperties ragProperties;

    @Autowired(required = false)
    private ContextGraderService contextGraderService;

    @Autowired(required = false)
    private WebSearchProvider webSearchProvider;

    @Autowired(required = false)
    private RagMetrics ragMetrics;

    /**
     * 检索相关文档片段（含反思-迭代闭环）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
        List<RetrievalEvidence> evidences = delegate.retrieve(query, kbIds, docIds, topK);
        return applyContextEvaluation(evidences, query, kbIds, docIds, topK, RetrievalRoute.VECTOR_ONLY);
    }

    /**
     * 混合检索（含反思-迭代闭环）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    public List<RetrievalEvidence> retrieveHybrid(String query, List<String> kbIds, List<String> docIds, int topK) {
        List<RetrievalEvidence> evidences = delegate.retrieveHybrid(query, kbIds, docIds, topK);
        return applyContextEvaluation(evidences, query, kbIds, docIds, topK, RetrievalRoute.HYBRID);
    }

    /**
     * 召回验证（直接委托）
     * @param request
     * @return
     */
    @Override
    public List<RecallResult> verifyRecall(RecallRequest request) {
        return delegate.verifyRecall(request);
    }

    /**
     * 单文档上下文扩展（直接委托）
     * @param docId
     * @param query
     * @param maxChars
     * @return
     */
    @Override
    public String expandContext(String docId, String query, int maxChars) {
        return delegate.expandContext(docId, query, maxChars);
    }

    /**
     * 上下文评估与重检索触发
     * @param evidences
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @param currentRoute
     * @return
     */
    private List<RetrievalEvidence> applyContextEvaluation(List<RetrievalEvidence> evidences,
                                                             String query, List<String> kbIds,
                                                             List<String> docIds, int topK,
                                                             RetrievalRoute currentRoute) {
        if (contextGraderService == null || !ragProperties.getContextGrade().isEnabled()) {
            return evidences;
        }
        if (evidences == null || evidences.isEmpty()) {
            log.info("本地检索结果为空, 直接触发重检索闭环");
            return triggerRetrievalLoop(query, kbIds, docIds, topK, currentRoute,
                    ContextGradeResult.insufficient(List.of("完整性"), 0.0, "本地结果为空"),
                    new ArrayList<>());
        }
        try {
            ContextGradeResult result = contextGraderService.grade(query, evidences);
            if (ragMetrics != null) {
                ragMetrics.recordContextGradeSufficient(result.isSufficient(), result.getConfidence());
            }
            if (result.isSufficient()) {
                log.debug("上下文评估充分, 返回原始证据");
                return evidences;
            }
            log.info("上下文评估不充分: missing={}, 触发重检索", result.getMissingDimensions());
            return triggerRetrievalLoop(query, kbIds, docIds, topK, currentRoute, result, evidences);
        } catch (Exception e) {
            log.warn("上下文评估异常, 返回原始证据", e);
            return evidences;
        }
    }

    /**
     * 重检索闭环（最多maxRetries次）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @param currentRoute
     * @param gradeResult
     * @param currentEvidences
     * @return
     */
    private List<RetrievalEvidence> triggerRetrievalLoop(String query, List<String> kbIds,
                                                           List<String> docIds, int topK,
                                                           RetrievalRoute currentRoute,
                                                           ContextGradeResult gradeResult,
                                                           List<RetrievalEvidence> currentEvidences) {
        int maxRetries = ragProperties.getContextGrade().getMaxRetries();
        List<RetrievalEvidence> bestEvidences = new ArrayList<>(currentEvidences);
        RetrievalRoute route = currentRoute;
        int currentTopK = topK;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (ragMetrics != null) {
                ragMetrics.incrementRetrievalRetry();
            }
            // 扩大topK重检索
            if (attempt == 0) {
                currentTopK = topK * ragProperties.getRetrieve().getAdaptiveTopKMultiplier();
                log.info("重检索阶段1: 扩大topK从{}到{}", topK, currentTopK);
                List<RetrievalEvidence> retried = delegate.retrieveHybrid(query, kbIds, docIds, currentTopK);
                bestEvidences = mergeEvidences(bestEvidences, retried);
                bestEvidences = trimToTopK(bestEvidences, topK);
            } else if (attempt == 1) {
                // 切换路由策略（VECTOR_ONLY/FULLTEXT_ONLY→HYBRID, HYBRID→WEB_SEARCH）
                route = upgradeRoute(route);
                log.info("重检索阶段2: 路由升级到{}", route);
                List<RetrievalEvidence> retried = executeRetryByRoute(query, kbIds, docIds, currentTopK, route);
                bestEvidences = mergeEvidences(bestEvidences, retried);
                bestEvidences = trimToTopK(bestEvidences, topK);
            } else {
                // attempt >= 2: 无新动作,直接退出避免空转浪费LLM调用
                log.debug("重检索第{}次无新动作,退出循环", attempt);
                break;
            }
            // 重新评估
            ContextGradeResult reGrade = contextGraderService.grade(query, bestEvidences);
            if (ragMetrics != null) {
                ragMetrics.recordContextGradeSufficient(reGrade.isSufficient(), reGrade.getConfidence());
            }
            if (reGrade.isSufficient()) {
                log.info("重检索第{}次后上下文充分, 返回", attempt + 1);
                return bestEvidences;
            }
            gradeResult = reGrade;
        }
        // Web搜索补充（不计入maxRetries，仅当WebSearchProvider可用且路由已升级到WEB_SEARCH）
        if (webSearchProvider != null && route == RetrievalRoute.WEB_SEARCH) {
            log.info("重检索阶段3: 触发Web搜索补充");
            List<RetrievalEvidence> webEvidences = retrieveWithWebSearch(query, topK);
            bestEvidences = mergeEvidences(bestEvidences, webEvidences);
            bestEvidences = trimToTopK(bestEvidences, topK);
            ContextGradeResult webGrade = contextGraderService.grade(query, bestEvidences);
            if (ragMetrics != null) {
                ragMetrics.recordContextGradeSufficient(webGrade.isSufficient(), webGrade.getConfidence());
            }
            if (webGrade.isSufficient()) {
                log.info("Web搜索补充后上下文充分, 返回");
            } else {
                log.warn("Web搜索补充后上下文仍不充分, 返回当前最佳证据");
            }
        } else if (webSearchProvider == null) {
            log.debug("WebSearchProvider未注入, 跳过Web搜索补充");
        }
        log.warn("重检索达到最大次数{}, 返回当前最佳证据", maxRetries);
        return bestEvidences;
    }

    /**
     * 按路由执行重检索
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @param route
     * @return
     */
    private List<RetrievalEvidence> executeRetryByRoute(String query, List<String> kbIds,
                                                          List<String> docIds, int topK,
                                                          RetrievalRoute route) {
        try {
            switch (route) {
                case HYBRID:
                    return delegate.retrieveHybrid(query, kbIds, docIds, topK);
                case VECTOR_ONLY:
                    return delegate.retrieve(query, kbIds, docIds, topK);
                case WEB_SEARCH:
                    return retrieveWithWebSearch(query, topK);
                case FULLTEXT_ONLY:
                case SKIP:
                default:
                    return new ArrayList<>();
            }
        } catch (Exception e) {
            log.warn("按路由重检索失败: route={}", route, e);
            return new ArrayList<>();
        }
    }

    /**
     * Web搜索补充检索
     * @param query
     * @param topK
     * @return
     */
    private List<RetrievalEvidence> retrieveWithWebSearch(String query, int topK) {
        if (webSearchProvider == null) {
            log.warn("WebSearchProvider未注入, 返回空列表");
            return new ArrayList<>();
        }
        try {
            int maxResults = ragProperties.getRoute().getWebSearchMaxResults();
            List<WebSearchResult> webResults = webSearchProvider.search(query, maxResults);
            return convertWebResults(webResults, topK);
        } catch (Exception e) {
            log.warn("Web搜索失败, 返回空列表", e);
            return new ArrayList<>();
        }
    }

    /**
     * 路由升级策略
     * @param current
     * @return
     */
    private RetrievalRoute upgradeRoute(RetrievalRoute current) {
        switch (current) {
            case VECTOR_ONLY:
                return RetrievalRoute.HYBRID;
            case FULLTEXT_ONLY:
                return RetrievalRoute.HYBRID;
            case HYBRID:
                return RetrievalRoute.WEB_SEARCH;
            case WEB_SEARCH:
            case SKIP:
            default:
                return current;
        }
    }

    /**
     * 合并去重证据（按sliceId去重，保留分数高的）
     * @param base
     * @param incoming
     * @return
     */
    private List<RetrievalEvidence> mergeEvidences(List<RetrievalEvidence> base,
                                                     List<RetrievalEvidence> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return base;
        }
        List<RetrievalEvidence> merged = new ArrayList<>(base);
        Set<String> existingKeys = new HashSet<>();
        for (RetrievalEvidence e : base) {
            existingKeys.add(dedupeKey(e));
        }
        for (RetrievalEvidence e : incoming) {
            String key = dedupeKey(e);
            if (!existingKeys.contains(key)) {
                merged.add(e);
                existingKeys.add(key);
            }
        }
        return merged;
    }

    /**
     * 去重键（sliceId优先，否则sourceDocId+content hashCode）
     * @param evidence
     * @return
     */
    private String dedupeKey(RetrievalEvidence evidence) {
        if (evidence.getSliceId() != null && !evidence.getSliceId().isBlank()) {
            return evidence.getSliceId();
        }
        String docId = evidence.getSourceDocId() != null ? evidence.getSourceDocId() : "";
        String content = evidence.getContent() != null ? evidence.getContent() : "";
        return docId + "_" + content.hashCode();
    }

    /**
     * 按score降序截取topK
     * @param evidences
     * @param topK
     * @return
     */
    private List<RetrievalEvidence> trimToTopK(List<RetrievalEvidence> evidences, int topK) {
        if (evidences.size() <= topK) {
            return evidences;
        }
        return evidences.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 转换Web搜索结果为RetrievalEvidence
     * @param webResults
     * @param topK
     * @return
     */
    private List<RetrievalEvidence> convertWebResults(List<WebSearchResult> webResults, int topK) {
        if (webResults == null || webResults.isEmpty()) {
            return new ArrayList<>();
        }
        List<RetrievalEvidence> evidences = new ArrayList<>(webResults.size());
        for (WebSearchResult result : webResults) {
            String content = result.getSnippet();
            if (content == null || content.isBlank()) {
                content = result.getTitle();
            }
            RetrievalEvidence evidence = new RetrievalEvidence();
            evidence.setContent(content);
            evidence.setSourceDocId(result.getUrl());
            evidence.setSourceDocName(result.getTitle());
            evidence.setSliceId("web_" + result.getUrl().hashCode());
            evidence.setScore(result.getScore());
            evidences.add(evidence);
        }
        return trimToTopK(evidences, topK);
    }
}
