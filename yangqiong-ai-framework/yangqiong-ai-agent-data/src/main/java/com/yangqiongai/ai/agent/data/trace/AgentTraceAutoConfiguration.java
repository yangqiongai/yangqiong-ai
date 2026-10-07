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

import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import com.yangqiongai.ai.agent.runtime.trace.TraceEmitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Agent运行Trace落库自动配置
 * @author yangqiong
 */
@AutoConfiguration(after = com.yangqiongai.ai.agent.data.AiAgentDataAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ai.agent.trace", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentTraceProperties.class)
public class AgentTraceAutoConfiguration {

    /**
     * 注册平台Span批量落库导出器（与OTel等其他导出器并存，由装配处组合广播）
     * @param traceSpanRepository Span存储
     * @param properties Trace配置
     * @return
     */
    @Bean
    public TraceEmitter platformTraceEmitter(TraceSpanRepository traceSpanRepository,
                                             AgentTraceProperties properties) {
        return new PlatformTraceEmitter(traceSpanRepository, properties);
    }

    /**
     * 注册Span保留期清理任务（分批删除过期数据，防表无限膨胀；企业版可注入scope覆盖策略实现差异化保留）
     * @param traceSpanRepository Span存储
     * @param properties Trace配置
     * @param scopePolicyProvider scope保留期覆盖策略提供器(可空)
     * @return
     */
    @Bean
    public AgentTraceRetentionCleaner agentTraceRetentionCleaner(TraceSpanRepository traceSpanRepository,
                                                                 AgentTraceProperties properties,
                                                                 ObjectProvider<TraceRetentionScopePolicy> scopePolicyProvider) {
        return new AgentTraceRetentionCleaner(traceSpanRepository, properties, scopePolicyProvider.getIfAvailable());
    }
}
