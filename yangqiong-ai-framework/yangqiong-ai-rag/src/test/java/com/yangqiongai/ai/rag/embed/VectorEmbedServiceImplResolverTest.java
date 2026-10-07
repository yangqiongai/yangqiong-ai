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

import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultCollectionNameResolver;
import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.index.VersionPayloadBuilder;
import com.yangqiongai.ai.storage.qdrant.QdrantVectorStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultVectorEmbedder 集合名称解析器集成测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultVectorEmbedder 集合名称解析器测试")
class DefaultVectorEmbedderResolverTest {

    @Mock
    private QdrantVectorStorage qdrantVectorService;

    @Mock
    private VersionPayloadBuilder versionPayloadBuilder;

    @Mock
    private LanguageModelFactory languageModelFactory;

    @Mock
    private EmbeddingClient embeddingClient;

    @Captor
    private ArgumentCaptor<String> collectionNameCaptor;

    private DefaultVectorEmbedder service;

    @BeforeEach
    void setUp() {
        service = new DefaultVectorEmbedder();
        ReflectionTestUtils.setField(service, "qdrantVectorService", qdrantVectorService);
        ReflectionTestUtils.setField(service, "versionPayloadBuilder", versionPayloadBuilder);
        ReflectionTestUtils.setField(service, "languageModelFactory", languageModelFactory);
        ReflectionTestUtils.setField(service, "ragProperties", new RagProperties());
        ReflectionTestUtils.setField(service, "embeddingModelCode", "test-model");
        ReflectionTestUtils.setField(service, "ragMetrics", null);
        ReflectionTestUtils.setField(service, "failedEmbedQueue", new ArrayBlockingQueue<>(100));
    }

    @Test
    @DisplayName("默认解析器: ensureCollectionExists 使用原始 kbId 作为集合名")
    void ensureCollectionExists_usesOriginalKbId_withDefaultResolver() {
        ReflectionTestUtils.setField(service, "collectionNameResolver", new DefaultCollectionNameResolver());
        when(qdrantVectorService.collectionExists(anyString())).thenReturn(true);
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(1024);

        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setKbId("kb-001");
        slice.setContent("测试内容");
        slice.setVersion("v1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", "kb-001");
        when(versionPayloadBuilder.buildPayload(any(), anyString(), anyString())).thenReturn(payload);
        float[] vector = new float[1024];
        vector[0] = 0.5f;
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(vector));

        service.embedAndIndex(List.of(slice), "kb-001");

        verify(qdrantVectorService).collectionExists("kb-001");
    }

    @Test
    @DisplayName("作用域解析器: ensureCollectionExists 使用 scopeId_kbId 作为集合名")
    void ensureCollectionExists_usesTenantPrefixedName_withTenantResolver() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-001_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        when(qdrantVectorService.collectionExists(anyString())).thenReturn(true);
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(1024);

        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setKbId("kb-001");
        slice.setContent("测试内容");
        slice.setVersion("v1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", "kb-001");
        when(versionPayloadBuilder.buildPayload(any(), anyString(), anyString())).thenReturn(payload);
        float[] vector = new float[1024];
        vector[0] = 0.5f;
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(vector));

        service.embedAndIndex(List.of(slice), "kb-001");

        verify(qdrantVectorService).collectionExists("tenant-001_kb-001");
    }

    @Test
    @DisplayName("作用域解析器: upsertPoints 使用解析后的集合名")
    void upsertPoints_usesResolvedCollectionName_withTenantResolver() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-001_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        when(qdrantVectorService.collectionExists(anyString())).thenReturn(true);
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(1024);

        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setKbId("kb-001");
        slice.setContent("测试内容");
        slice.setVersion("v1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", "kb-001");
        when(versionPayloadBuilder.buildPayload(any(), anyString(), anyString())).thenReturn(payload);
        float[] vector = new float[1024];
        vector[0] = 0.5f;
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(vector));

        service.embedAndIndex(List.of(slice), "kb-001");

        verify(qdrantVectorService).upsertPoints(collectionNameCaptor.capture(), any());
        assertThat(collectionNameCaptor.getValue()).isEqualTo("tenant-001_kb-001");
    }

    @Test
    @DisplayName("默认解析器: upsertPoints 使用原始 kbId")
    void upsertPoints_usesOriginalKbId_withDefaultResolver() {
        ReflectionTestUtils.setField(service, "collectionNameResolver", new DefaultCollectionNameResolver());
        when(qdrantVectorService.collectionExists(anyString())).thenReturn(true);
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(1024);

        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setKbId("kb-001");
        slice.setContent("测试内容");
        slice.setVersion("v1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", "kb-001");
        when(versionPayloadBuilder.buildPayload(any(), anyString(), anyString())).thenReturn(payload);
        float[] vector = new float[1024];
        vector[0] = 0.5f;
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(vector));

        service.embedAndIndex(List.of(slice), "kb-001");

        verify(qdrantVectorService).upsertPoints(collectionNameCaptor.capture(), any());
        assertThat(collectionNameCaptor.getValue()).isEqualTo("kb-001");
    }

    @Test
    @DisplayName("ensureCollectionExists: 集合不存在时使用解析后的集合名创建")
    void ensureCollectionExists_createsWithResolvedName_whenNotExists() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-001_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        when(qdrantVectorService.collectionExists(anyString())).thenReturn(false);
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(1024);

        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setKbId("kb-001");
        slice.setContent("测试内容");
        slice.setVersion("v1");
        Map<String, Object> payload = new HashMap<>();
        payload.put("kbId", "kb-001");
        when(versionPayloadBuilder.buildPayload(any(), anyString(), anyString())).thenReturn(payload);
        float[] vector = new float[1024];
        vector[0] = 0.5f;
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(vector));

        service.embedAndIndex(List.of(slice), "kb-001");

        verify(qdrantVectorService).createCollection(eq("tenant-001_kb-001"), anyInt(), anyString());
    }

    @Test
    @DisplayName("validateEmbedding: 使用解析后的集合名查询集合信息")
    void validateEmbedding_usesResolvedCollectionName() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-001_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        io.qdrant.client.grpc.Collections.CollectionInfo collectionInfo =
                io.qdrant.client.grpc.Collections.CollectionInfo.newBuilder().build();
        when(qdrantVectorService.getCollectionInfo(anyString())).thenReturn(collectionInfo);

        SliceRecord slice = new SliceRecord();
        slice.setSliceId("slice-1");
        slice.setKbId("kb-001");
        slice.setContent("测试内容");
        slice.setVersion("v1");

        service.validateEmbedding(List.of(slice));

        verify(qdrantVectorService).getCollectionInfo("tenant-001_kb-001");
    }

    @Test
    @DisplayName("空切片列表不调用解析器")
    void emptySlices_doesNotCallResolver() {
        CollectionNameResolver trackingResolver = kbId -> {
            throw new IllegalStateException("解析器不应被调用");
        };
        ReflectionTestUtils.setField(service, "collectionNameResolver", trackingResolver);

        service.embedAndIndex(java.util.Collections.emptyList(), "kb-001");

        verify(qdrantVectorService, never()).collectionExists(anyString());
        verify(qdrantVectorService, never()).upsertPoints(anyString(), any());
    }
}

