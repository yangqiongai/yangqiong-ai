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
package com.yangqiongai.ai.platform.auth.config;

import com.yangqiongai.ai.platform.auth.service.TokenExchangeService;
import com.yangqiongai.ai.platform.auth.web.JwtAuthenticationFilter;
import com.yangqiongai.ai.platform.bss.scope.UserContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 本地登录安全配置
 * <p>
 * 仅在 spring-security 存在且 ai.auth.enabled=true 时生效，
 * 注册 JWT 认证过滤器并保护除登录/刷新/开放接口外的全部请求；
 * SSO 商业链路启用时其过滤链优先级更高，本链整体让位。
 * </p>
 * @author yangqiong
 */
@Configuration
@EnableWebSecurity
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnProperty(name = "ai.auth.enabled", havingValue = "true")
public class LocalAuthSecurityConfig {

    /**
     * Token 交换服务
     */
    @Autowired
    private TokenExchangeService tokenExchangeService;

    /**
     * 本地安全过滤链
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    @Order(100)
    public SecurityFilterChain localAuthSecurityFilterChain(HttpSecurity http) throws Exception {
        // 无状态 JWT 认证（仅 Bearer 头，无 Cookie 会话），CSRF 防护不适用
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(registry -> registry
                // SSE等异步请求完成时Tomcat会以ASYNC分发回到过滤链，此时无认证上下文，放行避免流收尾被拒绝
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers(
                        "/api/auth/login",
                        // 平台管理员登录端点（管理后台登录页调用，与 /login 同为免认证入口）
                        "/api/auth/login/platform-admin",
                        "/api/auth/refresh-token",
                        // 连接器入站网关（渠道回调，自带验签）
                        "/open/**",
                        // SPA 静态资源与前端入口免认证
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        // 健康探测（仅暴露 status，供运维与启动脚本使用）
                        "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated());

        // 未认证请求按访问意图分流：HTML 页面导航转发到 SPA 入口（保留原始路径交由前端路由接管），其余返回 401 JSON
        http.exceptionHandling(handler -> handler.authenticationEntryPoint((request, response, ex) -> {
            if (isHtmlNavigation(request)) {
                request.getRequestDispatcher("/index.html").forward(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"success\":false,\"code\":401,\"message\":\"未登录或登录已过期\"}");
        }));

        // 注册 JWT 认证过滤器，解析请求头 Bearer JWT
        http.addFilterBefore(new JwtAuthenticationFilter(tokenExchangeService),
                UsernamePasswordAuthenticationFilter.class);

        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    // 吊销当前请求携带的 Bearer 令牌，使其立即失效
                    String authHeader = request.getHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        tokenExchangeService.logout(authHeader.substring(7));
                    }
                    UserContext.clear();
                    response.setStatus(HttpServletResponse.SC_OK);
                }));
        return http.build();
    }

    /**
     * 判断请求是否为浏览器 HTML 页面导航（GET 且 Accept 含 text/html，
     * 并排除 API、开放接口与健康检查路径，避免接口调用被重定向干扰）
     * @param request
     * @return
     */
    private boolean isHtmlNavigation(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains("text/html")) {
            return false;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith("/api") && !uri.startsWith("/open") && !uri.startsWith("/actuator");
    }
}
