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
package com.yangqiongai.ai.agent.rag.resolver;

import com.yangqiongai.ai.agent.core.provider.RerankOptions;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.RagRetrieveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 默认知识解析器
 * @author yangqiong
 */
@Component
public class DefaultKnowledgeResolver implements KnowledgeResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeResolver.class);

    private final ObjectProvider<RagRetrieveService> ragRetrieveServiceProvider;

    public DefaultKnowledgeResolver(ObjectProvider<RagRetrieveService> ragRetrieveServiceProvider) {
        this.ragRetrieveServiceProvider = ragRetrieveServiceProvider;
    }

    /**
     * 解析知识上下文
     * @param query
     * @param kbIds
     * @param topK
     * @return
     */
    @Override
    public List<RetrievalEvidence> resolve(String query, List<String> kbIds, int topK) {
        return resolve(query, kbIds, topK, null);
    }

    /**
     * 解析知识上下文，支持重排序选项覆盖
     * @param query
     * @param kbIds
     * @param topK
     * @param rerankOptions
     * @return
     */
    @Override
    public List<RetrievalEvidence> resolve(String query, List<String> kbIds, int topK, RerankOptions rerankOptions) {
        RagRetrieveService ragRetrieveService = ragRetrieveServiceProvider.getIfAvailable();
        if (ragRetrieveService == null) {
            log.warn("RagRetrieveService不可用, 返回空知识上下文: query={}", query);
            return Collections.emptyList();
        }

        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        if (kbIds == null || kbIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<RetrievalEvidence> results = ragRetrieveService.retrieve(query, kbIds, null, topK, rerankOptions);
            if (results == null) {
                return Collections.emptyList();
            }
            return results.stream().limit(topK).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("知识解析失败: query={}, kbIds={}", query, kbIds, e);
            return Collections.emptyList();
        }
    }
}
