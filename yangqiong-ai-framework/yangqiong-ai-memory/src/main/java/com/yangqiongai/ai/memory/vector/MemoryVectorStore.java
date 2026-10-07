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
package com.yangqiongai.ai.memory.vector;

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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记忆向量服务
 * @author yangqiong
 */
@Service
@ConditionalOnCloudMemoryMode
@ConditionalOnBean(QdrantVectorStorage.class)
@ConditionalOnProperty(prefix = "ai.memory.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryVectorStore.class);

    private static final String DEFAULT_DISTANCE = "Cosine";

    private static final String PAYLOAD_KEY_MEMORY_ID = "memoryId";

    private static final String PAYLOAD_KEY_USER_ID = "userId";

    private static final String PAYLOAD_KEY_MEMORY_TYPE = "memoryType";

    @Autowired
    private QdrantVectorStorage qdrantVectorService;

    @Autowired
    private LanguageModelFactory languageModelFactory;

    @Value("${ai.memory.vector.collection:ai_mem}")
    private String collectionName;

    @Value("${ai.memory.vector.model-code:bge-base-zh-djl}")
    private String embeddingModelCode;

    private volatile boolean collectionInitialized;

    /**
     * 将记忆内容嵌入向量并存储（双写：Qdrant + DB embedding 兜底由调用方写入）
     * @param memoryId
     * @param content
     * @param userId
     * @param memoryType
     * @return
     */
    public boolean embedAndIndex(Long memoryId, String content, String userId, String memoryType) {
        if (memoryId == null || content == null || content.isBlank()) {
            return false;
        }
        try {
            float[] vector = embed(content);
            if (vector == null || vector.length == 0) {
                return false;
            }
            ensureCollectionExists(vector.length);
            PointStruct point = buildPoint(memoryId, vector, userId, memoryType);
            qdrantVectorService.upsertPoints(collectionName, List.of(point));
            return true;
        } catch (Exception e) {
            log.warn("记忆向量索引失败, memoryId={}", memoryId, e);
            return false;
        }
    }

    /**
     * 向量语义检索，返回按相似度排序的记忆ID列表
     * @param queryText
     * @param userId
     * @param memoryType
     * @param topK
     * @return
     */
    public List<Long> searchByVector(String queryText, String userId, String memoryType, int topK) {
        List<ScoredMemoryId> scored = searchScored(queryText, userId, memoryType, topK);
        List<Long> ids = new ArrayList<>(scored.size());
        for (ScoredMemoryId s : scored) {
            ids.add(s.getMemoryId());
        }
        return ids;
    }

    /**
     * 向量语义检索，返回按相似度排序的记忆ID与相似度对
     * @param queryText
     * @param userId
     * @param memoryType
     * @param topK
     * @return
     */
    public List<ScoredMemoryId> searchScored(String queryText, String userId, String memoryType, int topK) {
        if (queryText == null || queryText.isBlank() || userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            float[] queryVector = embed(queryText);
            if (queryVector == null || queryVector.length == 0) {
                return Collections.emptyList();
            }
            // 集合尚未创建时先建集合，避免查询报 Collection not found（与写路径同一嵌入模型，维度一致）
            ensureCollectionExists(queryVector.length);
            Filter filter = composeFilter(userId, memoryType);
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
            log.warn("向量检索失败, userId={}, query={}", userId, truncate(queryText), e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除记忆向量
     * @param memoryId
     */
    public void deleteByMemoryId(Long memoryId) {
        if (memoryId == null) {
            return;
        }
        try {
            qdrantVectorService.deletePoints(collectionName, List.of(String.valueOf(memoryId)));
        } catch (Exception e) {
            log.warn("删除记忆向量失败, memoryId={}", memoryId, e);
        }
    }

    /**
     * 按用户ID批量删除所有记忆向量，先滚动统计数量再按filter删除
     * @param userId
     * @return
     */
    public int deleteByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return -1;
        }
        try {
            Filter filter = composeFilter(userId, null);
            int count = countPointsByFilter(filter);
            if (count <= 0) {
                return 0;
            }
            qdrantVectorService.deleteByFilter(collectionName, filter);
            log.info("按用户删除记忆向量完成: userId={}, 删除数量={}", userId, count);
            return count;
        } catch (Exception e) {
            log.warn("按用户删除记忆向量失败: userId={}", userId, e);
            return -1;
        }
    }

    /**
     * 通过滚动查询统计满足过滤条件的点数量
     * @param filter
     * @return
     */
    private int countPointsByFilter(Filter filter) {
        try {
            List<io.qdrant.client.grpc.Points.RetrievedPoint> points =
                    qdrantVectorService.scrollPoints(collectionName, filter, 1000);
            return points == null ? 0 : points.size();
        } catch (Exception e) {
            log.warn("统计记忆向量数量失败", e);
            return 0;
        }
    }

    /**
     * 计算两段文本的余弦相似度
     * @param textA
     * @param textB
     * @return
     */
    public double cosineSimilarity(String textA, String textB) {
        if (textA == null || textA.isBlank() || textB == null || textB.isBlank()) {
            return 0.0;
        }
        try {
            float[] vecA = embed(textA);
            float[] vecB = embed(textB);
            if (vecA == null || vecB == null || vecA.length != vecB.length) {
                return 0.0;
            }
            return computeCosine(vecA, vecB);
        } catch (Exception e) {
            log.warn("计算余弦相似度失败", e);
            return 0.0;
        }
    }

    /**
     * 将 float[] 向量序列化为 byte[]（DB 兜底存储用）
     * @param vector
     * @return
     */
    public static byte[] serializeVector(float[] vector) {
        if (vector == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * vector.length);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
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
                log.info("创建记忆向量集合: {}, 维度: {}", collectionName, vectorSize);
            }
            collectionInitialized = true;
        }
    }

    /**
     * 构建Qdrant向量点
     * @param memoryId
     * @param vector
     * @param userId
     * @param memoryType
     * @return
     */
    private PointStruct buildPoint(Long memoryId, float[] vector, String userId, String memoryType) {
        PointStruct.Builder builder = PointStruct.newBuilder()
                .setId(PointId.newBuilder().setNum(memoryId).build())
                .setVectors(Vectors.newBuilder()
                        .setVector(Vector.newBuilder()
                                .addAllData(toFloatList(vector))
                                .build())
                        .build());
        builder.putPayload(PAYLOAD_KEY_MEMORY_ID, ValueFactory.value(String.valueOf(memoryId)));
        if (userId != null) {
            builder.putPayload(PAYLOAD_KEY_USER_ID, ValueFactory.value(userId));
        }
        if (memoryType != null) {
            builder.putPayload(PAYLOAD_KEY_MEMORY_TYPE, ValueFactory.value(memoryType));
        }
        return builder.build();
    }

    /**
     * 构建Qdrant检索过滤条件（按userId和memoryType过滤）
     * @param userId
     * @param memoryType
     * @return
     */
    private Filter composeFilter(String userId, String memoryType) {
        Filter.Builder builder = Filter.newBuilder();
        if (userId != null && !userId.isBlank()) {
            builder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey(PAYLOAD_KEY_USER_ID)
                            .setMatch(Match.newBuilder().setKeyword(userId).build())
                            .build())
                    .build());
        }
        if (memoryType != null && !memoryType.isBlank()) {
            builder.addMust(Condition.newBuilder()
                    .setField(FieldCondition.newBuilder()
                            .setKey(PAYLOAD_KEY_MEMORY_TYPE)
                            .setMatch(Match.newBuilder().setKeyword(memoryType).build())
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
        // 优先从payload中提取memoryId（最可靠）
        if (point.getPayloadMap() != null && point.getPayloadMap().containsKey(PAYLOAD_KEY_MEMORY_ID)) {
            try {
                return Long.parseLong(point.getPayloadMap().get(PAYLOAD_KEY_MEMORY_ID).getStringValue());
            } catch (Exception e) {
                // ignore
            }
        }
        // 兜底从pointId的num字段提取
        if (point.getId() != null) {
            long num = point.getId().getNum();
            if (num != 0L) {
                return num;
            }
        }
        return null;
    }

    /**
     * 计算两个向量的余弦相似度
     * @param vecA
     * @param vecB
     * @return
     */
    private double computeCosine(float[] vecA, float[] vecB) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vecA.length; i++) {
            dot += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
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
