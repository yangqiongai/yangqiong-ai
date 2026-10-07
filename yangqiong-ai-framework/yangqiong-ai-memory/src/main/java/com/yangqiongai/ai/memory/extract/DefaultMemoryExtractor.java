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
package com.yangqiongai.ai.memory.extract;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.memory.config.ConditionalOnCloudMemoryMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆提取
 * @author yangqiong
 */
@Service
@ConditionalOnCloudMemoryMode
@ConditionalOnProperty(prefix = "ai.memory.extraction", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DefaultMemoryExtractor implements MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(DefaultMemoryExtractor.class);

    @Prompt(code = "memory.extract", name = "记忆抽取",
            description = "从对话中抽取结构化记忆", category = PromptCategory.BUSINESS,
            readonly = true, visible = false)
    private static final String EXTRACTION_PROMPT_TEMPLATE = """
            从以下文本中提取结构化记忆，仅输出JSON数组（不要输出任何其他内容、不要markdown代码块）：
            [{"type":"FACT","content":"...","key":"...","tags":"...","confidence":0.9,"validFrom":"2026-01-01T00:00:00","validUntil":"2026-12-31T00:00:00"}]
            
            类型说明：
            - FACT: 客观事实（截止日期、项目名称、人员信息等）
            - PREFERENCE: 用户偏好（饮食、编程语言、沟通方式等）
            - CONSTRAINT: 约束条件（预算上限、时间限制等）
            - PROFILE: 用户画像（职业、技能、身份等静态属性）
            - EVENT: 动态事件（发生的事情、行为记录）
            
            字段说明：
            - type: 记忆类型（必须）
            - content: 记忆内容（中文，必须）
            - key: 事实/偏好的键（如"项目截止日期"、"饮食习惯"），可为空
            - tags: 标签（逗号分隔），可为空
            - confidence: 置信度 0-1（必须）
            - validFrom: 生效时间（ISO格式，仅FACT有时间范围时填写），可为空
            - validUntil: 失效时间（ISO格式，仅FACT有时间范围时填写），可为空
            
            文本：{text}""";

    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter[] ISO_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DATE_ONLY_FORMATTER
    };

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    @Autowired(required = false)
    private PromptResolver promptResolver;

    @Value("${ai.memory.extraction.model-code:defaultAgent}")
    private String modelCode;

    /**
     * 从文本中提取结构化记忆条目
     * @param text
     * @param userId
     * @return
     */
    @Override
    public List<ExtractedMemory> extract(String text, String userId) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        if (languageModelFactory == null) {
            log.debug("LanguageModelFactory 未注入, 跳过结构化记忆提取");
            return Collections.emptyList();
        }
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("text", text);
            String prompt = promptResolver == null
                    ? EXTRACTION_PROMPT_TEMPLATE.replace("{text}", text)
                    : promptResolver.resolve("memory.extract", EXTRACTION_PROMPT_TEMPLATE, variables);
            String response = languageModelFactory.generateText(modelCode, prompt);
            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }
            success = true;
            return parseExtractionResponse(response);
        } catch (Exception e) {
            log.warn("结构化记忆提取失败, 降级为不提取: userId={}", userId, e);
            return Collections.emptyList();
        } finally {
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("memory.extract", userId, success, System.currentTimeMillis() - startTime);
            }
        }
    }

    /**
     * 解析LLM返回的结构化记忆JSON数组
     * @param response
     * @return
     */
    private List<ExtractedMemory> parseExtractionResponse(String response) {
        String json = stripMarkdownCodeBlock(response).trim();
        // 提取第一个JSON数组
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("提取响应非JSON数组格式: {}", truncate(json));
            return Collections.emptyList();
        }
        json = json.substring(start, end + 1);
        try {
            JSONArray array = JSON.parseArray(json);
            List<ExtractedMemory> result = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ExtractedMemory memory = parseExtractedMemory(obj);
                if (memory != null) {
                    result.add(memory);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析结构化记忆JSON失败: {}", truncate(json), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析单个记忆JSON对象
     * @param obj
     * @return
     */
    private ExtractedMemory parseExtractedMemory(JSONObject obj) {
        String type = obj.getString("type");
        String content = obj.getString("content");
        if (type == null || type.isBlank() || content == null || content.isBlank()) {
            return null;
        }
        ExtractedMemory memory = new ExtractedMemory();
        memory.setMemoryType(type.toUpperCase());
        memory.setContent(content);
        memory.setKey(obj.getString("key"));
        memory.setTags(obj.getString("tags"));
        Double confidence = obj.getDouble("confidence");
        memory.setConfidence(confidence != null ? confidence : 0.5);
        memory.setValidFrom(parseDateTime(obj.getString("validFrom")));
        memory.setValidUntil(parseDateTime(obj.getString("validUntil")));
        return memory;
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

    /**
     * 解析ISO格式时间字符串
     * @param value
     * @return
     */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : ISO_FORMATS) {
            try {
                if (formatter == DATE_ONLY_FORMATTER) {
                    return java.time.LocalDate.parse(value, formatter).atStartOfDay();
                }
                return LocalDateTime.parse(value, formatter);
            } catch (Exception e) {
                // try next format
            }
        }
        log.debug("无法解析时间字符串: {}", value);
        return null;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
