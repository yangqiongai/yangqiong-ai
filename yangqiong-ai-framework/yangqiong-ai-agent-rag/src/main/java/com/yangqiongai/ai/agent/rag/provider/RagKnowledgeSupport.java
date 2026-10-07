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
package com.yangqiongai.ai.agent.rag.provider;

import com.yangqiongai.ai.agent.core.provider.KnowledgeRetrievalResult;
import com.yangqiongai.ai.agent.core.provider.KnowledgeSupport;
import com.yangqiongai.ai.agent.core.provider.RerankOptions;
import com.yangqiongai.ai.agent.rag.resolver.KnowledgeResolver;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识检索RAG支持
 * <p>
 * 实现ai-agent-core的KnowledgeSupport SPI，桥接KnowledgeResolver。
 * 当agentConfig配置了knowledgeBase时，由AbstractAgentProcessor调用。
 * 支持多个知识库同时检索，结果拼接返回。
 * </p>
 * @author yangqiong
 */
@Component
public class RagKnowledgeSupport implements KnowledgeSupport {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeSupport.class);

    private final ObjectProvider<KnowledgeResolver> knowledgeResolverProvider;

    public RagKnowledgeSupport(ObjectProvider<KnowledgeResolver> knowledgeResolverProvider) {
        this.knowledgeResolverProvider = knowledgeResolverProvider;
    }

    @Override
    public String retrieveKnowledge(String query, List<String> kbCodes, int topK) {
        return retrieveWithEvidences(query, kbCodes, topK).context();
    }

    /**
     * 检索知识上下文并携带结构化命中证据
     * @param query
     * @param kbCodes
     * @param topK
     * @return
     */
    @Override
    public KnowledgeRetrievalResult retrieveWithEvidences(String query, List<String> kbCodes, int topK) {
        return retrieveWithEvidences(query, kbCodes, topK, null);
    }

    /**
     * 检索知识上下文并携带结构化命中证据，支持重排序选项覆盖
     * @param query
     * @param kbCodes
     * @param topK
     * @param rerankOptions
     * @return
     */
    @Override
    public KnowledgeRetrievalResult retrieveWithEvidences(String query, List<String> kbCodes, int topK, RerankOptions rerankOptions) {
        if (query == null || query.isBlank() || kbCodes == null || kbCodes.isEmpty()) {
            return KnowledgeRetrievalResult.empty();
        }
        KnowledgeResolver resolver = knowledgeResolverProvider.getIfAvailable();
        if (resolver == null) {
            log.warn("KnowledgeResolver不可用, 跳过知识检索: kbCodes={}", kbCodes);
            return KnowledgeRetrievalResult.empty();
        }
        List<RetrievalEvidence> allResults = new ArrayList<>();
        for (String kbCode : kbCodes) {
            if (kbCode == null || kbCode.isBlank()) {
                continue;
            }
            try {
                List<RetrievalEvidence> results = rerankOptions != null
                        ? resolver.resolve(query, List.of(kbCode), topK, rerankOptions)
                        : resolver.resolve(query, List.of(kbCode), topK);
                if (results != null && !results.isEmpty()) {
                    allResults.addAll(results);
                }
            } catch (Exception e) {
                log.warn("知识库检索失败: kbCode={}", kbCode, e);
            }
        }
        if (allResults.isEmpty()) {
            return KnowledgeRetrievalResult.empty();
        }
        String context = KnowledgeContextFormatter.format(allResults);
        List<Map<String, Object>> evidences = allResults.stream()
                .map(RagKnowledgeSupport::toEvidenceMap)
                .collect(Collectors.toList());
        log.info("agentConfig知识检索完成: kbCodes={}, topK={}, resultCount={}", kbCodes, topK, allResults.size());
        return new KnowledgeRetrievalResult(context, evidences);
    }

    /**
     * 将检索证据转为Map承载（避免agent-core依赖rag模块模型）
     * @param evidence
     * @return
     */
    public static Map<String, Object> toEvidenceMap(RetrievalEvidence evidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", evidence.getContent());
        map.put("kbId", evidence.getKbId());
        map.put("kbName", evidence.getKbName());
        map.put("sourceDocId", evidence.getSourceDocId());
        map.put("sourceDocName", evidence.getSourceDocName());
        map.put("sliceId", evidence.getSliceId());
        map.put("score", evidence.getScore());
        if (evidence.getBody() != null) {
            map.put("body", evidence.getBody());
        }
        return map;
    }
}
