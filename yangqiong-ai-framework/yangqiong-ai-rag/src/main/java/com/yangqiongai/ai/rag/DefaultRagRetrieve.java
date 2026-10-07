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
package com.yangqiongai.ai.rag;

import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.agent.core.provider.RerankOptions;
import com.yangqiongai.ai.rag.config.KnowledgeBaseVersionResolver;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.embed.VectorEmbedder;
import com.yangqiongai.ai.rag.metrics.RagMetrics;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.query.QueryDecomposer;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.rag.query.QueryRewriter;
import com.yangqiongai.ai.rag.rerank.Reranker;
import com.yangqiongai.ai.rag.rerank.RerankerFactory;
import com.yangqiongai.ai.rag.retriever.AdaptiveContentRetriever;
import com.yangqiongai.ai.rag.retriever.ContextWindowExpander;
import com.yangqiongai.ai.rag.retriever.FullTextSearcher;
import com.yangqiongai.ai.rag.retriever.QdrantUnifiedRetriever;
import com.yangqiongai.ai.rag.retriever.RecallVerifier;
import com.yangqiongai.ai.rag.model.RecallRequest;
import com.yangqiongai.ai.rag.model.RecallResult;
import com.yangqiongai.ai.rag.route.RetrievalPathResolver;
import com.yangqiongai.ai.rag.route.RetrievalRoute;

import io.micrometer.tracing.annotation.NewSpan;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG检索
 * @author yangqiong
 */
@Service
public class DefaultRagRetrieve implements RagRetrieveService {

    private static final Logger log = LoggerFactory.getLogger(DefaultRagRetrieve.class);

    private static final String LANE_VECTOR = "vector";
    private static final String LANE_FULLTEXT = "fulltext";
    private static final Pattern TERM_SPLITTER = Pattern.compile("[\\p{Punct}\\p{Space}\\p{IsPunctuation}]+");

    @Autowired
    private VectorEmbedder vectorEmbedder;

    @Autowired
    private QdrantUnifiedRetriever qdrantUnifiedRetriever;

    @Autowired
    private AdaptiveContentRetriever adaptiveContentRetriever;

    @Autowired
    private FullTextSearcher fullTextSearcher;

    @Autowired
    private SliceRecordRepository sliceRecordRepository;

    @Autowired
    private KnowledgeBaseVersionResolver versionResolver;

    @Autowired(required = false)
    private RagMetrics ragMetrics;

    @Autowired
    @Qualifier("ragExecutor")
    private Executor ragExecutor;

    @Autowired
    private RagProperties ragProperties;

    @Autowired(required = false)
    private QueryRewriter queryRewriter;

    @Autowired(required = false)
    private QueryDecomposer queryDecomposer;

    @Autowired(required = false)
    private RerankerFactory rerankerFactory;

    @Autowired(required = false)
    private RetrievalPathResolver retrievalPathResolver;

    /**
     * 集合名称解析器
     */
    @Autowired
    private CollectionNameResolver collectionNameResolver;

    /**
     * 扩展检索策略列表（如 GRAPH 路由对应的 GraphRetrievalStrategy），由外部模块注入
     */
    @Autowired(required = false)
    private List<RetrievalStrategy> externalStrategies;

    /**
     * 召回验证服务
     */
    @Autowired(required = false)
    private RecallVerifier recallVerifier;

    /**
     * 上下文扩展器
     */
    @Autowired(required = false)
    private ContextWindowExpander contextWindowExpander;

    /**
     * 内置策略实例
     */
    private final RetrievalStrategy vectorStrategy = new VectorOnlyStrategy();

    private final RetrievalStrategy hybridStrategy = new HybridStrategy();

    private final RetrievalStrategy fulltextStrategy = new FulltextOnlyStrategy();

    /**
     * 获取默认最小相似度分数
     * @return
     */
    private double defaultMinScore() {
        return ragProperties.getRetrieve().getDefaultMinScore();
    }

    /**
     * 获取单文档最大返回条数
     * @return
     */
    private int maxPerDoc() {
        return ragProperties.getRetrieve().getMaxPerDoc();
    }

    /**
     * 可选查询改写
     * @param query
     * @return
     */
    private String applyQueryRewrite(String query) {
        if (queryRewriter == null || !ragProperties.getQueryRewrite().isEnabled()) {
            return query;
        }
        try {
            String rewritten = queryRewriter.rewrite(query);
            if (rewritten != null && !rewritten.isBlank()) {
                log.debug("查询改写: [{}] -> [{}]", query, rewritten);
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("查询改写失败, 使用原始查询: {}", query, e);
        }
        return query;
    }

    /**
     * 可选Rerank
     * @param evidences
     * @param query
     * @param topK
     * @return
     */
    private List<RetrievalEvidence> applyRerank(List<RetrievalEvidence> evidences, String query, int topK) {
        RerankOptions options = RerankOptions.current();
        boolean globalEnabled = ragProperties.getRerank().isEnabled();
        // 覆盖优先级：调用级RerankOptions > 全局配置
        boolean effectiveEnabled = options != null ? options.resolveEnabled(globalEnabled) : globalEnabled;
        if (rerankerFactory == null || !effectiveEnabled) {
            return evidences;
        }
        // 图谱证据已专属重排，全部为图谱重排结果时跳过通用 Reranker
        if (allGraphRanked(evidences)) {
            log.debug("所有证据均为图谱专属重排结果, 跳过通用 Reranker");
            return evidences;
        }
        try {
            Reranker reranker = rerankerFactory.getRerankService();
            List<RetrievalEvidence> reranked = reranker.rerank(evidences, query, topK,
                    options != null ? options.getModelCode() : null);
            log.debug("Rerank完成, 输入: {}, 输出: {}", evidences.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("Rerank失败, 返回原始排序", e);
            return evidences;
        }
    }

    /**
     * 判断证据列表是否全部为图谱专属重排结果
     * @param evidences
     * @return
     */
    private boolean allGraphRanked(List<RetrievalEvidence> evidences) {
        if (evidences == null || evidences.isEmpty()) {
            return false;
        }
        for (RetrievalEvidence evidence : evidences) {
            Map<String, Object> body = evidence.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("graphRanked"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检索相关文档片段
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
        return retrieve(query, kbIds, docIds, topK, null);
    }

    /**
     * 检索相关文档片段，支持重排序选项覆盖（Agent级/请求级配置，经由ThreadLocal传递给内部策略）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @param rerankOptions 重排序覆盖选项，null时跟随全局配置
     * @return
     */
    @Override
    @NewSpan("rag-retrieve")
    public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK, RerankOptions rerankOptions) {
        try {
            RerankOptions.bind(rerankOptions);
            return doRetrieve(query, kbIds, docIds, topK);
        } finally {
            RerankOptions.clear();
        }
    }

    /**
     * 执行检索主流程（路由决策与策略分发）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    private List<RetrievalEvidence> doRetrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
        log.info("RAG检索请求, query: {}, kbIds: {}, docIds: {}, topK: {}", query, kbIds, docIds, topK);

        if (kbIds == null || kbIds.isEmpty()) {
            log.warn("知识库ID列表为空, 无法执行检索");
            return new ArrayList<>();
        }

        // 路由决策
        RetrievalRoute route = resolveRoute(query, kbIds);
        if (route == RetrievalRoute.SKIP) {
            return new ArrayList<>();
        }
        // WEB_SEARCH 路由由 RetrievalAgent 层处理，此处兜底返回空避免策略未匹配
        if (route == RetrievalRoute.WEB_SEARCH) {
            log.warn("收到 WEB_SEARCH 路由, 该路由应由 RetrievalAgent 层处理, 返回空列表");
            return new ArrayList<>();
        }

        // 扩展策略（如 GRAPH）优先匹配
        if (externalStrategies != null) {
            for (RetrievalStrategy strategy : externalStrategies) {
                if (strategy.supports(route)) {
                    return strategy.retrieve(query, kbIds, docIds, topK);
                }
            }
        }

        // 内置策略分发
        RetrievalStrategy builtIn = selectBuiltInStrategy(route);
        if (builtIn != null) {
            return builtIn.retrieve(query, kbIds, docIds, topK);
        }
        return new ArrayList<>();
    }

    /**
     * 选择内置检索策略
     * @param route
     * @return
     */
    private RetrievalStrategy selectBuiltInStrategy(RetrievalRoute route) {
        if (route == RetrievalRoute.HYBRID) {
            return hybridStrategy;
        }
        if (route == RetrievalRoute.FULLTEXT_ONLY) {
            return fulltextStrategy;
        }
        if (route == RetrievalRoute.VECTOR_ONLY) {
            return vectorStrategy;
        }
        return null;
    }

    /**
     * 记录检索指标
     * @param method
     * @param durationMillis
     * @param resultCount
     */
    private void recordMetrics(String method, long durationMillis, int resultCount) {
        if (ragMetrics == null) {
            return;
        }
        ragMetrics.recordRetrieveDuration(method, durationMillis);
        ragMetrics.recordRetrieveResults(method, resultCount);
    }

    /**
     * 路由决策
     * @param query
     * @param kbIds
     * @return
     */
    private RetrievalRoute resolveRoute(String query, List<String> kbIds) {
        if (retrievalPathResolver == null) {
            return RetrievalRoute.HYBRID;
        }
        try {
            return retrievalPathResolver.decide(query, kbIds);
        } catch (Exception e) {
            log.warn("路由决策失败, 降级为HYBRID", e);
            return RetrievalRoute.HYBRID;
        }
    }

    /**
     * 仅全文检索（用于FULLTEXT_ONLY路由）
     * @param query
     * @param kbIds
     * @param topK
     * @return
     */
    private List<RetrievalEvidence> retrieveFulltextOnly(String query, List<String> kbIds, int topK) {
        log.info("全文检索路由, query: {}, kbIds: {}, topK: {}", query, kbIds, topK);
        long startedAt = System.currentTimeMillis();

        String effectiveQuery = applyQueryRewrite(query);

        List<ChunkCandidate> fulltextHits = fetchFulltextLane(effectiveQuery, kbIds, topK);
        if (fulltextHits.isEmpty()) {
            log.info("全文检索无结果, query: {}", effectiveQuery);
            return new ArrayList<>();
        }

        fulltextHits.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (fulltextHits.size() > topK) {
            fulltextHits = fulltextHits.subList(0, topK);
        }

        enrichWithParentContent(fulltextHits);

        List<RetrievalEvidence> evidences = wrapAsEvidences(fulltextHits);
        evidences = applyRerank(evidences, effectiveQuery, topK);
        long duration = System.currentTimeMillis() - startedAt;
        log.info("全文检索完成, 最终结果数: {}, 耗时: {}ms", evidences.size(), duration);
        recordMetrics("retrieveFulltext", duration, evidences.size());
        return evidences;
    }

    /**
     * 混合检索（稠密向量+稀疏全文双通道融合重排）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    @NewSpan("rag-retrieve-hybrid")
    public List<RetrievalEvidence> retrieveHybrid(String query, List<String> kbIds, List<String> docIds, int topK) {
        log.info("混合检索请求, query: {}, kbIds: {}, docIds: {}, topK: {}", query, kbIds, docIds, topK);

        if (kbIds == null || kbIds.isEmpty()) {
            log.warn("知识库ID列表为空, 无法执行混合检索");
            return new ArrayList<>();
        }

        long startedAt = System.currentTimeMillis();

        // 可选查询改写
        String effectiveQuery = applyQueryRewrite(query);

        double minScore = defaultMinScore();
        int maxPerDoc = maxPerDoc();
        int timeoutSeconds = ragProperties.getRetrieve().getFutureTimeoutSeconds();

        // 并行执行双通道检索
        float[] queryVector = vectorEmbedder.embedQuery(effectiveQuery);
        CompletableFuture<List<ChunkCandidate>> vectorFuture = CompletableFuture.supplyAsync(
                wrapWithMdc(() -> fetchVectorLane(queryVector, kbIds, docIds, topK, minScore)), ragExecutor)
                .exceptionally(ex -> {
                    log.warn("向量检索通道失败: {}", ex.getMessage());
                    return Collections.emptyList();
                });
        CompletableFuture<List<ChunkCandidate>> fulltextFuture = CompletableFuture.supplyAsync(
                wrapWithMdc(() -> fetchFulltextLane(effectiveQuery, kbIds, topK)), ragExecutor)
                .exceptionally(ex -> {
                    log.warn("全文检索通道失败: {}", ex.getMessage());
                    return Collections.emptyList();
                });

        // 带超时控制的等待
        List<ChunkCandidate> vectorHits = awaitWithTimeout(vectorFuture, timeoutSeconds, "vector");
        List<ChunkCandidate> fulltextHits = awaitWithTimeout(fulltextFuture, timeoutSeconds, "fulltext");

        // 双通道都失败时降级返回空结果
        if (vectorHits.isEmpty() && fulltextHits.isEmpty()) {
            log.warn("双通道检索均失败, 降级返回空结果, query: {}", effectiveQuery);
            return Collections.emptyList();
        }

        log.info("双通道检索完成, 向量命中: {}, 全文命中: {}, 耗时: {}ms",
                vectorHits.size(), fulltextHits.size(), System.currentTimeMillis() - startedAt);

        // 合并去重 + 融合评分 + 覆盖度控制
        List<ChunkCandidate> fused = mergeAndScore(effectiveQuery, vectorHits, fulltextHits, topK, maxPerDoc);

        enrichWithParentContent(fused);

        List<RetrievalEvidence> evidences = wrapAsEvidences(fused);
        // 可选Rerank
        evidences = applyRerank(evidences, effectiveQuery, topK);
        long duration = System.currentTimeMillis() - startedAt;
        log.info("混合检索完成, 最终结果数: {}, 总耗时: {}ms", evidences.size(), duration);
        recordMetrics("retrieveHybrid", duration, evidences.size());
        return evidences;
    }

    /**
     * 带超时控制的Future结果获取
     * @param future
     * @param timeoutSeconds
     * @param laneName
     * @return
     */
    private List<ChunkCandidate> awaitWithTimeout(CompletableFuture<List<ChunkCandidate>> future,
                                                   int timeoutSeconds, String laneName) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("{}通道检索超时({}s), 返回已完成结果", laneName, timeoutSeconds);
            future.cancel(true);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{}通道检索被中断", laneName);
            return Collections.emptyList();
        } catch (ExecutionException e) {
            log.warn("{}通道检索异常: {}", laneName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 包裹Supplier以传递MDC上下文到异步线程
     * @param supplier
     * @return
     */
    private <T> Supplier<T> wrapWithMdc(Supplier<T> supplier) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                return supplier.get();
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * 向量通道检索
     * @param queryVector
     * @param kbIds
     * @param docIds
     * @param topK
     * @param minScore
     * @return
     */
    private List<ChunkCandidate> fetchVectorLane(float[] queryVector, List<String> kbIds,
                                                  List<String> docIds, int topK, double minScore) {
        List<ChunkCandidate> aggregated = new ArrayList<>();
        for (String kbId : kbIds) {
            Filter searchFilter = composeFilter(kbId, docIds);
            String collectionName = collectionNameResolver.resolve(kbId);
            List<ChunkCandidate> hits = adaptiveContentRetriever.retrieveWithVector(
                    queryVector, collectionName, topK, minScore, searchFilter);
            aggregated.addAll(hits);
        }
        // minScore兜底
        if (aggregated.isEmpty()) {
            for (String kbId : kbIds) {
                Filter searchFilter = composeFilter(kbId, docIds);
                String collectionName = collectionNameResolver.resolve(kbId);
                List<ChunkCandidate> hits = adaptiveContentRetriever.retrieveWithVector(
                        queryVector, collectionName, topK, 0.0, searchFilter);
                aggregated.addAll(hits);
            }
        }
        return aggregated;
    }

    /**
     * 全文通道检索
     * @param query
     * @param kbIds
     * @param topK
     * @return
     */
    private List<ChunkCandidate> fetchFulltextLane(String query, List<String> kbIds, int topK) {
        List<ChunkCandidate> aggregated = new ArrayList<>();
        for (String kbId : kbIds) {
            String activeVersion = resolveActiveVersion(kbId);
            List<ChunkCandidate> hits = fullTextSearcher.searchFullText(query, kbId, topK, activeVersion);
            aggregated.addAll(hits);
        }
        return aggregated;
    }

    /**
     * 合并去重与融合评分
     * @param query
     * @param vectorHits
     * @param fulltextHits
     * @param topK
     * @param maxPerDoc
     * @return
     */
    private List<ChunkCandidate> mergeAndScore(String query, List<ChunkCandidate> vectorHits,
                                                List<ChunkCandidate> fulltextHits, int topK, int maxPerDoc) {
        Map<String, MergeSlot> slotMap = new LinkedHashMap<>();

        // 向量通道入槽
        for (ChunkCandidate candidate : vectorHits) {
            String slotKey = dedupeKey(candidate);
            slotMap.computeIfAbsent(slotKey, k -> new MergeSlot(candidate))
                    .absorb(candidate, LANE_VECTOR);
        }

        // 全文通道入槽
        for (ChunkCandidate candidate : fulltextHits) {
            String slotKey = dedupeKey(candidate);
            slotMap.computeIfAbsent(slotKey, k -> new MergeSlot(candidate))
                    .absorb(candidate, LANE_FULLTEXT);
        }

        if (slotMap.isEmpty()) {
            return new ArrayList<>();
        }

        // 计算融合分并排序
        List<MergeSlot> scored = new ArrayList<>(slotMap.values());
        for (MergeSlot slot : scored) {
            slot.fusionScore = calcFusionScore(query, slot);
        }
        scored.sort((a, b) -> Double.compare(b.fusionScore, a.fusionScore));

        // 覆盖度控制：单文档最多保留maxPerDoc条
        List<ChunkCandidate> selected = applyCoverageCap(scored, topK, maxPerDoc);

        // 不足topK时用溢出部分补充
        if (selected.size() < topK) {
            List<ChunkCandidate> overflow = collectOverflow(scored, selected);
            for (ChunkCandidate candidate : overflow) {
                selected.add(candidate);
                if (selected.size() >= topK) {
                    break;
                }
            }
        }

        return selected;
    }

    /**
     * 覆盖度控制：单文档最多保留指定条数
     * @param scoredSlots
     * @param limit
     * @param maxPerDoc
     * @return
     */
    private List<ChunkCandidate> applyCoverageCap(List<MergeSlot> scoredSlots, int limit, int maxPerDoc) {
        Map<String, Integer> docCounter = new HashMap<>();
        List<ChunkCandidate> accepted = new ArrayList<>();
        for (MergeSlot slot : scoredSlots) {
            String docId = blankSafe(slot.representative.getDocId());
            int count = docCounter.getOrDefault(docId, 0);
            if (count < maxPerDoc) {
                accepted.add(slot.representative);
                docCounter.put(docId, count + 1);
            }
            if (accepted.size() >= limit) {
                return accepted;
            }
        }
        return accepted;
    }

    /**
     * 收集溢出候选（覆盖度控制中被截断的）
     * @param scoredSlots
     * @param alreadySelected
     * @return
     */
    private List<ChunkCandidate> collectOverflow(List<MergeSlot> scoredSlots, List<ChunkCandidate> alreadySelected) {
        Set<String> selectedKeys = alreadySelected.stream()
                .map(this::dedupeKey)
                .collect(Collectors.toSet());
        List<ChunkCandidate> overflow = new ArrayList<>();
        for (MergeSlot slot : scoredSlots) {
            String key = dedupeKey(slot.representative);
            if (!selectedKeys.contains(key)) {
                overflow.add(slot.representative);
            }
        }
        return overflow;
    }

    /**
     * 计算融合评分
     * @param query
     * @param slot
     * @return
     */
    private double calcFusionScore(String query, MergeSlot slot) {
        RagProperties.Retrieve.Fusion fusion = ragProperties.getRetrieve().getFusion();
        double score = 0D;
        Set<String> lanes = slot.lanes;

        // 通道权重
        if (lanes.contains(LANE_VECTOR)) {
            score += fusion.getVectorWeight();
        }
        if (lanes.contains(LANE_FULLTEXT)) {
            score += fusion.getFulltextWeight();
        }
        // 双通道命中加成
        if (lanes.size() > 1) {
            score += fusion.getDualLaneBonus();
        }

        // 原始分贡献
        double rawScore = slot.bestRawScore;
        score += rawScore <= 1D ? rawScore * fusion.getRawScoreFactor() : Math.min(rawScore, fusion.getRawScoreCap());

        // 查询词命中加成
        String lowerQuery = lowerStr(query);
        String lowerContent = lowerStr(slot.representative.getText());
        if (hasText(lowerQuery) && lowerContent.contains(lowerQuery)) {
            score += fusion.getQueryHitBonus();
        }
        for (String term : extractTerms(query)) {
            if (lowerContent.contains(term)) {
                score += fusion.getTermHitBonus();
            }
        }

        return score;
    }

    /**
     * 去重键
     * @param candidate
     * @return
     */
    private String dedupeKey(ChunkCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        if (hasText(candidate.getParentId())) {
            return blankSafe(candidate.getDocId()) + "::p::" + candidate.getParentId().trim();
        }
        if (hasText(candidate.getSliceId())) {
            return blankSafe(candidate.getDocId()) + "::s::" + candidate.getSliceId().trim();
        }
        return blankSafe(candidate.getDocId()) + "::c::" + candidate.getText().hashCode();
    }

    /**
     * 跨知识库扫描检索
     * @param queryVector
     * @param kbIds
     * @param docIds
     * @param topK
     * @param minScore
     * @return
     */
    private List<ChunkCandidate> scanKnowledgeBases(float[] queryVector, List<String> kbIds,
                                                     List<String> docIds, int topK, double minScore) {
        List<ChunkCandidate> aggregated = new ArrayList<>();
        for (String kbId : kbIds) {
            Filter searchFilter = composeFilter(kbId, docIds);
            String collectionName = collectionNameResolver.resolve(kbId);
            List<ChunkCandidate> hits = adaptiveContentRetriever.retrieveWithVector(
                    queryVector, collectionName, topK, minScore, searchFilter);
            aggregated.addAll(hits);
        }
        return aggregated;
    }

    /**
     * 构建Qdrant检索过滤条件
     * @param kbId
     * @param docIds
     * @return
     */
    private Filter composeFilter(String kbId, List<String> docIds) {
        Filter.Builder builder = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("kbId")
                                .setMatch(Match.newBuilder()
                                        .setKeyword(kbId)
                                        .build())
                                .build())
                        .build());

        // 索引版本过滤：仅检索当前活跃版本的数据
        String activeVersion = resolveActiveVersion(kbId);
        if (hasText(activeVersion)) {
            builder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey("version")
                            .setMatch(Match.newBuilder()
                                    .setKeyword(activeVersion)
                                    .build())
                            .build())
                    .build());
        }

        if (docIds != null && !docIds.isEmpty()) {
            Filter.Builder docIdOrClause = Filter.newBuilder();
            for (String docId : docIds) {
                docIdOrClause.addShould(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("docId")
                                .setMatch(Match.newBuilder()
                                        .setKeyword(docId)
                                        .build())
                                .build())
                        .build());
            }
            builder.addMust(Condition.newBuilder()
                    .setFilter(docIdOrClause.build())
                    .build());
        }

        return builder.build();
    }

    /**
     * 子块命中时回填父块完整内容
     * @param candidates
     */
    private void enrichWithParentContent(List<ChunkCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        Set<String> parentRefIds = new LinkedHashSet<>();
        for (ChunkCandidate candidate : candidates) {
            if (candidate.isChildHit() && hasText(candidate.getParentId())) {
                parentRefIds.add(candidate.getParentId().trim());
            }
        }

        if (parentRefIds.isEmpty()) {
            log.debug("无子块命中需要回填父块内容");
            return;
        }

        log.info("检测到子块命中, 需回填父块内容, 父块ID数: {}", parentRefIds.size());

        Map<String, SliceRecord> parentLookup = sliceRecordRepository.loadParentChunkLookup(parentRefIds);

        if (parentLookup.isEmpty()) {
            log.warn("未查询到任何父块记录, parentRefIds: {}", parentRefIds);
            return;
        }

        int enrichedCount = 0;
        for (ChunkCandidate candidate : candidates) {
            if (candidate.isChildHit() && hasText(candidate.getParentId())) {
                SliceRecord parentSlice = parentLookup.get(candidate.getParentId().trim());
                if (parentSlice != null && hasText(parentSlice.getContent())) {
                    candidate.setText(parentSlice.getContent());
                    enrichedCount++;
                }
            }
        }

        log.info("父块内容回填完成, 成功回填: {}/需回填: {}", enrichedCount, parentRefIds.size());
    }

    /**
     * 获取知识库当前活跃版本号（走缓存）
     * @param kbId
     * @return
     */
    private String resolveActiveVersion(String kbId) {
        return versionResolver.resolveActiveVersion(kbId);
    }

    /**
     * 将候选切片封装为检索证据
     * @param candidates
     * @return
     */
    private List<RetrievalEvidence> wrapAsEvidences(List<ChunkCandidate> candidates) {
        List<RetrievalEvidence> evidences = new ArrayList<>();
        for (ChunkCandidate candidate : candidates) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("isChildHit", candidate.isChildHit());
            if (candidate.getParentId() != null) {
                meta.put("parentId", candidate.getParentId());
            }

            RetrievalEvidence evidence = new RetrievalEvidence(
                    candidate.getText(),
                    candidate.getDocId(),
                    candidate.getDocName(),
                    candidate.getSliceId(),
                    candidate.getScore(),
                    meta
            );
            evidence.setKbId(candidate.getKbId());
            evidences.add(evidence);
        }
        return evidences;
    }

    private Set<String> extractTerms(String query) {
        if (!hasText(query)) {
            return Set.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        for (String part : TERM_SPLITTER.split(normalized)) {
            String token = part.trim();
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.length() >= 2 && compact.length() <= 12) {
            terms.add(compact);
        }
        return terms;
    }

    private String lowerStr(String value) {
        return hasText(value) ? value.toLowerCase(Locale.ROOT) : "";
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 向量检索策略（VECTOR_ONLY 路由）
     */
    private final class VectorOnlyStrategy implements RetrievalStrategy {

        /**
         * 是否支持指定路由
         * @param route
         * @return
         */
        @Override
        public boolean supports(RetrievalRoute route) {
            return route == RetrievalRoute.VECTOR_ONLY;
        }

        /**
         * 执行向量检索（含可选查询分解与改写）
         * @param query
         * @param kbIds
         * @param docIds
         * @param topK
         * @return
         */
        @Override
        public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
            long startedAt = System.currentTimeMillis();

            // 可选查询分解：复杂查询分解为子问题并行检索
            if (queryDecomposer != null && ragProperties.getQueryDecompose().isEnabled()
                    && queryDecomposer.shouldDecompose(query)) {
                List<ChunkCandidate> decomposedCandidates = queryDecomposer.decomposeAndRetrieve(
                        query, kbIds, docIds, topK);
                if (!decomposedCandidates.isEmpty()) {
                    enrichWithParentContent(decomposedCandidates);
                    List<RetrievalEvidence> evidences = wrapAsEvidences(decomposedCandidates);
                    evidences = applyRerank(evidences, query, topK);
                    long duration = System.currentTimeMillis() - startedAt;
                    log.info("查询分解检索完成, 最终结果数: {}, 耗时: {}ms", evidences.size(), duration);
                    recordMetrics("retrieveDecomposed", duration, evidences.size());
                    return evidences;
                }
                log.info("查询分解未返回结果, 降级为常规检索");
            }

            // 可选查询改写
            String effectiveQuery = applyQueryRewrite(query);

            double minScore = defaultMinScore();
            float[] queryVector = vectorEmbedder.embedQuery(effectiveQuery);

            List<ChunkCandidate> mergedCandidates = scanKnowledgeBases(
                    queryVector, kbIds, docIds, topK, minScore);

            // minScore兜底：无结果时去掉阈值重试
            if (mergedCandidates.isEmpty()) {
                log.info("带minScore={}检索无结果, 尝试无阈值重试", minScore);
                mergedCandidates = scanKnowledgeBases(queryVector, kbIds, docIds, topK, 0.0);
            }

            mergedCandidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            if (mergedCandidates.size() > topK) {
                mergedCandidates = mergedCandidates.subList(0, topK);
            }

            enrichWithParentContent(mergedCandidates);

            List<RetrievalEvidence> evidences = wrapAsEvidences(mergedCandidates);
            evidences = applyRerank(evidences, effectiveQuery, topK);
            long duration = System.currentTimeMillis() - startedAt;
            log.info("向量检索完成, 最终结果数: {}, 耗时: {}ms", evidences.size(), duration);
            recordMetrics("retrieve", duration, evidences.size());
            return evidences;
        }
    }

    /**
     * 混合检索策略（HYBRID 路由）
     */
    private final class HybridStrategy implements RetrievalStrategy {

        /**
         * 是否支持指定路由
         * @param route
         * @return
         */
        @Override
        public boolean supports(RetrievalRoute route) {
            return route == RetrievalRoute.HYBRID;
        }

        /**
         * 执行混合检索（委托 retrieveHybrid）
         * @param query
         * @param kbIds
         * @param docIds
         * @param topK
         * @return
         */
        @Override
        public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
            return retrieveHybrid(query, kbIds, docIds, topK);
        }
    }

    /**
     * 全文检索策略（FULLTEXT_ONLY 路由）
     */
    private final class FulltextOnlyStrategy implements RetrievalStrategy {

        /**
         * 是否支持指定路由
         * @param route
         * @return
         */
        @Override
        public boolean supports(RetrievalRoute route) {
            return route == RetrievalRoute.FULLTEXT_ONLY;
        }

        /**
         * 执行全文检索（委托 retrieveFulltextOnly）
         * @param query
         * @param kbIds
         * @param docIds
         * @param topK
         * @return
         */
        @Override
        public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
            return retrieveFulltextOnly(query, kbIds, topK);
        }
    }

    /**
     * 融合槽位：管理同一候选在不同通道的命中信息
     */
    private final class MergeSlot {

        ChunkCandidate representative;
        double bestRawScore;
        final Set<String> lanes = new LinkedHashSet<>();
        double fusionScore;

        MergeSlot(ChunkCandidate initial) {
            this.representative = initial;
            this.bestRawScore = initial.getScore();
        }

        void absorb(ChunkCandidate incoming, String lane) {
            lanes.add(lane);
            if (incoming.getScore() > bestRawScore) {
                bestRawScore = incoming.getScore();
                representative = incoming;
            }
        }
    }

    /**
     * 召回验证
     * @param request
     * @return
     */
    @Override
    public List<RecallResult> verifyRecall(RecallRequest request) {
        if (recallVerifier == null) {
            log.warn("召回验证服务未启用");
            return List.of();
        }
        return recallVerifier.verifyRecall(request);
    }

    /**
     * 单文档上下文扩展
     * @param docId
     * @param query
     * @param maxChars
     * @return
     */
    @Override
    public String expandContext(String docId, String query, int maxChars) {
        if (contextWindowExpander == null) {
            log.warn("上下文扩展器未启用");
            return "";
        }
        return contextWindowExpander.expandSingleDoc(docId, query, maxChars);
    }
}
