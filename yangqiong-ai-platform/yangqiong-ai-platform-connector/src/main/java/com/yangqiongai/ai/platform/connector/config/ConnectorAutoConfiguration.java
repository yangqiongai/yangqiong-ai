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
package com.yangqiongai.ai.platform.connector.config;

import com.yangqiongai.ai.platform.connector.database.DatabaseConnectionManager;
import com.yangqiongai.ai.platform.connector.database.DatabaseProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 连接器自动配置
 * <p>
 * Mapper扫描与配置属性启用；全部组件条件装配：
 * ai.connector.enabled=false（默认）时零装配零开销。
 * </p>
 * @author yangqiong
 */
@Configuration
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
@EnableConfigurationProperties(ConnectorProperties.class)
public class ConnectorAutoConfiguration {

    /**
     * 数据库连接器装配（引入HikariCP时生效）
     * <p>
     * 内嵌配置类会先于外层条件注册，需重复声明开关条件，
     * 避免 ai.connector.enabled=false 时仍装配并缺失 ConnectorProperties。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HikariDataSource.class)
    @ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
    public static class DatabaseConnectorConfiguration {

        /**
         * 数据库连接池管理
         * @return
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        public DatabaseConnectionManager databaseConnectionManager() {
            return new DatabaseConnectionManager();
        }

        /**
         * 数据库查询连接器提供商
         * @param properties 连接器配置
         * @param connectionManager 连接池管理
         * @return
         */
        @Bean
        @ConditionalOnMissingBean
        public DatabaseProvider databaseProvider(ConnectorProperties properties,
                                                 DatabaseConnectionManager connectionManager) {
            return new DatabaseProvider(properties, connectionManager);
        }
    }
}
