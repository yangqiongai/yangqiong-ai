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
package com.yangqiongai.ai.agent.rag.resolver;

import com.yangqiongai.ai.agent.core.provider.RerankOptions;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;

import java.util.List;

/**
 * 知识解析器
 * @author yangqiong
 */
public interface KnowledgeResolver {

    /**
     * 解析知识上下文
     * @param query
     * @param kbIds
     * @param topK
     * @return
     */
    List<RetrievalEvidence> resolve(String query, List<String> kbIds, int topK);

    /**
     * 解析知识上下文，支持重排序选项覆盖
     * @param query
     * @param kbIds
     * @param topK
     * @param rerankOptions 重排序覆盖选项，null时跟随全局配置
     * @return
     */
    default List<RetrievalEvidence> resolve(String query, List<String> kbIds, int topK, RerankOptions rerankOptions) {
        return resolve(query, kbIds, topK);
    }
}
