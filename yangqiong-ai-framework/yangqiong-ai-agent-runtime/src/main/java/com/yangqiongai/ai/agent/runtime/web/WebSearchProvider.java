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
package com.yangqiongai.ai.agent.runtime.web;

import java.util.List;

/**
 * Web检索提供方
 * <p>
 * web_search工具的后端检索SPI。
 * </p>
 * @author yangqiong
 */
public interface WebSearchProvider {

    /**
     * 执行Web检索
     * @param query 查询文本
     * @param topK 返回条数上限
     * @return 检索结果列表
     */
    List<WebSearchResult> search(String query, int topK);
}
