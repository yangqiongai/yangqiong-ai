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

import com.yangqiongai.ai.agent.registry.gray.ModelRoutePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册中心配置装配
 * @author yangqiong
 */
@Configuration
@EnableConfigurationProperties({AgentRegistryProperties.class, ModelRouteProperties.class})
public class AgentRegistryConfiguration {

    /**
     * 模型分级路由策略(无状态，供各执行入口复用)
     * @return
     */
    @Bean
    public ModelRoutePolicy modelRoutePolicy() {
        return new ModelRoutePolicy();
    }
}
