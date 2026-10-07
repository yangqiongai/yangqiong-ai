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
package com.yangqiongai.ai.agent.tool.config;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.ToolCategory;
import com.yangqiongai.ai.agent.tool.ToolConfigManager;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.repository.ToolConfigRepository;
import com.yangqiongai.ai.common.spring.ApplicationContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ToolConfigManager 单元测试
 * @author yangqiong
 */
@DisplayName("ToolConfigManager 单元测试")
@ExtendWith(MockitoExtension.class)
class AiToolConfigManagerTest {

    @Mock
    private ToolConfigRepository toolConfigRepository;

    private ToolConfigManager service;

    private MockedStatic<ApplicationContextHelper> applicationContextHelper;

    @BeforeEach
    void setUp() {
        service = new ToolConfigManager();
        ReflectionTestUtils.setField(service, "toolConfigRepository", toolConfigRepository);
    }

    @AfterEach
    void tearDown() {
        if (applicationContextHelper != null) {
            applicationContextHelper.close();
            applicationContextHelper = null;
        }
    }

    @Test
    @DisplayName("syncToolsFromSpring - 数据库不存在时插入新工具")
    void syncToolsFromSpring_insertsNewTool_whenNotExist() {
        Tool testTool = new TestTool();
        mockSpringTools(List.of(testTool));
        when(toolConfigRepository.getByToolCode("TestTool")).thenReturn(null);

        service.syncToolsFromSpring();

        verify(toolConfigRepository, times(1)).save(any(ToolConfigInfo.class));
        verify(toolConfigRepository, never()).updateById(any(ToolConfigInfo.class));
    }

    @Test
    @DisplayName("syncToolsFromSpring - toolClass变化时更新已有工具")
    void syncToolsFromSpring_updatesExistingTool_whenClassChanged() {
        Tool testTool = new TestTool();
        mockSpringTools(List.of(testTool));
        ToolConfigInfo existing = new ToolConfigInfo();
        existing.setToolCode("TestTool");
        existing.setToolName("TestTool");
        existing.setToolClass("com.old.ClassName");
        existing.setToolType("TOOL");
        existing.setToolCategory("");
        existing.setToolOrder(0);
        existing.setToolStatus(1);
        when(toolConfigRepository.getByToolCode("TestTool")).thenReturn(existing);

        service.syncToolsFromSpring();

        verify(toolConfigRepository, times(1)).updateById(any(ToolConfigInfo.class));
        verify(toolConfigRepository, never()).save(any(ToolConfigInfo.class));
    }

    @Test
    @DisplayName("syncToolsFromSpring - 元数据一致时跳过更新")
    void syncToolsFromSpring_skipsUnchangedTool_whenMetadataEqual() {
        Tool testTool = new TestTool();
        mockSpringTools(List.of(testTool));
        ToolConfigInfo existing = new ToolConfigInfo();
        existing.setToolCode("TestTool");
        existing.setToolName("测试工具");
        existing.setToolDesc("测试工具描述");
        existing.setToolClass(testTool.getClass().getName());
        existing.setToolType("TOOL");
        existing.setToolCategory("CUSTOM");
        existing.setToolOrder(0);
        existing.setToolStatus(1);
        when(toolConfigRepository.getByToolCode("TestTool")).thenReturn(existing);

        service.syncToolsFromSpring();

        verify(toolConfigRepository, never()).updateById(any(ToolConfigInfo.class));
        verify(toolConfigRepository, never()).save(any(ToolConfigInfo.class));
    }

    @Test
    @DisplayName("syncToolsFromSpring - 空工具列表时仅记录警告")
    void syncToolsFromSpring_handlesEmptyTools() {
        mockSpringTools(Collections.emptyList());

        service.syncToolsFromSpring();

        verify(toolConfigRepository, never()).getByToolCode(any());
        verify(toolConfigRepository, never()).save(any(ToolConfigInfo.class));
        verify(toolConfigRepository, never()).updateById(any(ToolConfigInfo.class));
    }

    @Test
    @DisplayName("toggleStatus - 委托仓库切换状态")
    void toggleStatus_delegatesToRepository() {
        when(toolConfigRepository.toggleStatus("WebSearchTool")).thenReturn(true);

        boolean result = service.toggleStatus("WebSearchTool");

        assertThat(result).isTrue();
        verify(toolConfigRepository, times(1)).toggleStatus("WebSearchTool");
    }

    /**
     * 模拟Spring容器中的Tool列表
     * @param tools
     */
    private void mockSpringTools(List<Tool> tools) {
        applicationContextHelper = mockStatic(ApplicationContextHelper.class);
        applicationContextHelper.when(() -> ApplicationContextHelper.getBeanList(Tool.class)).thenReturn(tools);
    }

    /**
     * 测试用Tool实现
     */
    static class TestTool implements Tool {

        @AgentTool(value = "测试工具描述", name = "测试工具", type = "TOOL", category = ToolCategory.CUSTOM)
        public String testMethod() {
            return "test";
        }
    }
}
