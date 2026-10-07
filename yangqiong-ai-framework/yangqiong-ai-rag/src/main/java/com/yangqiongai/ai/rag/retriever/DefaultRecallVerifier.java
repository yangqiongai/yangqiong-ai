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

import com.yangqiongai.ai.rag.model.ChunkCandidate;
import com.yangqiongai.ai.rag.model.RecallRequest;
import com.yangqiongai.ai.rag.model.RecallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 召回验证
 * @author yangqiong
 */
@Service
public class DefaultRecallVerifier implements RecallVerifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultRecallVerifier.class);

    @Autowired
    private AdaptiveContentRetriever adaptiveContentRetriever;

    /**
     * 纯向量召回验证
     * @param request
     * @return
     */
    @Override
    public List<RecallResult> verifyRecall(RecallRequest request) {
        List<RecallResult> verificationOutputs = new ArrayList<>();
        int topK = request.getTopK() > 0 ? request.getTopK() : 5;
        double threshold = request.getMinScore();
        Set<String> expectedDocIds = request.getExpectedRelevantDocIds();

        for (String queryText : request.getQueries()) {
            List<ChunkCandidate> aggregatedHits = new ArrayList<>();
            Set<String> distinctDocIds = new HashSet<>();

            for (String knowledgeBaseId : request.getKbIds()) {
                List<ChunkCandidate> matchedChunks = adaptiveContentRetriever.retrieve(
                        queryText, knowledgeBaseId, topK, threshold);
                aggregatedHits.addAll(matchedChunks);

                for (ChunkCandidate chunk : matchedChunks) {
                    if (chunk.getDocId() != null) {
                        distinctDocIds.add(chunk.getDocId());
                    }
                }
            }

            RecallResult output = new RecallResult();
            output.setQuery(queryText);
            output.setHitCount(aggregatedHits.size());
            output.setHitSlices(aggregatedHits);

            // 召回率计算
            double recallRate = calculateRecallRate(aggregatedHits, distinctDocIds, expectedDocIds, topK);
            output.setRecallRate(recallRate);

            // MRR计算
            double mrr = calculateMrr(aggregatedHits, expectedDocIds);
            output.setMrr(mrr);

            double coverage = calculateCoverage(aggregatedHits, distinctDocIds);
            output.setCoverageRate(coverage);

            verificationOutputs.add(output);
        }

        log.info("向量召回验证完成, 查询数: {}, 知识库数: {}, 预设相关文档数: {}",
                request.getQueries().size(), request.getKbIds().size(),
                expectedDocIds != null ? expectedDocIds.size() : 0);
        return verificationOutputs;
    }

    private double calculateRecallRate(List<ChunkCandidate> hits, Set<String> hitDocIds,
                                        Set<String> expectedDocIds, int topK) {
        if (expectedDocIds != null && !expectedDocIds.isEmpty()) {
            long hitRelevantCount = hitDocIds.stream()
                    .filter(expectedDocIds::contains)
                    .count();
            return (double) hitRelevantCount / expectedDocIds.size();
        }
        if (topK <= 0) {
            return hits.isEmpty() ? 0.0 : 1.0;
        }
        return Math.min((double) hits.size() / topK, 1.0);
    }

    private double calculateMrr(List<ChunkCandidate> hits, Set<String> expectedDocIds) {
        if (hits == null || hits.isEmpty()) {
            return 0.0;
        }
        if (expectedDocIds == null || expectedDocIds.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < hits.size(); i++) {
            String docId = hits.get(i).getDocId();
            if (docId != null && expectedDocIds.contains(docId)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private double calculateCoverage(List<ChunkCandidate> matchedChunks, Set<String> distinctDocIds) {
        if (matchedChunks.isEmpty() || distinctDocIds.isEmpty()) {
            return 0.0;
        }
        long childHitCount = matchedChunks.stream()
                .filter(ChunkCandidate::isChildHit)
                .count();
        if (childHitCount == 0) {
            return 1.0;
        }
        return (double) distinctDocIds.size() / (distinctDocIds.size() + childHitCount);
    }
}
