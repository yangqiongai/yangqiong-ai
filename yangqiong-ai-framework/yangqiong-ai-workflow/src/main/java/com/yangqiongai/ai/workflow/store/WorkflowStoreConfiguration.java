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
package com.yangqiongai.ai.workflow.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 工作流状态存储装配
 * <p>优先级：Redis > 数据库 > 内存；暂停恢复流水有数据库时持久化，否则退化为日志留痕。
 * 存储实现的选择延迟到Bean实例化时判定，避免普通配置类早于自动配置处理导致条件装配失效</p>
 * @author yangqiong
 */
@Configuration
public class WorkflowStoreConfiguration {

    /**
     * 工作流状态存储（按环境可用组件选择实现）
     * @param redisProvider
     * @param jdbcTemplateProvider
     * @param objectMapper
     * @return
     */
    @Bean
    public WorkflowStateStore workflowStateStore(ObjectProvider<RedisTemplate<String, String>> redisProvider,
                                                 ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                                 ObjectMapper objectMapper) {
        RedisTemplate<String, String> redisTemplate = redisProvider.getIfAvailable();
        if (redisTemplate != null) {
            return new RedisWorkflowStateStore(redisTemplate, objectMapper);
        }
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            return new JdbcWorkflowStateStore(jdbcTemplate, objectMapper);
        }
        return new InMemoryWorkflowStateStore();
    }

    /**
     * 暂停恢复流水存储（有数据库时持久化，无则日志留痕）
     * @param jdbcTemplateProvider
     * @return
     */
    @Bean
    public WorkflowPauseHistoryStore workflowPauseHistoryStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate != null) {
            return new JdbcWorkflowPauseHistoryStore(jdbcTemplate);
        }
        return new NoopWorkflowPauseHistoryStore();
    }
}
