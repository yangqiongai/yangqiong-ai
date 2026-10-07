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
package com.yangqiongai.ai.rag.query;

import com.yangqiongai.ai.rag.model.ChunkCandidate;

import java.util.List;

/**
 * 查询分解
 * @author yangqiong
 */
public interface QueryDecomposer {

    /**
     * 分解复杂查询并并行检索，合并结果
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    List<ChunkCandidate> decomposeAndRetrieve(String query, List<String> kbIds,
                                               List<String> docIds, int topK);

    /**
     * 判断查询是否需要分解
     * @param query
     * @return
     */
    boolean shouldDecompose(String query);
}
