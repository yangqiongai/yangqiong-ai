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

import java.util.ArrayList;
import java.util.List;

/**
 * 技能内容格式校验器
 * @author yangqiong
 */
public class SkillContentValidator {

    private final SkillProperties properties;

    public SkillContentValidator(SkillProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验Markdown格式
     * @param markdown
     * @return
     */
    public ValidationResult validate(String markdown) {
        ValidationResult result = new ValidationResult();
        String content = markdown == null ? "" : markdown.trim();

        if (content.isEmpty()) {
            result.addError("技能Markdown内容不能为空");
            return result;
        }

        if (!content.startsWith("---")) {
            result.addError("技能Markdown必须包含YAML front matter");
            return result;
        }

        int closingIndex = findClosingFrontMatter(content);
        if (closingIndex < 0) {
            result.addError("YAML front matter未正确关闭");
            return result;
        }

        int firstNewline = content.indexOf('\n');
        String frontMatter = content.substring(firstNewline + 1, closingIndex).trim();
        validateFrontMatterFields(frontMatter, result);

        int bodyStart = content.indexOf('\n', closingIndex);
        String bodyContent = bodyStart >= 0 ? content.substring(bodyStart + 1).trim() : "";
        validateRequiredSections(bodyContent, result);

        return result;
    }

    /**
     * 校验front matter必填字段
     * @param frontMatter
     * @param result
     */
    private void validateFrontMatterFields(String frontMatter, ValidationResult result) {
        if (!frontMatter.contains("name:")) {
            result.addError("front matter中缺少name字段");
        }

        if (!frontMatter.contains("description:")) {
            result.addError("front matter中缺少description字段");
        }
    }

    /**
     * 校验必要章节
     * @param bodyContent
     * @param result
     */
    private void validateRequiredSections(String bodyContent, ValidationResult result) {
        List<String> requiredSections = properties.getGeneration().getRequiredSections();
        for (String section : requiredSections) {
            if (!bodyContent.contains(section)) {
                result.addError("缺少必要章节: " + section);
            }
        }
    }

    /**
     * 从内容中查找front matter关闭标记（必须独占一行的---）
     * @param content
     * @return 关闭标记的起始位置，未找到返回-1
     */
    private int findClosingFrontMatter(String content) {
        int searchFrom = content.indexOf('\n') + 1;
        while (searchFrom > 0 && searchFrom < content.length()) {
            int lineStart = searchFrom;
            int lineEnd = content.indexOf('\n', searchFrom);
            String line = lineEnd >= 0
                    ? content.substring(lineStart, lineEnd)
                    : content.substring(lineStart);
            if (line.trim().equals("---")) {
                return lineStart;
            }
            if (lineEnd < 0) break;
            searchFrom = lineEnd + 1;
        }
        return -1;
    }

    /**
     * 校验结果
     * @author yangqiong
     */
    public static class ValidationResult {
        /**
         * 是否有效
         */
        private boolean valid = true;

        /**
         * 错误列表
         */
        private final List<String> errors = new ArrayList<>();

        /**
         * 添加错误
         * @param error
         */
        public void addError(String error) {
            errors.add(error);
            valid = false;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
