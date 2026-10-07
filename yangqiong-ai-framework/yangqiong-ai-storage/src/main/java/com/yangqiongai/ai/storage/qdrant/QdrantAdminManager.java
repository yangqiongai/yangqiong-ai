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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 向量存储管理
 * @author yangqiong
 */
@Service
public class QdrantAdminManager {

    private static final Logger log = LoggerFactory.getLogger(QdrantAdminManager.class);

    @Autowired
    private QdrantClient qdrantClient;

    /**
     * 查询集合列表
     * @return
     */
    public List<Map<String, Object>> listCollections() {
        try {
            List<String> collectionNames = qdrantClient.listCollectionsAsync().get();
            return collectionNames.stream().map(name -> {
                Map<String, Object> map = new HashMap<>();
                map.put("collectionName", name);
                try {
                    CollectionInfo info = qdrantClient.getCollectionInfoAsync(name).get();
                    map.put("vectorCount", info.getPointsCount());
                    map.put("status", info.getStatus().name());
                } catch (Exception e) {
                    log.warn("获取集合信息失败: {}", name, e);
                    map.put("status", "UNKNOWN");
                }
                return map;
            }).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, "查询集合列表被中断", e);
        } catch (ExecutionException e) {
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, "查询集合列表失败", e.getCause());
        }
    }

    /**
     * 查询集合详情
     * @param collectionName
     * @return
     */
    public Map<String, Object> getCollectionDetail(String collectionName) {
        try {
            CollectionInfo info = qdrantClient.getCollectionInfoAsync(collectionName).get();
            Map<String, Object> map = new HashMap<>();
            map.put("collectionName", collectionName);
            map.put("status", info.getStatus().name());
            map.put("pointsCount", info.getPointsCount());
            map.put("vectorsCount", info.getIndexedVectorsCount());
            map.put("segmentsCount", info.getSegmentsCount());
            return map;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, "查询集合详情被中断: " + collectionName, e);
        } catch (ExecutionException e) {
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, "查询集合详情失败: " + collectionName, e.getCause());
        }
    }

    /**
     * 删除集合
     * @param collectionName
     */
    public void deleteCollection(String collectionName) {
        try {
            qdrantClient.deleteCollectionAsync(collectionName).get();
            log.info("删除Qdrant集合: {}", collectionName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, "删除集合被中断: " + collectionName, e);
        } catch (ExecutionException e) {
            throw new AiException(AiErrorCode.STORAGE_QDRANT_ERROR, "删除集合失败: " + collectionName, e.getCause());
        }
    }
}
