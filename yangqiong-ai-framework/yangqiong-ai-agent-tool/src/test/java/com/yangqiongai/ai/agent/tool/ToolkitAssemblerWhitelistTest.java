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
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.ToolConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ToolkitAssembler 装配规则单元测试
 * <p>
 * BUILTIN内置工具自动装配不过滤；CUSTOM工具按白名单挂载。
 * </p>
 * @author yangqiong
 */
@DisplayName("ToolkitAssembler 装配规则单元测试")
class ToolkitAssemblerWhitelistTest {

    private McpConnectionPool mcpPool;

    private ToolConfigManager toolConfigManager;

    @BeforeEach
    void setUp() {
        mcpPool = mock(McpConnectionPool.class);
        toolConfigManager = mock(ToolConfigManager.class);
    }

    @Test
    @DisplayName("assemble - BUILTIN工具自动装配，不受白名单影响")
    void assemble_alwaysAssemblesBuiltinTools() {
        Tool builtinTool = new BuiltinTool();
        Tool customTool = new CustomTool();
        when(toolConfigManager.getByToolCode("BuiltinTool"))
                .thenReturn(enabledConfig("BuiltinTool"));
        when(toolConfigManager.getByToolCode("CustomTool"))
                .thenReturn(enabledConfig("CustomTool"));

        ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, List.of(builtinTool, customTool), toolConfigManager);
        Toolkit toolkit = assembler.assemble("kb_qa", null, Set.of());

        assertThat(toolkit.getTools()).hasSize(1);
        assertThat(toolkit.getTools().get(0)).isInstanceOf(BuiltinTool.class);
    }

    @Test
    @DisplayName("assemble - 白名单非空时CUSTOM工具按白名单挂载")
    void assemble_mountsCustomToolsByWhitelist() {
        Tool builtinTool = new BuiltinTool();
        Tool customTool = new CustomTool();
        when(toolConfigManager.getByToolCode("BuiltinTool"))
                .thenReturn(enabledConfig("BuiltinTool"));
        when(toolConfigManager.getByToolCode("CustomTool"))
                .thenReturn(enabledConfig("CustomTool"));

        ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, List.of(builtinTool, customTool), toolConfigManager);
        Toolkit toolkit = assembler.assemble("kb_qa", null, Set.of("CustomTool"));

        assertThat(toolkit.getTools()).hasSize(2);
    }

    @Test
    @DisplayName("assemble - 工具状态为0时跳过")
    void assemble_skipsDisabledTools_whenToolStatusIsZero() {
        Tool builtinTool = new BuiltinTool();
        Tool customTool = new CustomTool();
        when(toolConfigManager.getByToolCode("BuiltinTool"))
                .thenReturn(enabledConfig("BuiltinTool"));
        when(toolConfigManager.getByToolCode("CustomTool"))
                .thenReturn(disabledConfig("CustomTool"));

        ToolkitAssembler assembler = new ToolkitAssembler(mcpPool, List.of(builtinTool, customTool), toolConfigManager);
        Toolkit toolkit = assembler.assemble("kb_qa", null, Set.of("CustomTool"));

        assertThat(toolkit.getTools()).hasSize(1);
        assertThat(toolkit.getTools().get(0)).isInstanceOf(BuiltinTool.class);
    }

    private ToolConfigInfo enabledConfig(String toolCode) {
        ToolConfigInfo config = new ToolConfigInfo();
        config.setToolCode(toolCode);
        config.setToolStatus(1);
        return config;
    }

    private ToolConfigInfo disabledConfig(String toolCode) {
        ToolConfigInfo config = new ToolConfigInfo();
        config.setToolCode(toolCode);
        config.setToolStatus(0);
        return config;
    }

    static class BuiltinTool implements Tool {
        @Override
        public ToolCategory getToolCategory() {
            return ToolCategory.BUILTIN;
        }
    }

    static class CustomTool implements Tool {
    }
}
