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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 认证自动配置（本地登录与 JWT 签发/校验，SSO/OIDC/LDAP 由商业模块注入）
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthProperties.class)
@ConditionalOnProperty(name = "ai.auth.enabled", havingValue = "true")
@Import(LocalAuthSecurityConfig.class)
public class AuthAutoConfiguration {

    /**
     * 注册本地令牌交换服务
     * @param authProperties
     * @return
     */
    @Bean
    public TokenExchangeService tokenExchangeService(AuthProperties authProperties) {
        return new TokenExchangeService(authProperties);
    }
}
