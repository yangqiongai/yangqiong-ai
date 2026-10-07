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
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JSON Schema校验器
 * <p>
 * 基于 networknt/json-schema-validator(2.x API) 实现JSON Schema校验。
 * </p>
 * @author yangqiong
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final SchemaRegistry registry;

    /**
     * Schema缓存，避免重复加载
     */
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    public JsonSchemaValidator() {
        this.registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);
    }

    /**
     * 校验JSON对象是否符合Schema
     * @param data 待校验数据
     * @param schemaPath Schema资源路径（如 capabilities/project-overview/output.schema.json）
     * @return 校验结果
     */
    public ValidationResult validate(Object data, String schemaPath) {
        try {
            Schema schema = loadSchema(schemaPath);
            if (schema == null) {
                return ValidationResult.fail(java.util.List.of("Schema文件不存在: " + schemaPath), data);
            }
            JsonNode dataNode = JSON_MAPPER.valueToTree(data);
            List<Error> errors = schema.validate(dataNode);

            if (errors.isEmpty()) {
                return ValidationResult.pass(data);
            }

            java.util.List<String> errorMessages = errors.stream()
                    .map(Error::getMessage)
                    .collect(Collectors.toList());

            return ValidationResult.fail(errorMessages, data);
        } catch (Exception e) {
            log.warn("JSON Schema校验异常: schemaPath={}", schemaPath, e);
            return ValidationResult.fail(java.util.List.of("Schema校验异常: " + e.getMessage()), data);
        }
    }

    /**
     * 校验JSON字符串是否符合Schema
     * @param jsonText JSON字符串
     * @param schemaPath Schema资源路径
     * @return 校验结果
     */
    public ValidationResult validateJson(String jsonText, String schemaPath) {
        try {
            JsonNode dataNode = JSON_MAPPER.readTree(jsonText);
            return validate(dataNode, schemaPath);
        } catch (IOException e) {
            return ValidationResult.fail(java.util.List.of("JSON解析失败: " + e.getMessage()), jsonText);
        }
    }

    /**
     * 使用Schema内容直接校验（不从classpath加载）
     * @param data 待校验数据
     * @param schemaContent Schema JSON字符串内容
     * @return 校验结果
     */
    public ValidationResult validateWithSchemaContent(Object data, String schemaContent) {
        try {
            JsonNode schemaNode = JSON_MAPPER.readTree(schemaContent);
            Schema schema = registry.getSchema(schemaNode);
            JsonNode dataNode = JSON_MAPPER.valueToTree(data);
            List<Error> errors = schema.validate(dataNode);

            if (errors.isEmpty()) {
                return ValidationResult.pass(data);
            }

            java.util.List<String> errorMessages = errors.stream()
                    .map(Error::getMessage)
                    .collect(Collectors.toList());

            return ValidationResult.fail(errorMessages, data);
        } catch (Exception e) {
            log.warn("JSON Schema校验异常（内容模式）", e);
            return ValidationResult.fail(java.util.List.of("Schema校验异常: " + e.getMessage()), data);
        }
    }

    private Schema loadSchema(String schemaPath) {
        return schemaCache.computeIfAbsent(schemaPath, path -> {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource resource = resolver.getResource("classpath:" + path);
                if (!resource.exists()) {
                    log.warn("Schema文件不存在: {}", path);
                    return null;
                }
                try (InputStream is = resource.getInputStream()) {
                    JsonNode schemaNode = JSON_MAPPER.readTree(is);
                    return registry.getSchema(schemaNode);
                }
            } catch (IOException e) {
                log.warn("加载Schema文件失败: {}", path, e);
                return null;
            }
        });
    }

    /**
     * 清除Schema缓存
     */
    public void clearCache() {
        schemaCache.clear();
    }
}