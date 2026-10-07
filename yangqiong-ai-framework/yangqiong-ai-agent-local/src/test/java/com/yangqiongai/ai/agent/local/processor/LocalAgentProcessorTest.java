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
package com.yangqiongai.ai.agent.local.processor;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.workspace.UserWorkspaceService;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceProperties;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeFactory;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本地模式处理器工作区注入测试
 * @author yangqiong
 */
class LocalAgentProcessorTest {

    @TempDir
    Path tempDir;

    private LocalAgentProcessor processor;

    private AgentRuntimeFactory runtimeFactory;

    private HarnessAgentRuntimeBuilder builder;

    private UserWorkspaceService workspaceService;

    @BeforeEach
    void setUp() throws Exception {
        processor = new LocalAgentProcessor();
        runtimeFactory = mock(AgentRuntimeFactory.class);
        builder = mock(HarnessAgentRuntimeBuilder.class);
        workspaceService = mock(UserWorkspaceService.class);
        when(runtimeFactory.createBuilder()).thenReturn(builder);

        inject(AbstractAgentProcessorField(), processor, runtimeFactory);
        inject("userWorkspaceService", processor, workspaceService);
        // 注入工作区生成能力配置（默认启用，使提示词与工具注册链路可被断言）
        inject("workspaceProperties", processor, new WorkspaceProperties());
    }

    /**
     * 有效_workspaceId时注入沙箱根与审批层级
     */
    @Test
    void buildAgentBuilderInjectsWorkspaceSandbox() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws"));
        AgentRequest request = workspaceRequest(1L, "u1");
        when(workspaceService.resolveEnabled(1L, "u1")).thenReturn(workspaceInfo(root.toString(), "MANUAL"));

        processor.buildAgentBuilder(request, "系统提示词");

        verify(builder).fileToolsEnabled(true);
        verify(builder).fileToolkit(eq(root.toString()), isNull());
        verify(builder).approvalMode(AgentApprovalMode.MANUAL);
    }

    /**
     * 各审批层级正确映射到引擎枚举
     */
    @Test
    void buildAgentBuilderMapsAllApprovalModes() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws2"));
        AgentRequest request = workspaceRequest(2L, "u1");
        when(workspaceService.resolveEnabled(2L, "u1")).thenReturn(workspaceInfo(root.toString(), "FULL_ACCESS"));

        processor.buildAgentBuilder(request, "提示词");

        verify(builder).approvalMode(AgentApprovalMode.FULL_ACCESS);
    }

    /**
     * 无_workspaceId或工作区无效时不注入，降级为静态默认路径
     */
    @Test
    void buildAgentBuilderSkipsInjectionWithoutValidWorkspace() throws Exception {
        AgentRequest request = new AgentRequest();
        request.setUserId("u1");
        request.setBody(new HashMap<>());

        processor.buildAgentBuilder(request, "提示词");

        verify(builder, never()).fileToolsEnabled(true);
        verify(builder, never()).fileToolkit(anyString(), any());
        verify(builder, never()).approvalMode(any(AgentApprovalMode.class));
    }

    /**
     * _workspaceId非法格式时安全降级
     */
    @Test
    void buildAgentBuilderHandlesMalformedWorkspaceId() throws Exception {
        AgentRequest request = new AgentRequest();
        request.setUserId("u1");
        Map<String, Object> body = new HashMap<>();
        body.put("_workspaceId", "not-a-number");
        request.setBody(body);

        processor.buildAgentBuilder(request, "提示词");

        verify(builder, never()).fileToolkit(anyString(), any());
        Mockito.verifyNoInteractions(workspaceService);
    }

    /**
     * 系统提示词声明工作区边界且不暴露物理路径
     */
    @Test
    void resolveSystemPromptHidesActualRootPath() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("ws3"));
        AgentRequest request = workspaceRequest(3L, "u1");
        when(workspaceService.resolveEnabled(3L, "u1")).thenReturn(workspaceInfo(root.toString(), "MANUAL"));

        String prompt = processor.resolveSystemPrompt(request);
        assertFalse(prompt.contains(root.toString()));
        assertTrue(prompt.contains("当前工作区：工作区"));
        assertTrue(prompt.contains("相对路径"));
    }

    /**
     * 工作区校验失败时提示词不含工作区边界声明
     */
    @Test
    void resolveSystemPromptDegradesGracefully() throws Exception {
        AgentRequest request = workspaceRequest(9L, "u1");
        when(workspaceService.resolveEnabled(9L, "u1")).thenReturn(null);

        String prompt = processor.resolveSystemPrompt(request);
        assertFalse(prompt.contains("当前工作区："));
    }

    /**
     * 请求体携带运行ID时解析透传给工作区引用
     */
    @Test
    void resolveRunIdReturnsBodyValue() {
        AgentRequest request = workspaceRequest(1L, "u1");
        request.addBody("harness.runId", "run_abc123");

        assertThat(LocalAgentProcessor.resolveRunId(request)).isEqualTo("run_abc123");
    }

    /**
     * 无运行ID键或空白值时返回null，工作区引用runId保持为空
     */
    @Test
    void resolveRunIdReturnsNullWhenAbsentOrBlank() {
        assertThat(LocalAgentProcessor.resolveRunId(workspaceRequest(1L, "u1"))).isNull();
        assertThat(LocalAgentProcessor.resolveRunId(null)).isNull();

        AgentRequest blankRequest = workspaceRequest(1L, "u1");
        blankRequest.addBody("harness.runId", "  ");
        assertThat(LocalAgentProcessor.resolveRunId(blankRequest)).isNull();

        WorkspaceRef ref = WorkspaceRef.of(workspaceInfo("D:/ws", "MANUAL"));
        assertThat(ref.getRunId()).isNull();
        ref.setRunId("run_1");
        assertThat(ref.getRunId()).isEqualTo("run_1");
    }

    private AgentRequest workspaceRequest(Long workspaceId, String userId) {
        AgentRequest request = new AgentRequest();
        request.setUserId(userId);
        Map<String, Object> body = new HashMap<>();
        body.put("_workspaceId", workspaceId);
        request.setBody(body);
        return request;
    }

    private WorkspaceInfo workspaceInfo(String rootPath, String approvalMode) {
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(1L);
        info.setUserId("u1");
        info.setName("工作区");
        info.setRootPath(rootPath);
        info.setApprovalMode(approvalMode);
        info.setStatus(1);
        return info;
    }

    private Field AbstractAgentProcessorField() throws Exception {
        return com.yangqiongai.ai.agent.core.processor.AbstractAgentProcessor.class
                .getDeclaredField("agentRuntimeFactory");
    }

    private static void inject(Field field, Object target, Object value) throws Exception {
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void inject(String fieldName, Object target, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
