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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 登录凭证RSA解密工具
 * <p>
 * 前端使用RSA-2048公钥(PKCS#1 v1.5填充)加密账号密码后以rsa:前缀密文传输，
 * 本工具用PKCS#8私钥解密还原明文；无rsa:前缀或非密文格式的值按明文原样返回，
 * 兼容API直接调用方与旧客户端。
 * </p>
 * @author yangqiong
 */
public final class RsaCipherUtil {

    /**
     * 密文前缀
     */
    private static final String CIPHER_PREFIX = "rsa:";

    /**
     * RSA算法及填充标识
     */
    private static final String ALGORITHM = "RSA/ECB/PKCS1Padding";

    private RsaCipherUtil() {
    }

    /**
     * 还原登录凭证，rsa:前缀密文用私钥解密，其余原样返回
     * @param value 登录凭证（rsa:前缀密文或明文）
     * @param base64PrivateKey Base64编码的PKCS#8私钥
     * @return 明文凭证
     */
    public static String resolveCredential(String value, String base64PrivateKey) {
        if (value == null || !value.startsWith(CIPHER_PREFIX)) {
            return value;
        }
        String cipherText = value.substring(CIPHER_PREFIX.length());
        // rsa:开头但非密文格式时按明文返回，避免误伤含前缀的明文账号
        if (!isValidCipherText(cipherText)) {
            return value;
        }
        if (base64PrivateKey == null || base64PrivateKey.isBlank()) {
            throw new AiException(AiErrorCode.UNKNOWN, "检测到rsa:密文但auth-rsa-private-key未配置");
        }
        return decrypt(cipherText, base64PrivateKey);
    }

    /**
     * RSA私钥解密Base64密文
     * @param base64CipherText Base64编码密文
     * @param base64PrivateKey Base64编码的PKCS#8私钥（支持单行Base64或PEM格式）
     * @return 明文
     */
    public static String decrypt(String base64CipherText, String base64PrivateKey) {
        try {
            // 剥离PEM头尾与空白字符后严格解码，兼容单行Base64与PEM两种私钥格式
            String normalizedKey = base64PrivateKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(base64CipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "登录凭证解密失败（密文无效或密钥不匹配）", e);
        }
    }

    /**
     * 校验是否为合法RSA密文：Base64可解码且长度为RSA块大小(128的倍数)
     * @param text 去除前缀后的密文文本
     * @return true时需走私钥解密
     */
    private static boolean isValidCipherText(String text) {
        if (text.isEmpty()) {
            return false;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return bytes.length > 0 && bytes.length % 128 == 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
