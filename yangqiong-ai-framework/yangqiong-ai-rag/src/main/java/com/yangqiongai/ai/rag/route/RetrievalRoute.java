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
package com.yangqiongai.ai.rag.route;

/**
 * 检索路由枚举
 * @author yangqiong
 */
public enum RetrievalRoute {

    /**
     * 仅向量检索
     */
    VECTOR_ONLY,

    /**
     * 混合检索（向量+全文）
     */
    HYBRID,

    /**
     * 仅全文检索
     */
    FULLTEXT_ONLY,

    /**
     * Web搜索补充路由（本地检索后调用Web搜索补充）
     */
    WEB_SEARCH,

    /**
     * 知识图谱检索路由（委托RagGraphService执行图谱检索）
     */
    GRAPH,

    /**
     * 跳过检索
     */
    SKIP
}
