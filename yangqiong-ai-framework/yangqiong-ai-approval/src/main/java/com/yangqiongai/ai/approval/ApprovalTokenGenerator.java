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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 审批令牌生成器
 * @author yangqiong
 */
@Component
public class ApprovalTokenGenerator {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTokenGenerator.class);

    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final int REQUEST_ID_BYTE_LENGTH = 16;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成审批令牌
     * @return
     */
    public String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(randomBytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        }
    }

    /**
     * 生成请求ID
     * @return
     */
    public String generateRequestId() {
        byte[] randomBytes = new byte[REQUEST_ID_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(randomBytes);
            return "req_" + Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256算法不可用", e);
            return "req_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes).substring(0, 24);
        }
    }

    /**
     * 校验令牌格式
     * @param token
     * @return
     */
    public boolean isTokenValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            return decoded.length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
