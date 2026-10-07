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
package com.yangqiongai.ai.agent.tool.rag;

import com.yangqiongai.ai.rag.model.RetrievalEvidence;

import java.util.List;

/**
 * RAG上下文提供者
 * @author yangqiong
 */
public interface RagContextProvider {

    /**
     * 执行RAG检索并返回格式化上下文
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    String execute(String query, List<String> kbIds, List<String> docIds, int topK);

    /**
     * 执行RAG检索并返回原始结果
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK);
}
