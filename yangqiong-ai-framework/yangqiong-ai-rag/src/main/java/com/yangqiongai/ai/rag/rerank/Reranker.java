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
package com.yangqiongai.ai.rag.rerank;

import com.yangqiongai.ai.rag.model.RetrievalEvidence;

import java.util.List;

/**
 * Rerank重排序
 * @author yangqiong
 */
public interface Reranker {

    /**
     * 对检索结果重排序
     * @param evidences
     * @param query
     * @param topK
     * @return
     */
    List<RetrievalEvidence> rerank(List<RetrievalEvidence> evidences, String query, int topK);

    /**
     * 对检索结果重排序，支持按调用覆盖模型编码
     * @param evidences
     * @param query
     * @param topK
     * @param modelCode 覆盖模型编码，null或空白时使用实现默认模型
     * @return
     */
    default List<RetrievalEvidence> rerank(List<RetrievalEvidence> evidences, String query, int topK, String modelCode) {
        return rerank(evidences, query, topK);
    }

    /**
     * 获取策略标识，用于工厂按名称查找
     * @return
     */
    String getStrategy();
}
