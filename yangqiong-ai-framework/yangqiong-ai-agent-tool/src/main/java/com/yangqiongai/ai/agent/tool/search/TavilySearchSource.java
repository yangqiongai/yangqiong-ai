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
import com.alibaba.fastjson.JSONObject;
import com.yangqiongai.ai.rag.websearch.WebSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily搜索源
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.websearch-search.tavily.controller-key")
public class TavilySearchSource implements SearchSource {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchSource.class);

    private static final String DEFAULT_URL = "https://api.tavily.com/search";

    @Value("${ai.websearch-search.tavily.url:}")
    private String customUrl;

    @Value("${ai.websearch-search.tavily.controller-key:}")
    private String apiKey;

    @Value("${ai.websearch-search.tavily.enabled:true}")
    private boolean enabled;

    @Value("${ai.websearch-search.tavily.connect-timeout:1}")
    private int connectTimeoutSeconds;

    @Value("${ai.websearch-search.tavily.read-timeout:5}")
    private int readTimeoutSeconds;

    private RestTemplate restTemplate;

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeoutSeconds * 1000);
            factory.setReadTimeout(readTimeoutSeconds * 1000);
            this.restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }

    @Override
    public String getName() {
        return "tavily";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Tavily API key未配置, 跳过搜索");
            return Collections.emptyList();
        }
        String endpoint = (customUrl == null || customUrl.isBlank()) ? DEFAULT_URL : customUrl;

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("api_key", apiKey);
        requestBody.put("query", query);
        requestBody.put("max_results", maxResults);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = getRestTemplate().postForEntity(endpoint, entity, String.class);
        String json = response.getBody();
        if (json == null || json.isBlank()) {
            log.warn("Tavily响应为空");
            return Collections.emptyList();
        }
        return parseJson(json, maxResults);
    }

    private List<WebSearchResult> parseJson(String json, int maxResults) {
        try {
            JSONObject root = JSON.parseObject(json);
            JSONArray results = root.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }
            List<WebSearchResult> list = new ArrayList<>(results.size());
            for (int i = 0; i < results.size() && list.size() < maxResults; i++) {
                JSONObject item = results.getJSONObject(i);
                String title = item.getString("title");
                String url = item.getString("url");
                String content = item.getString("content");
                Double score = item.getDouble("score");
                if (score == null) {
                    score = 1.0 - (i * 0.1);
                }
                list.add(new WebSearchResult(title, url, content, score));
            }
            return list;
        } catch (Exception e) {
            log.warn("解析Tavily JSON失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
