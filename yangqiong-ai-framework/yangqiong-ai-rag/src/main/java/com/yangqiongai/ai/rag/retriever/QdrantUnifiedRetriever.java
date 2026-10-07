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
import com.yangqiongai.ai.rag.embed.VectorEmbedder;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import com.yangqiongai.ai.storage.VectorStorageService;
import io.micrometer.tracing.annotation.NewSpan;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.ValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant统一检索器
 * @author yangqiong
 */
@Service
public class QdrantUnifiedRetriever {

    private static final Logger log = LoggerFactory.getLogger(QdrantUnifiedRetriever.class);

    private static final String RESOURCE_EXHAUSTED = "RESOURCE_EXHAUSTED";

    @Autowired
    private RagProperties ragProperties;

    @Autowired
    private VectorStorageService qdrantVectorService;

    @Autowired
    private VectorEmbedder vectorEmbedder;

    /**
     * Qdrant统一检索
     * @param query
     * @param kbId
     * @param topK
     * @return
     */
    @NewSpan("rag-qdrant-search")
    public List<ChunkCandidate> search(String query, String kbId, int topK) {
        float[] queryVector = vectorEmbedder.embedQuery(query);

        Filter filter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("kbId")
                                .setMatch(io.qdrant.client.grpc.Common.Match.newBuilder()
                                        .setKeyword(kbId)
                                        .build())
                                .build())
                        .build())
                .build();

        return searchWithRetry(kbId, queryVector, topK, filter);
    }

    /**
     * 使用预计算向量执行检索
     * @param queryVector
     * @param kbId
     * @param topK
     * @param filter
     * @return
     */
    @NewSpan("rag-qdrant-search-vector")
    public List<ChunkCandidate> searchWithVector(float[] queryVector, String kbId, int topK, Filter filter) {
        return searchWithRetry(kbId, queryVector, topK, filter);
    }

    /**
     * 带容错重试的检索，检测RESOURCE_EXHAUSTED错误自动降低topK重试
     * @param kbId
     * @param queryVector
     * @param topK
     * @param filter
     * @return
     */
    private List<ChunkCandidate> searchWithRetry(String kbId, float[] queryVector, int topK, Filter filter) {
        int currentTopK = topK;
        while (currentTopK >= ragProperties.getRetrieve().getMinTopK()) {
            try {
                List<ScoredPoint> scoredPoints = qdrantVectorService.search(kbId, queryVector, currentTopK, filter);
                List<ChunkCandidate> candidates = mapToCandidates(scoredPoints);
                log.info("向量检索完成, kbId: {}, topK: {}, 结果数: {}", kbId, currentTopK, candidates.size());
                return candidates;
            } catch (Exception e) {
                if (isResourceExhausted(e)) {
                    int reducedTopK = currentTopK / 2;
                    if (reducedTopK < ragProperties.getRetrieve().getMinTopK()) {
                        log.warn("Qdrant资源耗尽且topK已降至最低, kbId: {}, topK: {}", kbId, currentTopK, e);
                        throw e;
                    }
                    log.warn("Qdrant资源耗尽, 降低topK重试: kbId={}, topK={} -> {}", kbId, currentTopK, reducedTopK, e);
                    currentTopK = reducedTopK;
                } else {
                    throw e;
                }
            }
        }
        return List.of();
    }

    /**
     * 判断异常是否为gRPC RESOURCE_EXHAUSTED错误
     * @param e
     * @return
     */
    private boolean isResourceExhausted(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains(RESOURCE_EXHAUSTED)) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && causeMessage.contains(RESOURCE_EXHAUSTED)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 将ScoredPoint映射为ChunkCandidate
     * @param scoredPoints
     * @return
     */
    private List<ChunkCandidate> mapToCandidates(List<ScoredPoint> scoredPoints) {
        List<ChunkCandidate> candidates = new ArrayList<>();
        for (ScoredPoint point : scoredPoints) {
            ChunkCandidate candidate = new ChunkCandidate();
            candidate.setScore(point.getScore());

            Map<String, Value> payload = point.getPayloadMap();
            if (payload.containsKey("content")) {
                candidate.setText(payload.get("content").getStringValue());
            }
            if (payload.containsKey("docId")) {
                candidate.setDocId(payload.get("docId").getStringValue());
            }
            if (payload.containsKey("kbId")) {
                candidate.setKbId(payload.get("kbId").getStringValue());
            }
            if (payload.containsKey("docName")) {
                String docName = payload.get("docName").getStringValue();
                candidate.setDocName(docName.isEmpty() ? null : docName);
            }
            if (payload.containsKey("sliceId")) {
                candidate.setSliceId(payload.get("sliceId").getStringValue());
            }
            if (payload.containsKey("parentId")) {
                String parentId = payload.get("parentId").getStringValue();
                candidate.setParentId(parentId.isEmpty() ? null : parentId);
            }
            if (payload.containsKey("sliceType")) {
                candidate.setChildHit("child".equals(payload.get("sliceType").getStringValue()));
            }

            candidates.add(candidate);
        }
        return candidates;
    }
}
