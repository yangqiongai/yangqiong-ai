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
package com.yangqiongai.ai.agent.harness.config;

import java.util.List;

import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.model.provider.AnthropicModelProvider;
import com.yangqiongai.agent.harness.model.provider.DashScopeModelProvider;
import com.yangqiongai.agent.harness.model.provider.OllamaModelProvider;
import com.yangqiongai.agent.harness.model.provider.OpenAIModelProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelProvider;
import org.springframework.core.annotation.Order;

/**
 * 模型Provider自动配置
 * <p>
 * 自动注册所有{@link AgentModelProvider} Bean到{@link AgentModelRegistry}，
 * 通过@ConditionalOnProperty控制各provider的启用状态。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnClass(AgentModelRegistry.class)
public class ModelProviderAutoConfiguration {

    /**
     * 注册OpenAI provider
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(OpenAIModelProvider.class)
    @ConditionalOnProperty(name = "ai.agent.harness.model.providers.openai.enabled", havingValue = "true", matchIfMissing = true)
    public OpenAIModelProvider openAIModelProvider() {
        return new OpenAIModelProvider();
    }

    /**
     * 注册DashScope provider
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(DashScopeModelProvider.class)
    @ConditionalOnProperty(name = "ai.agent.harness.model.providers.dashscope.enabled", havingValue = "true", matchIfMissing = true)
    public DashScopeModelProvider dashScopeModelProvider() {
        return new DashScopeModelProvider();
    }

    /**
     * 注册Ollama provider
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(OllamaModelProvider.class)
    @ConditionalOnProperty(name = "ai.agent.harness.model.providers.ollama.enabled", havingValue = "true", matchIfMissing = true)
    public OllamaModelProvider ollamaModelProvider() {
        return new OllamaModelProvider();
    }

    /**
     * 注册Anthropic provider
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AnthropicModelProvider.class)
    @ConditionalOnProperty(name = "ai.agent.harness.model.providers.anthropic.enabled", havingValue = "true", matchIfMissing = true)
    public AnthropicModelProvider anthropicModelProvider() {
        return new AnthropicModelProvider();
    }

    /**
     * 注册模型中心注册表
     * <p>
     * 自动收集所有{@link AgentModelProvider} Bean，注册到{@link AgentModelRegistry}。
     * 默认provider从{@link HarnessModelProperties}读取。
     * </p>
     * @param properties
     * @param providerProvider
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AgentModelRegistry.class)
    public AgentModelRegistry agentModelRegistry(HarnessModelProperties properties,
                                                 ObjectProvider<AgentModelProvider> providerProvider) {
        AgentModelRegistry registry = new AgentModelRegistry(properties.getDefaultProvider());
        List<AgentModelProvider> providers = providerProvider.stream().toList();
        for (AgentModelProvider provider : providers) {
            if (properties.isProviderEnabled(provider.providerId())) {
                registry.registerProvider(provider);
            }
        }
        return registry;
    }
}
