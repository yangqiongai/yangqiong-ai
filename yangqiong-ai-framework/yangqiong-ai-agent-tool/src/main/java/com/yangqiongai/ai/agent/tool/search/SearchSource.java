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

import java.util.List;

/**
 * 搜索源接口
 * <p>
 * 每个搜索渠道实现此接口，通过 {@link SearchSourceRegistry} 注册后可被搜索工具使用。
 * </p>
 * <p>
 * 异常约定：实现类在调用远程搜索服务失败时应抛出 RuntimeException，而非返回空列表。
 * 这样上层调用方可以在捕获异常后自动切换到下一个搜索源。仅在输入参数无效等前置校验场景下返回空列表。
 * </p>
 * @author yangqiong
 */
public interface SearchSource {

    /**
     * 搜索源标识（如 duckduckgo、tavily、serpapi）
     * @return
     */
    String getName();

    /**
     * 是否启用此搜索源，可通过配置动态控制
     * @return
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 执行搜索，调用失败时抛出异常
     * @param query 查询关键词
     * @param maxResults 最大结果数
     * @return
     */
    List<WebSearchResult> search(String query, int maxResults);
}
