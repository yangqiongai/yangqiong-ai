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
package com.yangqiongai.ai.agent.registry.config;

import com.yangqiongai.ai.agent.core.processor.AgentProcessor;
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentDefinitionMapper;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentGrayRuleMapper;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentVersionMapper;
import com.yangqiongai.ai.llm.LlmModelService;
import com.yangqiongai.ai.agent.registry.controller.AgentRegistryController;
import com.yangqiongai.ai.agent.registry.materialize.AgentConfigMaterializer;
import com.yangqiongai.ai.agent.registry.gray.GrayRouter;
import com.yangqiongai.ai.agent.registry.service.AgentRegistryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册中心条件装配单元测试
 * @author yangqiong
 */
@DisplayName("AgentRegistry条件装配单元测试")
class AgentRegistryConditionalAssemblyTest {

    /**
     * 提供registry装配所需的外部依赖mock
     */
    @Configuration(proxyBeanMethods = false)
    static class DependencyConfig {

        @Bean
        public AgentDefinitionMapper agentDefinitionMapper() {
            return Mockito.mock(AgentDefinitionMapper.class);
        }

        @Bean
        public AgentVersionMapper agentVersionMapper() {
            return Mockito.mock(AgentVersionMapper.class);
        }

        @Bean
        public AgentGrayRuleMapper agentGrayRuleMapper() {
            // GrayRouter mock经@Bean返回后Spring仍会做@Autowired字段注入所需
            return Mockito.mock(AgentGrayRuleMapper.class);
        }

        @Bean
        public AgentManager agentManager() {
            return Mockito.mock(AgentManager.class);
        }

        @Bean
        public LlmModelService llmModelService() {
            // AgentManager mock经@Bean返回后Spring仍会做@Autowired字段注入所需
            return Mockito.mock(LlmModelService.class);
        }

        @Bean
        public AgentProcessor agentProcessor() {
            // AgentManager mock继承的@Autowired List<AgentProcessor>字段注入所需
            return Mockito.mock(AgentProcessor.class);
        }

        @Bean
        public AgentRepository agentRepository() {
            return Mockito.mock(AgentRepository.class);
        }

        @Bean
        public ApplicationEventPublisher applicationEventPublisher() {
            return Mockito.mock(ApplicationEventPublisher.class);
        }
    }

    @Test
    @DisplayName("enabled=true时注册中心组件全部装配")
    void enabledAssemblesAllComponents() {
        new ApplicationContextRunner()
                .withPropertyValues("ai.agent.registry.enabled=true")
                .withConfiguration(AutoConfigurations.of(AgentRegistryConfiguration.class))
                .withUserConfiguration(DependencyConfig.class, GrayRouter.class, AgentRegistryServiceImpl.class,
                        AgentConfigMaterializer.class, AgentRegistryController.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentRegistryController.class);
                    assertThat(context).hasSingleBean(AgentRegistryServiceImpl.class);
                    assertThat(context).hasSingleBean(AgentConfigMaterializer.class);
                    assertThat(context).hasSingleBean(AgentRegistryProperties.class);
                });
    }

    @Test
    @DisplayName("enabled=false时注册中心组件全部不装配")
    void disabledSkipsAllComponents() {
        new ApplicationContextRunner()
                .withPropertyValues("ai.agent.registry.enabled=false")
                .withConfiguration(AutoConfigurations.of(AgentRegistryConfiguration.class))
                .withUserConfiguration(DependencyConfig.class, GrayRouter.class, AgentRegistryServiceImpl.class,
                        AgentConfigMaterializer.class, AgentRegistryController.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AgentRegistryController.class);
                    assertThat(context).doesNotHaveBean(AgentRegistryServiceImpl.class);
                    assertThat(context).doesNotHaveBean(AgentConfigMaterializer.class);
                });
    }
}
