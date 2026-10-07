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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力入参校验器
 * @author yangqiong
 */
class CapabilityInputValidatorTest {

    private CapabilityInputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CapabilityInputValidator();
    }

    /**
     * 构造入参
     * @param pairs
     * @return
     */
    private Map<String, Object> args(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void testBlankSchemaPasses() {
        assertThat(validator.validate(args("a", "1"), null).isValid()).isTrue();
        assertThat(validator.validate(args("a", "1"), "  ").isValid()).isTrue();
    }

    @Test
    void testMissingRequired() {
        String schema = "{\"type\":\"object\",\"required\":[\"projectName\"],"
                + "\"properties\":{\"projectName\":{\"type\":\"string\"}}}";
        ValidationResult result = validator.validate(args("other", "x"), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("projectName").contains("缺少必填入参");
    }

    @Test
    void testTypeMismatchDefaultMessage() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"budgetAmount\":{\"type\":\"number\"}}}";
        ValidationResult result = validator.validate(args("budgetAmount", "不是数字"), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("budgetAmount").contains("类型应为数字");
    }

    @Test
    void testIntegerRejectsDecimal() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"count\":{\"type\":\"integer\"}}}";
        assertThat(validator.validate(args("count", 3), schema).isValid()).isTrue();
        assertThat(validator.validate(args("count", 3.5), schema).isValid()).isFalse();
    }

    @Test
    void testPatternFailureWithCustomMessage() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"projectCode\":{\"type\":\"string\",\"pattern\":\"^PRJ-\\\\d{4}$\","
                + "\"errorMessage\":\"项目编码必须为PRJ-开头的4位数字\"}}}";
        ValidationResult result = validator.validate(args("projectCode", "ABC123"), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("projectCode").contains("PRJ-开头的4位数字");
    }

    @Test
    void testPatternSuccess() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"projectCode\":{\"type\":\"string\",\"pattern\":\"^PRJ-\\\\d{4}$\","
                + "\"errorMessage\":\"项目编码必须为PRJ-开头的4位数字\"}}}";
        assertThat(validator.validate(args("projectCode", "PRJ-2026"), schema).isValid()).isTrue();
    }

    @Test
    void testNumberPattern() {
        // 数字转字符串做正则匹配（如仅允许正数）
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"budgetAmount\":{\"type\":\"number\",\"pattern\":\"^\\\\d+(\\\\.\\\\d+)?$\","
                + "\"errorMessage\":\"预算金额必须为非负数字\"}}}";
        assertThat(validator.validate(args("budgetAmount", 120.5), schema).isValid()).isTrue();
        ValidationResult result = validator.validate(args("budgetAmount", -10), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("预算金额必须为非负数字");
    }

    @Test
    void testEnumFailureWithCustomMessage() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"riskLevel\":{\"type\":\"string\",\"enum\":[\"低\",\"中\",\"高\"],"
                + "\"errorMessage\":\"风险等级只能为低/中/高\"}}}";
        ValidationResult result = validator.validate(args("riskLevel", "超高"), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("风险等级只能为低/中/高");
    }

    @Test
    void testEnumSuccess() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"riskLevel\":{\"type\":\"string\",\"enum\":[\"低\",\"中\",\"高\"]}}}";
        assertThat(validator.validate(args("riskLevel", "中"), schema).isValid()).isTrue();
    }

    @Test
    void testNestedObjectRecursion() {
        String schema = "{\"type\":\"object\",\"properties\":{\"budgetAnalysis\":{\"type\":\"object\","
                + "\"required\":[\"totalAmount\"],"
                + "\"properties\":{\"totalAmount\":{\"type\":\"number\"}}}}}";
        ValidationResult result = validator.validate(args("budgetAnalysis", args("riskLevel", "低")), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("budgetAnalysis.totalAmount").contains("缺少必填入参");
    }

    @Test
    void testArrayItemsValidation() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"keyPoints\":{\"type\":\"array\","
                + "\"items\":{\"type\":\"string\",\"pattern\":\"^\\\\S+$\","
                + "\"errorMessage\":\"关键要点不能包含空白\"}}}}";
        ValidationResult result = validator.validate(
                args("keyPoints", List.of("要点一", "要点 二")), schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("keyPoints[1]");
    }

    @Test
    void testInvalidPatternSkipped() {
        String schema = "{\"type\":\"object\","
                + "\"properties\":{\"name\":{\"type\":\"string\",\"pattern\":\"([bad\"}}}";
        assertThat(validator.validate(args("name", "任意值"), schema).isValid()).isTrue();
    }

    @Test
    void testInvalidSchemaContentFails() {
        ValidationResult result = validator.validate(args("a", 1), "{not-json");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors().get(0)).contains("输入Schema解析失败");
    }

    @Test
    void testFullInputSchema() {
        String schema = "{\"type\":\"object\",\"title\":\"项目概况输入参数\","
                + "\"required\":[\"projectName\",\"projectCode\"],"
                + "\"properties\":{"
                + "\"projectName\":{\"type\":\"string\"},"
                + "\"projectCode\":{\"type\":\"string\",\"pattern\":\"^PRJ-\\\\d{4}$\","
                + "\"errorMessage\":\"项目编码必须为PRJ-开头的4位数字\"},"
                + "\"budgetAmount\":{\"type\":\"number\",\"pattern\":\"^\\\\d+(\\\\.\\\\d+)?$\","
                + "\"errorMessage\":\"预算金额必须为非负数字\"},"
                + "\"projectType\":{\"type\":\"string\",\"enum\":[\"基建\",\"信息化\",\"设备采购\",\"其他\"]}"
                + "}}";
        Map<String, Object> good = args("projectName", "数据中心", "projectCode", "PRJ-0001",
                "budgetAmount", 500, "projectType", "信息化");
        assertThat(validator.validate(good, schema).isValid()).isTrue();

        Map<String, Object> bad = args("projectName", "数据中心", "projectCode", "XX-1",
                "budgetAmount", -3, "projectType", " unknow ");
        ValidationResult result = validator.validate(bad, schema);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("PRJ-开头的4位数字"));
        assertThat(result.getErrors()).anyMatch(e -> e.contains("预算金额必须为非负数字"));
        assertThat(result.getErrors()).anyMatch(e -> e.contains("projectType")).anyMatch(e -> e.contains("取值必须为"));
    }
}
