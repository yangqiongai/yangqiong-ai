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
package com.yangqiongai.ai.platform.ecosystem.a2a;

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * A2A签名卡签发
 * <p>
 * 从T12身份主密钥派生独立卡签名密钥（HMAC(A2A-CARD)），避免与身份凭证共用同一密钥语义。
 * </p>
 * @author yangqiong
 */
public class A2aCardSigner {

    /**
     * 卡签名密钥派生盐
     */
    private static final String KEY_DERIVATION_SALT = "a2a-card";

    private final byte[] masterKeyBytes;

    public A2aCardSigner(String masterKey) {
        this.masterKeyBytes = masterKey != null ? masterKey.getBytes(StandardCharsets.UTF_8) : null;
    }

    /**
     * 对卡片JSON签发JWS完整签名(payload为卡片JSON原文,纯content不掺claims)
     * @param cardJson
     * @return
     */
    public String sign(String cardJson) {
        if (masterKeyBytes == null || masterKeyBytes.length < 32) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "身份主密钥未配置，无法签发卡片签名");
        }
        SecretKey key = Keys.hmacShaKeyFor(deriveKey());
        return Jwts.builder()
                .content(cardJson.getBytes(StandardCharsets.UTF_8))
                .signWith(key)
                .compact();
    }

    /**
     * 主密钥派生卡签名密钥(HMAC-SHA256(masterKey, "a2a-card"))
     * @return
     */
    byte[] deriveKeyForTest() {
        return deriveKey();
    }

    /**
     * 主密钥派生卡签名密钥(HMAC-SHA256(masterKey, "a2a-card"))
     * @return
     */
    private byte[] deriveKey() {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(masterKeyBytes, "HmacSHA256"));
            return mac.doFinal(KEY_DERIVATION_SALT.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("卡签名密钥派生失败", e);
        }
    }
}
