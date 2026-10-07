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
package com.yangqiongai.ai.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent输出Schema校验器
 * <p>
 * 基于networknt/json-schema-validator(2.x API)的draft-07校验通用工具，
 * 供配置校验、输出契约中间件、评测Schema匹配策略共用。
 * Schema按内容哈希缓存，避免重复编译。
 * </p>
 * @author yangqiong
 */
public final class AgentOutputSchemaValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    /**
     * Schema缓存，key为Schema内容SHA-256
     */
    private static final ConcurrentHashMap<String, Schema> SCHEMA_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存上限，防止Schema无限增长
     */
    private static final int CACHE_MAX_SIZE = 256;

    /**
     * draft-07合法的基础类型
     */
    private static final List<String> DRAFT07_TYPES =
            List.of("object", "array", "string", "number", "integer", "boolean", "null");

    private AgentOutputSchemaValidator() {
    }

    /**
     * 校验Schema本身是否为合法的draft-07 JSON Schema
     * @param schemaJson Schema内容
     * @return 错误列表，空列表表示合法；schemaJson为空时视为未配置，返回空列表
     */
    public static List<String> validateSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return List.of();
        }
        JsonNode schemaNode;
        try {
            schemaNode = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            return List.of("outputSchema不是合法JSON: " + e.getMessage());
        }
        if (schemaNode == null || !schemaNode.isObject()) {
            return List.of("outputSchema必须是JSON对象");
        }
        try {
            buildSchema(schemaNode, schemaJson);
            return checkDraft07Subset(schemaNode);
        } catch (Exception e) {
            return List.of("outputSchema不是合法的JSON Schema(draft-07): " + e.getMessage());
        }
    }

    /**
     * 校验输出文本是否符合Schema
     * @param schemaJson Schema内容
     * @param outputText 模型输出文本（JSON或markdown代码块包裹的JSON）
     * @return 错误列表，空列表表示合规；schemaJson为空时视为未配置契约，返回空列表
     */
    public static List<String> validateOutput(String schemaJson, String outputText) {
        if (schemaJson == null || schemaJson.isBlank()) {
            return List.of();
        }
        JsonNode schemaNode;
        try {
            schemaNode = MAPPER.readTree(schemaJson);
        } catch (Exception e) {
            return List.of("outputSchema不是合法JSON: " + e.getMessage());
        }
        Schema schema;
        try {
            schema = buildSchema(schemaNode, schemaJson);
        } catch (Exception e) {
            return List.of("outputSchema不是合法的JSON Schema(draft-07): " + e.getMessage());
        }
        if (outputText == null || outputText.isBlank()) {
            return List.of("输出内容为空");
        }
        String jsonText = extractJsonText(outputText);
        JsonNode outputNode;
        try {
            outputNode = MAPPER.readTree(jsonText);
        } catch (Exception e) {
            return List.of("输出内容不是合法JSON: " + e.getMessage());
        }
        try {
            List<Error> errors = schema.validate(outputNode);
            if (errors.isEmpty()) {
                return List.of();
            }
            return errors.stream()
                    .map(Error::getMessage)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of("Schema校验异常: " + e.getMessage());
        }
    }

    /**
     * 判断Schema是否合法
     * @param schemaJson
     * @return
     */
    public static boolean isSchemaValid(String schemaJson) {
        return validateSchema(schemaJson).isEmpty();
    }

    /**
     * draft-07常用关键字子集结构校验（networknt编译期对部分元约束宽松，此处补充把关）
     * @param schemaNode
     * @return
     */
    private static List<String> checkDraft07Subset(JsonNode schemaNode) {
        List<String> errors = new ArrayList<>();
        checkTypeKeyword(schemaNode, errors);
        for (String field : List.of("properties", "patternProperties", "definitions")) {
            JsonNode node = schemaNode.get(field);
            if (node != null && !node.isNull() && !node.isObject()) {
                errors.add(field + "必须是JSON对象");
            }
        }
        JsonNode required = schemaNode.get("required");
        if (required != null && !required.isNull()) {
            if (!required.isArray()) {
                errors.add("required必须是字符串数组");
            } else {
                for (JsonNode item : required) {
                    if (!item.isTextual()) {
                        errors.add("required必须是字符串数组");
                        break;
                    }
                }
            }
        }
        JsonNode items = schemaNode.get("items");
        if (items != null && !items.isNull() && !items.isObject() && !items.isArray()) {
            errors.add("items必须是JSON对象或数组");
        }
        JsonNode enumNode = schemaNode.get("enum");
        if (enumNode != null && !enumNode.isNull() && (!enumNode.isArray() || enumNode.size() == 0)) {
            errors.add("enum必须是非空数组");
        }
        return errors;
    }

    /**
     * 校验type关键字取值（单个字符串或字符串数组）
     * @param schemaNode
     * @param errors
     */
    private static void checkTypeKeyword(JsonNode schemaNode, List<String> errors) {
        JsonNode type = schemaNode.get("type");
        if (type == null || type.isNull()) {
            return;
        }
        if (type.isTextual()) {
            if (!DRAFT07_TYPES.contains(type.asText())) {
                errors.add("type取值非法: " + type.asText());
            }
            return;
        }
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (!item.isTextual() || !DRAFT07_TYPES.contains(item.asText())) {
                    errors.add("type数组包含非法取值");
                    return;
                }
            }
            return;
        }
        errors.add("type必须是字符串或字符串数组");
    }

    /**
     * 编译Schema并缓存
     * @param schemaNode
     * @param schemaJson
     * @return
     */
    private static Schema buildSchema(JsonNode schemaNode, String schemaJson) {
        String cacheKey = sha256(schemaJson);
        Schema cached = SCHEMA_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Schema schema = REGISTRY.getSchema(schemaNode);
        if (SCHEMA_CACHE.size() >= CACHE_MAX_SIZE) {
            SCHEMA_CACHE.clear();
        }
        SCHEMA_CACHE.put(cacheKey, schema);
        return schema;
    }

    /**
     * 从模型输出中提取JSON文本，容忍markdown代码块包裹
     * @param outputText
     * @return
     */
    private static String extractJsonText(String outputText) {
        String text = outputText.trim();
        int fenceIdx = text.indexOf("```");
        if (fenceIdx >= 0) {
            int contentStart = text.indexOf('\n', fenceIdx);
            int fenceEnd = text.indexOf("```", contentStart > 0 ? contentStart : fenceIdx + 3);
            if (contentStart > 0 && fenceEnd > contentStart) {
                text = text.substring(contentStart + 1, fenceEnd).trim();
            }
        }
        return text;
    }

    /**
     * 计算SHA-256
     * @param content
     * @return
     */
    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256必然存在，理论上不会到达
            return String.valueOf(content.hashCode());
        }
    }
}
