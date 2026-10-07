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
import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.ToolCategory;
import com.yangqiongai.ai.rag.websearch.WebSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Web搜索工具
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.websearch-search.enabled", havingValue = "true")
public class WebSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    /**
     * maxResults 上限，防止 LLM 请求过多结果浪费 token
     */
    private static final int MAX_RESULTS_CAP = 10;

    @Autowired
    private WebSearchHttpClient httpClient;

    /**
     * 内置工具自动装配到所有Agent工具箱，与web-search技能配套
     * @return
     */
    @Override
    public ToolCategory getToolCategory() {
        return ToolCategory.BUILTIN;
    }

    /**
     * 执行Web搜索（使用默认搜索源）
     * @param query
     * @param maxResults
     * @return
     */
    @AgentTool("执行Web搜索，返回与查询相关的网页摘要信息，推荐maxResults值为5条，最多10条")
    public String webSearch(@AgentToolParam("搜索关键词") String query, @AgentToolParam("返回结果数量，推荐5条，最多10条") int maxResults) {
        int capped = Math.min(Math.max(maxResults, 1), MAX_RESULTS_CAP);
        log.debug("执行Web搜索: query={}, maxResults={}", query, capped);
        List<WebSearchResult> results = httpClient.search(query, capped);
        return JSON.toJSONString(results);
    }

    /**
     * 指定搜索源执行Web搜索
     * @param query
     * @param maxResults
     * @param sourceName 搜索源名称（如duckduckgo、tavily）
     * @return
     */
    @AgentTool("指定搜索源执行Web搜索，sourceName可选值: duckduckgo、tavily等，不传则使用默认搜索源")
    public String webSearchWithSource(@AgentToolParam("搜索关键词") String query, @AgentToolParam("返回结果数量，推荐5条，最多10条") int maxResults, @AgentToolParam("搜索源名称，可选值：duckduckgo、tavily等") String sourceName) {
        int capped = Math.min(Math.max(maxResults, 1), MAX_RESULTS_CAP);
        log.debug("执行Web搜索: query={}, maxResults={}, source={}", query, capped, sourceName);
        List<WebSearchResult> results = httpClient.search(query, capped, sourceName);
        return JSON.toJSONString(results);
    }
}
