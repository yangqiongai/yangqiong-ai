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
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.retriever.FullTextSearcher;
import com.yangqiongai.ai.rag.route.RetrievalPathResolver;
import com.yangqiongai.ai.rag.route.RetrievalRoute;
import com.yangqiongai.ai.rag.RagRetrieveService;
import com.yangqiongai.ai.rag.DefaultRagRetrieve;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultRagRetrieve 单元测试
 * @author yangqiong
 */
@DisplayName("DefaultRagRetrieve 单元测试")
class DefaultRagRetrieveTest {

    private DefaultRagRetrieve service;

    @BeforeEach
    void setUp() {
        service = new DefaultRagRetrieve();
        ReflectionTestUtils.setField(service, "ragProperties", new RagProperties());
        CollectionNameResolver defaultResolver = new DefaultCollectionNameResolver();
        ReflectionTestUtils.setField(service, "collectionNameResolver", defaultResolver);
    }

    @Test
    @DisplayName("retrieve - kbIds为null时返回空列表")
    void retrieve_returnsEmpty_whenKbIdsNull() {
        List<RetrievalEvidence> result = service.retrieve("query", null, null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retrieve - kbIds为空列表时返回空列表")
    void retrieve_returnsEmpty_whenKbIdsEmpty() {
        List<RetrievalEvidence> result = service.retrieve("query", Collections.emptyList(), null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retrieveHybrid - kbIds为null时返回空列表")
    void retrieveHybrid_returnsEmpty_whenKbIdsNull() {
        List<RetrievalEvidence> result = service.retrieveHybrid("query", null, null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retrieveHybrid - kbIds为空列表时返回空列表")
    void retrieveHybrid_returnsEmpty_whenKbIdsEmpty() {
        List<RetrievalEvidence> result = service.retrieveHybrid("query", Collections.emptyList(), null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retrieve - 路由决策为SKIP时返回空列表")
    void retrieve_returnsEmpty_whenRouteSkip() {
        injectPathResolver(RetrievalRoute.SKIP);

        List<RetrievalEvidence> result = service.retrieve("query", List.of("kb-1"), null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retrieve - FULLTEXT_ONLY路由返回全文检索结果")
    void retrieve_returnsFulltextHits_whenRouteFulltextOnly() {
        injectPathResolver(RetrievalRoute.FULLTEXT_ONLY);
        injectVersionResolver("v-20260101");
        injectFullTextSearcher(List.of(
                new ChunkCandidate("内容A", "doc-1", "slice-1", 0.8, false, null),
                new ChunkCandidate("内容B", "doc-1", "slice-2", 0.6, false, null)
        ));

        List<RetrievalEvidence> result = service.retrieve("测试", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("内容A");
        assertThat(result.get(0).getSourceDocId()).isEqualTo("doc-1");
        assertThat(result.get(0).getSliceId()).isEqualTo("slice-1");
        assertThat(result.get(0).getScore()).isEqualTo(0.8);
        assertThat(result.get(1).getContent()).isEqualTo("内容B");
    }

    @Test
    @DisplayName("retrieve - FULLTEXT_ONLY路由无结果时返回空列表")
    void retrieve_returnsEmpty_whenFulltextNoHits() {
        injectPathResolver(RetrievalRoute.FULLTEXT_ONLY);
        injectVersionResolver("v-20260101");
        injectFullTextSearcher(Collections.emptyList());

        List<RetrievalEvidence> result = service.retrieve("测试", List.of("kb-1"), null, 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retrieve - FULLTEXT_ONLY路由结果按分数降序排序")
    void retrieve_sortsByScoreDesc_whenFulltextOnly() {
        injectPathResolver(RetrievalRoute.FULLTEXT_ONLY);
        injectVersionResolver("v-20260101");
        injectFullTextSearcher(List.of(
                new ChunkCandidate("低分", "doc-1", "slice-1", 0.3, false, null),
                new ChunkCandidate("高分", "doc-1", "slice-2", 0.9, false, null),
                new ChunkCandidate("中分", "doc-1", "slice-3", 0.5, false, null)
        ));

        List<RetrievalEvidence> result = service.retrieve("测试", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getContent()).isEqualTo("高分");
        assertThat(result.get(1).getContent()).isEqualTo("中分");
        assertThat(result.get(2).getContent()).isEqualTo("低分");
    }

    @Test
    @DisplayName("retrieve - FULLTEXT_ONLY路由结果超过topK时截断")
    void retrieve_truncatesToTopK_whenFulltextOnly() {
        injectPathResolver(RetrievalRoute.FULLTEXT_ONLY);
        injectVersionResolver("v-20260101");
        injectFullTextSearcher(List.of(
                new ChunkCandidate("内容1", "doc-1", "s1", 0.9, false, null),
                new ChunkCandidate("内容2", "doc-1", "s2", 0.8, false, null),
                new ChunkCandidate("内容3", "doc-1", "s3", 0.7, false, null)
        ));

        List<RetrievalEvidence> result = service.retrieve("测试", List.of("kb-1"), null, 2);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("内容1");
        assertThat(result.get(1).getContent()).isEqualTo("内容2");
    }

    @Test
    @DisplayName("retrieve - 证据回填候选块的kbId与docName")
    void retrieve_fillsKbIdAndDocName_onEvidence() {
        injectPathResolver(RetrievalRoute.FULLTEXT_ONLY);
        injectVersionResolver("v-20260101");
        ChunkCandidate candidate = new ChunkCandidate("内容A", "doc-1", "slice-1", 0.8, false, null);
        candidate.setKbId("kb-1");
        candidate.setDocName("TLCL-25.12A.pdf");
        injectFullTextSearcher(List.of(candidate));

        List<RetrievalEvidence> result = service.retrieve("测试", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKbId()).isEqualTo("kb-1");
        assertThat(result.get(0).getKbName()).isNull();
        assertThat(result.get(0).getSourceDocName()).isEqualTo("TLCL-25.12A.pdf");
    }

    /**
     * 注入指定路由的RetrievalPathResolver桩
     */
    private void injectPathResolver(RetrievalRoute route) {
        RetrievalPathResolver stubResolver = (query, kbIds) -> route;
        ReflectionTestUtils.setField(service, "retrievalPathResolver", stubResolver);
    }

    /**
     * 注入返回固定版本的KnowledgeBaseVersionResolver桩
     */
    private void injectVersionResolver(String activeVersion) {
        KnowledgeBaseVersionResolver stub = new KnowledgeBaseVersionResolver() {
            @Override
            public String resolveActiveVersion(String kbId) {
                return activeVersion;
            }
        };
        ReflectionTestUtils.setField(service, "versionResolver", stub);
    }

    /**
     * 注入返回固定候选列表的FullTextSearcher桩
     */
    private void injectFullTextSearcher(List<ChunkCandidate> hits) {
        FullTextSearcher stub = (query, kbId, limit, activeVersion) -> new ArrayList<>(hits);
        ReflectionTestUtils.setField(service, "fullTextSearcher", stub);
    }
}
