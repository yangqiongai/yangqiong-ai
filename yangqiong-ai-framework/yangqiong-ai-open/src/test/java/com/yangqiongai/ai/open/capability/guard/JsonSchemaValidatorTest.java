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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON Schema校验器
 * @author yangqiong
 */
class JsonSchemaValidatorTest {

    private JsonSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JsonSchemaValidator();
    }

    @Test
    void testValidateWithSchemaFileNotFound() {
        ValidationResult result = validator.validate("test-data", "non-existent-schema.json");

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        // Schema文件不存在时返回明确的错误信息
        assertThat(result.getErrors().get(0)).contains("Schema");
    }

    @Test
    void testValidateWithNullData() {
        ValidationResult result = validator.validate(null, "non-existent-schema.json");

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        // 即使data为null，也应该返回校验失败结果，而不是抛出异常
        assertThat(result.getErrors().get(0)).contains("Schema");
    }

    @Test
    void testValidateJsonWithSchemaFileNotFound() {
        ValidationResult result = validator.validateJson("{\"key\":\"value\"}", "non-existent-schema.json");

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors().get(0)).contains("Schema");
    }

    @Test
    void testValidateJsonWithInvalidJson() {
        ValidationResult result = validator.validateJson("{invalid json}", "non-existent-schema.json");

        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
        // JSON解析失败
        assertThat(result.getErrors().get(0)).contains("JSON解析失败");
    }

    @Test
    void testClearCache() {
        // 调用clearCache不应抛出异常
        validator.clearCache();
    }
}