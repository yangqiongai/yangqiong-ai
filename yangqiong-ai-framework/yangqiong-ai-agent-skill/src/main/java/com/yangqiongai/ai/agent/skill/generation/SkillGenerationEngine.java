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
package com.yangqiongai.ai.agent.skill.generation;

import com.yangqiongai.ai.agent.skill.config.SkillProperties;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 技能草稿引擎
 * @author yangqiong
 */
@Service
public class SkillGenerationEngine {

    private static final Logger log = LoggerFactory.getLogger(SkillGenerationEngine.class);

    private final String extractionPrompt;

    private final String renderPrompt;

    private final SkillProperties properties;

    @Autowired(required = false)
    private LanguageModelFactory languageModelFactory;

    private final SkillContentValidator contentValidator;

    public SkillGenerationEngine(SkillContentValidator contentValidator, SkillProperties properties) {
        this.contentValidator = contentValidator;
        this.properties = properties;
        this.extractionPrompt = loadPrompt("prompts/skill-extract-spec.txt");
        this.renderPrompt = loadPrompt("prompts/skill-render-markdown.txt");
    }

    /**
     * 加载外部Prompt模板
     * @param path
     * @return
     */
    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                if (content.isBlank()) {
                    log.error("Prompt模板内容为空: {}", path);
                    throw new IllegalStateException("Prompt模板内容为空: " + path);
                }
                return content;
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("加载Prompt模板失败: " + path, e);
        }
    }

    /**
     * 生成技能规范
     * @param skillName
     * @param description
     * @return
     */
    public GenerationResult generateSkillSpec(String skillName, String description) {
        try {
            String requirements = extractRequirements(description);
            if (requirements == null || requirements.isBlank()) {
                return GenerationResult.builder()
                        .success(false)
                        .errorMessage("需求提取失败")
                        .build();
            }
            return GenerationResult.builder()
                    .success(true)
                    .spec(requirements)
                    .build();
        } catch (Exception e) {
            log.error("技能规范生成异常 skillName={}", skillName, e);
            return GenerationResult.builder()
                    .success(false)
                    .errorMessage("规范生成异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 渲染技能Markdown
     * @param spec
     * @return
     */
    public GenerationResult renderSkillMarkdown(String spec) {
        try {
            String markdown;
            if (languageModelFactory == null) {
                markdown = renderFromTemplate(spec);
            } else {
                String prompt = String.format(renderPrompt, spec);
                markdown = languageModelFactory.generateText(properties.getGeneration().getModelCode(), prompt);
            }

            if (markdown == null || markdown.isBlank()) {
                return GenerationResult.builder()
                        .success(false)
                        .errorMessage("Markdown渲染失败")
                        .build();
            }

            SkillContentValidator.ValidationResult validation = contentValidator.validate(markdown);
            if (!validation.isValid()) {
                return GenerationResult.builder()
                        .success(false)
                        .errorMessage("Markdown校验未通过: " + String.join(", ", validation.getErrors()))
                        .build();
            }

            return GenerationResult.builder()
                    .success(true)
                    .spec(spec)
                    .markdown(markdown)
                    .build();
        } catch (Exception e) {
            log.error("Markdown渲染异常", e);
            return GenerationResult.builder()
                    .success(false)
                    .errorMessage("渲染异常: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 从用户描述提取结构化需求
     * @param description
     * @return
     */
    public String extractRequirements(String description) {
        if (languageModelFactory == null) {
            log.warn("LanguageModelFactory未注入，返回原始描述作为规范");
            return buildFallbackSpec(description);
        }

        try {
            String prompt = String.format(extractionPrompt,
                    description != null ? description : "");
            return languageModelFactory.generateText(properties.getGeneration().getModelCode(), prompt);
        } catch (Exception e) {
            log.error("LLM需求提取失败", e);
            return buildFallbackSpec(description);
        }
    }

    /**
     * 构建兜底规范
     * @param description
     * @return
     */
    private String buildFallbackSpec(String description) {
        return """
                {
                  "skillName": "custom-skill",
                  "displayName": "自定义技能",
                  "description": "%s",
                  "workflow": ["分析用户意图", "执行对应操作", "返回结果"],
                  "inputs": ["userInput"],
                  "outputs": ["result"]
                }
                """.formatted(description != null ? description.replace("\"", "\\\"") : "用户自定义技能");
    }

    /**
     * 模板渲染Markdown
     * @param spec
     * @return
     */
    private String renderFromTemplate(String spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: custom-skill\n");
        sb.append("description: 自动生成的技能\n");
        sb.append("version: 1.0.0\n");
        sb.append("type: simple\n");
        sb.append("---\n\n");
        sb.append("## 目标\n\n");
        sb.append("根据用户描述执行相应操作\n\n");
        sb.append("## 工作流\n\n");
        sb.append("- 分析用户意图\n");
        sb.append("- 执行对应操作\n");
        sb.append("- 返回结果\n\n");
        sb.append("## 输入\n\n");
        sb.append("- userInput\n\n");
        sb.append("## 输出\n\n");
        sb.append("- result\n");
        return sb.toString();
    }

    /**
     * 生成结果
     * @author yangqiong
     */
    @Getter
    @Builder
    public static class GenerationResult {

        /**
         * 是否成功
         */
        private final boolean success;

        /**
         * 技能规范
         */
        private final String spec;

        /**
         * Markdown内容
         */
        private final String markdown;

        /**
         * 错误消息
         */
        private final String errorMessage;
    }
}
