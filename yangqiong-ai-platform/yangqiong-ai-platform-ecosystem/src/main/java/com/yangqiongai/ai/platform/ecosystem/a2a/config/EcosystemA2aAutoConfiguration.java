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
package com.yangqiongai.ai.platform.ecosystem.a2a.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.registry.service.AgentRegistryService;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aCardSigner;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aProperties;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aPushConfigService;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aPushConfigServiceImpl;
import com.yangqiongai.ai.platform.ecosystem.a2a.A2aPushNotifier;
import com.yangqiongai.ai.platform.ecosystem.a2a.controller.AgentCardController;
import com.yangqiongai.ai.platform.ecosystem.a2a.controller.A2aTaskController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * A2A出口自动配置
 * <p>
 * 对象模型用官方a2a-java-sdk-spec，传输为Spring MVC自研三端点；
 * 任务映射复用E2队列入队器，回调走完成事件监听。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration(afterName = {
        "com.yangqiongai.ai.agent.data.AiAgentDataAutoConfiguration",
        "com.yangqiongai.ai.trust.config.TrustAutoConfiguration"
})
@ConditionalOnClass(BaseMapper.class)
@ConditionalOnProperty(prefix = "ai.ecosystem", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(A2aProperties.class)
public class EcosystemA2aAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public A2aCardSigner a2aCardSigner(
            @Value("${ai.trust.identity.master-key:}") String identityMasterKey) {
        return new A2aCardSigner(identityMasterKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aPushConfigService a2aPushConfigService() {
        return new A2aPushConfigServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AgentTaskRepository.class)
    @ConditionalOnProperty(prefix = "ai.ecosystem.a2a", name = "enabled", havingValue = "true")
    public A2aPushNotifier a2aPushNotifier(A2aPushConfigService pushConfigService) {
        return new A2aPushNotifier(pushConfigService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    @ConditionalOnBean({AgentTaskRepository.class, AgentTaskEnqueuer.class, AgentRegistryService.class})
    @ConditionalOnProperty(prefix = "ai.ecosystem.a2a", name = "enabled", havingValue = "true")
    public AgentCardController agentCardController(AgentRegistryService registryService,
                                                   A2aCardSigner cardSigner,
                                                   A2aProperties properties,
                                                   @Value("${ai.ecosystem.a2a.base-url:http://localhost:8082}")
                                                   String baseUrl) {
        return new AgentCardController(registryService, cardSigner, properties, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication
    @ConditionalOnBean({AgentTaskRepository.class, AgentTaskEnqueuer.class, AgentEngine.class})
    @ConditionalOnProperty(prefix = "ai.ecosystem.a2a", name = "enabled", havingValue = "true")
    public A2aTaskController a2aTaskController(AgentTaskEnqueuer taskEnqueuer,
                                               AgentTaskRepository agentTaskRepository,
                                               A2aPushConfigService pushConfigService,
                                               QueueProperties queueProperties,
                                               AgentEngine agentEngine) {
        return new A2aTaskController(taskEnqueuer, agentTaskRepository, pushConfigService,
                queueProperties, agentEngine);
    }
}
