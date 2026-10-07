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

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * RAG上下文感知检索工具
 * @author yangqiong
 */
public class ContextAwareRagTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ContextAwareRagTool.class);

    private static final int DEFAULT_TOP_K = 5;

    private final RagContextProvider contextProvider;

    public ContextAwareRagTool(RagContextProvider contextProvider) {
        if (contextProvider == null) {
            throw new IllegalArgumentException("contextProvider不允许为空");
        }
        this.contextProvider = contextProvider;
    }

    /**
     * 从知识库中检索与问题相关的上下文信息
     * @param query
     * @param kbIds
     * @param docIds
     * @return
     */
    @AgentTool("从指定知识库或文档中检索与问题相关的上下文，返回结果可作为回答依据")
    public String fetch_rag_context(@AgentToolParam("检索关键词或问题") String query, @AgentToolParam(value = "知识库ID列表，为空时检索所有知识库", required = false) List<String> kbIds, @AgentToolParam(value = "文档ID列表，为空时检索所有文档", required = false) List<String> docIds) {
        String trimmedQuery = query == null ? "" : query.trim();

        if (trimmedQuery.isEmpty()) {
            log.warn("检索语句为空, 跳过RAG上下文获取");
            return "";
        }

        List<String> safeKbIds = kbIds == null ? Collections.emptyList() : kbIds;
        List<String> safeDocIds = docIds == null ? Collections.emptyList() : docIds;

        log.info("开始RAG上下文检索: query长度={}, kbIds数量={}, docIds数量={}",
                trimmedQuery.length(), safeKbIds.size(), safeDocIds.size());

        String contextResult = contextProvider.execute(trimmedQuery, safeKbIds, safeDocIds, DEFAULT_TOP_K);

        String output = contextResult == null ? "" : contextResult;
        log.info("RAG检索完成: 上下文长度={}", output.length());
        return output;
    }
}
