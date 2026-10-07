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
package com.yangqiongai.ai.server.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * SPA静态资源托管
 * @author yangqiong
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    /**
     * 不参与 SPA 兜底的接口路径前缀（未命中时走原生 404）
     */
    private static final String[] API_PREFIXES = {
            "api/", "open/", "actuator/", "v3/api-docs", "swagger-ui", "webjars/"
    };

    /**
     * 前端静态资源目录
     */
    @Value("${app.web-dir:./web/}")
    private String webDir;

    /**
     * 注册静态资源映射：优先读取外置 web 目录，其次 classpath 内置资源；
     * 所有位置均未命中且非接口路径时回退到 index.html，由前端路由接管
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(toFileLocation(webDir), "classpath:/META-INF/resources/", "classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {

                    /**
                     * 先按默认行为在全部资源位置查找真实文件；
                     * 全部未命中且为 SPA 深层路由时回退到首个含 index.html 的资源位置，
                     * 接口路径（api/open/actuator 等）不回退，走原生 404
                     * @param request
                     * @param requestPath
                     * @param locations
                     * @param chain
                     * @return
                     */
                    @Override
                    protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
                                                               List<? extends Resource> locations,
                                                               ResourceResolverChain chain) {
                        Resource resolved = super.resolveResourceInternal(request, requestPath, locations, chain);
                        if (resolved != null || isApiPath(requestPath)) {
                            return resolved;
                        }
                        for (Resource location : locations) {
                            try {
                                Resource index = location.createRelative("index.html");
                                if (index.exists() && index.isReadable()) {
                                    return index;
                                }
                            } catch (IOException ignored) {
                                // 当前资源位置不可用，继续尝试下一个
                            }
                        }
                        return null;
                    }
                });
    }

    /**
     * 根路径转发到 SPA 入口 index.html，由静态资源处理器负责返回
     * @param registry
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /**
     * 判断请求路径是否为接口类路径（资源查找时使用相对路径，无前导斜杠）
     * @param requestPath
     * @return
     */
    private boolean isApiPath(String requestPath) {
        for (String prefix : API_PREFIXES) {
            if (requestPath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将目录转换为以斜杠结尾的 file: 资源位置地址，兼容 Windows 与 Linux 路径
     * @param dir
     * @return
     */
    private String toFileLocation(String dir) {
        return Paths.get(dir).toAbsolutePath().normalize().toUri().toString();
    }
}
