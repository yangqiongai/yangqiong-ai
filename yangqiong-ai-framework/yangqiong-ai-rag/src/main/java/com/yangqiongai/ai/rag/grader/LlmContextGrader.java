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
package com.yangqiongai.ai.rag.grader;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagCacheConfiguration;
import com.yangqiongai.ai.rag.metrics.RagMetrics;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LLM上下文评估
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.rag.context-grade.enabled", havingValue = "true")
public class LlmContextGrader implements ContextGraderService {

    private static final Logger log = LoggerFactory.getLogger(LlmContextGrader.class);

    @Prompt(code = "rag.context-grade.system", name = "上下文评估系统提示词",
            description = "LLM判断检索证据是否足以回答查询的系统提示词", category = PromptCategory.SYSTEM,
            readonly = true, visible = false)
    private static final String GRADE_SYSTEM_INSTRUCTION =
            "你是一个严谨的上下文评估助手。你的任务是判断提供的证据是否足以完整、准确地回答用户查询。\n" +
            "评估维度（须逐一核查）：\n" +
            "1. 完整性：证据是否覆盖回答查询所需的全部关键信息点\n" +
            "2. 相关性：证据内容是否与查询主题直接相关，无冗余或偏题内容\n" +
            "3. 时效性：证据中的时间信息是否与查询要求的时间范围匹配\n" +
            "4. 具体性：证据是否包含回答查询所需的具体数据、数值或细节，而非笼统描述\n" +
            "判定规则：若四个维度均满足，则sufficient=true；若任一维度不满足，则sufficient=false，并在missing字段列出具体缺失的维度。\n" +
            "只输出JSON，格式严格如下：{\"sufficient\":true/false,\"confidence\":0.0-1.0," +
            "\"missing\":[\"缺失维度1\",\"缺失维度2\"],\"reason\":\"简短理由\"}\n" +
            "说明：\n" +
            "- confidence表示评估结果的置信度，取值范围0.0-1.0\n" +
            "- missing字段仅在sufficient=false时填写，sufficient=true时留空数组\n" +
            "- missing中的维度名称用中文（如时效性、具体数据、完整性、相关性）\n" +
            "- reason字段简明扼要说明判定理由";

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private RagMetrics ragMetrics;

    @Autowired(required = false)
    private PromptResolver promptResolver;

    @Value("${ai.rag.context-grade.model-code:defaultAgent}")
    private String modelCode;

    @Value("${ai.rag.context-grade.max-evidences:10}")
    private int maxEvidences;

    /**
     * 评估证据是否足以回答查询（结果走缓存）
     * @param query
     * @param evidences
     * @return
     */
    @Override
    @Cacheable(value = RagCacheConfiguration.CACHE_CONTEXT_GRADE,
            key = "#query + '_' + #evidences.hashCode()",
            unless = "#result == null || !#result.sufficient")
    public ContextGradeResult grade(String query, List<RetrievalEvidence> evidences) {
        if (query == null || query.isBlank()) {
            return ContextGradeResult.sufficient("查询为空,跳过评估");
        }
        if (evidences == null || evidences.isEmpty()) {
            return ContextGradeResult.insufficient(List.of("完整性"), 0.0, "证据为空");
        }
        if (languageModelFactory == null) {
            log.warn("LanguageModelFactory未注入, 降级返回充分");
            return ContextGradeResult.sufficient("LLM未注入降级");
        }

        long startedAt = System.currentTimeMillis();
        boolean llmSuccess = false;
        try {
            String userPrompt = buildGradePrompt(query, evidences);
            String systemInstruction = promptResolver == null
                    ? GRADE_SYSTEM_INSTRUCTION
                    : promptResolver.resolve("rag.context-grade.system", GRADE_SYSTEM_INSTRUCTION);
            String response = languageModelFactory.generateText(modelCode, systemInstruction, userPrompt);
            llmSuccess = response != null && !response.isBlank();
            ContextGradeResult result = parseGradeResponse(response);
            log.info("上下文评估完成: sufficient={}, confidence={}, missing={}",
                    result.isSufficient(), result.getConfidence(), result.getMissingDimensions());
            return result;
        } catch (Exception e) {
            log.warn("上下文评估失败, 降级返回充分", e);
            return ContextGradeResult.sufficient("评估异常降级");
        } finally {
            if (ragMetrics != null) {
                ragMetrics.recordContextGradeDuration(System.currentTimeMillis() - startedAt);
            }
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("rag.context-grade.system", query, llmSuccess,
                        System.currentTimeMillis() - startedAt);
            }
        }
    }

    /**
     * 构建评估提示词
     * @param query
     * @param evidences
     * @return
     */
    private String buildGradePrompt(String query, List<RetrievalEvidence> evidences) {
        List<RetrievalEvidence> truncated = evidences.size() > maxEvidences
                ? evidences.subList(0, maxEvidences) : evidences;
        StringBuilder sb = new StringBuilder();
        sb.append("查询：").append(query).append("\n\n");
        sb.append("证据：\n");
        for (int i = 0; i < truncated.size(); i++) {
            RetrievalEvidence evidence = truncated.get(i);
            String content = evidence.getContent();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200);
            }
            sb.append("[").append(i).append("] ").append(content).append("\n\n");
        }
        sb.append("请评估上述证据是否足以回答查询，输出JSON：");
        return sb.toString();
    }

    /**
     * 解析LLM评估响应
     * @param response
     * @return
     */
    private ContextGradeResult parseGradeResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("LLM评估响应为空, 降级返回充分");
            return ContextGradeResult.sufficient("响应为空降级");
        }
        String json = stripMarkdownCodeBlock(response).trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("LLM评估响应非JSON格式: {}", truncate(json, 200));
            return ContextGradeResult.sufficient("响应非JSON降级");
        }
        json = json.substring(start, end + 1);
        try {
            JSONObject obj = JSON.parseObject(json);
            Boolean sufficient = obj.getBoolean("sufficient");
            if (sufficient == null) {
                return ContextGradeResult.sufficient("sufficient字段缺失降级");
            }
            Double confidence = obj.getDouble("confidence");
            if (confidence == null) {
                confidence = sufficient ? 1.0 : 0.0;
            }
            String reason = obj.getString("reason");
            if (reason == null) {
                reason = "";
            }
            if (sufficient) {
                return ContextGradeResult.sufficient(reason);
            }
            List<String> missing = parseMissingDimensions(obj);
            return ContextGradeResult.insufficient(missing, confidence, reason);
        } catch (Exception e) {
            log.warn("解析LLM评估JSON失败: {}", truncate(json, 200), e);
            return ContextGradeResult.sufficient("JSON解析失败降级");
        }
    }

    /**
     * 解析缺失维度列表
     * @param obj
     * @return
     */
    private List<String> parseMissingDimensions(JSONObject obj) {
        com.alibaba.fastjson.JSONArray array = obj.getJSONArray("missing");
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> missing = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String dim = array.getString(i);
            if (dim != null && !dim.isBlank()) {
                missing.add(dim.trim());
            }
        }
        return missing;
    }

    /**
     * 去除markdown代码块标记
     * @param text
     * @return
     */
    private String stripMarkdownCodeBlock(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
