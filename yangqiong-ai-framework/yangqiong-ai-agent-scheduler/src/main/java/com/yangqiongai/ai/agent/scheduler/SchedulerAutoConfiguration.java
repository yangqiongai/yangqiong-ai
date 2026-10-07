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
package com.yangqiongai.ai.agent.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 平台定时调度装配
 * <p>
 * 用户级Agent调度默认自建内存调度器；存在AgentSchedulerProvider实现
 * （如集群模块）时自动切换为外部调度器。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnProperty(name = "ai.agent.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerAutoConfiguration {

    @Bean
    public UserSchedulerService userSchedulerService() {
        return new UserSchedulerService();
    }

    /**
     * 注入ApplicationContext到AgentScheduleJob，供Quartz Job手动获取Spring Bean
     * @param ctx
     */
    @Autowired
    public void injectApplicationContext(ApplicationContext ctx) {
        AgentScheduleJob.setApplicationContext(ctx);
    }
}
