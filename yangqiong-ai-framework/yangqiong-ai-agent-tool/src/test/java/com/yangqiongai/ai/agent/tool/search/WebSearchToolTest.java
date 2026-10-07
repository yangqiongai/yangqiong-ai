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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.yangqiongai.ai.rag.websearch.WebSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSearchTool 单元测试
 * @author yangqiong
 */
@DisplayName("WebSearchTool 单元测试")
class WebSearchToolTest {

    private WebSearchTool tool;

    private WebSearchHttpClient httpClient;

    private List<WebSearchResult> httpClientResponse;

    @BeforeEach
    void setUp() {
        tool = new WebSearchTool();
        httpClient = new WebSearchHttpClient() {
            @Override
            public List<WebSearchResult> search(String query, int maxResults) {
                if (query == null || query.isBlank()) {
                    return Collections.emptyList();
                }
                if (httpClientResponse == null) {
                    throw new RuntimeException("httpClient调用失败");
                }
                return httpClientResponse;
            }
        };
        ReflectionTestUtils.setField(tool, "httpClient", httpClient);
    }

    @Test
    @DisplayName("httpClient返回结果时返回JSON字符串")
    void web_search_returnsJson_whenResultsProvided() {
        httpClientResponse = List.of(
                new WebSearchResult("标题1", "http://example.com", "摘要1", 0.9));

        String result = tool.webSearch("查询", 5);

        assertThat(result).isNotBlank();
        JSONArray array = JSON.parseArray(result);
        assertThat(array).hasSize(1);
        assertThat(array.getJSONObject(0).getString("title")).isEqualTo("标题1");
        assertThat(array.getJSONObject(0).getString("url")).isEqualTo("http://example.com");
    }

    @Test
    @DisplayName("httpClient返回空列表时返回空数组JSON")
    void web_search_returnsEmptyJson_whenHttpClientReturnsEmpty() {
        httpClientResponse = Collections.emptyList();

        String result = tool.webSearch("查询", 5);

        assertThat(result).isEqualTo("[]");
    }

    @Test
    @DisplayName("query为空时返回空数组JSON")
    void web_search_returnsEmpty_whenQueryBlank() {
        String result = tool.webSearch("", 5);

        assertThat(result).isEqualTo("[]");
    }
}
