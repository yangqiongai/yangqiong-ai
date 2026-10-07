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
package com.yangqiongai.ai.agent.core.provider;

import java.util.List;

/**
 * 知识检索提供者
 * @author yangqiong
 */
public interface KnowledgeRetrieveProvider {

    /**
     * 执行RAG检索并返回格式化上下文文本
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    String retrieve(String query, List<String> kbIds, List<String> docIds, int topK);
}
