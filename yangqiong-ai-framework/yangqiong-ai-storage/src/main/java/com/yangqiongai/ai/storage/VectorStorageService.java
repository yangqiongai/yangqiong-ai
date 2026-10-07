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
package com.yangqiongai.ai.storage;

import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import io.qdrant.client.grpc.Points.ScoredPoint;

import java.util.List;

/**
 * 向量存储
 * @author yangqiong
 */
public interface VectorStorageService {

    /**
     * 创建集合
     * @param collectionName
     * @param vectorSize
     * @param distance
     */
    void createCollection(String collectionName, int vectorSize, String distance);

    /**
     * 判断集合是否存在
     * @param collectionName
     * @return
     */
    boolean collectionExists(String collectionName);

    /**
     * 删除集合
     * @param collectionName
     */
    void deleteCollection(String collectionName);

    /**
     * 插入或更新向量点
     * @param collectionName
     * @param points
     */
    void upsertPoints(String collectionName, List<PointStruct> points);

    /**
     * 向量搜索
     * @param collectionName
     * @param queryVector
     * @param limit
     * @param filter
     * @return
     */
    List<ScoredPoint> search(String collectionName, float[] queryVector, int limit, Filter filter);

    /**
     * 滚动查询向量点
     * @param collectionName
     * @param filter
     * @param limit
     * @return
     */
    List<RetrievedPoint> scrollPoints(String collectionName, Filter filter, int limit);

    /**
     * 删除向量点
     * @param collectionName
     * @param pointIds
     */
    void deletePoints(String collectionName, List<String> pointIds);

    /**
     * 按过滤条件批量删除向量点
     * @param collectionName
     * @param filter
     */
    void deleteByFilter(String collectionName, Filter filter);

    /**
     * 获取集合信息
     * @param collectionName
     * @return
     */
    CollectionInfo getCollectionInfo(String collectionName);
}
