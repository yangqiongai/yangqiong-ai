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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证自动配置测试
 * @author yangqiong
 */
@DisplayName("认证自动配置测试")
class AuthAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    // 引入 MVC 自动配置以提供 requestMatchers 所需的 HandlerMappingIntrospector
                    org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                    AuthAutoConfiguration.class))
            .withPropertyValues(
                    "ai.auth.enabled=true",
                    "ai.auth.jwt.secret=test-jwt-secret-key-0123456789-very-long!!");

    @Test
    @DisplayName("ai.auth.enabled=true 时装配本地令牌服务与安全过滤链")
    void registersBeansWhenEnabled() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TokenExchangeService.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context.getBean(AuthProperties.class).isEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("ai.auth.enabled=false 时不装配任何认证 Bean")
    void absentWhenDisabled() {
        runner.withPropertyValues("ai.auth.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TokenExchangeService.class);
                    assertThat(context).doesNotHaveBean(AuthProperties.class);
                });
    }

    @Test
    @DisplayName("密钥未配置时上下文启动失败")
    void failsWhenSecretMissing() {
        runner.withPropertyValues("ai.auth.jwt.secret=")
                .run(context -> assertThat(context).hasFailed());
    }
}
