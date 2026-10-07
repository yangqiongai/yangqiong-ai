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
package com.yangqiongai.ai.agent.tool.search;

import com.yangqiongai.ai.rag.websearch.WebSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSearchProviderImpl 单元测试
 * @author yangqiong
 */
@DisplayName("WebSearchProviderImpl 单元测试")
class WebSearchProviderImplTest {

    private WebSearchProviderImpl provider;

    private WebSearchHttpClient httpClient;

    private List<WebSearchResult> httpClientResponse;

    @BeforeEach
    void setUp() {
        provider = new WebSearchProviderImpl();
        httpClient = new WebSearchHttpClient() {
            @Override
            public List<WebSearchResult> search(String query, int maxResults) {
                if (query == null || query.isBlank()) {
                    return Collections.emptyList();
                }
                return httpClientResponse != null ? httpClientResponse : Collections.emptyList();
            }
        };
        ReflectionTestUtils.setField(provider, "httpClient", httpClient);
    }

    @Test
    @DisplayName("search委派给httpClient")
    void search_delegatesToHttpClient() {
        httpClientResponse = List.of(
                new WebSearchResult("标题1", "http://example.com/1", "摘要1", 0.9));

        List<WebSearchResult> result = provider.search("查询", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("标题1");
    }

    @Test
    @DisplayName("query为空时返回空列表")
    void search_returnsEmpty_whenQueryBlank() {
        List<WebSearchResult> result = provider.search("", 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("httpClient返回结果时透传")
    void search_returnsResults_whenHttpClientReturns() {
        httpClientResponse = List.of(
                new WebSearchResult("标题A", "http://a.com", "摘要A", 0.8),
                new WebSearchResult("标题B", "http://b.com", "摘要B", 0.6));

        List<WebSearchResult> result = provider.search("test", 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("标题A");
        assertThat(result.get(1).getUrl()).isEqualTo("http://b.com");
    }
}
