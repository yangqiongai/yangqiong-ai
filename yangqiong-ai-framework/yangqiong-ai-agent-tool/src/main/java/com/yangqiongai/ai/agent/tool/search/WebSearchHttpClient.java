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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Web搜索客户端
 * <p>
 * 基于搜索源注册中心执行搜索，支持配置默认搜索源和按名称指定搜索源。
 * 当一个搜索源调用失败或返回空结果时，自动切换到下一个可用搜索源。
 * 新增搜索渠道只需实现 {@link SearchSource} 接口并加 @Component 即可自动注册。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.websearch-search.enabled", havingValue = "true")
public class WebSearchHttpClient {

    private static final Logger log = LoggerFactory.getLogger(WebSearchHttpClient.class);

    @Autowired
    private SearchSourceRegistry searchSourceRegistry;

    @Value("${ai.websearch-search.default-source:}")
    private String defaultSourceName;

    /**
     * 使用默认搜索源执行搜索
     * @param query
     * @param maxResults
     * @return
     */
    public List<WebSearchResult> search(String query, int maxResults) {
        return search(query, maxResults, null);
    }

    /**
     * 指定搜索源执行搜索，失败时自动切换到下一个可用搜索源
     * @param query
     * @param maxResults
     * @param sourceName 搜索源名称，为空时使用默认搜索源
     * @return
     */
    public List<WebSearchResult> search(String query, int maxResults, String sourceName) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        List<SearchSource> candidates = buildCandidateSources(sourceName);
        if (candidates.isEmpty()) {
            log.warn("无可用的搜索源: requested={}, default={}, available={}",
                    sourceName, defaultSourceName, searchSourceRegistry.getNames());
            return Collections.emptyList();
        }
        List<String> failedSources = new ArrayList<>();
        for (SearchSource source : candidates) {
            try {
                log.debug("执行Web搜索: source={}, query={}, maxResults={}", source.getName(), query, maxResults);
                List<WebSearchResult> results = source.search(query, maxResults);
                if (results != null && !results.isEmpty()) {
                    if (!failedSources.isEmpty()) {
                        log.info("搜索源自动切换成功: failed={}, current={}", failedSources, source.getName());
                    }
                    return results;
                }
                // 结果为空也尝试下一个源
                log.debug("搜索源返回空结果: source={}, query={}", source.getName(), query);
                failedSources.add(source.getName());
            } catch (Exception e) {
                log.warn("搜索源调用失败: source={}, query={}, error={}", source.getName(), query, e.getMessage());
                failedSources.add(source.getName());
            }
        }
        log.warn("所有搜索源均未返回结果: tried={}, query={}", failedSources, query);
        return Collections.emptyList();
    }

    /**
     * 构建搜索源优先级列表：指定源 → 默认源 → 其他源（去重）
     * @param sourceName
     * @return
     */
    private List<SearchSource> buildCandidateSources(String sourceName) {
        List<SearchSource> candidates = new ArrayList<>();
        Set<String> added = new HashSet<>();

        // 优先使用指定的搜索源
        if (sourceName != null && !sourceName.isBlank()) {
            SearchSource source = searchSourceRegistry.get(sourceName);
            if (source != null && source.isEnabled()) {
                candidates.add(source);
                added.add(source.getName().toLowerCase());
            } else if (source != null && !source.isEnabled()) {
                log.warn("指定的搜索源已禁用: {}, 将尝试其他源", sourceName);
            } else {
                log.warn("指定的搜索源不存在: {}, 可用: {}", sourceName, searchSourceRegistry.getNames());
            }
        }

        // 其次使用配置的默认搜索源
        if (defaultSourceName != null && !defaultSourceName.isBlank()) {
            SearchSource source = searchSourceRegistry.get(defaultSourceName);
            if (source != null && source.isEnabled() && !added.contains(source.getName().toLowerCase())) {
                candidates.add(source);
                added.add(source.getName().toLowerCase());
            }
        }

        // 最后添加其他已注册搜索源
        for (SearchSource source : searchSourceRegistry.getAll()) {
            if (source.isEnabled() && !added.contains(source.getName().toLowerCase())) {
                candidates.add(source);
                added.add(source.getName().toLowerCase());
            }
        }

        return candidates;
    }
}
