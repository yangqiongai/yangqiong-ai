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
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感信息加密工具
 * <p>
 * AES-256-GCM 加解密，随机IV、密文格式 v1:{iv_b64}:{cipher_b64}:{tag_b64}，
 * 支持AAD附加认证数据绑定业务键防密文挪用。master key经{@link #configure(String)}
 * 注入（Base64编码的32字节）；未配置时降级为v0前缀的Base64明文编码保持兼容。
 * </p>
 * @author yangqiong
 */
public final class SecretCipherUtil {

    /**
     * 密文版本前缀（AES-256-GCM）
     */
    private static final String PREFIX_V1 = "v1";

    /**
     * 降级编码前缀（未配置master key时）
     */
    private static final String PREFIX_V0 = "v0";

    /**
     * 密文分段分隔符
     */
    private static final String SEPARATOR = ":";

    /**
     * GCM认证标签长度（位）
     */
    private static final int GCM_TAG_BITS = 128;

    /**
     * IV字节数
     */
    private static final int IV_BYTES = 12;

    /**
     * master key字节数
     */
    private static final int KEY_BYTES = 32;

    /**
     * GCM算法标识
     */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /**
     * master key（Base64解码后的32字节），configure后生效
     */
    private static volatile byte[] masterKey;

    /**
     * 随机数源
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretCipherUtil() {
    }

    /**
     * 注入master key
     * @param base64MasterKey Base64编码的32字节密钥，空入参忽略
     */
    public static void configure(String base64MasterKey) {
        if (base64MasterKey == null || base64MasterKey.isBlank()) {
            return;
        }
        byte[] key = Base64.getDecoder().decode(base64MasterKey.trim());
        if (key.length != KEY_BYTES) {
            throw new AiException(500, "secret-master-key必须为Base64编码的32字节(256位)");
        }
        masterKey = key;
    }

    /**
     * 判断master key是否已配置
     * @return true时加密走AES-256-GCM
     */
    public static boolean isConfigured() {
        return masterKey != null;
    }

    /**
     * 加密明文
     * @param plainText 明文，null或空原样返回
     * @param aad 附加认证数据（如modelCode），可空
     * @return v1:{iv}:{cipher}:{tag}密文，未配置master key时返回v0降级编码
     */
    public static String encrypt(String plainText, String aad) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        byte[] key = masterKey;
        if (key == null) {
            return PREFIX_V0 + SEPARATOR + Base64.getEncoder().encodeToString(
                    plainText.getBytes(StandardCharsets.UTF_8));
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] cipherWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            int cipherLength = cipherWithTag.length - GCM_TAG_BITS / 8;
            byte[] cipherBytes = new byte[cipherLength];
            byte[] tagBytes = new byte[GCM_TAG_BITS / 8];
            System.arraycopy(cipherWithTag, 0, cipherBytes, 0, cipherLength);
            System.arraycopy(cipherWithTag, cipherLength, tagBytes, 0, tagBytes.length);
            return PREFIX_V1 + SEPARATOR
                    + Base64.getEncoder().encodeToString(iv) + SEPARATOR
                    + Base64.getEncoder().encodeToString(cipherBytes) + SEPARATOR
                    + Base64.getEncoder().encodeToString(tagBytes);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.UNKNOWN, "敏感信息加密失败", e);
        }
    }

    /**
     * 解密密文
     * @param cipherText 密文，null或空原样返回
     * @param aad 附加认证数据，必须与加密时一致
     * @return 明文
     */
    public static String decrypt(String cipherText, String aad) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        String[] parts = cipherText.split("\\" + SEPARATOR, -1);
        // 无版本前缀的裸Base64按v0明文编码兼容处理
        if (parts.length == 1) {
            return decodeV0(cipherText);
        }
        if (PREFIX_V0.equals(parts[0])) {
            return parts.length == 2 ? decodeV0(parts[1]) : cipherText;
        }
        if (!PREFIX_V1.equals(parts[0]) || parts.length != 4) {
            // 非密文格式按原文返回，兼容历史明文存量
            return cipherText;
        }
        byte[] key = masterKey;
        if (key == null) {
            throw new AiException(AiErrorCode.UNKNOWN, "检测到v1密文但secret-master-key未配置");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherBytes = Base64.getDecoder().decode(parts[2]);
            byte[] tagBytes = Base64.getDecoder().decode(parts[3]);
            byte[] cipherWithTag = new byte[cipherBytes.length + tagBytes.length];
            System.arraycopy(cipherBytes, 0, cipherWithTag, 0, cipherBytes.length);
            System.arraycopy(tagBytes, 0, cipherWithTag, cipherBytes.length, tagBytes.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null && !aad.isEmpty()) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(cipherWithTag), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.UNKNOWN, "敏感信息解密失败（AAD不匹配或密文被篡改）", e);
        }
    }

    /**
     * 密钥掩码展示
     * @param secret 明文或掩码前文本
     * @return sk-前缀保留，主体保留前4位与尾4位，中间以星号替换
     */
    public static String mask(String secret) {
        if (secret == null || secret.isEmpty()) {
            return secret;
        }
        String prefix = secret.startsWith("sk-") ? "sk-" : "";
        String body = prefix.isEmpty() ? secret : secret.substring(3);
        if (body.length() <= 4) {
            return prefix + "****";
        }
        if (body.length() <= 8) {
            return prefix + body.substring(0, 2) + "****" + body.substring(body.length() - 2);
        }
        return prefix + body.substring(0, 4) + "****" + body.substring(body.length() - 4);
    }

    /**
     * 判断是否为掩码展示值
     * @param secret 待判断文本
     * @return true表示已是掩码，不可作为明文使用
     */
    public static boolean isMasked(String secret) {
        return secret != null && secret.contains("****");
    }

    /**
     * 由任意口令派生32字节master key（仅供开发测试辅助生成配置值）
     * @param secret 口令
     * @return Base64编码的32字节
     */
    public static String deriveBase64Key(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.UNKNOWN, "派生master key失败", e);
        }
    }

    /**
     * 解码v0降级编码为明文
     * @param base64Text Base64编码文本
     * @return 明文
     */
    private static String decodeV0(String base64Text) {
        try {
            return new String(Base64.getDecoder().decode(base64Text), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 非Base64内容按原文返回，兼容历史明文
            return base64Text;
        }
    }
}
