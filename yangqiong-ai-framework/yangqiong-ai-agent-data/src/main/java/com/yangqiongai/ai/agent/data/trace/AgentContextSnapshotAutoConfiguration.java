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
package com.yangqiongai.ai.agent.data.trace;

import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Agent上下文快照落库自动配置
 * @author yangqiong
 */
@AutoConfiguration(after = com.yangqiongai.ai.agent.data.AiAgentDataAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ai.agent.context-snapshot", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgentContextSnapshotProperties.class)
public class AgentContextSnapshotAutoConfiguration {

    /**
     * 注册平台上下文快照批量落库记录器（经运行时装配回调采集，开关关闭时不装配）
     * @param contextSnapshotRepository 快照存储
     * @param properties 快照配置
     * @return
     */
    @Bean
    public ContextSnapshotListener platformContextRecorder(ContextSnapshotRepository contextSnapshotRepository,
                                                           AgentContextSnapshotProperties properties) {
        return new PlatformContextRecorder(contextSnapshotRepository, properties);
    }
}
