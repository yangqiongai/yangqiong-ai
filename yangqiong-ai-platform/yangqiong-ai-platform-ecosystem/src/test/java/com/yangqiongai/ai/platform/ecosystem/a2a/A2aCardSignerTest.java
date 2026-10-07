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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A2A签名卡签发测试
 * @author yangqiong
 */
class A2aCardSignerTest {

    private static final String MASTER_KEY = "test-master-key-0123456789-0123456789";

    private static final String CARD_JSON = "{\"name\":\"demo-agent\",\"version\":\"1.0.0\"}";

    @Test
    void signShouldProduceVerifiableJws() {
        A2aCardSigner signer = new A2aCardSigner(MASTER_KEY);

        String compact = signer.sign(CARD_JSON);

        String[] parts = compact.split("\\.");
        assertThat(parts).hasSize(3);
        // 派生密钥可验签(JSON载荷JJWT按Claims承载,签名校验仍覆盖载荷原文)
        byte[] derived = new A2aCardSigner(MASTER_KEY).deriveKeyForTest();
        SecretKey key = Keys.hmacShaKeyFor(derived);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                Jwts.parser().verifyWith(key).build().parseSignedClaims(compact));
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("demo-agent");
    }

    @Test
    void signShouldRejectWithoutMasterKey() {
        A2aCardSigner signer = new A2aCardSigner("");

        assertThatThrownBy(() -> signer.sign(CARD_JSON))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("主密钥未配置");
    }

    @Test
    void signShouldUseDerivedKeyDifferentFromMaster() {
        A2aCardSigner signer = new A2aCardSigner(MASTER_KEY);

        // 主密钥直接验签应失败,证明派生密钥独立
        SecretKey masterKey = Keys.hmacShaKeyFor(MASTER_KEY.getBytes(StandardCharsets.UTF_8));
        String compact = signer.sign(CARD_JSON);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                Jwts.parser().verifyWith(masterKey).build().parseSignedClaims(compact));
    }
}
