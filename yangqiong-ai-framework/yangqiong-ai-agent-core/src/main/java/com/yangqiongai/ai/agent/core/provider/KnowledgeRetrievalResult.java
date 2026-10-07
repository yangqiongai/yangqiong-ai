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
import java.util.Map;

/**
 * 知识检索结果
 * <p>
 * 携带注入用的上下文文本与结构化命中证据（知识库/文档/切片信息），
 * 证据以Map承载避免agent-core直接依赖rag模块。
 * </p>
 * @param context 知识上下文文本
 * @param evidences 命中证据列表（content/kbId/kbName/sourceDocId/sourceDocName/sliceId/score等）
 * @author yangqiong
 */
public record KnowledgeRetrievalResult(String context, List<Map<String, Object>> evidences) {

    /**
     * 空结果
     * @return
     */
    public static KnowledgeRetrievalResult empty() {
        return new KnowledgeRetrievalResult("", List.of());
    }

    /**
     * 是否无证据
     * @return
     */
    public boolean isEmpty() {
        return context == null || context.isBlank();
    }
}
