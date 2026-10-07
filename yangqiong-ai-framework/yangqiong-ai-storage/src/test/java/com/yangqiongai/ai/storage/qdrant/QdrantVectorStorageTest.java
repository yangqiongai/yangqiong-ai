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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.yangqiongai.ai.common.exception.AiException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CollectionOperationResponse;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QdrantVectorStorage 单元测试")
class QdrantVectorStorageTest {

    @Mock
    private QdrantClient qdrantClient;

    private QdrantVectorStorage service;

    @BeforeEach
    void setUp() {
        service = new QdrantVectorStorage(qdrantClient, new QdrantProperties());
    }

    @Test
    @DisplayName("createCollection: 创建成功")
    void createCollection_createsSuccessfully() throws Exception {
        ListenableFuture<CollectionOperationResponse> future = Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
        when(qdrantClient.createCollectionAsync(any())).thenReturn(future);

        try (MockedStatic<Distance> distanceMock = mockStatic(Distance.class)) {
            distanceMock.when(() -> Distance.valueOf("Cosine")).thenReturn(Distance.Cosine);
            service.createCollection("test-collection", 128, "Cosine");
        }

        verify(qdrantClient).createCollectionAsync(any());
    }

    @Test
    @DisplayName("createCollection: 创建失败抛出AiException")
    void createCollection_failure_throwsAiException() throws Exception {
        ListenableFuture<CollectionOperationResponse> failed = Futures.immediateFailedFuture(new RuntimeException("create failed"));
        when(qdrantClient.createCollectionAsync(any())).thenReturn(failed);

        try (MockedStatic<Distance> distanceMock = mockStatic(Distance.class)) {
            distanceMock.when(() -> Distance.valueOf("Cosine")).thenReturn(Distance.Cosine);
            assertThatThrownBy(() -> service.createCollection("test-collection", 128, "Cosine"))
                    .isInstanceOf(AiException.class);
        }
    }

    @Test
    @DisplayName("collectionExists: 集合存在返回true")
    void collectionExists_returnsTrue() throws Exception {
        when(qdrantClient.collectionExistsAsync("test-collection"))
                .thenReturn(Futures.immediateFuture(true));

        boolean result = service.collectionExists("test-collection");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("collectionExists: 集合不存在返回false")
    void collectionExists_returnsFalse() throws Exception {
        when(qdrantClient.collectionExistsAsync("test-collection"))
                .thenReturn(Futures.immediateFuture(false));

        boolean result = service.collectionExists("test-collection");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("collectionExists: 异常抛出AiException")
    void collectionExists_exception_throwsAiException() throws Exception {
        ListenableFuture<Boolean> failed = Futures.immediateFailedFuture(new RuntimeException("connection error"));
        when(qdrantClient.collectionExistsAsync("test-collection")).thenReturn(failed);

        assertThatThrownBy(() -> service.collectionExists("test-collection"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("deleteCollection: 删除成功")
    void deleteCollection_deletesSuccessfully() throws Exception {
        ListenableFuture<CollectionOperationResponse> future = Futures.immediateFuture(CollectionOperationResponse.getDefaultInstance());
        when(qdrantClient.deleteCollectionAsync("test-collection")).thenReturn(future);

        service.deleteCollection("test-collection");

        verify(qdrantClient).deleteCollectionAsync("test-collection");
    }

    @Test
    @DisplayName("deleteCollection: 删除失败抛出AiException")
    void deleteCollection_failure_throwsAiException() throws Exception {
        ListenableFuture<CollectionOperationResponse> failed = Futures.immediateFailedFuture(new RuntimeException("delete failed"));
        when(qdrantClient.deleteCollectionAsync("test-collection")).thenReturn(failed);

        assertThatThrownBy(() -> service.deleteCollection("test-collection"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("search: 返回搜索结果")
    void search_returnsResults() throws Exception {
        ScoredPoint point = ScoredPoint.getDefaultInstance();
        when(qdrantClient.searchAsync(any())).thenReturn(Futures.immediateFuture(List.of(point)));

        float[] queryVector = {0.1f, 0.2f, 0.3f};
        List<ScoredPoint> results = service.search("test-collection", queryVector, 5, null);

        assertThat(results).hasSize(1);
        verify(qdrantClient).searchAsync(any());
    }

    @Test
    @DisplayName("search: 搜索失败抛出AiException")
    void search_failure_throwsAiException() throws Exception {
        ListenableFuture<List<ScoredPoint>> failed = Futures.immediateFailedFuture(new RuntimeException("search failed"));
        when(qdrantClient.searchAsync(any())).thenReturn(failed);

        float[] queryVector = {0.1f, 0.2f, 0.3f};

        assertThatThrownBy(() -> service.search("test-collection", queryVector, 5, null))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("upsertPoints: 插入成功")
    void upsertPoints_insertsSuccessfully() throws Exception {
        ListenableFuture<Points.UpdateResult> future = Futures.immediateFuture(Points.UpdateResult.getDefaultInstance());
        when(qdrantClient.upsertAsync(anyString(), any(List.class))).thenReturn(future);

        service.upsertPoints("test-collection", List.of(PointStruct.getDefaultInstance()));

        verify(qdrantClient).upsertAsync(eq("test-collection"), any(List.class));
    }

    @Test
    @DisplayName("upsertPoints: 插入失败抛出AiException")
    void upsertPoints_failure_throwsAiException() throws Exception {
        ListenableFuture<Points.UpdateResult> failed = Futures.immediateFailedFuture(new RuntimeException("upsert failed"));
        when(qdrantClient.upsertAsync(anyString(), any(List.class))).thenReturn(failed);

        assertThatThrownBy(() -> service.upsertPoints("test-collection", List.of(PointStruct.getDefaultInstance())))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("collectionExists: 调用超时快速失败抛出AiException")
    void collectionExists_timeout_throwsAiException() {
        QdrantProperties timeoutProps = new QdrantProperties();
        timeoutProps.setTimeoutSeconds(1);
        QdrantVectorStorage timeoutService = new QdrantVectorStorage(qdrantClient, timeoutProps);
        // 永不完成的future模拟Qdrant不可达
        when(qdrantClient.collectionExistsAsync("test-collection"))
                .thenReturn(SettableFuture.create());

        assertThatThrownBy(() -> timeoutService.collectionExists("test-collection"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("超时");
    }
}
