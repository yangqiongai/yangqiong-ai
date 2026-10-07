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
package com.yangqiongai.ai.llm.embed;

import java.util.List;

/**
 * 文本嵌入客户端
 * @author yangqiong
 */
public interface EmbeddingClient {

    /**
     * 单条文本嵌入
     * @param text
     * @return
     */
    float[] embed(String text);

    /**
     * 批量文本嵌入
     * @param texts
     * @return
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     * @return
     */
    int getDimensions();
}
