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
package com.yangqiongai.ai.trust.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 凭证密钥哈希测试
 * @author yangqiong
 */
class SecretHasherTest {

    @Test
    @DisplayName("生成凭证编码格式合法且不重复")
    void generateCredentialCodeShouldReturnUniqueCode() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String code = SecretHasher.generateCredentialCode();
            assertThat(code).startsWith(SecretHasher.CODE_PREFIX);
            assertThat(code).hasSize(SecretHasher.CODE_PREFIX.length() + 10);
            codes.add(code);
        }
        assertThat(codes).hasSize(100);
    }

    @Test
    @DisplayName("生成密钥明文包含凭证编码且格式合法")
    void generateSecretShouldContainCredentialCode() {
        String code = SecretHasher.generateCredentialCode();
        String secret = SecretHasher.generateSecret(code);
        assertThat(secret).startsWith(SecretHasher.SECRET_PREFIX + code + "-");
        assertThat(SecretHasher.parseCredentialCode(secret)).isEqualTo(code);
    }

    @Test
    @DisplayName("哈希确定性:同输入同输出且为64位十六进制")
    void hashShouldBeDeterministicHex64() {
        String code = "okc0001";
        String secret = "ok-okc0001-abcdef0123456789";
        String hash1 = SecretHasher.hash(code, secret);
        String hash2 = SecretHasher.hash(code, secret);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("哈希区分度:不同输入产生不同哈希")
    void hashShouldDifferForDifferentInput() {
        String hash1 = SecretHasher.hash("okc0001", "secret-a");
        String hash2 = SecretHasher.hash("okc0001", "secret-b");
        String hash3 = SecretHasher.hash("okc0002", "secret-a");
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(hash3);
    }

    @Test
    @DisplayName("解析凭证编码:null与空串返回null")
    void parseCredentialCodeShouldReturnNullForBlank() {
        assertThat(SecretHasher.parseCredentialCode(null)).isNull();
        assertThat(SecretHasher.parseCredentialCode("")).isNull();
    }

    @Test
    @DisplayName("解析凭证编码:缺少前缀返回null")
    void parseCredentialCodeShouldReturnNullWithoutPrefix() {
        assertThat(SecretHasher.parseCredentialCode("xx-okc0001-abc")).isNull();
        assertThat(SecretHasher.parseCredentialCode("okc0001-abc")).isNull();
    }

    @Test
    @DisplayName("解析凭证编码:分段非法返回null")
    void parseCredentialCodeShouldReturnNullForIllegalSegments() {
        // 无分隔符
        assertThat(SecretHasher.parseCredentialCode("ok-okc0001")).isNull();
        // 编码为空
        assertThat(SecretHasher.parseCredentialCode("ok--secret")).isNull();
        // 密钥段为空
        assertThat(SecretHasher.parseCredentialCode("ok-okc0001-")).isNull();
    }

    @Test
    @DisplayName("解析凭证编码:合法格式成功解析")
    void parseCredentialCodeShouldReturnCodeForLegalFormat() {
        assertThat(SecretHasher.parseCredentialCode("ok-okc0001-abcdef")).isEqualTo("okc0001");
        assertThat(SecretHasher.parseCredentialCode("ok-okc0001-a-b-c")).isEqualTo("okc0001");
    }
}
