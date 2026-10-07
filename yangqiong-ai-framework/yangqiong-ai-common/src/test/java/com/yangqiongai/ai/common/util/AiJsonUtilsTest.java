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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiJsonUtils 单元测试")
class AiJsonUtilsTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = AiJsonUtils.getMapper();
    }

    @Nested
    @DisplayName("toJson 测试")
    class ToJsonTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(AiJsonUtils.toJson(null)).isNull();
        }

        @Test
        @DisplayName("简单对象序列化")
        void shouldSerializeSimpleObject() {
            String json = AiJsonUtils.toJson(Map.of("name", "test", "value", 42));
            assertThat(json).contains("\"name\"").contains("\"test\"").contains("42");
        }

        @Test
        @DisplayName("复杂对象序列化")
        void shouldSerializeComplexObject() {
            Map<String, Object> complex = Map.of(
                    "user", Map.of("id", 1, "name", "张三"),
                    "tags", List.of("admin", "vip")
            );
            String json = AiJsonUtils.toJson(complex);
            assertThat(json).isNotNull();
            assertThat(json).contains("\"user\"").contains("\"tags\"");
        }
    }

    @Nested
    @DisplayName("fromJson 测试")
    class FromJsonTest {

        @Test
        @DisplayName("null返回null")
        void shouldReturnNullForNull() {
            assertThat(AiJsonUtils.fromJson(null, Map.class)).isNull();
        }

        @Test
        @DisplayName("空字符串返回null")
        void shouldReturnNullForEmpty() {
            assertThat(AiJsonUtils.fromJson("", Map.class)).isNull();
        }

        @Test
        @DisplayName("有效JSON正确反序列化")
        void shouldDeserializeValidJson() {
            String json = "{\"name\":\"test\",\"age\":25}";
            Map result = AiJsonUtils.fromJson(json, Map.class);

            assertThat(result).isNotNull();
            assertThat(result).containsEntry("name", "test");
            assertThat(result).containsEntry("age", 25);
        }

        @Test
        @DisplayName("无效JSON返回null")
        void shouldReturnNullForInvalidJson() {
            assertThat(AiJsonUtils.fromJson("{invalid}", Map.class)).isNull();
        }
    }

    @Nested
    @DisplayName("fromJsonToList 测试")
    class FromJsonToListTest {

        @Test
        @DisplayName("有效JSON数组正确反序列化")
        void shouldDeserializeValidJsonArray() {
            String json = "[\"a\",\"b\",\"c\"]";
            List<String> result = AiJsonUtils.fromJsonToList(json, String.class);

            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("无效JSON返回空列表")
        void shouldReturnEmptyListForInvalidJson() {
            assertThat(AiJsonUtils.fromJsonToList("not json", String.class)).isEmpty();
        }

        @Test
        @DisplayName("null返回空列表")
        void shouldReturnEmptyListForNull() {
            assertThat(AiJsonUtils.fromJsonToList(null, String.class)).isEmpty();
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void shouldReturnEmptyListForEmpty() {
            assertThat(AiJsonUtils.fromJsonToList("", String.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromJsonToMap 测试")
    class FromJsonToMapTest {

        @Test
        @DisplayName("有效JSON对象正确反序列化")
        void shouldDeserializeValidJsonObject() {
            String json = "{\"key1\":\"value1\",\"key2\":123}";
            Map<String, Object> result = AiJsonUtils.fromJsonToMap(json);

            assertThat(result).containsEntry("key1", "value1");
            assertThat(result).containsEntry("key2", 123);
        }

        @Test
        @DisplayName("无效JSON返回空Map")
        void shouldReturnEmptyMapForInvalidJson() {
            assertThat(AiJsonUtils.fromJsonToMap("not json")).isEmpty();
        }

        @Test
        @DisplayName("null返回空Map")
        void shouldReturnEmptyMapForNull() {
            assertThat(AiJsonUtils.fromJsonToMap(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("readNullable 测试")
    class ReadNullableTest {

        @Test
        @DisplayName("null node返回null")
        void shouldReturnNullForNullNode() {
            assertThat(AiJsonUtils.readNullable(null, "field")).isNull();
        }

        @Test
        @DisplayName("不存在的字段返回null")
        void shouldReturnNullForMissingField() throws Exception {
            JsonNode node = objectMapper.readTree("{\"name\":\"test\"}");
            assertThat(AiJsonUtils.readNullable(node, "missing")).isNull();
        }

        @Test
        @DisplayName("null值字段返回null")
        void shouldReturnNullForNullValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":null}");
            assertThat(AiJsonUtils.readNullable(node, "field")).isNull();
        }

        @Test
        @DisplayName("存在的字符串字段返回值")
        void shouldReturnPresentValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":\"hello\"}");
            assertThat(AiJsonUtils.readNullable(node, "field")).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("readNullableInt 测试")
    class ReadNullableIntTest {

        @Test
        @DisplayName("null node返回null")
        void shouldReturnNullForNullNode() {
            assertThat(AiJsonUtils.readNullableInt(null, "field")).isNull();
        }

        @Test
        @DisplayName("不存在的字段返回null")
        void shouldReturnNullForMissingField() throws Exception {
            JsonNode node = objectMapper.readTree("{\"name\":\"test\"}");
            assertThat(AiJsonUtils.readNullableInt(node, "missing")).isNull();
        }

        @Test
        @DisplayName("null值字段返回null")
        void shouldReturnNullForNullValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":null}");
            assertThat(AiJsonUtils.readNullableInt(node, "field")).isNull();
        }

        @Test
        @DisplayName("存在的整数字段返回值")
        void shouldReturnPresentIntValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":42}");
            assertThat(AiJsonUtils.readNullableInt(node, "field")).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("readNullableLong 测试")
    class ReadNullableLongTest {

        @Test
        @DisplayName("null node返回null")
        void shouldReturnNullForNullNode() {
            assertThat(AiJsonUtils.readNullableLong(null, "field")).isNull();
        }

        @Test
        @DisplayName("不存在的字段返回null")
        void shouldReturnNullForMissingField() throws Exception {
            JsonNode node = objectMapper.readTree("{\"name\":\"test\"}");
            assertThat(AiJsonUtils.readNullableLong(node, "missing")).isNull();
        }

        @Test
        @DisplayName("存在的长整数字段返回值")
        void shouldReturnPresentLongValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":9999999999}");
            assertThat(AiJsonUtils.readNullableLong(node, "field")).isEqualTo(9999999999L);
        }
    }

    @Nested
    @DisplayName("readNullableBoolean 测试")
    class ReadNullableBooleanTest {

        @Test
        @DisplayName("null node返回null")
        void shouldReturnNullForNullNode() {
            assertThat(AiJsonUtils.readNullableBoolean(null, "field")).isNull();
        }

        @Test
        @DisplayName("不存在的字段返回null")
        void shouldReturnNullForMissingField() throws Exception {
            JsonNode node = objectMapper.readTree("{\"name\":\"test\"}");
            assertThat(AiJsonUtils.readNullableBoolean(node, "missing")).isNull();
        }

        @Test
        @DisplayName("null值字段返回null")
        void shouldReturnNullForNullValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":null}");
            assertThat(AiJsonUtils.readNullableBoolean(node, "field")).isNull();
        }

        @Test
        @DisplayName("存在的布尔字段返回值")
        void shouldReturnPresentBooleanValue() throws Exception {
            JsonNode node = objectMapper.readTree("{\"field\":true}");
            assertThat(AiJsonUtils.readNullableBoolean(node, "field")).isTrue();
        }
    }
}
