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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AI平台JSON工具
 * @author yangqiong
 */
public class AiJsonUtils {

    private static final Logger log = LoggerFactory.getLogger(AiJsonUtils.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * 对象转JSON字符串
     * @param obj
     * @return
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON字符串转对象
     * @param json
     * @param clazz
     * @return
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON字符串转List
     * @param json
     * @param elementType
     * @return
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> elementType) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化List失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * JSON字符串转Map
     * @param json
     * @return
     */
    public static Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("JSON反序列化Map失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 获取ObjectMapper实例
     * @return
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 读取JsonNode中可空的字符串字段
     * @param node
     * @param fieldName
     * @return
     */
    public static String readNullable(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asText();
    }

    /**
     * 读取JsonNode中可空的整数字段
     * @param node
     * @param fieldName
     * @return
     */
    public static Integer readNullableInt(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asInt();
    }

    /**
     * 读取JsonNode中可空的长整数字段
     * @param node
     * @param fieldName
     * @return
     */
    public static Long readNullableLong(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asLong();
    }

    /**
     * 读取JsonNode中可空的布尔字段
     * @param node
     * @param fieldName
     * @return
     */
    public static Boolean readNullableBoolean(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        return node.get(fieldName).asBoolean();
    }
}
