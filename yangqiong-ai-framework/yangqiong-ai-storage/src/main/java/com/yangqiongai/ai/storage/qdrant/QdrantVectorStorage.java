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
package com.yangqiongai.ai.storage.qdrant;

import com.google.common.util.concurrent.ListenableFuture;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.storage.VectorStorageService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import io.qdrant.client.grpc.Points.DeletePoints;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.PointsIdsList;
import io.qdrant.client.grpc.Points.PointsSelector;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScrollPoints;
import io.qdrant.client.grpc.Points.ScrollResponse;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Common.PointId;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Qdrant向量存储
 * @author yangqiong
 */
@Service
public class QdrantVectorStorage implements VectorStorageService {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStorage.class);

    private final QdrantClient qdrantClient;

    private final int timeoutSeconds;

    /**
     * 统一执行异步调用并施加超时（超时快速失败，由上层降级，避免Qdrant不可达时阻塞业务链路）
     * @param action
     * @param collectionName
     * @param call
     * @return
     */
    private <T> T execute(String action, String collectionName, Supplier<ListenableFuture<T>> call) {
        try {
            return call.get().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, action + "被中断: " + collectionName, e);
        } catch (ExecutionException e) {
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, action + "失败: " + collectionName, e.getCause());
        } catch (TimeoutException e) {
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR,
                    action + "超时(" + timeoutSeconds + "s): " + collectionName, e);
        }
    }

    /**
     * 创建集合
     * @param collectionName
     * @param vectorSize
     * @param distance
     */
    public void createCollection(String collectionName, int vectorSize, String distance) {
        Distance distanceEnum = Distance.valueOf(distance);
        CreateCollection request = CreateCollection.newBuilder()
                .setCollectionName(collectionName)
                .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParams(VectorParams.newBuilder()
                                .setSize(vectorSize)
                                .setDistance(distanceEnum)
                                .build())
                        .build())
                .build();
        execute("创建集合", collectionName, () -> qdrantClient.createCollectionAsync(request));
        log.info("创建Qdrant集合: {}, 向量维度: {}, 距离度量: {}", collectionName, vectorSize, distance);
    }

    /**
     * 判断集合是否存在
     * @param collectionName
     * @return
     */
    public boolean collectionExists(String collectionName) {
        return execute("检查集合存在", collectionName, () -> qdrantClient.collectionExistsAsync(collectionName));
    }

    /**
     * 删除集合
     * @param collectionName
     */
    public void deleteCollection(String collectionName) {
        execute("删除集合", collectionName, () -> qdrantClient.deleteCollectionAsync(collectionName));
        log.info("删除Qdrant集合: {}", collectionName);
    }

    /**
     * 插入或更新向量点
     * @param collectionName
     * @param points
     */
    public void upsertPoints(String collectionName, List<PointStruct> points) {
        execute("插入向量点", collectionName, () -> qdrantClient.upsertAsync(collectionName, points));
    }

    /**
     * 向量搜索
     * @param collectionName
     * @param queryVector
     * @param limit
     * @param filter
     * @return
     */
    public List<ScoredPoint> search(String collectionName, float[] queryVector, int limit, Filter filter) {
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            vectorList.add(v);
        }
        SearchPoints.Builder builder = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(vectorList)
                .setLimit(limit)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());
        if (filter != null) {
            builder.setFilter(filter);
        }
        return execute("向量搜索", collectionName, () -> qdrantClient.searchAsync(builder.build()));
    }

    /**
     * 滚动查询向量点
     * @param collectionName
     * @param filter
     * @param limit
     * @return
     */
    public List<RetrievedPoint> scrollPoints(String collectionName, Filter filter, int limit) {
        ScrollPoints.Builder builder = ScrollPoints.newBuilder()
                .setCollectionName(collectionName)
                .setLimit(limit)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build());
        if (filter != null) {
            builder.setFilter(filter);
        }
        ScrollResponse result = execute("滚动查询", collectionName, () -> qdrantClient.scrollAsync(builder.build()));
        return new ArrayList<>(result.getResultList());
    }

    /**
     * 删除向量点
     * @param collectionName
     * @param pointIds
     */
    public void deletePoints(String collectionName, List<String> pointIds) {
        List<PointId> ids = pointIds.stream()
                .map(id -> PointId.newBuilder().setUuid(id).build())
                .collect(Collectors.toList());
        DeletePoints request = DeletePoints.newBuilder()
                .setCollectionName(collectionName)
                .setPoints(PointsSelector.newBuilder()
                        .setPoints(PointsIdsList.newBuilder()
                                .addAllIds(ids)
                                .build())
                        .build())
                .build();
        execute("删除向量点", collectionName, () -> qdrantClient.deleteAsync(request));
    }

    /**
     * 按过滤条件批量删除向量点
     * @param collectionName
     * @param filter
     */
    public void deleteByFilter(String collectionName, Filter filter) {
        DeletePoints request = DeletePoints.newBuilder()
                .setCollectionName(collectionName)
                .setPoints(PointsSelector.newBuilder()
                        .setFilter(filter)
                        .build())
                .build();
        execute("按条件删除向量点", collectionName, () -> qdrantClient.deleteAsync(request));
    }

    /**
     * 获取集合信息
     * @param collectionName
     * @return
     */
    public CollectionInfo getCollectionInfo(String collectionName) {
        return execute("获取集合信息", collectionName, () -> qdrantClient.getCollectionInfoAsync(collectionName));
    }

    public QdrantVectorStorage(QdrantClient qdrantClient, QdrantProperties properties) {
        this.qdrantClient = qdrantClient;
        this.timeoutSeconds = Math.max(1, properties.getTimeoutSeconds());
    }
}
