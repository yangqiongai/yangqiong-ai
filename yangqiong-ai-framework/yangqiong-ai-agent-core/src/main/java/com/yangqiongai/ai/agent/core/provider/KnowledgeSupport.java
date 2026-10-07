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
 * 知识检索支持SPI
 * <p>
 * 由ai-agent-rag模块提供实现，ai-agent-core通过此接口调用知识检索，
 * 避免ai-agent-core直接依赖ai-agent-rag。
 * 当agentConfig配置了knowledgeBase时，由enrichFromAgentConfig调用此接口检索知识。
 * 支持多个知识库同时检索，结果拼接返回。
 * </p>
 * @author yangqiong
 */
public interface KnowledgeSupport {

    /**
     * 检索知识上下文（多库）
     * @param query 查询文本
     * @param kbCodes 知识库编码列表
     * @param topK 每个库的检索数量
     * @return 知识上下文文本，无结果时返回空字符串
     */
    String retrieveKnowledge(String query, List<String> kbCodes, int topK);

    /**
     * 检索知识上下文并携带结构化命中证据（多库）
     * <p>
     * 默认实现复用retrieveKnowledge（无证据），实现方可覆盖以单次检索同时返回两者。
     * </p>
     * @param query 查询文本
     * @param kbCodes 知识库编码列表
     * @param topK 每个库的检索数量
     * @return 上下文文本与证据列表，无结果时证据为空列表
     */
    default KnowledgeRetrievalResult retrieveWithEvidences(String query, List<String> kbCodes, int topK) {
        return new KnowledgeRetrievalResult(retrieveKnowledge(query, kbCodes, topK), List.of());
    }

    /**
     * 检索知识上下文并携带结构化命中证据，支持重排序选项覆盖（默认实现忽略选项走全局配置）
     * @param query 查询文本
     * @param kbCodes 知识库编码列表
     * @param topK 每个库的检索数量
     * @param rerankOptions 重排序覆盖选项，null时跟随全局配置
     * @return 上下文文本与证据列表，无结果时证据为空列表
     */
    default KnowledgeRetrievalResult retrieveWithEvidences(String query, List<String> kbCodes, int topK, RerankOptions rerankOptions) {
        return retrieveWithEvidences(query, kbCodes, topK);
    }
}
