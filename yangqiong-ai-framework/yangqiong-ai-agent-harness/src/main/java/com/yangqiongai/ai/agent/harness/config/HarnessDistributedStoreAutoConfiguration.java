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

import com.yangqiongai.agent.harness.durable.DistributedStores;
import com.yangqiongai.agent.harness.store.jdbc.JdbcDistributedStores;
import com.yangqiongai.ai.agent.harness.RuntimeSpiBridge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Harness共享存储自动装配
 * <p>
 * 多节点部署时通过 ai.agent.harness.distributed-store=jdbc 启用JDBC共享存储，
 * 检查点/审批/运行记录/运行锁持久化到数据库，配合运行锁实现跨节点暂停恢复；
 * 未配置时无bean，单机默认路径零改动。
 * </p>
 * @author yangqiong
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.agent.harness.distributed-store", havingValue = "jdbc")
public class HarnessDistributedStoreAutoConfiguration {

    /**
     * 装配框架侧共享存储（引擎JDBC实现经SPI桥接，仅桥接持久执行四项，记忆类由框架侧自有装配提供）
     * @param dataSource
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(com.yangqiongai.ai.agent.runtime.durable.DistributedStores.class)
    public com.yangqiongai.ai.agent.runtime.durable.DistributedStores harnessDistributedStores(
            DataSource dataSource) {
        return RuntimeSpiBridge.toRuntime((DistributedStores) new JdbcDistributedStores(dataSource));
    }
}
