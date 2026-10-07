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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DuckDuckGo搜索源
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.websearch-search.enabled", havingValue = "true")
public class DuckDuckGoSearchSource implements SearchSource {

    private static final Logger log = LoggerFactory.getLogger(DuckDuckGoSearchSource.class);

    private static final String DEFAULT_URL = "https://html.duckduckgo.com/html/?q=";

    private static final Pattern RESULT_LINK_PATTERN =
            Pattern.compile("<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL);

    private static final Pattern RESULT_SNIPPET_PATTERN =
            Pattern.compile("<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL);

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    @Value("${ai.websearch-search.duckduckgo.url:}")
    private String customUrl;

    @Value("${ai.websearch-search.duckduckgo.enabled:true}")
    private boolean enabled;

    @Value("${ai.websearch-search.duckduckgo.connect-timeout:1}")
    private int connectTimeoutSeconds;

    @Value("${ai.websearch-search.duckduckgo.read-timeout:5}")
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
        return "duckduckgo";
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
        String endpoint = (customUrl == null || customUrl.isBlank()) ? DEFAULT_URL : customUrl;
        String encodedQuery = UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
        String fullUrl = endpoint + encodedQuery;

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = getRestTemplate().exchange(
                fullUrl, HttpMethod.GET, entity, String.class);
        String html = response.getBody();
        if (html == null || html.isBlank()) {
            log.warn("DuckDuckGo响应为空");
            return Collections.emptyList();
        }
        return parseHtml(html, maxResults);
    }

    private List<WebSearchResult> parseHtml(String html, int maxResults) {
        List<WebSearchResult> results = new ArrayList<>();
        Matcher linkMatcher = RESULT_LINK_PATTERN.matcher(html);
        Matcher snippetMatcher = RESULT_SNIPPET_PATTERN.matcher(html);

        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (linkMatcher.find() && urls.size() < maxResults) {
            urls.add(linkMatcher.group(1));
            titles.add(stripHtmlTags(linkMatcher.group(2)));
        }

        List<String> snippets = new ArrayList<>();
        while (snippetMatcher.find() && snippets.size() < maxResults) {
            snippets.add(stripHtmlTags(snippetMatcher.group(1)));
        }

        int count = Math.min(urls.size(), Math.min(titles.size(), maxResults));
        for (int i = 0; i < count; i++) {
            String snippet = i < snippets.size() ? snippets.get(i) : "";
            double score = 1.0 - (i * 0.1);
            results.add(new WebSearchResult(titles.get(i), urls.get(i), snippet, score));
        }
        return results;
    }

    private String stripHtmlTags(String text) {
        if (text == null) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(text).replaceAll("").trim();
    }
}
