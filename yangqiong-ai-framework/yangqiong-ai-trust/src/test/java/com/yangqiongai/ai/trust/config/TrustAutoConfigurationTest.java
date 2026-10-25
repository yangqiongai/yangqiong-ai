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
package com.yangqiongai.ai.trust.config;

import com.yangqiongai.ai.trust.identity.AgentIdentityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 信任底座条件装配单元测试
 * @author yangqiong
 */
@DisplayName("TrustAutoConfiguration条件装配单元测试")
class TrustAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TrustAutoConfiguration.class))
            .withUserConfiguration(DependencyConfig.class);

    /**
     * 提供trust装配所需的外部依赖mock
     */
    @Configuration(proxyBeanMethods = false)
    static class DependencyConfig {

        @Bean
        public com.yangqiongai.ai.trust.identity.mapper.AgentIdentityMapper agentIdentityMapper() {
            return Mockito.mock(com.yangqiongai.ai.trust.identity.mapper.AgentIdentityMapper.class);
        }

        @Bean
        public com.yangqiongai.ai.trust.audit.mapper.AgentActionLogMapper agentActionLogMapper() {
            return Mockito.mock(com.yangqiongai.ai.trust.audit.mapper.AgentActionLogMapper.class);
        }

        @Bean
        public com.yangqiongai.ai.trust.audit.mapper.AgentActionAnchorMapper agentActionAnchorMapper() {
            return Mockito.mock(com.yangqiongai.ai.trust.audit.mapper.AgentActionAnchorMapper.class);
        }

        @Bean
        public com.yangqiongai.ai.trust.profile.mapper.AgentPermissionProfileMapper agentPermissionProfileMapper() {
            return Mockito.mock(com.yangqiongai.ai.trust.profile.mapper.AgentPermissionProfileMapper.class);
        }

        @Bean
        public com.yangqiongai.ai.agent.registry.service.AgentRegistryService agentRegistryService() {
            return Mockito.mock(com.yangqiongai.ai.agent.registry.service.AgentRegistryService.class);
        }
    }

    @Test
    @DisplayName("默认装配身份/权限画像/动作审计服务,开放凭证不再属于社区版")
    void defaultAssemblyShouldRegisterIdentityAndAudit() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AgentIdentityService.class);
            assertThat(context).hasSingleBean(com.yangqiongai.ai.trust.audit.ActionAuditService.class);
            assertThat(context).hasSingleBean(com.yangqiongai.ai.trust.profile.PermissionProfileService.class);
        });
    }

    @Test
    @DisplayName("ai.trust.enabled=false时不装配任何组件")
    void disabledTrustShouldAssembleNothing() {
        runner.withPropertyValues("ai.trust.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(AgentIdentityService.class);
            assertThat(context).doesNotHaveBean(com.yangqiongai.ai.trust.audit.ActionAuditService.class);
        });
    }

    @Test
    @DisplayName("已有自定义服务bean时不重复装配")
    void customServiceShouldTakePrecedence() {
        AgentIdentityService custom = Mockito.mock(AgentIdentityService.class);
        runner.withBean("customIdentityService", AgentIdentityService.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentIdentityService.class);
                    assertThat(context.getBean(AgentIdentityService.class)).isSameAs(custom);
                });
    }
}
