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
package com.yangqiongai.ai.rag.retriever;

import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import io.qdrant.client.grpc.Common.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自适应内容检索器
 * @author yangqiong
 */
@Service
public class AdaptiveContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveContentRetriever.class);

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private QdrantUnifiedRetriever qdrantUnifiedRetriever;

    /**
     * 自适应TopK检索
     * @param query
     * @param kbId
     * @param topK
     * @param minScore
     * @return
     */
    public List<ChunkCandidate> retrieve(String query, String kbId, int topK, double minScore) {
        List<ChunkCandidate> candidates = qdrantUnifiedRetriever.search(query, kbId, topK);

        // 过滤低分结果
        List<ChunkCandidate> filtered = candidates.stream()
                .filter(c -> c.getScore() >= minScore)
                .collect(Collectors.toList());

        // 结果不足且minScore兜底，扩大topK重新检索
        if (filtered.size() < topK && minScore > 0) {
            int expandedTopK = topK * ragProperties.getRetrieve().getAdaptiveTopKMultiplier();
            log.info("检索结果不足, 扩大topK从{}到{}, kbId: {}", topK, expandedTopK, kbId);
            List<ChunkCandidate> expandedCandidates = qdrantUnifiedRetriever.search(query, kbId, expandedTopK);
            filtered = expandedCandidates.stream()
                    .filter(c -> c.getScore() >= minScore)
                    .collect(Collectors.toList());
        }

        log.info("自适应检索完成, query: {}, kbId: {}, 结果数: {}", query, kbId, filtered.size());
        return filtered;
    }

    /**
     * 使用预计算向量自适应检索
     * @param queryVector
     * @param kbId
     * @param topK
     * @param minScore
     * @param filter
     * @return
     */
    public List<ChunkCandidate> retrieveWithVector(float[] queryVector, String kbId, int topK,
                                                    double minScore, Filter filter) {
        List<ChunkCandidate> candidates = qdrantUnifiedRetriever.searchWithVector(queryVector, kbId, topK, filter);

        List<ChunkCandidate> filtered = candidates.stream()
                .filter(c -> c.getScore() >= minScore)
                .collect(Collectors.toList());

        if (filtered.size() < topK && minScore > 0) {
            int expandedTopK = topK * ragProperties.getRetrieve().getAdaptiveTopKMultiplier();
            log.info("检索结果不足, 扩大topK从{}到{}, kbId: {}", topK, expandedTopK, kbId);
            List<ChunkCandidate> expandedCandidates = qdrantUnifiedRetriever.searchWithVector(
                    queryVector, kbId, expandedTopK, filter);
            filtered = expandedCandidates.stream()
                    .filter(c -> c.getScore() >= minScore)
                    .collect(Collectors.toList());
        }

        log.info("自适应向量检索完成, kbId: {}, minScore: {}, 结果数: {}", kbId, minScore, filtered.size());
        return filtered;
    }
}
