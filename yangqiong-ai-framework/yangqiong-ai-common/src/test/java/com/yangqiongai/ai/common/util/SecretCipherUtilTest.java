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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.yangqiongai.ai.common.exception.AiException;

/**
 * 敏感信息加密工具单元测试
 * @author yangqiong
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecretCipherUtilTest {

    /**
     * 测试用Base64编码的32字节master key
     */
    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @Order(1)
    @DisplayName("未配置master key时加密降级为v0编码且可解密")
    void encrypt_unconfigured_shouldFallbackToV0() {
        assertThat(SecretCipherUtil.isConfigured()).isFalse();
        String cipher = SecretCipherUtil.encrypt("sk-abcdef123456", "model");
        assertThat(cipher).startsWith("v0:");
        assertThat(SecretCipherUtil.decrypt(cipher, "model")).isEqualTo("sk-abcdef123456");
    }

    @Test
    @Order(2)
    @DisplayName("配置32字节master key后生效")
    void configure_validKey_shouldTakeEffect() {
        SecretCipherUtil.configure(TEST_KEY);
        assertThat(SecretCipherUtil.isConfigured()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("v1密文往返一致且随机IV使密文不同")
    void encryptDecrypt_roundTrip() {
        String plain = "sk-abcdef123456";
        String cipher1 = SecretCipherUtil.encrypt(plain, "deepseek");
        String cipher2 = SecretCipherUtil.encrypt(plain, "deepseek");
        assertThat(cipher1).startsWith("v1:").isNotEqualTo(cipher2);
        assertThat(SecretCipherUtil.decrypt(cipher1, "deepseek")).isEqualTo(plain);
        assertThat(SecretCipherUtil.decrypt(cipher2, "deepseek")).isEqualTo(plain);
    }

    @Test
    @Order(4)
    @DisplayName("AAD不匹配时解密失败")
    void decrypt_aadMismatch_shouldThrow() {
        String cipher = SecretCipherUtil.encrypt("sk-secret", "deepseek");
        assertThatThrownBy(() -> SecretCipherUtil.decrypt(cipher, "other-model"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @Order(5)
    @DisplayName("密文被篡改时解密失败")
    void decrypt_tamperedCipher_shouldThrow() {
        String cipher = SecretCipherUtil.encrypt("sk-secret", "deepseek");
        String[] parts = cipher.split(":");
        String tampered = parts[0] + ":" + parts[1] + ":" + "AAAA" + parts[2].substring(4) + ":" + parts[3];
        assertThatThrownBy(() -> SecretCipherUtil.decrypt(tampered, "deepseek"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @Order(6)
    @DisplayName("历史明文与非法格式按原文返回")
    void decrypt_legacyPlainText_shouldReturnAsIs() {
        assertThat(SecretCipherUtil.decrypt("sk-plain-text", null)).isEqualTo("sk-plain-text");
        assertThat(SecretCipherUtil.decrypt("v1:bad", null)).isEqualTo("v1:bad");
        assertThat(SecretCipherUtil.decrypt("v0:aGVsbG8=", null)).isEqualTo("hello");
    }

    @Test
    @Order(7)
    @DisplayName("null与空串加解密原样返回")
    void nullOrEmpty_shouldPassThrough() {
        assertThat(SecretCipherUtil.encrypt(null, null)).isNull();
        assertThat(SecretCipherUtil.encrypt("", null)).isEmpty();
        assertThat(SecretCipherUtil.decrypt(null, null)).isNull();
        assertThat(SecretCipherUtil.decrypt("", null)).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("掩码展示保留前缀、前四位与尾四位")
    void mask_shouldKeepHeadAndTail() {
        assertThat(SecretCipherUtil.mask("sk-1234567890")).isEqualTo("sk-1234****7890");
        assertThat(SecretCipherUtil.mask("abcdef")).isEqualTo("ab****ef");
        assertThat(SecretCipherUtil.mask("abc")).isEqualTo("****");
        assertThat(SecretCipherUtil.mask("")).isEmpty();
        assertThat(SecretCipherUtil.mask(null)).isNull();
    }

    @Test
    @Order(9)
    @DisplayName("派生密钥为Base64的32字节且可用于configure")
    void deriveBase64Key_shouldProduce32Bytes() {
        String derived = SecretCipherUtil.deriveBase64Key("dev-secret");
        assertThat(Base64.getDecoder().decode(derived)).hasSize(32);
    }

    @Test
    @Order(10)
    @DisplayName("空key配置被忽略且非法长度被拒绝")
    void configure_invalidInput_shouldBeHandled() {
        SecretCipherUtil.configure("");
        SecretCipherUtil.configure("   ");
        assertThat(SecretCipherUtil.isConfigured()).isTrue();
        assertThatThrownBy(() -> SecretCipherUtil.configure("AAAA"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("32字节");
    }
}
