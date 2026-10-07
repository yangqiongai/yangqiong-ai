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
package com.yangqiongai.ai.agent.tool;

import com.yangqiongai.ai.agent.mcp.McpConnectionPool;
import com.yangqiongai.ai.agent.tool.ToolConfigManager;
import com.yangqiongai.ai.agent.tool.rag.StandardRagContextExecutor;
import com.yangqiongai.ai.agent.tool.rag.ContextAwareRagTool;
import com.yangqiongai.ai.agent.tool.rag.RagContextProvider;
import com.yangqiongai.ai.rag.RagRetrieveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 工具自动配置
 * @author yangqiong
 */
@Configuration
public class ToolAutoConfiguration {

    @Autowired
    private McpConnectionPool mcpConnectionPool;

    @Autowired
    private RagRetrieveService ragRetrieveService;

    @Autowired
    private ToolConfigManager toolConfigManager;

    @Value("${ai.rag.tool.default-kb-ids:}")
    private String defaultKbIdsConfig;

    /**
     * 工具箱装配器
     * @return
     */
    @Bean
    public ToolkitAssembler toolkitAssembler(List<Tool> toolProviders) {
        return new ToolkitAssembler(mcpConnectionPool, toolProviders, toolConfigManager);
    }

    /**
     * RAG上下文提供者
     * @return
     */
    @Bean
    public RagContextProvider ragContextProvider() {
        List<String> defaultKbIds = parseKbIds(defaultKbIdsConfig);
        return new StandardRagContextExecutor(ragRetrieveService, defaultKbIds);
    }

    /**
     * RAG上下文感知检索工具
     * @return
     */
    @Bean
    public ContextAwareRagTool contextAwareRagTool() {
        return new ContextAwareRagTool(ragContextProvider());
    }

    /**
     * 解析默认知识库ID配置
     * @param config
     * @return
     */
    private List<String> parseKbIds(String config) {
        if (config == null || config.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }
}
