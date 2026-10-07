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
package com.yangqiongai.ai.rag.rerank;

import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.metrics.RagMetrics;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LLM重排序
 * @author yangqiong
 */
@Service
public class LlmReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);

    private static final int DEFAULT_MAX_CANDIDATES = 20;

    @Prompt(code = "rag.rerank.system", name = "检索重排序系统提示词",
            description = "LLM对检索结果进行相关性排序的系统提示词", category = PromptCategory.SYSTEM,
            readonly = true, visible = false)
    private static final String RERANK_SYSTEM_INSTRUCTION =
            "你是一个专业的文档相关性排序助手。用户会提供一个查询和一组候选文档（带编号）。" +
            "你的任务是根据每个文档与查询的相关性，从高到低排序，返回排序后的文档编号列表。\n" +
            "排序指导：\n" +
            "1. 优先考虑查询意图与文档内容的语义匹配度，即文档是否直接回应了查询的核心诉求\n" +
            "2. 综合评估文档与查询在主题、关键词、概念层面的相关性\n" +
            "3. 更相关、更切题的文档排在前面，相关性低的排在后面\n" +
            "输出要求：\n" +
            "- 只返回编号列表，用逗号分隔\n" +
            "- 必须包含所有候选文档的编号，不得遗漏\n" +
            "- 不要添加任何其他内容（如解释、说明、markdown标记等）\n" +
            "- 例如：3,1,4,2";

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private RagMetrics ragMetrics;

    @Autowired(required = false)
    private PromptResolver promptResolver;

    @Value("${ai.rag.rerank.model-code:defaultAgent}")
    private String modelCode;

    @Value("${ai.rag.rerank.max-candidates:20}")
    private int maxCandidates;

    @Override
    public String getStrategy() {
        return "llm";
    }

    @Override
    public List<RetrievalEvidence> rerank(List<RetrievalEvidence> evidences, String query, int topK) {
        return rerank(evidences, query, topK, null);
    }

    /**
     * 对检索结果重排序，支持按调用覆盖模型编码
     * @param evidences
     * @param query
     * @param topK
     * @param modelCodeOverride
     * @return
     */
    @Override
    public List<RetrievalEvidence> rerank(List<RetrievalEvidence> evidences, String query, int topK, String modelCodeOverride) {
        if (evidences == null || evidences.isEmpty()) {
            return evidences;
        }
        if (languageModelFactory == null) {
            log.warn("LanguageModelFactory未注入, 跳过Rerank, 返回原始排序");
            return sortByScore(evidences, topK);
        }
        String effectiveModelCode = modelCodeOverride != null && !modelCodeOverride.isBlank()
                ? modelCodeOverride : modelCode;

        long startedAt = System.currentTimeMillis();

        // 超过最大候选数时先按原始score截取
        List<RetrievalEvidence> candidates = evidences.size() > maxCandidates
                ? sortByScore(evidences, maxCandidates) : evidences;

        List<RetrievalEvidence> result;
        boolean llmSuccess = false;
        long llmStartTime = System.currentTimeMillis();
        try {
            String userPrompt = buildRerankPrompt(query, candidates);
            String systemInstruction = promptResolver == null
                    ? RERANK_SYSTEM_INSTRUCTION
                    : promptResolver.resolve("rag.rerank.system", RERANK_SYSTEM_INSTRUCTION);
            String llmResponse = languageModelFactory.generateText(effectiveModelCode, systemInstruction, userPrompt);
            List<Integer> rankedIndices = parseRankedIndices(llmResponse, candidates.size());
            llmSuccess = !rankedIndices.isEmpty();

            if (rankedIndices.isEmpty()) {
                log.warn("LLM Rerank解析失败, 返回原始排序");
                result = sortByScore(evidences, topK);
            } else {
                List<RetrievalEvidence> ranked = new ArrayList<>();
                for (int index : rankedIndices) {
                    if (index >= 0 && index < candidates.size()) {
                        ranked.add(candidates.get(index));
                    }
                    if (ranked.size() >= topK) {
                        break;
                    }
                }

                // 补充未被LLM排序的结果
                for (RetrievalEvidence evidence : candidates) {
                    if (ranked.size() >= topK) {
                        break;
                    }
                    if (!ranked.contains(evidence)) {
                        ranked.add(evidence);
                    }
                }
                result = ranked;
                log.info("LLM Rerank完成, 候选数: {}, 返回数: {}", candidates.size(), result.size());
            }
        } catch (Exception e) {
            log.warn("LLM Rerank失败, 返回原始排序", e);
            result = sortByScore(evidences, topK);
        } finally {
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("rag.rerank.system", query, llmSuccess,
                        System.currentTimeMillis() - llmStartTime);
            }
        }

        if (ragMetrics != null) {
            ragMetrics.recordRerankDuration(System.currentTimeMillis() - startedAt);
        }
        return result;
    }

    /**
     * 构建Rerank提示词
     * @param query
     * @param candidates
     * @return
     */
    private String buildRerankPrompt(String query, List<RetrievalEvidence> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询：").append(query).append("\n\n");
        sb.append("候选文档：\n");
        for (int i = 0; i < candidates.size(); i++) {
            RetrievalEvidence evidence = candidates.get(i);
            String content = evidence.getContent();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200);
            }
            sb.append("[").append(i).append("] ").append(content).append("\n\n");
        }
        sb.append("请按相关性从高到低排序, 返回编号列表（逗号分隔）：");
        return sb.toString();
    }

    /**
     * 解析LLM返回的排序索引
     * @param response
     * @param maxSize
     * @return
     */
    private List<Integer> parseRankedIndices(String response, int maxSize) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        String cleaned = response.replaceAll("[^0-9,]", "");
        for (String part : cleaned.split(",")) {
            try {
                int index = Integer.parseInt(part.trim());
                if (index >= 0 && index < maxSize && !indices.contains(index)) {
                    indices.add(index);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return indices;
    }

    /**
     * 按原始score降序排序并截取
     * @param evidences
     * @param limit
     * @return
     */
    private List<RetrievalEvidence> sortByScore(List<RetrievalEvidence> evidences, int limit) {
        return evidences.stream()
                .sorted(Comparator.comparingDouble(RetrievalEvidence::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
