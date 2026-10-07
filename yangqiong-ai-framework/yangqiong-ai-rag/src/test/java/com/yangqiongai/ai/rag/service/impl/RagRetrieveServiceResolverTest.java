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
package com.yangqiongai.ai.rag.service.impl;

import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultCollectionNameResolver;
import com.yangqiongai.ai.rag.config.KnowledgeBaseVersionResolver;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.embed.VectorEmbedder;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import com.yangqiongai.ai.rag.retriever.AdaptiveContentRetriever;
import com.yangqiongai.ai.rag.route.RetrievalPathResolver;
import com.yangqiongai.ai.rag.route.RetrievalRoute;
import com.yangqiongai.ai.rag.DefaultRagRetrieve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultRagRetrieve 集合名称解析器集成测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultRagRetrieve 集合名称解析器测试")
class RagRetrieveServiceResolverTest {

    @Mock
    private AdaptiveContentRetriever adaptiveContentRetriever;

    @Mock
    private VectorEmbedder vectorEmbedder;

    @Captor
    private ArgumentCaptor<String> collectionNameCaptor;

    private DefaultRagRetrieve service;

    @BeforeEach
    void setUp() {
        service = new DefaultRagRetrieve();
        ReflectionTestUtils.setField(service, "ragProperties", new RagProperties());
        ReflectionTestUtils.setField(service, "adaptiveContentRetriever", adaptiveContentRetriever);
        ReflectionTestUtils.setField(service, "vectorEmbedder", vectorEmbedder);
        Executor directExecutor = Runnable::run;
        ReflectionTestUtils.setField(service, "ragExecutor", directExecutor);
        injectPathResolver(RetrievalRoute.VECTOR_ONLY);
        injectVersionResolver("v-20260101");
        lenient().when(vectorEmbedder.embedQuery(anyString())).thenReturn(new float[1024]);
    }

    @Test
    @DisplayName("默认解析器: retrieve 调用 retrieveWithVector 传入原始 kbId 作为集合名")
    void retrieve_usesOriginalKbId_withDefaultResolver() {
        ReflectionTestUtils.setField(service, "collectionNameResolver", new DefaultCollectionNameResolver());
        ChunkCandidate candidate = new ChunkCandidate("内容", "doc-1", "slice-1", 0.9, false, null);
        when(adaptiveContentRetriever.retrieveWithVector(
                any(), anyString(), anyInt(), anyDouble(), any()))
                .thenReturn(List.of(candidate));

        service.retrieve("查询", List.of("kb-001"), null, 5);

        verify(adaptiveContentRetriever).retrieveWithVector(
                any(), collectionNameCaptor.capture(), anyInt(), anyDouble(), any());
        assertThat(collectionNameCaptor.getValue()).isEqualTo("kb-001");
    }

    @Test
    @DisplayName("作用域解析器: retrieve 调用 retrieveWithVector 传入 scopeId_kbId 作为集合名")
    void retrieve_usesResolvedCollectionName_withTenantResolver() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-001_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        ChunkCandidate candidate = new ChunkCandidate("内容", "doc-1", "slice-1", 0.9, false, null);
        when(adaptiveContentRetriever.retrieveWithVector(
                any(), anyString(), anyInt(), anyDouble(), any()))
                .thenReturn(List.of(candidate));

        service.retrieve("查询", List.of("kb-001"), null, 5);

        verify(adaptiveContentRetriever).retrieveWithVector(
                any(), collectionNameCaptor.capture(), anyInt(), anyDouble(), any());
        assertThat(collectionNameCaptor.getValue()).isEqualTo("tenant-001_kb-001");
    }

    @Test
    @DisplayName("作用域解析器: retrieveHybrid 调用 retrieveWithVector 传入 scopeId_kbId")
    void retrieveHybrid_usesResolvedCollectionName_withTenantResolver() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-001_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        ChunkCandidate candidate = new ChunkCandidate("内容", "doc-1", "slice-1", 0.9, false, null);
        when(adaptiveContentRetriever.retrieveWithVector(
                any(), anyString(), anyInt(), anyDouble(), any()))
                .thenReturn(List.of(candidate));

        service.retrieveHybrid("查询", List.of("kb-001"), null, 5);

        verify(adaptiveContentRetriever).retrieveWithVector(
                any(), collectionNameCaptor.capture(), anyInt(), anyDouble(), any());
        assertThat(collectionNameCaptor.getValue()).isEqualTo("tenant-001_kb-001");
    }

    @Test
    @DisplayName("多知识库检索: 每个 kbId 都被解析为对应集合名")
    void retrieve_multipleKbIds_eachResolvedIndependently() {
        CollectionNameResolver tenantResolver = kbId -> "tenant-X_" + kbId;
        ReflectionTestUtils.setField(service, "collectionNameResolver", tenantResolver);
        ChunkCandidate candidate = new ChunkCandidate("内容", "doc-1", "slice-1", 0.9, false, null);
        when(adaptiveContentRetriever.retrieveWithVector(
                any(), anyString(), anyInt(), anyDouble(), any()))
                .thenReturn(List.of(candidate));

        service.retrieve("查询", List.of("kb-1", "kb-2", "kb-3"), null, 5);

        verify(adaptiveContentRetriever, org.mockito.Mockito.times(3))
                .retrieveWithVector(any(), collectionNameCaptor.capture(), anyInt(), anyDouble(), any());
        assertThat(collectionNameCaptor.getAllValues())
                .containsExactlyInAnyOrder("tenant-X_kb-1", "tenant-X_kb-2", "tenant-X_kb-3");
    }

    @Test
    @DisplayName("空 kbIds 列表不调用解析器")
    void retrieve_emptyKbIds_doesNotCallResolver() {
        CollectionNameResolver trackingResolver = kbId -> {
            throw new IllegalStateException("解析器不应被调用");
        };
        ReflectionTestUtils.setField(service, "collectionNameResolver", trackingResolver);

        service.retrieve("查询", Collections.emptyList(), null, 5);

        verify(adaptiveContentRetriever, never())
                .retrieveWithVector(any(), anyString(), anyInt(), anyDouble(), any());
    }

    private void injectPathResolver(RetrievalRoute route) {
        RetrievalPathResolver stubResolver = (query, kbIds) -> route;
        ReflectionTestUtils.setField(service, "retrievalPathResolver", stubResolver);
    }

    private void injectVersionResolver(String activeVersion) {
        KnowledgeBaseVersionResolver stub = new KnowledgeBaseVersionResolver() {
            @Override
            public String resolveActiveVersion(String kbId) {
                return activeVersion;
            }
        };
        ReflectionTestUtils.setField(service, "versionResolver", stub);
    }
}
