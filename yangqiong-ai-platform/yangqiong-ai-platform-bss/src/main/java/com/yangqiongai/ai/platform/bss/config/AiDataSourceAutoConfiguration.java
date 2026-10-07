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
package com.yangqiongai.ai.platform.bss.config;

import com.yangqiongai.ai.common.datasource.RegisterDatasourceSupplier;
import com.yangqiongai.ai.platform.bss.datasource.RoutingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态多数据源装配
 * @author yangqiong
 */
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty("spring.datasource.url")
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(DataSourceProperties.class)
public class AiDataSourceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiDataSourceAutoConfiguration.class);

    /**
     * 构建路由数据源，默认数据源取自spring.datasource配置，动态数据源在应用就绪后由DynamicDatasourceRegistrar注册
     * @param properties
     * @return
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(DataSourceProperties properties) {
        DataSource defaultDataSource = DataSourceBuilder.create()
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .driverClassName(properties.determineDriverClassName())
                .build();

        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        routingDataSource.setTargetDataSources(new HashMap<>());
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    /**
     * 应用就绪后加载动态数据源并刷新路由目标，避免数据源创建期依赖MyBatis形成循环
     * @param dataSource
     * @param suppliers
     * @return
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> dynamicDatasourceRegistrar(
            DataSource dataSource,
            ObjectProvider<RegisterDatasourceSupplier> suppliers) {
        return event -> {
            if (!(dataSource instanceof RoutingDataSource routingDataSource)) {
                return;
            }
            Map<Object, Object> targetDataSources = new HashMap<>();
            for (RegisterDatasourceSupplier supplier : suppliers.orderedStream().toList()) {
                Map<String, Map<String, Object>> dynamicConfigs = supplier.get();
                if (dynamicConfigs == null) {
                    continue;
                }
                for (Map.Entry<String, Map<String, Object>> entry : dynamicConfigs.entrySet()) {
                    Map<String, Object> config = entry.getValue();
                    DataSource dynamicDataSource = DataSourceBuilder.create()
                            .url((String) config.get("url"))
                            .username((String) config.get("username"))
                            .password((String) config.get("password"))
                            .driverClassName((String) config.get("driverClassName"))
                            .build();
                    targetDataSources.put(entry.getKey(), dynamicDataSource);
                }
            }
            if (!targetDataSources.isEmpty()) {
                routingDataSource.setTargetDataSources(targetDataSources);
                routingDataSource.afterPropertiesSet();
                log.info("动态数据源注册完成，共{}个", targetDataSources.size());
            }
        };
    }
}
