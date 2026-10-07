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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 凭证密钥哈希
 * <p>
 * 密钥明文不落库，仅存SHA-256哈希；密钥格式为 ok-{credentialCode}-{random32hex}，
 * 校验时先从明文解析凭证编码定位记录再比对哈希，避免全表扫描。
 * </p>
 * @author yangqiong
 */
public final class SecretHasher {

    /**
     * 密钥明文前缀
     */
    public static final String SECRET_PREFIX = "ok-";

    /**
     * 凭证编码前缀
     */
    public static final String CODE_PREFIX = "okc";

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretHasher() {
    }

    /**
     * 生成凭证编码
     * @return
     */
    public static String generateCredentialCode() {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return CODE_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * 生成密钥明文(仅签发/轮换时返回一次)
     * @param credentialCode
     * @return
     */
    public static String generateSecret(String credentialCode) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return SECRET_PREFIX + credentialCode + "-" + HexFormat.of().formatHex(bytes);
    }

    /**
     * 计算密钥哈希(SHA-256(credentialCode:secret)十六进制小写)
     * @param credentialCode
     * @param secret
     * @return
     */
    public static String hash(String credentialCode, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((credentialCode + ":" + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256算法不可用", e);
        }
    }

    /**
     * 从密钥明文解析凭证编码(格式非法返回null)
     * @param secret
     * @return
     */
    public static String parseCredentialCode(String secret) {
        if (secret == null || !secret.startsWith(SECRET_PREFIX)) {
            return null;
        }
        String remain = secret.substring(SECRET_PREFIX.length());
        int split = remain.indexOf('-');
        if (split <= 0 || split == remain.length() - 1) {
            return null;
        }
        return remain.substring(0, split);
    }
}
