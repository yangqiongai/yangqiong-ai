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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPA静态资源托管测试
 * @author yangqiong
 */
@DisplayName("SPA静态资源托管测试")
class SpaWebConfigTest {

    @TempDir
    Path tempDir;

    /**
     * 模拟启动包前端目录
     */
    private Path webDir;

    @BeforeEach
    void prepareWebDir() throws IOException {
        webDir = tempDir.resolve("web");
        Files.createDirectories(webDir.resolve("assets"));
        Files.writeString(webDir.resolve("index.html"), "<html>SPA-INDEX</html>", StandardCharsets.UTF_8);
        Files.writeString(webDir.resolve("assets/app.js"), "console.log('app');", StandardCharsets.UTF_8);
    }

    /**
     * 以指定 web 目录构建 MockMvc 上下文
     * @param dir
     * @return
     */
    private MockMvc buildMockMvc(Path dir) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("app.web-dir", dir.toString())));
        context.register(MvcConfig.class, SpaWebConfig.class);
        context.refresh();
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("根路径转发到SPA入口页")
    void servesIndexAtRoot() throws Exception {
        MockMvc mockMvc = buildMockMvc(webDir);
        // MockMvc 不执行 forward，生产容器中转发后由静态资源处理器返回 index.html
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("直接请求index.html返回入口页内容")
    void servesIndexHtmlDirectly() throws Exception {
        MockMvc mockMvc = buildMockMvc(webDir);
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string("<html>SPA-INDEX</html>"));
    }

    @Test
    @DisplayName("命中真实静态文件时直接返回")
    void servesRealAsset() throws Exception {
        MockMvc mockMvc = buildMockMvc(webDir);
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string("console.log('app');"));
    }

    @Test
    @DisplayName("SPA深层路由未命中文件时回退index.html")
    void fallsBackToIndexForDeepRoute() throws Exception {
        MockMvc mockMvc = buildMockMvc(webDir);
        mockMvc.perform(get("/agents/123"))
                .andExpect(status().isOk())
                .andExpect(content().string("<html>SPA-INDEX</html>"));
    }

    @Test
    @DisplayName("业务接口优先于静态资源映射")
    void apiControllerTakesPrecedence() throws Exception {
        MockMvc mockMvc = buildMockMvc(webDir);
        mockMvc.perform(get("/api/echo"))
                .andExpect(status().isOk())
                .andExpect(content().string("api-ok"));
    }

    @Test
    @DisplayName("未命中的接口路径不回退SPA入口")
    void unknownApiPathDoesNotFallBackToIndex() throws Exception {
        MockMvc mockMvc = buildMockMvc(webDir);
        mockMvc.perform(get("/api/unknown/endpoint"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("开发模式下web目录不存在时走原生404")
    void returns404WhenWebDirMissing() throws Exception {
        MockMvc mockMvc = buildMockMvc(tempDir.resolve("web-not-exists"));
        mockMvc.perform(get("/missing.html"))
                .andExpect(status().isNotFound());
    }

    /**
     * 提供MVC基础设施与业务接口探测
     */
    @Configuration
    @EnableWebMvc
    static class MvcConfig {

        /**
         * 接口探测控制器
         */
        @RestController
        static class ProbeController {

            /**
             * 探测接口
             * @return
             */
            @GetMapping("/api/echo")
            public String echo() {
                return "api-ok";
            }
        }
    }
}
