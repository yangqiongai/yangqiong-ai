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
import com.yangqiongai.ai.rag.RagRetrieveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG上下文提供者标准实现
 * @author yangqiong
 */

public class StandardRagContextExecutor implements RagContextProvider {

    private static final Logger log = LoggerFactory.getLogger(StandardRagContextExecutor.class);

    private static final String SECTION_DELIMITER = "\n\n---\n\n";

    private static final String EVIDENCE_TEMPLATE = "【来源文档: %s | 切片: %s | 相似度: %.4f】\n%s";

    private final RagRetrieveService ragRetrieveService;

    /**
     * 默认知识库ID列表，当调用方未指定kbIds时使用
     */
    private final List<String> defaultKbIds;

    public StandardRagContextExecutor(RagRetrieveService ragRetrieveService) {
        this(ragRetrieveService, Collections.emptyList());
    }

    public StandardRagContextExecutor(RagRetrieveService ragRetrieveService, List<String> defaultKbIds) {
        this.ragRetrieveService = ragRetrieveService;
        this.defaultKbIds = defaultKbIds == null ? Collections.emptyList() : defaultKbIds;
    }

    /**
     * 执行RAG检索并返回格式化上下文
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    public String execute(String query, List<String> kbIds, List<String> docIds, int topK) {
        if (query == null || query.isBlank()) {
            log.warn("检索查询语句为空, 跳过RAG上下文构建");
            return "";
        }

        List<String> effectiveKbIds = resolveEffectiveKbIds(kbIds);

        long beginTs = System.currentTimeMillis();
        log.info("开始RAG上下文构建, query长度: {}, kbIds: {}, docIds: {}, topK: {}",
                query.length(), effectiveKbIds, docIds, topK);

        List<RetrievalEvidence> hitRecords = retrieve(query, effectiveKbIds, docIds, topK);

        String formattedContext = hitRecords.stream()
                .map(this::renderEvidence)
                .collect(Collectors.joining(SECTION_DELIMITER));

        long costMs = System.currentTimeMillis() - beginTs;
        log.info("RAG上下文构建完成, 命中条数: {}, 耗时: {}ms, 上下文长度: {}",
                hitRecords.size(), costMs, formattedContext.length());

        return formattedContext;
    }

    /**
     * 解析有效的kbIds，为空时回退到默认知识库
     * @param kbIds
     * @return
     */
    private List<String> resolveEffectiveKbIds(List<String> kbIds) {
        if (kbIds != null && !kbIds.isEmpty()) {
            return kbIds;
        }
        return defaultKbIds;
    }

    /**
     * 执行RAG检索并返回原始结果
     * @param query
     * @param kbIds
     * @param docIds
     * @param topK
     * @return
     */
    @Override
    public List<RetrievalEvidence> retrieve(String query, List<String> kbIds, List<String> docIds, int topK) {
        if (ragRetrieveService == null) {
            return Collections.emptyList();
        }
        try {
            List<String> effectiveKbIds = resolveEffectiveKbIds(kbIds);
            return ragRetrieveService.retrieve(query, effectiveKbIds, docIds, topK);
        } catch (Exception e) {
            log.error("RAG检索异常: query={}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 渲染单条检索证据为文本
     * @param evidence
     * @return
     */
    private String renderEvidence(RetrievalEvidence evidence) {
        String docId = evidence.getSourceDocId() != null ? evidence.getSourceDocId() : "";
        String sliceId = evidence.getSliceId() != null ? evidence.getSliceId() : "";
        double score = evidence.getScore();
        String content = evidence.getContent() != null ? evidence.getContent() : "";
        return String.format(EVIDENCE_TEMPLATE, docId, sliceId, score, content);
    }
}
