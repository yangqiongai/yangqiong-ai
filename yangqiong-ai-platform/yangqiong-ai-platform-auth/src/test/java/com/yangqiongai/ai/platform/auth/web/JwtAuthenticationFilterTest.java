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
package com.yangqiongai.ai.platform.auth.web;

import com.yangqiongai.ai.platform.auth.config.AuthProperties;
import com.yangqiongai.ai.platform.auth.service.TokenExchangeService;
import com.yangqiongai.ai.platform.bss.scope.UserContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 认证过滤器测试
 * @author yangqiong
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-jwt-secret-key-0123456789-very-long!!";

    private TokenExchangeService tokenExchangeService;

    private JwtAuthenticationFilter filter;

    /**
     * 初始化过滤器
     */
    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        AuthProperties.JwtConfig jwtConfig = new AuthProperties.JwtConfig();
        jwtConfig.setSecret(SECRET);
        jwtConfig.setExpirationMs(60_000);
        props.setJwt(jwtConfig);
        tokenExchangeService = new TokenExchangeService(props);
        filter = new JwtAuthenticationFilter(tokenExchangeService);
    }

    /**
     * 清理上下文，避免线程间残留
     */
    @BeforeEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    /**
     * 无 Authorization 头时应放行且不写入认证上下文
     * @throws Exception
     */
    @Test
    void noAuthHeaderPassesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo("system");
    }

    /**
     * 非 Bearer 头应放行且不写入认证上下文
     * @throws Exception
     */
    @Test
    void nonBearerHeaderPassesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Basic abc");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /**
     * 合法 JWT 应写入安全上下文与用户上下文
     * @throws Exception
     */
    @Test
    void validJwtSetsAuthentication() throws Exception {
        String token = buildToken("user-1");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(UserContext.getUserId()).isEqualTo("user-1");
    }

    /**
     * 非法 JWT 应放行且不写入认证上下文
     * @throws Exception
     */
    @Test
    void invalidJwtPassesThrough() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer not-a-valid-jwt");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo("system");
    }

    /**
     * 已登出被吊销的 JWT 应放行且不写入认证上下文
     * @throws Exception
     */
    @Test
    void revokedJwtPassesThrough() throws Exception {
        String token = buildToken("user-1");
        tokenExchangeService.logout(token);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(UserContext.getUserId()).isEqualTo("system");
    }

    /**
     * 构造测试令牌
     * @param subject
     * @return
     */
    private String buildToken(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
