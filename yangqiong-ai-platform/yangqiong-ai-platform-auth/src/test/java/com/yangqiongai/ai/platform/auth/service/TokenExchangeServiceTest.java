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
package com.yangqiongai.ai.platform.auth.service;

import com.yangqiongai.ai.platform.auth.config.AuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Token 交换服务测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class TokenExchangeServiceTest {

    private static final String SECRET = "test-jwt-secret-key-0123456789-very-long!!";

    private TokenExchangeService service;

    @Mock
    private OidcTokenValidator oidcTokenValidator;

    /**
     * 初始化服务
     */
    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        AuthProperties.JwtConfig jwtConfig = new AuthProperties.JwtConfig();
        jwtConfig.setSecret(SECRET);
        jwtConfig.setExpirationMs(60_000);
        props.setJwt(jwtConfig);
        service = new TokenExchangeService(props);
        injectValidator();
    }

    /**
     * 反射注入 OIDC 校验器
     */
    private void injectValidator() {
        try {
            Field field = TokenExchangeService.class.getDeclaredField("oidcTokenValidator");
            field.setAccessible(true);
            field.set(service, oidcTokenValidator);
        } catch (Exception e) {
            throw new IllegalStateException("注入 OIDC 校验器失败", e);
        }
    }

    /**
     * 缺少OIDC校验器时交换令牌应抛出异常
     */
    @Test
    void exchangeTokenWithoutValidatorThrows() {
        clearValidator();
        assertThatThrownBy(() -> service.exchangeToken("some-token"))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 空访问令牌交换应抛出异常
     */
    @Test
    void exchangeTokenWithEmptyTokenThrows() {
        assertThatThrownBy(() -> service.exchangeToken(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 交换令牌应解析用户标识并签发有效 JWT
     */
    @Test
    void exchangeTokenWithValidatorReturnsValidToken() {
        when(oidcTokenValidator.resolveSubject("access-token")).thenReturn("user-1");
        String token = service.exchangeToken("access-token");
        assertThat(token).isNotBlank();
        assertThat(service.validateToken(token)).isTrue();
        assertThat(service.parseToken(token).getSubject()).isEqualTo("user-1");
        assertThat(service.parseToken(token).get("provider", String.class)).isEqualTo("oidc");
    }

    /**
     * 交换令牌应去除 Bearer 前缀
     */
    @Test
    void exchangeTokenStripsBearerPrefix() {
        when(oidcTokenValidator.resolveSubject("access-token")).thenReturn("user-2");
        String token = service.exchangeToken("Bearer access-token");
        assertThat(service.validateToken(token)).isTrue();
        assertThat(service.parseToken(token).getSubject()).isEqualTo("user-2");
    }

    /**
     * 清空 OIDC 校验器字段
     */
    private void clearValidator() {
        try {
            Field field = TokenExchangeService.class.getDeclaredField("oidcTokenValidator");
            field.setAccessible(true);
            field.set(service, null);
        } catch (Exception e) {
            throw new IllegalStateException("清空 OIDC 校验器失败", e);
        }
    }

    /**
     * 刷新令牌应保持原subject
     */
    @Test
    void refreshTokenKeepsSubject() {
        String token = buildToken("user-1", "oidc");
        String refreshed = service.refreshToken(token);
        assertThat(service.validateToken(refreshed)).isTrue();
        assertThat(service.parseToken(refreshed).getSubject()).isEqualTo("user-1");
        assertThat(service.parseToken(refreshed).get("provider", String.class)).isEqualTo("oidc");
    }

    /**
     * 无效旧令牌刷新应抛出异常
     */
    @Test
    void refreshTokenWithInvalidTokenThrows() {
        assertThatThrownBy(() -> service.refreshToken("not-a-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 有效令牌校验通过
     */
    @Test
    void validateTokenWithValidTokenReturnsTrue() {
        String token = buildToken("user-1", "oidc");
        assertThat(service.validateToken(token)).isTrue();
    }

    /**
     * 已过期令牌校验失败
     */
    @Test
    void validateTokenWithExpiredTokenReturnsFalse() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("user-1")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key)
                .compact();
        assertThat(service.validateToken(expired)).isFalse();
    }

    /**
     * 篡改令牌校验失败
     */
    @Test
    void validateTokenWithTamperedTokenReturnsFalse() {
        String token = buildToken("user-1", "oidc");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThat(service.validateToken(tampered)).isFalse();
    }

    /**
     * 空令牌校验失败
     */
    @Test
    void validateTokenWithEmptyTokenReturnsFalse() {
        assertThat(service.validateToken("")).isFalse();
    }

    /**
     * 登出后令牌被吊销且校验失败
     */
    @Test
    void logoutRevokesTokenAndInvalidatesIt() {
        String token = buildToken("user-1", "oidc");

        boolean revoked = service.logout(token);

        assertThat(revoked).isTrue();
        assertThat(service.isRevoked(token)).isTrue();
        assertThat(service.validateToken(token)).isFalse();
    }

    /**
     * 非法令牌登出返回false且不写入黑名单
     */
    @Test
    void logoutWithInvalidTokenReturnsFalse() {
        assertThat(service.logout("not-a-token")).isFalse();
        assertThat(service.isRevoked("not-a-token")).isFalse();
    }

    /**
     * 未被吊销的令牌isRevoked返回false
     */
    @Test
    void isRevokedForNonRevokedTokenReturnsFalse() {
        String token = buildToken("user-1", "oidc");
        assertThat(service.isRevoked(token)).isFalse();
    }

    /**
     * 密钥过短应抛出异常
     */
    @Test
    void shortSecretThrows() {
        AuthProperties props = new AuthProperties();
        AuthProperties.JwtConfig jwtConfig = new AuthProperties.JwtConfig();
        jwtConfig.setSecret("short");
        props.setJwt(jwtConfig);
        assertThatThrownBy(() -> new TokenExchangeService(props))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 密钥未配置应抛出异常
     */
    @Test
    void missingSecretThrows() {
        AuthProperties props = new AuthProperties();
        assertThatThrownBy(() -> new TokenExchangeService(props))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 构造测试令牌
     * @param subject
     * @param provider
     * @return
     */
    private String buildToken(String subject, String provider) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("provider", provider)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}