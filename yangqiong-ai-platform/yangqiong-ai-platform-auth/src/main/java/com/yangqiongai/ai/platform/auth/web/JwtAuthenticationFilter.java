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

import com.yangqiongai.ai.platform.auth.service.TokenExchangeService;
import com.yangqiongai.ai.platform.bss.scope.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * <p>
 * 解析请求头 Authorization 中的 Bearer JWT，校验通过后将登录用户写入 SecurityContext 与 UserContext；
 * 缺失或非法时放行，由安全配置的认证规则统一兜底。
 * </p>
 * @author yangqiong
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Authorization 头部名称
     */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Bearer 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 默认角色
     */
    private static final String DEFAULT_ROLE = "ROLE_USER";

    /**
     * Token 交换服务
     */
    private final TokenExchangeService tokenExchangeService;

    public JwtAuthenticationFilter(TokenExchangeService tokenExchangeService) {
        this.tokenExchangeService = tokenExchangeService;
    }

    /**
     * 解析并校验 JWT，写入认证上下文
     * @param request
     * @param response
     * @param filterChain
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            Claims claims = tokenExchangeService.parseToken(token);
            if (claims != null && tokenExchangeService.validateToken(token)) {
                String subject = claims.getSubject();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subject, null,
                                Collections.singletonList(new SimpleGrantedAuthority(DEFAULT_ROLE)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                UserContext.setUserId(subject);
            }
        }
        filterChain.doFilter(request, response);
    }
}
