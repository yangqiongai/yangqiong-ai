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
package com.yangqiongai.ai.open.capability.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt模板引擎
 * <p>
 * 基于正则表达式实现变量替换，支持 ${variable} 语法。
 * 未找到的变量保留原样输出。
 * </p>
 * @author yangqiong
 */
public class PromptTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateEngine.class);

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 渲染Prompt模板
     * @param templateContent 模板内容
     * @param variables 变量映射
     * @return 渲染后的Prompt
     */
    public String render(String templateContent, Map<String, Object> variables) {
        if (templateContent == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return templateContent;
        }
        try {
            StringBuffer result = new StringBuffer();
            Matcher matcher = VARIABLE_PATTERN.matcher(templateContent);
            while (matcher.find()) {
                String varName = matcher.group(1).trim();
                Object value = variables.get(varName);
                if (value != null) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
                }
            }
            matcher.appendTail(result);
            return result.toString();
        } catch (Exception e) {
            log.warn("Prompt模板渲染失败，使用原模板内容", e);
            return templateContent;
        }
    }
}