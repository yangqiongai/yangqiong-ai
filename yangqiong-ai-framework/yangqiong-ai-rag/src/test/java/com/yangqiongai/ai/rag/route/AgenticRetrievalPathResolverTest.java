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
package com.yangqiongai.ai.rag.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgenticRetrievalPathResolver 单元测试
 * @author yangqiong
 */
@DisplayName("AgenticRetrievalPathResolver 单元测试")
class AgenticRetrievalPathResolverTest {

    private AgenticRetrievalPathResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AgenticRetrievalPathResolver();
    }

    /**
     * 注入指定意图的识别器桩
     */
    private void injectIntention(QueryIntention intention) {
        QueryIntentionRecognizer stubRecognizer = new QueryIntentionRecognizer() {
            @Override
            public QueryIntention recognize(String query) {
                return intention;
            }
        };
        ReflectionTestUtils.setField(resolver, "intentionRecognizer", stubRecognizer);
    }

    @Test
    @DisplayName("query为null时返回SKIP")
    void decide_returnsSkip_whenQueryNull() {
        injectIntention(QueryIntention.UNKNOWN);
        RetrievalRoute result = resolver.decide(null, List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("query为空白时返回SKIP")
    void decide_returnsSkip_whenQueryBlank() {
        injectIntention(QueryIntention.UNKNOWN);
        RetrievalRoute result = resolver.decide("   ", List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("kbIds为null时返回SKIP")
    void decide_returnsSkip_whenKbIdsNull() {
        injectIntention(QueryIntention.UNKNOWN);
        RetrievalRoute result = resolver.decide("测试查询", null);
        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("kbIds为空列表时返回SKIP")
    void decide_returnsSkip_whenKbIdsEmpty() {
        injectIntention(QueryIntention.UNKNOWN);
        RetrievalRoute result = resolver.decide("测试查询", Collections.emptyList());
        assertThat(result).isEqualTo(RetrievalRoute.SKIP);
    }

    @Test
    @DisplayName("FACTUAL意图映射为VECTOR_ONLY")
    void decide_returnsVectorOnly_whenFactual() {
        injectIntention(QueryIntention.FACTUAL);
        RetrievalRoute result = resolver.decide("RAG是什么", List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.VECTOR_ONLY);
    }

    @Test
    @DisplayName("RELATIONAL意图映射为FULLTEXT_ONLY")
    void decide_returnsFulltextOnly_whenRelational() {
        injectIntention(QueryIntention.RELATIONAL);
        RetrievalRoute result = resolver.decide("A和B的关系", List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.FULLTEXT_ONLY);
    }

    @Test
    @DisplayName("ANALYTICAL意图映射为HYBRID")
    void decide_returnsHybrid_whenAnalytical() {
        injectIntention(QueryIntention.ANALYTICAL);
        RetrievalRoute result = resolver.decide("为什么需要RAG", List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.HYBRID);
    }

    @Test
    @DisplayName("COMPLEX意图映射为HYBRID")
    void decide_returnsHybrid_whenComplex() {
        injectIntention(QueryIntention.COMPLEX);
        RetrievalRoute result = resolver.decide("对比A和B并分析优势", List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.HYBRID);
    }

    @Test
    @DisplayName("UNKNOWN意图降级为HYBRID")
    void decide_returnsHybrid_whenUnknown() {
        injectIntention(QueryIntention.UNKNOWN);
        RetrievalRoute result = resolver.decide("模糊查询", List.of("kb-1"));
        assertThat(result).isEqualTo(RetrievalRoute.HYBRID);
    }
}
