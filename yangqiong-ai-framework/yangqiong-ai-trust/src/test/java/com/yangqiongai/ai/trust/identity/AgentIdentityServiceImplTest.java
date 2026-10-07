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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent身份管理测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentIdentityServiceImplTest {

    /**
     * 测试主密钥(至少32字节)
     */
    private static final String MASTER_KEY = "test-master-key-0123456789-0123456789";

    @Mock
    private AgentIdentityRepository identityRepository;

    @Mock
    private AgentRegistryService agentRegistryService;

    private AgentIdentityServiceImpl service;

    @BeforeEach
    void setUp() {
        AgentIdentityProperties properties = new AgentIdentityProperties();
        properties.setMasterKey(MASTER_KEY);
        properties.setCredentialTtlSeconds(300L);
        properties.setDefaultRotateDays(90);
        service = new AgentIdentityServiceImpl(properties);
        ReflectionTestUtils.setField(service, "identityRepository", identityRepository);
        ReflectionTestUtils.setField(service, "agentRegistryService", agentRegistryService);
    }

    private AgentIdentity identity(String uid, String agentCode, String status) {
        AgentIdentity identity = new AgentIdentity();
        identity.setIdentityUid(uid);
        identity.setAgentCode(agentCode);
        identity.setStatus(status);
        return identity;
    }

    @Test
    void constructorShouldRejectShortMasterKey() {
        AgentIdentityProperties properties = new AgentIdentityProperties();
        properties.setMasterKey("short-key");

        assertThatThrownBy(() -> new AgentIdentityServiceImpl(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master-key 长度不足");
    }

    @Test
    void onAgentPublishedShouldCreateIdentity() {
        when(identityRepository.findByAgentCode("report_agent")).thenReturn(null);

        AgentIdentity created = service.onAgentPublished(
                new AgentPublishedEvent("report_agent", "v1", 1L, "PUBLISH"));

        ArgumentCaptor<AgentIdentity> captor = ArgumentCaptor.forClass(AgentIdentity.class);
        verify(identityRepository).insert(captor.capture());
        assertThat(created).isSameAs(captor.getValue());
        assertThat(created.getIdentityUid()).startsWith(AgentIdentityServiceImpl.UID_PREFIX);
        assertThat(created.getAgentCode()).isEqualTo("report_agent");
        assertThat(created.getStatus()).isEqualTo(AgentIdentity.STATUS_ACTIVE);
        assertThat(created.getRotateDays()).isEqualTo(90);
    }

    @Test
    void onAgentPublishedShouldBeIdempotent() {
        AgentIdentity existing = identity("aid-01", "report_agent", AgentIdentity.STATUS_ACTIVE);
        when(identityRepository.findByAgentCode("report_agent")).thenReturn(existing);

        AgentIdentity result = service.onAgentPublished(
                new AgentPublishedEvent("report_agent", "v2", 2L, "PUBLISH"));

        assertThat(result).isSameAs(existing);
        verify(identityRepository, never()).insert(any(AgentIdentity.class));
    }

    @Test
    void issueCredentialShouldReturnSignedJws() {
        AgentIdentity identity = identity("aid-01", "report_agent", AgentIdentity.STATUS_ACTIVE);
        when(identityRepository.findByUid("aid-01")).thenReturn(identity);

        String token = service.issueCredential("aid-01");

        SecretKey key = Keys.hmacShaKeyFor(MASTER_KEY.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo(AgentIdentityServiceImpl.CREDENTIAL_SUBJECT_PREFIX + "aid-01");
        assertThat(claims.get("agentCode", String.class)).isEqualTo("report_agent");
        // 凭证指纹落库且不含明文
        ArgumentCaptor<AgentIdentity> captor = ArgumentCaptor.forClass(AgentIdentity.class);
        verify(identityRepository).update(captor.capture());
        assertThat(captor.getValue().getCredentialFingerprint()).hasSize(32);
        assertThat(captor.getValue().getLastRotatedTime()).isNotNull();
    }

    @Test
    void issueCredentialShouldFailWithoutMasterKey() {
        AgentIdentityProperties properties = new AgentIdentityProperties();
        AgentIdentityServiceImpl noKeyService = new AgentIdentityServiceImpl(properties);
        ReflectionTestUtils.setField(noKeyService, "identityRepository", identityRepository);
        ReflectionTestUtils.setField(noKeyService, "agentRegistryService", agentRegistryService);

        assertThatThrownBy(() -> noKeyService.issueCredential("aid-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master-key 未配置");
    }

    @Test
    void issueCredentialShouldFailWhenRevoked() {
        when(identityRepository.findByUid("aid-01"))
                .thenReturn(identity("aid-01", "report_agent", AgentIdentity.STATUS_REVOKED));

        assertThatThrownBy(() -> service.issueCredential("aid-01"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("身份已吊销");
    }

    @Test
    void issueCredentialShouldFailWhenIdentityMissing() {
        when(identityRepository.findByUid("nope")).thenReturn(null);

        assertThatThrownBy(() -> service.issueCredential("nope"))
                .isInstanceOf(AiException.class)
                .extracting(e -> ((AiException) e).getCode())
                .isEqualTo(AiErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void rotateShouldRefreshFingerprint() {
        AgentIdentity identity = identity("aid-01", "report_agent", AgentIdentity.STATUS_ACTIVE);
        when(identityRepository.findByUid("aid-01")).thenReturn(identity);

        service.rotate("aid-01");
        service.rotate("aid-01");

        ArgumentCaptor<AgentIdentity> captor = ArgumentCaptor.forClass(AgentIdentity.class);
        verify(identityRepository, org.mockito.Mockito.times(2)).update(captor.capture());
        // 每次轮换都刷新指纹与轮换时间,旧凭证在TTL窗口内自然失效
        assertThat(captor.getAllValues())
                .allSatisfy(item -> {
                    assertThat(item.getCredentialFingerprint()).hasSize(32);
                    assertThat(item.getLastRotatedTime()).isNotNull();
                });
    }

    @Test
    void revokeShouldDisableAgent() {
        AgentIdentity identity = identity("aid-01", "report_agent", AgentIdentity.STATUS_ACTIVE);
        when(identityRepository.findByUid("aid-01")).thenReturn(identity);

        service.revoke("aid-01", "admin");

        ArgumentCaptor<AgentIdentity> captor = ArgumentCaptor.forClass(AgentIdentity.class);
        verify(identityRepository).update(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AgentIdentity.STATUS_REVOKED);
        verify(agentRegistryService).updateStatus("report_agent", false);
    }

    @Test
    void revokeShouldBeIdempotentWhenAlreadyRevoked() {
        when(identityRepository.findByUid("aid-01"))
                .thenReturn(identity("aid-01", "report_agent", AgentIdentity.STATUS_REVOKED));

        service.revoke("aid-01", "admin");

        verify(identityRepository, never()).update(any(AgentIdentity.class));
        verify(agentRegistryService, never()).updateStatus(any(), any(Boolean.class));
    }

    @Test
    void computeRotateDueTimeShouldDependOnRotateDays() {
        AgentIdentity identity = identity("aid-01", "report_agent", AgentIdentity.STATUS_ACTIVE);
        identity.setRotateDays(7);
        identity.setLastRotatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(service.computeRotateDueTime(identity))
                .isEqualTo(LocalDateTime.of(2026, 1, 8, 0, 0));

        identity.setRotateDays(null);
        assertThat(service.computeRotateDueTime(identity)).isNull();

        identity.setRotateDays(0);
        assertThat(service.computeRotateDueTime(identity)).isNull();
    }
}
