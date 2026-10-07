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
package com.yangqiongai.ai.open.capability.catalog;

import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力规格解析
 * @author yangqiong
 */
class CapabilityLoaderTest {

    @Test
    void testParseSpecFromJsonWithValidJson() {
        String json = "{\"code\":\"test-code\",\"name\":\"测试能力\",\"category\":\"test-cat\"," +
                "\"version\":\"1.0.0\",\"description\":\"测试描述\",\"agentCode\":\"agent-001\"}";

        CapabilitySpec result = CapabilityLoader.parseSpecFromJson(json);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("test-code");
        assertThat(result.getName()).isEqualTo("测试能力");
        assertThat(result.getCategory()).isEqualTo("test-cat");
        assertThat(result.getVersion()).isEqualTo("1.0.0");
        assertThat(result.getDescription()).isEqualTo("测试描述");
        assertThat(result.getAgentCode()).isEqualTo("agent-001");
    }

    @Test
    void testParseSpecFromJsonWithInvalidJson() {
        String json = "{invalid json}";

        CapabilitySpec result = CapabilityLoader.parseSpecFromJson(json);

        assertThat(result).isNull();
    }

    @Test
    void testParseSpecFromJsonWithEmptyJson() {
        String json = "";

        CapabilitySpec result = CapabilityLoader.parseSpecFromJson(json);

        assertThat(result).isNull();
    }

    @Test
    void testParseSpecFromJsonWithOverrideFields() {
        String json = "{\"code\":\"test-code\",\"name\":\"覆盖后的名称\",\"category\":\"新分类\"}";

        CapabilitySpec result = CapabilityLoader.parseSpecFromJson(json);

        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("test-code");
        assertThat(result.getName()).isEqualTo("覆盖后的名称");
        assertThat(result.getCategory()).isEqualTo("新分类");
    }
}
