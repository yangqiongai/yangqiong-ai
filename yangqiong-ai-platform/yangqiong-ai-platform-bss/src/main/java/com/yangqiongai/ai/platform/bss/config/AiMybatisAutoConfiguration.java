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

import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * MyBatis Mapper扫描装配
 * @author yangqiong
 */
@Configuration
@ConditionalOnClass(MapperScannerConfigurer.class)
public class AiMybatisAutoConfiguration {

    /**
     * 按mybatis.scan-basepackages配置扫描注册Mapper
     * <p>static声明：BeanDefinitionRegistryPostProcessor类型需提前实例化，静态方法避免配置类过早增强的警告</p>
     * @param environment
     * @return
     */
    @Bean
    @ConditionalOnProperty("mybatis.scan-basepackages")
    public static MapperScannerConfigurer mapperScannerConfigurer(Environment environment) {
        String scan = environment.getProperty("mybatis.scan-basepackages");
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        if (StringUtils.hasText(scan)) {
            configurer.setBasePackage(scan);
        }
        return configurer;
    }
}
