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
package com.yangqiongai.ai.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApprovalTokenGenerator 单元测试")
class ApprovalTokenGeneratorTest {

    private final ApprovalTokenGenerator generator = new ApprovalTokenGenerator();

    @Nested
    @DisplayName("generateToken 测试")
    class GenerateTokenTest {

        @Test
        @DisplayName("返回非空非空字符串")
        void shouldReturnNonNullNonEmpty() {
            String token = generator.generateToken();
            assertThat(token).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("连续调用返回不同值")
        void shouldReturnUniqueValues() {
            String token1 = generator.generateToken();
            String token2 = generator.generateToken();
            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("返回Base64URL编码字符串（不含+/=字符）")
        void shouldReturnBase64UrlEncoded() {
            String token = generator.generateToken();
            assertThat(token).doesNotContain("+", "/", "=");
        }
    }

    @Nested
    @DisplayName("generateRequestId 测试")
    class GenerateRequestIdTest {

        @Test
        @DisplayName("返回以req_开头的字符串")
        void shouldStartWithReqPrefix() {
            String requestId = generator.generateRequestId();
            assertThat(requestId).startsWith("req_");
        }

        @Test
        @DisplayName("连续调用返回不同值")
        void shouldReturnUniqueValues() {
            String id1 = generator.generateRequestId();
            String id2 = generator.generateRequestId();
            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("isTokenValid 测试")
    class IsTokenValidTest {

        @Test
        @DisplayName("有效令牌返回true")
        void shouldReturnTrueForValidToken() {
            String token = generator.generateToken();
            assertThat(generator.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("null返回false")
        void shouldReturnFalseForNull() {
            assertThat(generator.isTokenValid(null)).isFalse();
        }

        @Test
        @DisplayName("空字符串返回false")
        void shouldReturnFalseForEmpty() {
            assertThat(generator.isTokenValid("")).isFalse();
        }

        @Test
        @DisplayName("无效Base64返回false")
        void shouldReturnFalseForInvalidBase64() {
            assertThat(generator.isTokenValid("!!!invalid!!!")).isFalse();
        }

        @Test
        @DisplayName("解码后长度不等于32返回false")
        void shouldReturnFalseForWrongLength() {
            // "aa" 解码后为1字节，不等于32
            String shortToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
            assertThat(generator.isTokenValid(shortToken)).isFalse();
        }
    }
}
