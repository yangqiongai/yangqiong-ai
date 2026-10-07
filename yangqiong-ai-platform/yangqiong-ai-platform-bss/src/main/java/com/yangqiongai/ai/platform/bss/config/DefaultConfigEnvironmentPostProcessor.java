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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.boot.env.YamlPropertySourceLoader;

import java.io.IOException;
import java.util.List;

/**
 * 模块默认配置加载
 * <p>
 * 扫描classpath下所有模块的default-config/*.yml并以最低优先级（addLast）追加到环境，
 * 模块提供默认参数，业务侧application.yml配置可覆盖模块默认值
 * </p>
 * @author yangqiong
 */
public class DefaultConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DefaultConfigEnvironmentPostProcessor.class);

    /**
     * 模块默认配置扫描位置
     */
    private static final String DEFAULT_CONFIG_LOCATION = "classpath*:default-config/*.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MutablePropertySources propertySources = environment.getPropertySources();
        int loaded = 0;
        try {
            Resource[] resources = resolver.getResources(DEFAULT_CONFIG_LOCATION);
            for (Resource resource : resources) {
                String name = "defaultConfig[" + resource.getURI() + "]";
                List<PropertySource<?>> sources = loader.load(name, resource);
                for (PropertySource<?> propertySource : sources) {
                    // 追加到末尾，业务配置优先于模块默认值
                    propertySources.addLast(propertySource);
                    loaded++;
                }
            }
        } catch (IOException e) {
            log.warn("模块默认配置加载失败", e);
            return;
        }
        if (loaded > 0) {
            log.info("模块默认配置加载完成，共{}个属性源", loaded);
        }
    }

    @Override
    public int getOrder() {
        // 跟在ConfigDataEnvironmentPostProcessor之后执行，保证application.yml已就绪
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
