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
package com.yangqiongai.ai.rag.embed;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.metrics.RagMetrics;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.storage.VectorStorageService;
import com.yangqiongai.ai.rag.index.VersionPayloadBuilder;
import io.micrometer.core.annotation.Timed;
import io.micrometer.tracing.annotation.NewSpan;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.ValueFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * 向量嵌入
 * @author yangqiong
 */
@Service
public class DefaultVectorEmbedder implements VectorEmbedder {

    private static final Logger log = LoggerFactory.getLogger(DefaultVectorEmbedder.class);

    @Autowired
    private RagProperties ragProperties;

    private BlockingQueue<FailedEmbedRecord> failedEmbedQueue;

    @PostConstruct
    private void initFailedEmbedQueue() {
        failedEmbedQueue = new ArrayBlockingQueue<>(ragProperties.getEmbed().getFailedQueueCapacity());
    }

    @Autowired
    private VectorStorageService qdrantVectorService;

    @Autowired
    private VersionPayloadBuilder versionPayloadBuilder;

    @Autowired
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private RagMetrics ragMetrics;

    /**
     * 集合名称解析器
     */
    @Autowired
    private CollectionNameResolver collectionNameResolver;

    @Value("${ai.rag.embed.model-code}")
    private String embeddingModelCode;

    /**
     * 查询嵌入缓存（LRU，避免相同查询重复执行模型推理）
     */
    private final Map<String, float[]> queryEmbeddingCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > 500;
                }
            });

    /**
     * 批量向量化并入库到Qdrant
     * @param slices
     * @param kbId
     */
    @Override
    @NewSpan("rag-embed-index")
    public void embedAndIndex(List<SliceRecord> slices, String kbId) {
        if (slices == null || slices.isEmpty()) {
            log.warn("切片列表为空, 跳过向量化, kbId: {}", kbId);
            return;
        }

        int vectorSize = resolveVectorSize();
        ensureCollectionExists(kbId, vectorSize);

        List<List<SliceRecord>> batches = partitionBatches(slices, ragProperties.getEmbed().getBatchSize());
        int successCount = 0;
        int skipCount = 0;

        for (List<SliceRecord> batch : batches) {
            try {
                List<PointStruct> points = embedAndSave(batch, kbId, vectorSize);
                if (!points.isEmpty()) {
                    qdrantVectorService.upsertPoints(collectionNameResolver.resolve(kbId), points);
                    successCount += points.size();
                } else {
                    skipCount += batch.size();
                }
            } catch (AiException e) {
                log.error("批量向量化入库失败, kbId: {}, 批次大小: {}", kbId, batch.size(), e);
                batch.forEach(slice -> offerToFailedQueue(
                        new FailedEmbedRecord(slice, kbId)));
            }
        }

        log.info("向量化入库完成, kbId: {}, 成功: {}, 跳过: {}, 待补偿: {}",
                kbId, successCount, skipCount, failedEmbedQueue.size());
        if(successCount == 0){
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED, "向量化入库失败, kbId: " + kbId);
        }
    }

    /**
     * 批量向量化并保存
     * @param chunks
     * @param kbId
     * @param vectorSize
     * @return
     */
    public List<PointStruct> embedAndSave(List<SliceRecord> chunks, String kbId, int vectorSize) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> texts = chunks.stream()
                .map(SliceRecord::getContent)
                .collect(Collectors.toList());

        List<float[]> vectors = embedBatch(texts, vectorSize);

        List<PointStruct> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            SliceRecord slice = chunks.get(i);
            float[] vector = vectors.get(i);

            if (!validateEmbedding(vector, vectorSize)) {
                log.warn("向量校验不通过, 跳过切片, sliceId: {}", slice.getSliceId());
                continue;
            }

            Map<String, Object> payload = versionPayloadBuilder.buildPayload(slice, kbId, slice.getVersion());

            PointStruct.Builder pointBuilder = PointStruct.newBuilder()
                    .setId(Common.PointId.newBuilder()
                            .setUuid(slice.getSliceId() != null ? slice.getSliceId() : UUID.randomUUID().toString())
                            .build())
                    .setVectors(Vectors.newBuilder()
                            .setVector(Points.Vector.newBuilder()
                                    .addAllData(toFloatList(vector))
                                    .build())
                            .build());

            payload.forEach((key, val) -> {
                pointBuilder.putPayload(key, ValueFactory.value(String.valueOf(val)));
            });

            points.add(pointBuilder.build());
        }

        return points;
    }

    /**
     * 批量嵌入（带重试）
     * @param texts
     * @return
     */
    public List<float[]> embedBatch(List<String> texts) {
        return embedBatch(texts, ragProperties.getEmbed().getDefaultVectorSize());
    }

    /**
     * 批量嵌入（带重试和指定维度）
     * @param texts
     * @param targetSize
     * @return
     */
    public List<float[]> embedBatch(List<String> texts, int targetSize) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        // 尝试批量调用嵌入接口
        try {
            List<float[]> batchResult = invokeBatchEmbedding(texts);
            List<float[]> adjusted = new ArrayList<>(batchResult.size());
            for (float[] vector : batchResult) {
                float[] adj = adjustVectorDimension(vector, targetSize);
                adjusted.add(adj);
            }
            return adjusted;
        } catch (Exception e) {
            log.warn("批量嵌入失败, 降级为逐条调用", e);
        }

        // 降级为逐条调用
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            float[] vector = embedWithRetry(text, targetSize);
            results.add(vector);
        }
        return results;
    }

    /**
     * 带重试的嵌入
     * @param text
     * @param targetSize
     * @return
     */
    private float[] embedWithRetry(String text, int targetSize) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= ragProperties.getEmbed().getMaxRetryAttempts(); attempt++) {
            try {
                float[] vector = invokeEmbeddingModel(text);
                float[] adjusted = adjustVectorDimension(vector, targetSize);
                if (validateEmbedding(adjusted, targetSize)) {
                    return adjusted;
                }
                log.warn("向量校验失败, 第{}次尝试", attempt);
            } catch (Exception e) {
                lastException = e;
                log.warn("嵌入请求失败, 第{}/{}次尝试", attempt, ragProperties.getEmbed().getMaxRetryAttempts());
                if (attempt < ragProperties.getEmbed().getMaxRetryAttempts()) {
                    sleepWithBackoff(attempt);
                }
            }
        }
        log.error("嵌入重试耗尽, text: {}",
                text.substring(0, Math.min(50, text.length())));
        throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                "嵌入重试耗尽(" + ragProperties.getEmbed().getMaxRetryAttempts() + "次): " + lastException.getMessage(), lastException);
    }

    /**
     * 调用嵌入模型生成向量
     * @param text
     * @return
     */
    private float[] invokeEmbeddingModel(String text) {
        if (embeddingModelCode == null || embeddingModelCode.isEmpty()) {
            log.warn("未配置嵌入模型编码, 无法调用嵌入模型");
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED, "未配置嵌入模型编码");
        }
        EmbeddingClient client = languageModelFactory.getTextEmbeddingClient(embeddingModelCode);
        return client.embed(text);
    }

    /**
     * 批量调用嵌入模型生成向量
     * @param texts
     * @return
     */
    private List<float[]> invokeBatchEmbedding(List<String> texts) {
        if (embeddingModelCode == null || embeddingModelCode.isEmpty()) {
            log.warn("未配置嵌入模型编码, 无法调用嵌入模型");
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED, "未配置嵌入模型编码");
        }
        EmbeddingClient client = languageModelFactory.getTextEmbeddingClient(embeddingModelCode);
        return client.embedBatch(texts);
    }

    /**
     * 校验向量维度，维度不匹配时抛异常而非截断或填充
     * @param vector
     * @param targetSize
     * @return
     */
    private float[] adjustVectorDimension(float[] vector, int targetSize) {
        if (vector.length == targetSize) {
            return vector;
        }
        throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                "向量维度不匹配, 期望: " + targetSize + ", 实际: " + vector.length
                        + ", 请检查嵌入模型配置与集合维度是否一致");
    }

    /**
     * 校验向量维度和质量
     * @param vector
     * @param expectedSize
     * @return
     */
    public boolean validateEmbedding(float[] vector, int expectedSize) {
        if (vector == null || vector.length == 0) {
            return false;
        }
        if (vector.length != expectedSize) {
            log.warn("向量维度不匹配, 期望: {}, 实际: {}", expectedSize, vector.length);
            return false;
        }
        boolean allZero = true;
        for (float v : vector) {
            if (v != 0.0f) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            log.warn("向量为全零, 校验不通过");
            return false;
        }
        for (float v : vector) {
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                log.warn("向量包含NaN或Infinity, 校验不通过");
                return false;
            }
        }
        return true;
    }

    /**
     * 校验向量维度和质量
     * @param vector
     * @return
     */
    public boolean validateEmbedding(float[] vector) {
        return validateEmbedding(vector, ragProperties.getEmbed().getDefaultVectorSize());
    }

    /**
     * 验证向量化结果
     * @param slices
     */
    @Override
    public void validateEmbedding(List<SliceRecord> slices) {
        if (slices == null || slices.isEmpty()) {
            return;
        }

        String kbId = slices.get(0).getKbId();
        try {
            var collectionInfo = qdrantVectorService.getCollectionInfo(collectionNameResolver.resolve(kbId));
            long pointCount = collectionInfo.getPointsCount();
            log.info("向量化验证, kbId: {}, Qdrant中的点数: {}, 期望切片数: {}",
                    kbId, pointCount, slices.size());

            if (pointCount < slices.size()) {
                log.warn("向量化验证不通过, Qdrant中的点数少于期望切片数, kbId: {}", kbId);
            }
        } catch (AiException e) {
            log.error("向量化验证失败, kbId: {}", kbId, e);
        }
    }

    /**
     * 查询文本向量化（相同查询命中LRU缓存，返回向量副本防止调用方修改缓存内容）
     * @param text
     * @return
     */
    @Override
    public float[] embedQuery(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED, "查询文本不能为空");
        }
        String cacheKey = text.trim();
        float[] cached = queryEmbeddingCache.get(cacheKey);
        if (cached != null) {
            log.debug("查询嵌入缓存命中, text: {}", cacheKey);
            return Arrays.copyOf(cached, cached.length);
        }
        int vectorSize = resolveVectorSize();
        List<float[]> vectors = embedBatch(List.of(text), vectorSize);
        if (vectors.isEmpty() || vectors.get(0) == null) {
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED, "查询向量化失败, query: " + text);
        }
        float[] result = vectors.get(0);
        queryEmbeddingCache.put(cacheKey, result);
        return Arrays.copyOf(result, result.length);
    }

    /**
     * 重试失败的嵌入记录
     * @return
     */
    @Scheduled(fixedDelay = 300000)
    public int retryFailedEmbeddings() {
        List<FailedEmbedRecord> retryList = new ArrayList<>();
        failedEmbedQueue.drainTo(retryList);

        if (retryList.isEmpty()) {
            return 0;
        }
        updateQueueSizeMetric();

        log.info("开始补偿重试, 待重试数: {}", retryList.size());
        int vectorSize = resolveVectorSize();
        int retried = 0;
        for (FailedEmbedRecord record : retryList) {
            try {
                List<PointStruct> points = embedAndSave(
                        List.of(record.slice), record.kbId, vectorSize);
                if (!points.isEmpty()) {
                    qdrantVectorService.upsertPoints(collectionNameResolver.resolve(record.kbId), points);
                    retried++;
                }
            } catch (Exception e) {
                log.error("补偿重试仍失败, sliceId: {}", record.slice.getSliceId(), e);
                offerToFailedQueue(record);
            }
        }
        log.info("补偿重试完成, 成功: {}, 仍失败: {}", retried, failedEmbedQueue.size());
        updateQueueSizeMetric();
        return retried;
    }

    /**
     * 将失败记录加入队列，队列满时丢弃最旧记录并告警
     * @param record
     */
    private void offerToFailedQueue(FailedEmbedRecord record) {
        if (!failedEmbedQueue.offer(record)) {
            FailedEmbedRecord dropped = failedEmbedQueue.poll();
            if (dropped != null) {
                log.warn("失败队列已满, 丢弃最旧记录, sliceId: {}", dropped.slice.getSliceId());
            }
            failedEmbedQueue.offer(record);
        }
        if (failedEmbedQueue.size() > ragProperties.getEmbed().getFailedQueueCapacity() * 0.8) {
            log.warn("失败队列接近上限, 当前大小: {}/{}", failedEmbedQueue.size(), ragProperties.getEmbed().getFailedQueueCapacity());
        }
        updateQueueSizeMetric();
    }

    /**
     * 更新队列大小指标
     */
    private void updateQueueSizeMetric() {
        if (ragMetrics != null) {
            ragMetrics.updateEmbedQueueSize(failedEmbedQueue.size());
        }
    }

    /**
     * 解析向量维度（模型未配置或维度缺失时直接报错，拒绝以默认维度建集合导致维度不匹配）
     * @return
     */
    private int resolveVectorSize() {
        if (embeddingModelCode == null || embeddingModelCode.isEmpty()) {
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                    "未配置嵌入模型编码(ai.rag.embed.model-code), 无法确定向量维度");
        }
        try {
            EmbeddingClient client = languageModelFactory.getTextEmbeddingClient(embeddingModelCode);
            int dims = client.getDimensions();
            if (dims <= 0) {
                throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                        "嵌入模型 " + embeddingModelCode + " 未配置有效维度(dimensions), 请在模型配置中补充后重试");
            }
            return dims;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                    "获取嵌入模型维度失败: " + embeddingModelCode, e);
        }
    }

    private void ensureCollectionExists(String kbId, int vectorSize) {
        String collectionName = collectionNameResolver.resolve(kbId);
        if (!qdrantVectorService.collectionExists(collectionName)) {
            qdrantVectorService.createCollection(collectionName, vectorSize, "Cosine");
            log.info("创建Qdrant集合: {}, kbId: {}, 向量维度: {}", collectionName, kbId, vectorSize);
        }
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    /**
     * 分区批次
     * @param items
     * @param batchSize
     * @return
     */
    private <T> List<List<T>> partitionBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            batches.add(items.subList(i, Math.min(i + batchSize, items.size())));
        }
        return batches;
    }

    /**
     * 指数退避等待
     * @param attempt
     */
    private void sleepWithBackoff(int attempt) {
        long delay = ragProperties.getEmbed().getRetryBaseDelayMs() * (1L << (attempt - 1));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 失败嵌入记录
     */
    private static class FailedEmbedRecord {

        final SliceRecord slice;

        final String kbId;

        FailedEmbedRecord(SliceRecord slice, String kbId) {
            this.slice = slice;
            this.kbId = kbId;
        }
    }
}
