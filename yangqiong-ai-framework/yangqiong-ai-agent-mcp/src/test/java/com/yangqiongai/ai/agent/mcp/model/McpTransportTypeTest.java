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
package com.yangqiongai.ai.agent.mcp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpTransportType 单元测试")
class McpTransportTypeTest {

    @Nested
    @DisplayName("枚举值")
    class EnumValuesTest {

        @Test
        @DisplayName("包含 STREAMABLE_HTTP")
        void hasStreamableHttp() {
            assertThat(McpTransportType.valueOf("STREAMABLE_HTTP")).isNotNull();
            assertThat(McpTransportType.STREAMABLE_HTTP.getCode()).isEqualTo("streamable-http");
        }

        @Test
        @DisplayName("包含 SSE")
        void hasSse() {
            assertThat(McpTransportType.valueOf("SSE")).isNotNull();
            assertThat(McpTransportType.SSE.getCode()).isEqualTo("sse");
        }

        @Test
        @DisplayName("包含 STDIO")
        void hasStdio() {
            assertThat(McpTransportType.valueOf("STDIO")).isNotNull();
            assertThat(McpTransportType.STDIO.getCode()).isEqualTo("stdio");
        }
    }

    @Nested
    @DisplayName("fromString")
    class FromStringTest {

        @Test
        @DisplayName("null → 默认STREAMABLE_HTTP")
        void null_returnsDefault() {
            assertThat(McpTransportType.fromString(null)).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        }

        @Test
        @DisplayName("空字符串 → 默认STREAMABLE_HTTP")
        void empty_returnsDefault() {
            assertThat(McpTransportType.fromString("")).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        }

        @Test
        @DisplayName("sse → SSE")
        void sse_returnsSse() {
            assertThat(McpTransportType.fromString("sse")).isEqualTo(McpTransportType.SSE);
        }

        @Test
        @DisplayName("stdio → STDIO")
        void stdio_returnsStdio() {
            assertThat(McpTransportType.fromString("stdio")).isEqualTo(McpTransportType.STDIO);
        }

        @Test
        @DisplayName("streamable-http → STREAMABLE_HTTP")
        void streamableHttp_returnsStreamableHttp() {
            assertThat(McpTransportType.fromString("streamable-http")).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        }

        @Test
        @DisplayName("streamable_http 别名 → STREAMABLE_HTTP")
        void streamableHttpAlias() {
            assertThat(McpTransportType.fromString("streamable_http")).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        }

        @Test
        @DisplayName("http 别名 → STREAMABLE_HTTP")
        void httpAlias() {
            assertThat(McpTransportType.fromString("http")).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        }

        @Test
        @DisplayName("未知值 → 默认STREAMABLE_HTTP")
        void unknown_returnsDefault() {
            assertThat(McpTransportType.fromString("unknown")).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        }
    }
}
