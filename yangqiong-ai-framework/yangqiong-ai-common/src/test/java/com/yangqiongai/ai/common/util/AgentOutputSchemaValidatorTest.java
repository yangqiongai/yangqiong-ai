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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent输出Schema校验器测试
 * @author yangqiong
 */
class AgentOutputSchemaValidatorTest {

    private static final String SIMPLE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": {"type": "string"},
                "priority": {"type": "integer", "minimum": 1, "maximum": 5}
              },
              "required": ["title"]
            }
            """;

    @Test
    void validateSchema_nullAndBlank视为未配置返回空() {
        assertThat(AgentOutputSchemaValidator.validateSchema(null)).isEmpty();
        assertThat(AgentOutputSchemaValidator.validateSchema("")).isEmpty();
        assertThat(AgentOutputSchemaValidator.validateSchema("   ")).isEmpty();
    }

    @Test
    void validateSchema_合法draft07通过() {
        assertThat(AgentOutputSchemaValidator.validateSchema(SIMPLE_SCHEMA)).isEmpty();
    }

    @Test
    void validateSchema_非JSON报错() {
        List<String> errors = AgentOutputSchemaValidator.validateSchema("not json");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("不是合法JSON");
    }

    @Test
    void validateSchema_非对象报错() {
        List<String> errors = AgentOutputSchemaValidator.validateSchema("[1,2,3]");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("必须是JSON对象");
    }

    @Test
    void validateSchema_非法Schema关键字报错() {
        List<String> errors = AgentOutputSchemaValidator.validateSchema(
                "{\"type\": \"object\", \"properties\": \"should-be-object\"}");
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("properties");
    }

    @Test
    void validateSchema_type关键字非法取值报错() {
        List<String> errors = AgentOutputSchemaValidator.validateSchema("{\"type\": \"str\"}");
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("type");
    }

    @Test
    void isSchemaValid_与validateSchema一致() {
        assertThat(AgentOutputSchemaValidator.isSchemaValid(SIMPLE_SCHEMA)).isTrue();
        assertThat(AgentOutputSchemaValidator.isSchemaValid("not json")).isFalse();
        assertThat(AgentOutputSchemaValidator.isSchemaValid(null)).isTrue();
    }

    @Test
    void validateOutput_schema为空视为未配置契约() {
        assertThat(AgentOutputSchemaValidator.validateOutput(null, "anything")).isEmpty();
        assertThat(AgentOutputSchemaValidator.validateOutput("", "anything")).isEmpty();
    }

    @Test
    void validateOutput_合规输出通过() {
        String output = "{\"title\": \"任务\", \"priority\": 3}";
        assertThat(AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output)).isEmpty();
    }

    @Test
    void validateOutput_缺少必填字段报错() {
        String output = "{\"priority\": 3}";
        List<String> errors = AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("title");
    }

    @Test
    void validateOutput_类型错误报错() {
        String output = "{\"title\": \"任务\", \"priority\": \"high\"}";
        List<String> errors = AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output);
        assertThat(errors).isNotEmpty();
    }

    @Test
    void validateOutput_数值越界报错() {
        String output = "{\"title\": \"任务\", \"priority\": 9}";
        List<String> errors = AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output);
        assertThat(errors).isNotEmpty();
    }

    @Test
    void validateOutput_markdown代码块包裹的JSON通过() {
        String output = "```json\n{\"title\": \"任务\", \"priority\": 2}\n```";
        assertThat(AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output)).isEmpty();
    }

    @Test
    void validateOutput_非JSON输出报错() {
        List<String> errors = AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, "这是普通文本回答");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("不是合法JSON");
    }

    @Test
    void validateOutput_空输出报错() {
        assertThat(AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, null)).isNotEmpty();
        assertThat(AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, "   ")).isNotEmpty();
    }

    @Test
    void validateOutput_schema本身非法时报错不抛异常() {
        List<String> errors = AgentOutputSchemaValidator.validateOutput("not json", "{\"a\": 1}");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("outputSchema");
    }

    @Test
    void validateOutput_嵌套对象与数组类型边界() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "items": {
                      "type": "array",
                      "items": {"type": "string"}
                    }
                  },
                  "required": ["items"]
                }
                """;
        assertThat(AgentOutputSchemaValidator.validateOutput(schema, "{\"items\": [\"a\", \"b\"]}")).isEmpty();
        assertThat(AgentOutputSchemaValidator.validateOutput(schema, "{\"items\": [\"a\", 1]}")).isNotEmpty();
        assertThat(AgentOutputSchemaValidator.validateOutput(schema, "{\"items\": \"not-array\"}")).isNotEmpty();
    }

    @Test
    void validateOutput_相同Schema重复校验走缓存() {
        String output = "{\"title\": \"任务\"}";
        assertThat(AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output)).isEmpty();
        assertThat(AgentOutputSchemaValidator.validateOutput(SIMPLE_SCHEMA, output)).isEmpty();
    }
}
