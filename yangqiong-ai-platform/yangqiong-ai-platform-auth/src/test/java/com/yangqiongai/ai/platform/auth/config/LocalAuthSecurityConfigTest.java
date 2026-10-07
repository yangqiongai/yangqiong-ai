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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本地登录安全配置测试
 * @author yangqiong
 */
@DisplayName("本地登录安全配置测试")
class LocalAuthSecurityConfigTest {

    /**
     * 启动测试上下文并构建携带安全过滤链的 MockMvc（上下文保持开启供 MockMvc 使用）
     * @return
     */
    private MockMvc buildMockMvc() {
        AnnotationConfigServletWebApplicationContext context = new AnnotationConfigServletWebApplicationContext();
        context.setServletContext(new MockServletContext());
        // 不引入 SecurityAutoConfiguration，与生产装配一致（server 启动类已 exclude，避免默认过滤链冲突）
        context.register(DispatcherServletAutoConfiguration.class,
                WebMvcAutoConfiguration.class,
                AuthAutoConfiguration.class,
                TestControllerConfig.class);
        TestPropertyValues.of(
                "ai.auth.enabled=true",
                "ai.auth.jwt.secret=test-jwt-secret-key-0123456789-very-long!!").applyTo(context.getEnvironment());
        context.refresh();
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isNotEmpty();
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("SPA静态资源路径免认证访问")
    void staticResourcesPermitted() throws Exception {
        MockMvc mockMvc = buildMockMvc();
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("home"));
        // 以下路径无对应资源，返回 404 即代表已通过安全链放行（未放行时为 401）
        mockMvc.perform(get("/index.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/assets/app.js")).andExpect(status().isNotFound());
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未认证HTML导航转发到SPA入口并保留原路径")
    void htmlNavigationForwardsToSpaIndex() throws Exception {
        MockMvc mockMvc = buildMockMvc();
        // forward 保留原始 URI，生产容器中由静态资源处理器返回 index.html（MockMvc 中真实执行转发）
        mockMvc.perform(get("/agents/123").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("未认证API请求返回401JSON")
    void apiRequestReturns401Json() throws Exception {
        MockMvc mockMvc = buildMockMvc();
        mockMvc.perform(get("/api/secure"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"success\":false,\"code\":401,\"message\":\"未登录或登录已过期\"}"));
    }

    @Test
    @DisplayName("API路径即使Accept为HTML也不转发")
    void apiPathNeverRedirects() throws Exception {
        MockMvc mockMvc = buildMockMvc();
        mockMvc.perform(get("/api/secure").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("非GET请求即使Accept为HTML也不转发")
    void nonGetRequestNeverRedirects() throws Exception {
        MockMvc mockMvc = buildMockMvc();
        // csrf() 绕过默认 CSRF 拦截，聚焦验证未认证 EntryPoint 的分流逻辑
        mockMvc.perform(post("/agents/123").with(csrf()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("健康探测路径即使Accept为HTML也不重定向")
    void healthPathNeverRedirects() throws Exception {
        MockMvc mockMvc = buildMockMvc();
        mockMvc.perform(get("/actuator/health").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    /**
     * 提供探测控制器
     */
    @Configuration
    static class TestControllerConfig {

        /**
         * 安全链行为探测控制器
         */
        @RestController
        static class ProbeController {

            /**
             * 首页探测
             * @return
             */
            @GetMapping("/")
            public String home() {
                return "home";
            }

            /**
             * 受保护接口探测
             * @return
             */
            @GetMapping("/api/secure")
            public String secure() {
                return "ok";
            }
        }
    }
}
