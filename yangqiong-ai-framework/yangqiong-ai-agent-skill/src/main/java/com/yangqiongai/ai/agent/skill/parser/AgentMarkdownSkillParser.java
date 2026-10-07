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
package com.yangqiongai.ai.agent.skill.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent Markdown技能解析器
 * @author yangqiong
 */
public class AgentMarkdownSkillParser {

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile(
            "(?s)^---[ \\t]*\\r?\\n(.*?)\\r?\\n---[ \\t]*\\r?\\n(.*)$");

    private static final Pattern YAML_LINE_PATTERN = Pattern.compile(
            "^(\\w+)\\s*:\\s*(.*)$");

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^#\\s+(.+)$");

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile(
            "(?m)^---[ \\t]*\\r?\\n");

    /**
     * 解析单个Markdown技能
     * @param markdown
     * @return
     */
    public AgentParsedMarkdown parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new AgentParsedMarkdown(null, null, "", null);
        }
        String text = markdown.trim();
        Matcher fmMatcher = FRONT_MATTER_PATTERN.matcher(text);
        if (fmMatcher.matches()) {
            String frontMatter = fmMatcher.group(1);
            String content = fmMatcher.group(2).trim();
            Map<String, String> yaml = parseYaml(frontMatter);
            return new AgentParsedMarkdown(yaml.get("name"), yaml.get("description"), content, null);
        }
        // 无front matter，尝试从一级标题提取name
        String name = extractHeadingName(text);
        return new AgentParsedMarkdown(name, null, text, null);
    }

    /**
     * 解析包含多个技能的文件
     * @param content
     * @return
     */
    public List<AgentParsedMarkdown> parseFile(String content) {
        List<AgentParsedMarkdown> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        String[] segments = SEPARATOR_PATTERN.split(content);
        List<String> parts = new ArrayList<>();
        for (String segment : segments) {
            if (!segment.isBlank()) {
                parts.add(segment);
            }
        }
        int i = 0;
        while (i < parts.size()) {
            String current = parts.get(i);
            // 当前段为YAML front matter，与下一段正文配对
            if (isYamlLike(current) && i + 1 < parts.size()) {
                Map<String, String> yaml = parseYaml(current);
                String body = parts.get(i + 1).trim();
                result.add(new AgentParsedMarkdown(yaml.get("name"), yaml.get("description"), body, null));
                i += 2;
            } else {
                result.add(parse(current));
                i++;
            }
        }
        return result;
    }

    private Map<String, String> parseYaml(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = YAML_LINE_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                map.put(matcher.group(1).trim(), matcher.group(2).trim());
            }
        }
        return map;
    }

    private boolean isYamlLike(String text) {
        boolean hasKeyValue = false;
        for (String line : text.trim().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!YAML_LINE_PATTERN.matcher(trimmed).matches()) {
                return false;
            }
            hasKeyValue = true;
        }
        return hasKeyValue;
    }

    private String extractHeadingName(String text) {
        for (String line : text.split("\\r?\\n")) {
            Matcher matcher = HEADING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }
}
