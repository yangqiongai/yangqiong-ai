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
package com.yangqiongai.ai.platform.bss.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 摘要工具类
 * <p>
 * 提供 MD5 和 SHA-256 哈希计算，用于文档去重和内容指纹。
 * </p>
 * @author yangqiong
 */
public final class DigestUtils {

    private DigestUtils() {
    }

    /**
     * 计算字节数组的 MD5 哈希（十六进制字符串）
     * @param data 待计算数据
     * @return 32位小写十六进制字符串，data为null时返回空串
     */
    public static String md5Hex(byte[] data) {
        if (data == null) {
            return "";
        }
        return digestHex("MD5", data);
    }

    /**
     * 计算字符串的 SHA-256 哈希（十六进制字符串）
     * @param text 待计算文本（UTF-8编码）
     * @return 64位小写十六进制字符串，text为null时返回空串
     */
    public static String sha256Hex(String text) {
        if (text == null) {
            return "";
        }
        return digestHex("SHA-256", text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 HMAC-SHA256 哈希（十六进制字符串），用于接口签名校验
     * @param data 待计算数据（UTF-8编码）
     * @param secret 密钥（UTF-8编码）
     * @return 64位小写十六进制字符串，data或secret为null时返回空串
     */
    public static String hmacSha256Hex(String data, String secret) {
        if (data == null || secret == null) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 algorithm not available", e);
        }
    }

    private static String digestHex(String algorithm, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " algorithm not available", e);
        }
    }
}
