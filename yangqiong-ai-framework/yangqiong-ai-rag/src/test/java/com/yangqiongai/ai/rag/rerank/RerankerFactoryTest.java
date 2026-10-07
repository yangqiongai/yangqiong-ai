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
package com.yangqiongai.ai.rag.rerank;

import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RerankerFactory 单元测试
 * @author yangqiong
 */
@DisplayName("RerankerFactory 单元测试")
class RerankerFactoryTest {

    /**
     * 测试用RerankService桩实现
     */
    private static class StubRerankerService implements Reranker {

        private final String strategy;

        StubRerankerService(String strategy) {
            this.strategy = strategy;
        }

        @Override
        public String getStrategy() {
            return strategy;
        }

        @Override
        public List<RetrievalEvidence> rerank(List<RetrievalEvidence> evidences, String query, int topK) {
            return evidences;
        }
    }

    @Test
    @DisplayName("按策略名匹配对应的RerankService")
    void shouldMatchStrategyByName() {
        StubRerankerService llmService = new StubRerankerService("llm");
        StubRerankerService customService = new StubRerankerService("custom");

        RerankerFactory factory = new RerankerFactory(List.of(llmService, customService));
        ReflectionTestUtils.setField(factory, "strategy", "custom");

        Reranker result = factory.getRerankService();
        assertThat(result).isSameAs(customService);
    }

    @Test
    @DisplayName("未知策略降级为llm")
    void shouldFallbackToLlmForUnknownStrategy() {
        StubRerankerService llmService = new StubRerankerService("llm");

        RerankerFactory factory = new RerankerFactory(List.of(llmService));
        ReflectionTestUtils.setField(factory, "strategy", "unknown");

        Reranker result = factory.getRerankService();
        assertThat(result).isSameAs(llmService);
    }

    @Test
    @DisplayName("策略名大小写不敏感")
    void shouldBeCaseInsensitive() {
        StubRerankerService llmService = new StubRerankerService("llm");

        RerankerFactory factory = new RerankerFactory(List.of(llmService));
        ReflectionTestUtils.setField(factory, "strategy", "LLM");

        Reranker result = factory.getRerankService();
        assertThat(result).isSameAs(llmService);
    }

    @Test
    @DisplayName("空实现列表时返回null")
    void shouldReturnNullForEmptyList() {
        RerankerFactory factory = new RerankerFactory(Collections.emptyList());
        ReflectionTestUtils.setField(factory, "strategy", "llm");

        Reranker result = factory.getRerankService();
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("重复策略名时保留先注册的实现")
    void shouldKeepFirstRegisteredForDuplicateStrategy() {
        StubRerankerService first = new StubRerankerService("llm");
        StubRerankerService second = new StubRerankerService("llm");

        RerankerFactory factory = new RerankerFactory(List.of(first, second));
        ReflectionTestUtils.setField(factory, "strategy", "llm");

        Reranker result = factory.getRerankService();
        assertThat(result).isSameAs(first);
    }
}
