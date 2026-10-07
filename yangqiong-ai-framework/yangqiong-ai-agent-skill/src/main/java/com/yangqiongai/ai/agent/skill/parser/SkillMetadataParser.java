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

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;

import java.util.*;

/**
 * 技能元数据解析器
 * <p>
 * 委托AgentMarkdownSkillParser解析Markdown正文，本地解析YAML Front Matter提取元数据，支持嵌套对象。
 * </p>
 * @author yangqiong
 */
public class SkillMetadataParser {

    private static final AgentMarkdownSkillParser PARSER = new AgentMarkdownSkillParser();

    /**
     * 解析Markdown技能文件
     * @param content
     * @param skillId
     * @return
     */
    public static SkillDefinition parse(String content, String skillId) {
        if (content == null || content.isBlank()) {
            SkillDefinition def = new SkillDefinition();
            def.setSkillId(skillId);
            return def;
        }

        AgentParsedMarkdown parsed = PARSER.parse(content);
        Map<String, Object> meta = parseFrontMatter(content);

        SkillDefinition def = new SkillDefinition();
        def.setSkillId(skillId);
        def.setSkillName(getString(meta, "name", skillId));
        def.setSkillDescription(getString(meta, "description", ""));
        def.setSkillType(getString(meta, "type", "BUILTIN"));
        def.setBoundTools(getStringList(meta, "bound_tools"));
        def.setResources(getStringMap(meta, "resources"));
        def.setExecution(getMap(meta, "execution"));
        def.setDependencies(getMap(meta, "dependencies"));
        def.setPresetParameters(getMap(meta, "preset_parameters"));
        def.setConditions(getMap(meta, "conditions"));
        def.setSkillContent(parsed.getContent());
        def.setSkillVersion(getInt(meta, "version", 1));
        def.setTrustLevel(TrustLevel.fromString(getString(meta, "trust_level", null)));

        return def;
    }

    /**
     * 获取字符串值
     * @param map
     * @param key
     * @param defaultVal
     * @return
     */
    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultVal;
    }

    /**
     * 获取字符串列表
     * @param map
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) return (List<String>) v;
        return Collections.emptyList();
    }

    /**
     * 获取Map值
     * @param map
     * @return
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return null;
    }

    /**
     * 获取字符串Map值（resources等字段）
     * @param map
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> getStringMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof Map)) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) v).entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    /**
     * 获取整数值
     * @param map
     * @param key
     * @param defaultVal
     * @return
     */
    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt((String) v); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    /**
     * 解析YAML Front Matter，支持嵌套对象和列表
     * @param content
     * @return
     */
    private static Map<String, Object> parseFrontMatter(String content) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return meta;
        }
        String text = content.trim();
        if (!text.startsWith("---")) {
            return meta;
        }
        String[] lines = text.split("\\r?\\n", -1);
        // 查找front matter结束标记
        int endLine = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals("---")) {
                endLine = i;
                break;
            }
        }
        if (endLine <= 1) {
            return meta;
        }
        // 解析YAML行（支持嵌套对象和列表）
        int i = 1;
        while (i < endLine) {
            String line = lines[i];
            if (line.isBlank()) {
                i++;
                continue;
            }
            if (getIndent(line) > 0) {
                i++;
                continue;
            }
            String trimmed = line.trim();
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) {
                i++;
                continue;
            }
            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();
            if (!value.isEmpty()) {
                // 简单标量值
                meta.put(key, value);
                i++;
            } else {
                // 收集后续缩进行构建嵌套结构
                List<String> childLines = new ArrayList<>();
                int j = i + 1;
                while (j < endLine) {
                    String childLine = lines[j];
                    if (childLine.isBlank()) {
                        j++;
                        continue;
                    }
                    if (getIndent(childLine) > 0) {
                        childLines.add(childLine.trim());
                        j++;
                    } else {
                        break;
                    }
                }
                if (!childLines.isEmpty()) {
                    if (childLines.get(0).startsWith("-")) {
                        // 列表
                        List<String> list = new ArrayList<>();
                        for (String cl : childLines) {
                            String item = cl.replaceFirst("^-\\s*", "").trim();
                            if (!item.isEmpty()) {
                                list.add(item);
                            }
                        }
                        meta.put(key, list);
                    } else {
                        // 嵌套Map
                        Map<String, Object> nested = new LinkedHashMap<>();
                        for (String cl : childLines) {
                            int ci = cl.indexOf(':');
                            if (ci > 0) {
                                String nk = cl.substring(0, ci).trim();
                                String nv = cl.substring(ci + 1).trim();
                                nested.put(nk, nv.isEmpty() ? null : nv);
                            }
                        }
                        meta.put(key, nested);
                    }
                }
                i = j;
            }
        }
        return meta;
    }

    /**
     * 计算行首缩进空格数
     * @param line
     * @return
     */
    private static int getIndent(String line) {
        int indent = 0;
        while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }
        return indent;
    }
}
