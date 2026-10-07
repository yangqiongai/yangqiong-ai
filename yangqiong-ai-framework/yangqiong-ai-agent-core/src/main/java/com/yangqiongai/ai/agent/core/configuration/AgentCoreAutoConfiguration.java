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
package com.yangqiongai.ai.agent.core.configuration;

import com.yangqiongai.ai.agent.core.orchestration.OrchestrationProcessor;
import com.yangqiongai.ai.agent.core.processor.DefaultAgentProcessor;
import com.yangqiongai.ai.agent.core.session.AgentSessionStore;
import com.yangqiongai.ai.agent.core.session.InMemoryAgentSessionStore;
import com.yangqiongai.ai.agent.core.spi.AgentScopeFilter;
import com.yangqiongai.ai.agent.core.spi.DefaultAgentScopeFilter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Agent核心自动配置
 * @author yangqiong
 */
@AutoConfiguration
public class AgentCoreAutoConfiguration {

    @Bean
    public DefaultAgentProcessor defaultTaskProcessor() {
        return new DefaultAgentProcessor();
    }

    @Bean
    public OrchestrationProcessor orchestrationTaskProcessor() {
        return new OrchestrationProcessor();
    }

    @Bean
    @ConditionalOnMissingBean(AgentSessionStore.class)
    public InMemoryAgentSessionStore inMemoryAgentSessionStore() {
        return new InMemoryAgentSessionStore();
    }

    /**
     * 社区版默认按平台域过滤，企业版装配租户过滤实现后本Bean自动让位
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AgentScopeFilter.class)
    public DefaultAgentScopeFilter defaultAgentScopeFilter() {
        return new DefaultAgentScopeFilter();
    }

}
