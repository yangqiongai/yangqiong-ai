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

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.trust.credential.entity.OpenCredential;
import com.yangqiongai.ai.trust.credential.mapper.OpenCredentialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 开放凭证管理测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCredentialService 单元测试")
class OpenCredentialServiceImplTest {

    @Mock
    private OpenCredentialMapper credentialMapper;

    private OpenCredentialServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new OpenCredentialServiceImpl();
        java.lang.reflect.Field mapperField = OpenCredentialServiceImpl.class.getDeclaredField("credentialMapper");
        mapperField.setAccessible(true);
        mapperField.set(service, credentialMapper);
    }

    @Test
    @DisplayName("凭证名称为空时签发被拒绝")
    void issueShouldRejectBlankName() {
        OpenCredential credential = new OpenCredential();
        assertThatThrownBy(() -> service.issue(credential))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证名称不能为空");
    }

    @Test
    @DisplayName("签发生成编码与密钥,明文不落库且状态缺省ACTIVE")
    void issueShouldGenerateCodeAndHash() {
        when(credentialMapper.insert(any(OpenCredential.class))).thenAnswer(invocation -> {
            OpenCredential credential = invocation.getArgument(0);
            credential.setId(1L);
            return 1;
        });

        OpenCredential credential = new OpenCredential();
        credential.setName("开放平台接入");

        IssueResult result = service.issue(credential);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCredentialCode()).startsWith(SecretHasher.CODE_PREFIX);
        assertThat(result.getSecret()).startsWith(SecretHasher.SECRET_PREFIX + result.getCredentialCode() + "-");
        assertThat(credential.getSecretHash()).isEqualTo(SecretHasher.hash(result.getCredentialCode(), result.getSecret()));
        assertThat(credential.getStatus()).isEqualTo(OpenCredentialServiceImpl.STATUS_ACTIVE);
        verify(credentialMapper).insert(credential);
    }

    @Test
    @DisplayName("轮换重置密钥哈希并清空限流窗口")
    void rotateShouldResetSecret() {
        OpenCredential existing = new OpenCredential();
        existing.setId(1L);
        existing.setCredentialCode("okc0001");
        existing.setSecretHash("old-hash");
        existing.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        when(credentialMapper.selectById(1L)).thenReturn(existing);

        IssueResult result = service.rotate(1L);

        assertThat(result.getCredentialCode()).isEqualTo("okc0001");
        assertThat(existing.getSecretHash())
                .isNotEqualTo("old-hash")
                .isEqualTo(SecretHasher.hash("okc0001", result.getSecret()));
        verify(credentialMapper).updateById(existing);
    }

    @Test
    @DisplayName("已吊销凭证不可轮换")
    void rotateShouldRejectRevoked() {
        OpenCredential existing = new OpenCredential();
        existing.setId(1L);
        existing.setStatus(OpenCredentialServiceImpl.STATUS_REVOKED);
        when(credentialMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> service.rotate(1L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证已吊销");
    }

    @Test
    @DisplayName("吊销更新状态为REVOKED")
    void revokeShouldUpdateStatus() {
        OpenCredential existing = new OpenCredential();
        existing.setId(1L);
        existing.setCredentialCode("okc0001");
        existing.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        when(credentialMapper.selectById(1L)).thenReturn(existing);

        service.revoke(1L);

        assertThat(existing.getStatus()).isEqualTo(OpenCredentialServiceImpl.STATUS_REVOKED);
        verify(credentialMapper).updateById(existing);
    }

    @Test
    @DisplayName("启用/禁用在ACTIVE与DISABLED间切换")
    void toggleShouldSwitchStatus() {
        OpenCredential active = new OpenCredential();
        active.setId(1L);
        active.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        when(credentialMapper.selectById(1L)).thenReturn(active);
        service.toggle(1L);
        assertThat(active.getStatus()).isEqualTo(OpenCredentialServiceImpl.STATUS_DISABLED);

        OpenCredential disabled = new OpenCredential();
        disabled.setId(2L);
        disabled.setStatus(OpenCredentialServiceImpl.STATUS_DISABLED);
        when(credentialMapper.selectById(2L)).thenReturn(disabled);
        service.toggle(2L);
        assertThat(disabled.getStatus()).isEqualTo(OpenCredentialServiceImpl.STATUS_ACTIVE);
    }

    @Test
    @DisplayName("已吊销凭证不可变更状态")
    void toggleShouldRejectRevoked() {
        OpenCredential existing = new OpenCredential();
        existing.setId(1L);
        existing.setStatus(OpenCredentialServiceImpl.STATUS_REVOKED);
        when(credentialMapper.selectById(1L)).thenReturn(existing);

        assertThatThrownBy(() -> service.toggle(1L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证已吊销");
    }

    @Test
    @DisplayName("操作不存在的凭证抛资源不存在异常")
    void requireExistingShouldRejectMissing() {
        when(credentialMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.rotate(9L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证不存在");
        assertThatThrownBy(() -> service.toggle(null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证ID不能为空");
    }

    @Test
    @DisplayName("校验通过返回凭证记录")
    void verifyShouldReturnCredentialOnSuccess() {
        OpenCredential existing = new OpenCredential();
        existing.setId(1L);
        existing.setCredentialCode("okc0001");
        existing.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        String secret = SecretHasher.generateSecret("okc0001");
        existing.setSecretHash(SecretHasher.hash("okc0001", secret));
        when(credentialMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.verify(secret)).isSameAs(existing);
    }

    @Test
    @DisplayName("校验拒绝:格式非法/不存在/哈希不匹配")
    void verifyShouldRejectInvalidSecret() {
        assertThatThrownBy(() -> service.verify("bad-format"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("密钥格式非法");

        when(credentialMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.verify(SecretHasher.generateSecret("okc404")))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证不存在");

        OpenCredential existing = new OpenCredential();
        existing.setId(1L);
        existing.setCredentialCode("okc0001");
        existing.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        existing.setSecretHash(SecretHasher.hash("okc0001", "correct-secret"));
        when(credentialMapper.selectOne(any())).thenReturn(existing);
        assertThatThrownBy(() -> service.verify("ok-okc0001-wrong"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证校验失败");
    }

    @Test
    @DisplayName("校验拒绝:已吊销/已禁用/已过期")
    void verifyShouldRejectInvalidStatus() {
        String secret = SecretHasher.generateSecret("okc0001");

        OpenCredential revoked = new OpenCredential();
        revoked.setCredentialCode("okc0001");
        revoked.setStatus(OpenCredentialServiceImpl.STATUS_REVOKED);
        revoked.setSecretHash(SecretHasher.hash("okc0001", secret));
        when(credentialMapper.selectOne(any())).thenReturn(revoked);
        assertThatThrownBy(() -> service.verify(secret))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证已吊销");

        OpenCredential disabled = new OpenCredential();
        disabled.setCredentialCode("okc0001");
        disabled.setStatus(OpenCredentialServiceImpl.STATUS_DISABLED);
        disabled.setSecretHash(SecretHasher.hash("okc0001", secret));
        when(credentialMapper.selectOne(any())).thenReturn(disabled);
        assertThatThrownBy(() -> service.verify(secret))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证已禁用");

        OpenCredential expired = new OpenCredential();
        expired.setCredentialCode("okc0001");
        expired.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        expired.setSecretHash(SecretHasher.hash("okc0001", secret));
        expired.setExpiresTime(LocalDateTime.now().minusMinutes(1));
        when(credentialMapper.selectOne(any())).thenReturn(expired);
        assertThatThrownBy(() -> service.verify(secret))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("凭证已过期");
    }

    @Test
    @DisplayName("过期时间为空视为永不过期")
    void verifyShouldAllowNullExpiresTime() {
        String secret = SecretHasher.generateSecret("okc0001");
        OpenCredential existing = new OpenCredential();
        existing.setCredentialCode("okc0001");
        existing.setStatus(OpenCredentialServiceImpl.STATUS_ACTIVE);
        existing.setSecretHash(SecretHasher.hash("okc0001", secret));
        when(credentialMapper.selectOne(any())).thenReturn(existing);

        assertThat(service.verify(secret)).isSameAs(existing);
    }

    @Test
    @DisplayName("未配置限流阈值时直接放行")
    void tryAcquireShouldPassWithoutQps() {
        OpenCredential credential = new OpenCredential();
        assertThat(service.tryAcquire(credential)).isTrue();
        credential.setRateLimitQps(0);
        assertThat(service.tryAcquire(credential)).isTrue();
    }

    @Test
    @DisplayName("配置限流阈值后按滑动窗口拒绝超额请求")
    void tryAcquireShouldRejectOverQps() {
        OpenCredential credential = new OpenCredential();
        credential.setCredentialCode("okc0001");
        credential.setRateLimitQps(1);
        assertThat(service.tryAcquire(credential)).isTrue();
        assertThat(service.tryAcquire(credential)).isFalse();
    }

    @Test
    @DisplayName("最近使用时间节流:窗口内不重复落库")
    void touchLastUsedShouldThrottle() {
        OpenCredential credential = new OpenCredential();
        credential.setId(1L);
        service.touchLastUsed(credential);
        service.touchLastUsed(credential);
        // 首次落库一次,节流窗口内不再落库
        verify(credentialMapper).updateById(any(OpenCredential.class));
    }
}
