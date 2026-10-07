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
import com.yangqiongai.ai.rag.model.RecallRequest;
import com.yangqiongai.ai.rag.model.RecallResult;
import com.yangqiongai.ai.agent.core.provider.RerankOptions;

import java.util.List;

/**
 * RAG检索
 * @author yangqiong
 */
public interface RagRetrieveService {

    /**
     * 检索相关文档片段
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK);

    /**
     * 检索相关文档片段，支持重排序选项覆盖（Agent级/请求级配置）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @param rerankOptions 重排序覆盖选项，null时跟随全局配置
     * @return
     */
    default List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK, RerankOptions rerankOptions) {
        return retrieve(query, kbIds, docIds, topK);
    }

    /**
     * 混合检索（稠密向量+稀疏全文双通道融合重排）
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    List<RetrievalEvidence> retrieveHybrid(String query, List<String> kbIds, List<String> docIds, int topK);

    /**
     * 召回验证
     * @param request
     * @return
     */
    List<RecallResult> verifyRecall(RecallRequest request);

    /**
     * 单文档上下文扩展
     * @param docId
     * @param query
     * @param maxChars
     * @return
     */
    String expandContext(String docId, String query, int maxChars);
}
