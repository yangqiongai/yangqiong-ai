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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.common.exception.AiException;

/**
 * 登录凭证RSA解密工具单元测试
 * @author yangqiong
 */
class RsaCipherUtilTest {

    /**
     * 测试用密钥对
     */
    private static KeyPair keyPair;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    /**
     * 模拟前端RSA公钥加密(PKCS#1 v1.5填充)
     * @param plainText 明文
     * @return Base64密文
     */
    private static String encryptLikeFrontend(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic());
        return Base64.getEncoder().encodeToString(
                cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 获取Base64编码的PKCS#8私钥
     * @return Base64私钥
     */
    private static String base64PrivateKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    @Test
    @DisplayName("rsa:前缀密文解密还原明文")
    void resolveCredential_cipherText_shouldDecrypt() throws Exception {
        String username = "admin";
        String password = "P@ssw0rd-默认密码2026";
        String cipherUsername = "rsa:" + encryptLikeFrontend(username);
        String cipherPassword = "rsa:" + encryptLikeFrontend(password);
        assertThat(RsaCipherUtil.resolveCredential(cipherUsername, base64PrivateKey())).isEqualTo(username);
        assertThat(RsaCipherUtil.resolveCredential(cipherPassword, base64PrivateKey())).isEqualTo(password);
    }

    @Test
    @DisplayName("无前缀明文原样返回，兼容API直接调用")
    void resolveCredential_plainText_shouldReturnAsIs() {
        assertThat(RsaCipherUtil.resolveCredential("admin", base64PrivateKey())).isEqualTo("admin");
        assertThat(RsaCipherUtil.resolveCredential("明文账号", null)).isEqualTo("明文账号");
        assertThat(RsaCipherUtil.resolveCredential(null, base64PrivateKey())).isNull();
        assertThat(RsaCipherUtil.resolveCredential("", base64PrivateKey())).isEmpty();
    }

    @Test
    @DisplayName("rsa:开头但非密文格式时按明文返回，避免误伤含前缀的账号")
    void resolveCredential_prefixLikePlainText_shouldReturnAsIs() {
        String plainLikePrefix = "rsa:admin";
        assertThat(RsaCipherUtil.resolveCredential(plainLikePrefix, base64PrivateKey())).isEqualTo(plainLikePrefix);
        assertThat(RsaCipherUtil.resolveCredential("rsa:", base64PrivateKey())).isEqualTo("rsa:");
    }

    @Test
    @DisplayName("检测到合法密文但私钥未配置时报错")
    void resolveCredential_missingPrivateKey_shouldThrow() throws Exception {
        String cipher = "rsa:" + encryptLikeFrontend("admin");
        assertThatThrownBy(() -> RsaCipherUtil.resolveCredential(cipher, null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(() -> RsaCipherUtil.resolveCredential(cipher, "  "))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("密钥不匹配时解密失败")
    void decrypt_mismatchedKey_shouldThrow() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherKeyPair = generator.generateKeyPair();
        String otherPrivateKey = Base64.getEncoder().encodeToString(otherKeyPair.getPrivate().getEncoded());
        String cipher = encryptLikeFrontend("admin");
        assertThatThrownBy(() -> RsaCipherUtil.decrypt(cipher, otherPrivateKey))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @DisplayName("密文被篡改时解密失败")
    void decrypt_tamperedCipher_shouldThrow() throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(encryptLikeFrontend("admin"));
        cipherBytes[cipherBytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(cipherBytes);
        assertThatThrownBy(() -> RsaCipherUtil.decrypt(tampered, base64PrivateKey()))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("密钥非法Base64或非法PKCS#8结构时解密失败")
    void decrypt_invalidKey_shouldThrow() {
        assertThatThrownBy(() -> RsaCipherUtil.decrypt("AAAA", "not-base64-###"))
                .isInstanceOf(AiException.class);
        String invalidPkcs8 = Base64.getEncoder().encodeToString(new byte[128]);
        assertThatThrownBy(() -> RsaCipherUtil.decrypt("AAAA", invalidPkcs8))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("支持PEM完整格式与单行Base64私钥")
    void decrypt_pemFormattedKey_shouldWork() throws Exception {
        String base64Key = base64PrivateKey();
        String pemKey = "-----BEGIN PRIVATE KEY-----\n"
                + base64Key.replaceAll("(.{64})", "$1\n")
                + "-----END PRIVATE KEY-----\n";
        String cipher = encryptLikeFrontend("admin");
        assertThat(RsaCipherUtil.decrypt(cipher, pemKey)).isEqualTo("admin");
        assertThat(RsaCipherUtil.resolveCredential("rsa:" + cipher, pemKey)).isEqualTo("admin");
    }

    @Test
    @DisplayName("PKCS#8私钥可由KeyFactory还原且与KeyPair私钥等价")
    void privateKey_pkcs8Encoding_shouldMatch() throws Exception {
        PrivateKey restored = java.security.KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()));
        assertThat(restored).isEqualTo(keyPair.getPrivate());
    }
}
