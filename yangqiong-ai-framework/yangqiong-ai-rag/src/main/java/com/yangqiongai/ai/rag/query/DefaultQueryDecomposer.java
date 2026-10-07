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
package com.yangqiongai.ai.rag.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.embed.VectorEmbedder;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import com.yangqiongai.ai.rag.retriever.AdaptiveContentRetriever;
import io.micrometer.tracing.annotation.NewSpan;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 查询分解
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.rag.query-decompose.enabled", havingValue = "true")
public class DefaultQueryDecomposer implements QueryDecomposer {

    private static final Logger log = LoggerFactory.getLogger(DefaultQueryDecomposer.class);

    private static final String DECOMPOSE_SYSTEM_INSTRUCTION =
            "你是一个查询分解助手。请将用户的复杂查询分解为2-3个独立的子问题，每个子问题应能独立检索回答。" +
            "要求：子问题应覆盖原查询的不同方面；保持简洁；只返回JSON数组格式，如：[\"子问题1\", \"子问题2\"]；" +
            "不要添加任何其他说明。如果查询已经足够简单无需分解，返回空数组 []。";

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired
    private VectorEmbedder vectorEmbedder;

    @Autowired
    private AdaptiveContentRetriever adaptiveContentRetriever;

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    @Qualifier("ragExecutor")
    private Executor ragExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分解复杂查询并并行检索，合并结果
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    @NewSpan("rag-query-decompose")
    public List<ChunkCandidate> decomposeAndRetrieve(String query, List<String> kbIds,
                                                      List<String> docIds, int topK) {
        if (!shouldDecompose(query)) {
            return Collections.emptyList();
        }

        RagProperties.QueryDecompose cfg = ragProperties.getQueryDecompose();
        List<String> subQueries = decomposeQuery(query, cfg.getMaxSubQueries());
        if (subQueries.isEmpty()) {
            log.info("查询分解未生成子问题, query: {}", query);
            return Collections.emptyList();
        }

        log.info("查询分解完成, 原查询: {}, 子问题数: {}, 子问题: {}", query, subQueries.size(), subQueries);

        int subTopK = cfg.getSubQueryTopK();
        int timeoutSeconds = ragProperties.getRetrieve().getFutureTimeoutSeconds();
        double minScore = ragProperties.getRetrieve().getDefaultMinScore();

        // 并行检索每个子问题
        List<CompletableFuture<List<ChunkCandidate>>> futures = subQueries.stream()
                .map(subQuery -> CompletableFuture.supplyAsync(
                        () -> retrieveForSubQuery(subQuery, kbIds, docIds, subTopK, minScore), ragExecutor)
                        .exceptionally(ex -> {
                            log.warn("子问题检索失败: {}, error: {}", subQuery, ex.getMessage());
                            return Collections.emptyList();
                        }))
                .collect(Collectors.toList());

        // 等待所有子问题检索完成
        List<ChunkCandidate> merged = new ArrayList<>();
        for (CompletableFuture<List<ChunkCandidate>> future : futures) {
            try {
                List<ChunkCandidate> hits = future.get(timeoutSeconds, TimeUnit.SECONDS);
                merged.addAll(hits);
            } catch (Exception e) {
                log.warn("子问题检索等待失败: {}", e.getMessage());
            }
        }

        // 合并去重，取最高分
        List<ChunkCandidate> deduped = mergeAndDeduplicate(merged);

        // 按分数排序并截取topK
        deduped.sort(Comparator.comparingDouble(ChunkCandidate::getScore).reversed());
        if (deduped.size() > topK) {
            deduped = deduped.subList(0, topK);
        }

        log.info("查询分解检索完成, 子问题数: {}, 合并前: {}, 合并后: {}", subQueries.size(), merged.size(), deduped.size());
        return deduped;
    }

    /**
     * 判断查询是否需要分解
     * @param query
     * @return
     */
    @Override
    public boolean shouldDecompose(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        if (languageModelFactory == null) {
            return false;
        }
        // 简单查询不分解
        if (query.length() < ragProperties.getQueryDecompose().getMinLength()) {
            return false;
        }
        // 包含多问句、连接词的复杂查询才分解
        return query.contains("和") || query.contains("以及") || query.contains("同时")
                || query.contains("？") && query.indexOf("？") != query.lastIndexOf("？")
                || query.contains("?") && query.indexOf("?") != query.lastIndexOf("?");
    }

    private List<String> decomposeQuery(String query, int maxSubQueries) {
        try {
            String response = languageModelFactory.generateText(
                    ragProperties.getQueryDecompose().getModelCode(),
                    DECOMPOSE_SYSTEM_INSTRUCTION, query);
            return parseSubQueries(response, maxSubQueries);
        } catch (Exception e) {
            log.warn("查询分解LLM调用失败: {}", query, e);
            return Collections.emptyList();
        }
    }

    private List<String> parseSubQueries(String response, int maxSubQueries) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }

        String trimmed = response.trim();

        // 尝试JSON数组解析
        if (trimmed.startsWith("[")) {
            try {
                List<String> subQueries = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                return subQueries.stream()
                        .filter(q -> q != null && !q.isBlank())
                        .limit(maxSubQueries)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.debug("JSON解析失败, 尝试编号列表解析: {}", trimmed);
            }
        }

        // 降级：编号列表解析
        return parseNumberedList(trimmed, maxSubQueries);
    }

    private List<String> parseNumberedList(String text, int maxSubQueries) {
        List<String> result = new ArrayList<>();
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // 去除编号前缀
            String content = trimmed.replaceFirst("^\\d+[.、)）]\\s*", "");
            if (!content.isEmpty() && !content.equals(trimmed)) {
                result.add(content);
            }
            if (result.size() >= maxSubQueries) {
                break;
            }
        }
        return result;
    }

    private List<ChunkCandidate> retrieveForSubQuery(String subQuery, List<String> kbIds,
                                                      List<String> docIds, int topK, double minScore) {
        float[] queryVector = vectorEmbedder.embedQuery(subQuery);
        List<ChunkCandidate> aggregated = new ArrayList<>();
        for (String kbId : kbIds) {
            Filter searchFilter = composeFilter(kbId, docIds);
            List<ChunkCandidate> hits = adaptiveContentRetriever.retrieveWithVector(
                    queryVector, kbId, topK, minScore, searchFilter);
            aggregated.addAll(hits);
        }
        return aggregated;
    }

    private List<ChunkCandidate> mergeAndDeduplicate(List<ChunkCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, ChunkCandidate> dedupMap = new LinkedHashMap<>();
        for (ChunkCandidate candidate : candidates) {
            String key = candidate.getSliceId() != null ? candidate.getSliceId() : candidate.getText();
            ChunkCandidate existing = dedupMap.get(key);
            if (existing == null || candidate.getScore() > existing.getScore()) {
                dedupMap.put(key, candidate);
            }
        }
        return new ArrayList<>(dedupMap.values());
    }

    private Filter composeFilter(String kbId, List<String> docIds) {
        Filter.Builder builder = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("kbId")
                                .setMatch(Match.newBuilder().setKeyword(kbId)))
                        .build());

        if (docIds != null && !docIds.isEmpty()) {
            for (String docId : docIds) {
                builder.addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("docId")
                                .setMatch(Match.newBuilder().setKeyword(docId)))
                        .build());
            }
        }
        return builder.build();
    }
}
