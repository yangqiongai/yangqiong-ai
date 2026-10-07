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

import java.util.List;
import java.util.Map;

/**
 * 命名检索器
 * <p>
 * RAG检索SPI，作为rag_search工具的数据源，按名称注册后由模型按名调用。
 * </p>
 * @author yangqiong
 */
public interface Retriever {

    /**
     * 执行检索
     * @param query 查询文本
     * @param topK 返回条数上限
     * @param filters 过滤条件，可为null
     * @return 检索结果列表
     */
    List<RetrievedChunk> retrieve(String query, int topK, Map<String, Object> filters);
}
