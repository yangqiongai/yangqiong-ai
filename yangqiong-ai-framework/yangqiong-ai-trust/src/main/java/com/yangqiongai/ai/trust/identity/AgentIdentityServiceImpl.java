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
package com.yangqiongai.ai.trust.identity;

import com.yangqiongai.ai.agent.registry.event.AgentPublishedEvent;
import com.yangqiongai.ai.agent.registry.service.AgentRegistryService;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.trust.identity.entity.AgentIdentity;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Agent身份管理
 * @author yangqiong
 */
public class AgentIdentityServiceImpl implements AgentIdentityService {

    private static final Logger log = LoggerFactory.getLogger(AgentIdentityServiceImpl.class);

    /**
     * 身份唯一标识前缀
     */
    public static final String UID_PREFIX = "aid-";

    /**
     * 身份凭证subject前缀
     */
    public static final String CREDENTIAL_SUBJECT_PREFIX = "ok-id-";

    /**
     * 主密钥最小长度(字节)
     */
    private static final int MIN_MASTER_KEY_LENGTH = 32;

    /**
     * 凭证指纹长度(SHA-256前32位)
     */
    private static final int FINGERPRINT_LENGTH = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private AgentIdentityRepository identityRepository;

    @Autowired
    private AgentRegistryService agentRegistryService;

    private final AgentIdentityProperties properties;

    public AgentIdentityServiceImpl(AgentIdentityProperties properties) {
        AgentIdentityProperties config = properties != null ? properties : new AgentIdentityProperties();
        String masterKey = config.getMasterKey();
        if (masterKey != null && !masterKey.isBlank()
                && masterKey.getBytes(StandardCharsets.UTF_8).length < MIN_MASTER_KEY_LENGTH) {
            throw new IllegalStateException("ai.trust.identity.master-key 长度不足，至少需要 "
                    + MIN_MASTER_KEY_LENGTH + " 字节");
        }
        this.properties = config;
    }

    @Override
    @EventListener
    public AgentIdentity onAgentPublished(AgentPublishedEvent event) {
        String agentCode = event.getAgentCode();
        AgentIdentity existing = identityRepository.findByAgentCode(agentCode);
        if (existing != null) {
            return existing;
        }
        AgentIdentity identity = new AgentIdentity();
        identity.setIdentityUid(generateIdentityUid());
        identity.setAgentCode(agentCode);
        identity.setDisplayName(agentCode);
        identity.setStatus(AgentIdentity.STATUS_ACTIVE);
        identity.setRotateDays(properties.getDefaultRotateDays());
        identityRepository.insert(identity);
        log.info("Agent发布自动建档: agentCode={}, identityUid={}", agentCode, identity.getIdentityUid());
        return identity;
    }

    @Override
    public String issueCredential(String identityUid) {
        SecretKey signingKey = requireSigningKey();
        AgentIdentity identity = requireIdentity(identityUid);
        if (!AgentIdentity.STATUS_ACTIVE.equals(identity.getStatus())) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "身份已吊销，无法签发凭证: " + identityUid);
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getCredentialTtlSeconds() * 1000);
        String token = Jwts.builder()
                .subject(CREDENTIAL_SUBJECT_PREFIX + identityUid)
                .claim("agentCode", identity.getAgentCode())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
        identity.setCredentialFingerprint(fingerprint(token));
        identity.setLastRotatedTime(LocalDateTime.now());
        identityRepository.update(identity);
        return token;
    }

    @Override
    public String rotate(String identityUid) {
        return issueCredential(identityUid);
    }

    @Override
    public void revoke(String identityUid, String operator) {
        AgentIdentity identity = requireIdentity(identityUid);
        if (AgentIdentity.STATUS_REVOKED.equals(identity.getStatus())) {
            return;
        }
        identity.setStatus(AgentIdentity.STATUS_REVOKED);
        identityRepository.update(identity);
        // 吊销联动禁用Agent
        agentRegistryService.updateStatus(identity.getAgentCode(), false);
        log.info("Agent身份已吊销并联动禁用: identityUid={}, agentCode={}, operator={}",
                identityUid, identity.getAgentCode(), operator);
    }

    @Override
    public List<AgentIdentity> list() {
        List<AgentIdentity> identities = identityRepository.findAll();
        identities.forEach(this::fillRotateDueTime);
        return identities;
    }

    @Override
    public AgentIdentity get(String identityUid) {
        AgentIdentity identity = requireIdentity(identityUid);
        fillRotateDueTime(identity);
        return identity;
    }

    @Override
    public LocalDateTime computeRotateDueTime(AgentIdentity identity) {
        if (identity.getRotateDays() == null || identity.getRotateDays() <= 0
                || identity.getLastRotatedTime() == null) {
            return null;
        }
        return identity.getLastRotatedTime().plusDays(identity.getRotateDays());
    }

    private void fillRotateDueTime(AgentIdentity identity) {
        identity.setRotateDueTime(computeRotateDueTime(identity));
    }

    private SecretKey requireSigningKey() {
        String masterKey = properties.getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("ai.trust.identity.master-key 未配置，无法签发身份凭证");
        }
        return Keys.hmacShaKeyFor(masterKey.getBytes(StandardCharsets.UTF_8));
    }

    private AgentIdentity requireIdentity(String identityUid) {
        if (identityUid == null || identityUid.isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR.getCode(), "身份唯一标识不能为空");
        }
        AgentIdentity identity = identityRepository.findByUid(identityUid);
        if (identity == null) {
            throw new AiException(AiErrorCode.NOT_FOUND.getCode(), "身份档案不存在: " + identityUid);
        }
        return identity;
    }

    private String generateIdentityUid() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return UID_PREFIX + HexFormat.of().formatHex(bytes) + UUID.randomUUID().toString().substring(0, 4);
    }

    private String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256算法不可用", e);
        }
    }
}
