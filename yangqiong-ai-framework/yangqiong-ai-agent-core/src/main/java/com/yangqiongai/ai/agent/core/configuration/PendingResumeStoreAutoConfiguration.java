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

import com.yangqiongai.ai.agent.core.executor.InMemoryPendingResumeStore;
import com.yangqiongai.ai.agent.core.executor.JdbcPendingResumeStore;
import com.yangqiongai.ai.agent.core.executor.PendingResumeStore;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * 暂停恢复登记存储自动配置
 * <p>
 * 配置 ai.agent.harness.distributed-store=jdbc 时装配JDBC实现支持多节点跨节点恢复，
 * 默认装配内存实现保持单机行为。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
public class PendingResumeStoreAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.agent.harness.distributed-store", havingValue = "jdbc")
    public PendingResumeStore jdbcPendingResumeStore(DataSource dataSource) {
        return new JdbcPendingResumeStore(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(PendingResumeStore.class)
    public PendingResumeStore inMemoryPendingResumeStore() {
        return new InMemoryPendingResumeStore();
    }
}
