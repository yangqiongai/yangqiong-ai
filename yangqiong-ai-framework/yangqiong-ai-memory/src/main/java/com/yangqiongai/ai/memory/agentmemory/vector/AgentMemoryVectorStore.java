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
package com.yangqiongai.ai.memory.agentmemory.vector;

import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.memory.config.ConditionalOnCloudMemoryMode;
import com.yangqiongai.ai.storage.qdrant.QdrantVectorStorage;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.FieldCondition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.Match;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.ValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent运行记忆向量服务
 * @author yangqiong
 */
@Service
@ConditionalOnCloudMemoryMode
@ConditionalOnBean(QdrantVectorStorage.class)
@ConditionalOnProperty(prefix = "ai.memory.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentMemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryVectorStore.class);

    private static final String DEFAULT_DISTANCE = "Cosine";

    private static final String PAYLOAD_KEY_MEMORY_ID = "memoryId";

    private static final String PAYLOAD_KEY_AGENT_CODE = "agentCode";

    private static final String PAYLOAD_KEY_USER_ANCHOR = "userAnchor";

    private static final String PAYLOAD_KEY_MEMORY_TYPE = "memoryType";

    @Autowired
    private QdrantVectorStorage qdrantVectorService;

    @Autowired
    private LanguageModelFactory languageModelFactory;

    @Value("${ai.memory.vector.agent-collection:ai_agent_mem}")
    private String collectionName;

    @Value("${ai.memory.vector.model-code:bge-base-zh-djl}")
    private String embeddingModelCode;

    private volatile boolean collectionInitialized;

    /**
     * 将记忆内容嵌入向量并存储（异常内部吞掉并告警，不影响主流程）
     * @param memoryId
     * @param content
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @return
     */
    public boolean embedAndIndex(Long memoryId, String content, String agentCode, String userAnchor, String memoryType) {
        if (memoryId == null || content == null || content.isBlank()) {
            return false;
        }
        try {
            float[] vector = embed(content);
            if (vector == null || vector.length == 0) {
                return false;
            }
            ensureCollectionExists(vector.length);
            PointStruct point = buildPoint(memoryId, vector, agentCode, userAnchor, memoryType);
            qdrantVectorService.upsertPoints(collectionName, List.of(point));
            return true;
        } catch (Exception e) {
            log.warn("Agent记忆向量索引失败, memoryId={}", memoryId, e);
            return false;
        }
    }

    /**
     * 向量语义检索，返回按相似度排序的记忆ID与相似度对
     * @param queryText
     * @param agentCode
     * @param userAnchor
     * @param topK
     * @return
     */
    public List<ScoredMemoryId> searchScored(String queryText, String agentCode, String userAnchor, int topK) {
        if (queryText == null || queryText.isBlank() || userAnchor == null || userAnchor.isBlank()) {
            return Collections.emptyList();
        }
        try {
            float[] queryVector = embed(queryText);
            if (queryVector == null || queryVector.length == 0) {
                return Collections.emptyList();
            }
            // 集合尚未创建时先建集合，避免查询报 Collection not found（与写路径同一嵌入模型，维度一致）
            ensureCollectionExists(queryVector.length);
            Filter filter = composeFilter(agentCode, userAnchor);
            List<ScoredPoint> points = qdrantVectorService.search(collectionName, queryVector, topK, filter);
            List<ScoredMemoryId> result = new ArrayList<>(points.size());
            for (ScoredPoint p : points) {
                Long id = extractMemoryId(p);
                if (id != null) {
                    result.add(new ScoredMemoryId(id, p.getScore()));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Agent记忆向量检索失败, agentCode={}, query={}", agentCode, truncate(queryText), e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量删除记忆向量
     * @param memoryIds
     * @return 成功返回删除数量，空入参或失败返回-1
     */
    public int deleteByMemoryIds(List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return -1;
        }
        try {
            List<String> pointIds = memoryIds.stream().map(String::valueOf).toList();
            qdrantVectorService.deletePoints(collectionName, pointIds);
            return memoryIds.size();
        } catch (Exception e) {
            log.warn("删除Agent记忆向量失败, size={}", memoryIds.size(), e);
            return -1;
        }
    }

    /**
     * 调用嵌入模型生成向量
     * @param text
     * @return
     */
    private float[] embed(String text) {
        EmbeddingClient client = languageModelFactory.getTextEmbeddingClient(embeddingModelCode);
        return client.embed(text);
    }

    /**
     * 确保Qdrant集合存在
     * @param vectorSize
     */
    private void ensureCollectionExists(int vectorSize) {
        if (collectionInitialized) {
            return;
        }
        synchronized (this) {
            if (collectionInitialized) {
                return;
            }
            if (!qdrantVectorService.collectionExists(collectionName)) {
                qdrantVectorService.createCollection(collectionName, vectorSize, DEFAULT_DISTANCE);
                log.info("创建Agent记忆向量集合: {}, 维度: {}", collectionName, vectorSize);
            }
            collectionInitialized = true;
        }
    }

    /**
     * 构建Qdrant向量点
     * @param memoryId
     * @param vector
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @return
     */
    private PointStruct buildPoint(Long memoryId, float[] vector, String agentCode,
                                   String userAnchor, String memoryType) {
        PointStruct.Builder builder = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setNum(memoryId).build())
                .setVectors(Vectors.newBuilder()
                        .setVector(Vector.newBuilder()
                                .addAllData(toFloatList(vector))
                                .build())
                        .build());
        builder.putPayload(PAYLOAD_KEY_MEMORY_ID, ValueFactory.value(String.valueOf(memoryId)));
        if (agentCode != null) {
            builder.putPayload(PAYLOAD_KEY_AGENT_CODE, ValueFactory.value(agentCode));
        }
        if (userAnchor != null) {
            builder.putPayload(PAYLOAD_KEY_USER_ANCHOR, ValueFactory.value(userAnchor));
        }
        if (memoryType != null) {
            builder.putPayload(PAYLOAD_KEY_MEMORY_TYPE, ValueFactory.value(memoryType));
        }
        return builder.build();
    }

    /**
     * 构建Qdrant检索过滤条件（按agentCode和userAnchor过滤）
     * @param agentCode
     * @param userAnchor
     * @return
     */
    private Filter composeFilter(String agentCode, String userAnchor) {
        Filter.Builder builder = Filter.newBuilder();
        if (agentCode != null && !agentCode.isBlank()) {
            builder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey(PAYLOAD_KEY_AGENT_CODE)
                            .setMatch(Match.newBuilder().setKeyword(agentCode).build())
                            .build())
                    .build());
        }
        if (userAnchor != null && !userAnchor.isBlank()) {
            builder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey(PAYLOAD_KEY_USER_ANCHOR)
                            .setMatch(Match.newBuilder().setKeyword(userAnchor).build())
                            .build())
                    .build());
        }
        return builder.build();
    }

    /**
     * 从ScoredPoint中提取记忆ID（优先从payload获取，兜底从pointId获取）
     * @param point
     * @return
     */
    private Long extractMemoryId(ScoredPoint point) {
        if (point == null) {
            return null;
        }
        if (point.getPayloadMap() != null && point.getPayloadMap().containsKey(PAYLOAD_KEY_MEMORY_ID)) {
            try {
                return Long.parseLong(point.getPayloadMap().get(PAYLOAD_KEY_MEMORY_ID).getStringValue());
            } catch (Exception e) {
                // ignore
            }
        }
        if (point.getId() != null) {
            long num = point.getId().getNum();
            if (num != 0L) {
                return num;
            }
        }
        return null;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }

    /**
     * 带相似度评分的记忆ID
     */
    public static final class ScoredMemoryId {

        private final Long memoryId;

        private final double similarity;

        public ScoredMemoryId(Long memoryId, double similarity) {
            this.memoryId = memoryId;
            this.similarity = similarity;
        }

        public Long getMemoryId() {
            return memoryId;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}
