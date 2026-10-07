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
package com.yangqiongai.ai.rag;

import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.route.RetrievalRoute;

import java.util.List;

/**
 * 检索策略
 * <p>三层职责分层：L1 RetrievalAgent 闭环协调 → L2 DefaultRagRetrieve 路由分发 → L3 RetrievalStrategy 单次检索执行。
 * 内置路由（VECTOR_ONLY/HYBRID/FULLTEXT_ONLY）由 DefaultRagRetrieve 内置策略处理；
 * 扩展路由（如 GRAPH）由外部模块注入对应 RetrievalStrategy 实现处理。
 * WEB_SEARCH 路由由 RetrievalAgent 层处理，不走 RetrievalStrategy。
 * @author yangqiong
 */
public interface RetrievalStrategy {

    /**
     * 判断是否支持指定路由
     * @param route
     * @return
     */
    boolean supports(RetrievalRoute route);

    /**
     * 执行单次检索
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK);
}
