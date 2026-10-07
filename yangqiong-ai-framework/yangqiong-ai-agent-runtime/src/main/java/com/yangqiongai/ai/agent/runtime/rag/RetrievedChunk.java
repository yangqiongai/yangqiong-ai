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
package com.yangqiongai.ai.agent.runtime.rag;

import java.util.Map;

/**
 * 检索结果块
 * @author yangqiong
 * @param content 检索内容
 * @param score 匹配分值
 * @param source 来源标识
 * @param metadata 扩展元数据
 */
public record RetrievedChunk(
        String content,
        double score,
        String source,
        Map<String, Object> metadata
) {

    /**
     * 创建检索结果块
     * @param content
     * @param score
     * @param source
     * @return
     */
    public static RetrievedChunk of(String content, double score, String source) {
        return new RetrievedChunk(content, score, source, Map.of());
    }

    /**
     * 创建检索结果块
     * @param content
     * @param score
     * @param source
     * @param metadata
     * @return
     */
    public static RetrievedChunk of(String content, double score, String source, Map<String, Object> metadata) {
        return new RetrievedChunk(content, score, source, metadata);
    }
}
