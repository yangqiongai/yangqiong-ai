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
import com.yangqiongai.ai.agent.local.workspace.gateway.PreviewOptions;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.local.workspace.gateway.WriteResult;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区写删工具测试
 * @author yangqiong
 */
class WorkspaceFileToolsTest {

    @TempDir
    Path tempDir;

    /**
     * 记录写删调用的网关桩
     */
    static class RecordingGateway implements WorkspaceGateway {

        String writtenPath;

        byte[] writtenContent;

        String deletedPath;

        Map<String, Object> readResult = Map.of();

        List<Map<String, Object>> treeEntries = List.of();

        @Override
        public boolean supports(String type) {
            return true;
        }

        @Override
        public List<Map<String, Object>> tree(WorkspaceRef ref, String dir) {
            return treeEntries;
        }

        @Override
        public Map<String, Object> read(WorkspaceRef ref, String path, PreviewOptions options) {
            return readResult;
        }

        @Override
        public WriteResult write(WorkspaceRef ref, String path, byte[] content) {
            this.writtenPath = path;
            this.writtenContent = content;
            return new WriteResult();
        }

        @Override
        public Map<String, Object> mkdir(WorkspaceRef ref, String path, String name) {
            return Map.of();
        }

        @Override
        public Map<String, Object> move(WorkspaceRef ref, String path, String targetDir) {
            return Map.of();
        }

        @Override
        public void rename(WorkspaceRef ref, String path, String name) {
        }

        @Override
        public void delete(WorkspaceRef ref, String path) {
            this.deletedPath = path;
        }

        @Override
        public Map<String, Object> copy(WorkspaceRef ref, String path, String targetDir) {
            return Map.of();
        }
    }

    /**
     * 简易工具箱收集注册的工具
     */
    static class ListToolkit implements AgentToolkit {

        final List<AgentTool> tools = new ArrayList<>();

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

    private AgentTool tool(ListToolkit toolkit, String name) {
        return toolkit.tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    private AgentToolResultBlock call(AgentTool t, Map<String, Object> input) {
        return t.callAsync(new AgentToolCallParam(input)).block();
    }

    /**
     * 虚拟根（rootPath为空）注册不抛NPE，写操作直达网关且不做本地存在性校验
     */
    @Test
    void virtualRootRegisterAndWriteDelegatesToGateway() {
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(1L);
        info.setName("连接器工作区");
        info.setType(WorkspaceRef.TYPE_CONNECTOR);
        info.setRootPath("connector://2106246710350061569/root_cc59fcdcb64e");
        WorkspaceRef ref = WorkspaceRef.of(info);
        assertNull(ref.getRootPath());

        RecordingGateway gateway = new RecordingGateway();
        ListToolkit toolkit = new ListToolkit();
        assertDoesNotThrow(() -> WorkspaceFileTools.registerTo(
                toolkit, ref, gateway, "AUTO", "s1", "u1", null));
        assertEquals(2, toolkit.tools.size());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "docs/notes.md");
        input.put("content", "hello");
        AgentToolResultBlock result = call(tool(toolkit, "file_write"), input);

        assertFalse(result.isError());
        assertEquals("docs/notes.md", gateway.writtenPath);
        assertEquals("hello", new String(gateway.writtenContent, StandardCharsets.UTF_8));
    }

    /**
     * 虚拟根拒绝穿越与绝对路径，不触发网关调用
     */
    @Test
    void virtualRootRejectsTraversalAndAbsolutePaths() {
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(1L);
        info.setName("连接器工作区");
        info.setType(WorkspaceRef.TYPE_CONNECTOR);
        info.setRootPath("connector://dev/root_x");
        WorkspaceRef ref = WorkspaceRef.of(info);

        RecordingGateway gateway = new RecordingGateway();
        ListToolkit toolkit = new ListToolkit();
        WorkspaceFileTools.registerTo(toolkit, ref, gateway, "AUTO", "s1", "u1", null);

        Map<String, Object> traversal = new LinkedHashMap<>();
        traversal.put("path", "a/../b.txt");
        traversal.put("content", "x");
        AgentToolResultBlock r1 = call(tool(toolkit, "file_write"), traversal);
        assertTrue(r1.isError());
        assertNull(gateway.writtenPath);

        Map<String, Object> absolute = new LinkedHashMap<>();
        absolute.put("path", "C:\\escape.txt");
        absolute.put("content", "x");
        AgentToolResultBlock r2 = call(tool(toolkit, "file_write"), absolute);
        assertTrue(r2.isError());
        assertNull(gateway.writtenPath);
    }

    /**
     * 虚拟根删除跳过本地存在性检查，直达网关
     */
    @Test
    void virtualRootDeleteSkipsLocalExistenceCheck() {
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(1L);
        info.setName("连接器工作区");
        info.setType(WorkspaceRef.TYPE_CONNECTOR);
        info.setRootPath("connector://dev/root_x");
        WorkspaceRef ref = WorkspaceRef.of(info);

        RecordingGateway gateway = new RecordingGateway();
        ListToolkit toolkit = new ListToolkit();
        WorkspaceFileTools.registerTo(toolkit, ref, gateway, "AUTO", "s1", "u1", null);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "notes.txt");
        AgentToolResultBlock result = call(tool(toolkit, "file_delete"), input);

        assertFalse(result.isError());
        assertEquals("notes.txt", gateway.deletedPath);
    }

    /**
     * 本地根行为不变：根内放行、越界拒绝
     */
    @Test
    void localRootStillValidatesInsideRoot() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(1L);
        info.setName("本地工作区");
        info.setType(WorkspaceRef.TYPE_SERVER);
        info.setRootPath(root.toString());
        WorkspaceRef ref = WorkspaceRef.of(info);
        assertEquals(root.toString(), ref.getRootPath());

        RecordingGateway gateway = new RecordingGateway();
        ListToolkit toolkit = new ListToolkit();
        WorkspaceFileTools.registerTo(toolkit, ref, gateway, "AUTO", "s1", "u1", null);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("path", "ok.txt");
        ok.put("content", "fine");
        AgentToolResultBlock r1 = call(tool(toolkit, "file_write"), ok);
        assertFalse(r1.isError());
        assertEquals("ok.txt", gateway.writtenPath);

        Map<String, Object> escape = new LinkedHashMap<>();
        escape.put("path", "..\\escape.txt");
        escape.put("content", "x");
        gateway.writtenPath = null;
        AgentToolResultBlock r2 = call(tool(toolkit, "file_write"), escape);
        assertTrue(r2.isError());
        assertNull(gateway.writtenPath);
    }
}
