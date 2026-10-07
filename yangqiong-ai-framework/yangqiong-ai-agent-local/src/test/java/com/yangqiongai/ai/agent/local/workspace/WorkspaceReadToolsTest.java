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
package com.yangqiongai.ai.agent.local.workspace;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区读列工具测试
 * @author yangqiong
 */
class WorkspaceReadToolsTest {

    private WorkspaceRef virtualRef() {
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(1L);
        info.setName("连接器工作区");
        info.setType(WorkspaceRef.TYPE_CONNECTOR);
        info.setRootPath("connector://dev/root_x");
        return WorkspaceRef.of(info);
    }

    private AgentTool tool(ListToolkit toolkit, String name) {
        return toolkit.tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    private AgentToolResultBlock call(AgentTool t, Map<String, Object> input) {
        return t.callAsync(new AgentToolCallParam(input)).block();
    }

    /**
     * 文本文件读取直出内容，截断时附说明
     */
    @Test
    void readReturnsTextContent() {
        WorkspaceFileToolsTest.RecordingGateway gateway = new WorkspaceFileToolsTest.RecordingGateway();
        gateway.readResult = Map.of("type", "text", "content", "第一行\n第二行", "truncated", false);
        ListToolkit toolkit = new ListToolkit();
        WorkspaceReadTools.registerTo(toolkit, virtualRef(), gateway);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "notes.txt");
        AgentToolResultBlock result = call(tool(toolkit, "file_read"), input);

        assertFalse(result.isError());
        assertTrue(result.getTextContent().contains("第一行\n第二行"));

        gateway.readResult = Map.of("type", "text", "content", "abc", "truncated", true);
        AgentToolResultBlock truncated = call(tool(toolkit, "file_read"), input);
        assertTrue(truncated.getTextContent().contains("已截断"));
    }

    /**
     * 结构化类型转JSON，二进制类型返回说明
     */
    @Test
    void readFormatsStructuredAndBinaryTypes() {
        WorkspaceFileToolsTest.RecordingGateway gateway = new WorkspaceFileToolsTest.RecordingGateway();
        gateway.readResult = Map.of("type", "excel", "workbook", Map.of("sheets", List.of()));
        ListToolkit toolkit = new ListToolkit();
        WorkspaceReadTools.registerTo(toolkit, virtualRef(), gateway);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "table.xlsx");
        AgentToolResultBlock excel = call(tool(toolkit, "file_read"), input);
        assertFalse(excel.isError());
        assertTrue(excel.getTextContent().contains("workbook"));

        gateway.readResult = Map.of("type", "image", "content", "data:image/png;base64,xxx");
        AgentToolResultBlock image = call(tool(toolkit, "file_read"), input);
        assertTrue(image.getTextContent().contains("二进制"));
        assertFalse(image.getTextContent().contains("base64"));
    }

    /**
     * 列目录输出条目清单，空目录给说明
     */
    @Test
    void listFormatsEntriesAndEmptyDir() {
        WorkspaceFileToolsTest.RecordingGateway gateway = new WorkspaceFileToolsTest.RecordingGateway();
        gateway.treeEntries = List.of(
                Map.of("name", "docs", "dir", true),
                Map.of("name", "notes.txt", "dir", false));
        ListToolkit toolkit = new ListToolkit();
        WorkspaceReadTools.registerTo(toolkit, virtualRef(), gateway);

        AgentToolResultBlock result = call(tool(toolkit, "file_list"), new LinkedHashMap<>());

        assertFalse(result.isError());
        String text = result.getTextContent();
        assertTrue(text.contains("[目录] docs"));
        assertTrue(text.contains("[文件] notes.txt"));

        gateway.treeEntries = List.of();
        AgentToolResultBlock empty = call(tool(toolkit, "file_list"), new LinkedHashMap<>());
        assertTrue(empty.getTextContent().contains("空目录"));
    }

    /**
     * 网关抛出异常时返回错误结果而非中断
     */
    @Test
    void readGatewayFailureReturnsErrorResult() {
        WorkspaceFileToolsTest.RecordingGateway gateway = new WorkspaceFileToolsTest.RecordingGateway() {
            @Override
            public Map<String, Object> read(com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef ref,
                                            String path,
                                            com.yangqiongai.ai.agent.local.workspace.gateway.PreviewOptions options) {
                throw new IllegalArgumentException("路径非法或越界：" + path);
            }
        };
        ListToolkit toolkit = new ListToolkit();
        WorkspaceReadTools.registerTo(toolkit, virtualRef(), gateway);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "../escape.txt");
        AgentToolResultBlock result = call(tool(toolkit, "file_read"), input);

        assertTrue(result.isError());
        assertTrue(result.getTextContent().contains("路径非法或越界"));
    }

    /**
     * 简易工具箱收集注册的工具
     */
    static class ListToolkit implements AgentToolkit {

        final List<AgentTool> tools = new java.util.ArrayList<>();

        @Override
        public List<AgentTool> getTools() {
            return tools;
        }

        @Override
        public boolean isEmpty() {
            return tools.isEmpty();
        }

        @Override
        public void addTool(AgentTool tool) {
            tools.add(tool);
        }
    }
}
