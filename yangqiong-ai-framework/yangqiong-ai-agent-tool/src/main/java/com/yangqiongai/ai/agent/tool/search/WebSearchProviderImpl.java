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

import com.yangqiongai.ai.rag.websearch.WebSearchProvider;
import com.yangqiongai.ai.rag.websearch.WebSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Web搜索提供者实现
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.websearch-search.enabled", havingValue = "true")
public class WebSearchProviderImpl implements WebSearchProvider {

    @Autowired
    private WebSearchHttpClient httpClient;

    /**
     * 执行Web搜索
     * @param query
     * @param maxResults
     * @return
     */
    @Override
    public List<WebSearchResult> search(String query, int maxResults) {
        return httpClient.search(query, maxResults);
    }
}
