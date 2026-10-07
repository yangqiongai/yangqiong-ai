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
package com.yangqiongai.ai.rag.embed;

import com.yangqiongai.ai.rag.model.SliceRecord;

import java.util.List;

/**
 * 向量嵌入
 * @author yangqiong
 */
public interface VectorEmbedder {

    /**
     * 批量向量化并入库到Qdrant
     * @param slices
     * @param kbId
     */
    void embedAndIndex(List<SliceRecord> slices, String kbId);

    /**
     * 验证向量化结果
     * @param slices
     */
    void validateEmbedding(List<SliceRecord> slices);

    /**
     * 查询文本向量化
     * @param text
     * @return
     */
    float[] embedQuery(String text);
}
