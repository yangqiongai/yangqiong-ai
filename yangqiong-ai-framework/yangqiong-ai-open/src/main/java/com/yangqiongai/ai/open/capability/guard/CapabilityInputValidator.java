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
package com.yangqiongai.ai.open.capability.guard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 能力入参校验器
 * <p>
 * 基于内联输入Schema递归校验arguments：required/type/enum/pattern。
 * 字段定义上的errorMessage作为校验失败的自定义提示（pattern/enum/类型不符时优先返回）。
 * </p>
 * @author yangqiong
 */
public class CapabilityInputValidator {

    private static final Logger log = LoggerFactory.getLogger(CapabilityInputValidator.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 校验入参是否符合输入Schema
     * @param arguments 入参
     * @param schemaContent Schema JSON字符串
     * @return 校验结果
     */
    public ValidationResult validate(Map<String, Object> arguments, String schemaContent) {
        if (schemaContent == null || schemaContent.isBlank()) {
            return ValidationResult.pass(arguments);
        }
        try {
            JsonNode schemaNode = JSON_MAPPER.readTree(schemaContent);
            List<String> errors = new ArrayList<>();
            validateObject(schemaNode, arguments == null ? Map.of() : arguments, "", errors);
            return errors.isEmpty()
                    ? ValidationResult.pass(arguments)
                    : ValidationResult.fail(errors, arguments);
        } catch (Exception e) {
            log.warn("输入Schema解析失败", e);
            return ValidationResult.fail(List.of("输入Schema解析失败: " + e.getMessage()), arguments);
        }
    }

    /**
     * 递归校验对象字段（required与properties）
     * @param schema 当前层Schema节点
     * @param values 当前层数据
     * @param path 数据路径（嵌套时以点连接）
     * @param errors 错误收集
     */
    private void validateObject(JsonNode schema, Map<String, Object> values, String path, List<String> errors) {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode name : required) {
                String field = name.asText();
                if (values.get(field) == null) {
                    errors.add((path.isEmpty() ? field : path + "." + field) + ": 缺少必填入参");
                }
            }
        }
        JsonNode props = schema.get("properties");
        if (props == null || !props.isObject()) {
            return;
        }
        props.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            Object value = values.get(field);
            if (value == null) {
                return;
            }
            validateField(entry.getValue(), value, path.isEmpty() ? field : path + "." + field, errors);
        });
    }

    /**
     * 校验单个字段值（类型/枚举/正则及嵌套结构）
     * @param def 字段Schema定义
     * @param value 字段值
     * @param path 数据路径
     * @param errors 错误收集
     */
    @SuppressWarnings("unchecked")
    private void validateField(JsonNode def, Object value, String path, List<String> errors) {
        String type = def.hasNonNull("type") ? def.get("type").asText() : null;
        if (!matchesType(type, value)) {
            errors.add(fieldMessage(def, path, "类型应为" + typeLabel(type)));
            return;
        }
        if ("object".equals(type) && value instanceof Map) {
            validateObject(def, (Map<String, Object>) value, path, errors);
            return;
        }
        if ("array".equals(type) && value instanceof List && def.has("items") && def.get("items").isObject()) {
            JsonNode items = def.get("items");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                validateField(items, list.get(i), path + "[" + i + "]", errors);
            }
            return;
        }
        JsonNode enumNode = def.get("enum");
        if (enumNode != null && enumNode.isArray() && !enumNode.isEmpty()) {
            boolean hit = false;
            for (JsonNode option : enumNode) {
                if (String.valueOf(value).equals(option.asText())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                List<String> options = new ArrayList<>();
                enumNode.forEach(option -> options.add(option.asText()));
                errors.add(fieldMessage(def, path, "取值必须为: " + String.join(", ", options)));
            }
        }
        JsonNode patternNode = def.get("pattern");
        if (patternNode != null && supportsPattern(type)) {
            String text = String.valueOf(value);
            try {
                // JSON Schema的pattern语义为部分匹配（search）
                if (!Pattern.compile(patternNode.asText()).matcher(text).find()) {
                    errors.add(fieldMessage(def, path, "不符合格式要求"));
                }
            } catch (Exception e) {
                log.warn("正则配置无效，跳过该字段校验: path={}, pattern={}", path, patternNode.asText());
            }
        }
    }

    /**
     * 组装字段校验失败消息（errorMessage自定义提示优先）
     * @param def 字段Schema定义
     * @param path 数据路径
     * @param defaultMessage 默认失败原因
     * @return
     */
    private String fieldMessage(JsonNode def, String path, String defaultMessage) {
        JsonNode message = def.get("errorMessage");
        String custom = message != null && !message.asText().isBlank() ? message.asText() : defaultMessage;
        return path + ": " + custom;
    }

    /**
     * 值是否符合Schema类型（integer要求无小数部分的数字）
     * @param type
     * @param value
     * @return
     */
    private boolean matchesType(String type, Object value) {
        if (type == null) {
            return true;
        }
        switch (type) {
            case "string":
                return value instanceof String;
            case "number":
                return value instanceof Number;
            case "integer":
                if (!(value instanceof Number)) {
                    return false;
                }
                double num = ((Number) value).doubleValue();
                return Double.isFinite(num) && num == Math.floor(num);
            case "boolean":
                return value instanceof Boolean;
            case "object":
                return value instanceof Map;
            case "array":
                return value instanceof List;
            default:
                return true;
        }
    }

    /**
     * 该类型是否执行pattern校验（string直接匹配，number/integer转字符串匹配）
     * @param type
     * @return
     */
    private boolean supportsPattern(String type) {
        return type == null || "string".equals(type) || "number".equals(type) || "integer".equals(type);
    }

    /**
     * 类型中文标签
     * @param type
     * @return
     */
    private String typeLabel(String type) {
        if (type == null) {
            return "未知";
        }
        switch (type) {
            case "string":
                return "字符串";
            case "number":
                return "数字";
            case "integer":
                return "整数";
            case "boolean":
                return "布尔";
            case "object":
                return "对象";
            case "array":
                return "数组";
            default:
                return type;
        }
    }
}
