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
package com.yangqiongai.ai.platform.ecosystem.mcp;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolAdapter;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import com.yangqiongai.ai.platform.ecosystem.mcp.config.McpAppsProperties;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台工具映射测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class McpToolMapperTest {

    @Mock
    private McpExposeService exposeService;

    @Mock
    private McpToolsCallGuard guard;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private AgentTaskEnqueuer taskEnqueuer;

    @Mock
    private AgentEngine agentEngine;

    /**
     * 队列配置(默认非队列模式，用例按需开启)
     */
    private final QueueProperties queueProperties = new QueueProperties();

    /**
     * 测试用工具提供者(无参方法规避编译参数依赖)
     */
    static class DemoToolProvider implements Tool {

        @AgentTool(name = "demo_tool", value = "演示工具")
        public String demo() {
            return "demo-result";
        }
    }

    private McpServerExpose toolExpose(String code, String description) {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_TOOL);
        expose.setExposeCode(code);
        expose.setDescription(description);
        expose.setEnabled(1);
        return expose;
    }

    private McpToolMapper mapper(List<Tool> providers) {
        return new McpToolMapper(exposeService, guard, agentTaskRepository, providers, 1000L,
                new McpAppsUiContract(new com.yangqiongai.ai.platform.ecosystem.mcp.config.McpAppsProperties(),
                        exposeService),
                queueProperties, taskEnqueuer, agentEngine);
    }

    private McpToolMapper mapperWithApps(List<Tool> providers, McpAppsProperties appsProperties) {
        return new McpToolMapper(exposeService, guard, agentTaskRepository, providers, 1000L,
                new McpAppsUiContract(appsProperties, exposeService),
                queueProperties, taskEnqueuer, agentEngine);
    }

    @Test
    void buildToolSpecificationsShouldMapEnabledTool() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL))
                .thenReturn(List.of(toolExpose("demo_tool", "自定义描述")));
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of());
        McpToolMapper mapper = mapper(List.of(new DemoToolProvider()));

        List<McpServerFeatures.SyncToolSpecification> specs = mapper.buildToolSpecifications();

        assertThat(specs).hasSize(1);
        McpSchema.Tool tool = specs.get(0).tool();
        assertThat(tool.name()).isEqualTo("demo_tool");
        assertThat(tool.description()).isEqualTo("自定义描述");
        assertThat(tool.inputSchema().type()).isEqualTo("object");
    }

    @Test
    void buildToolSpecificationsShouldSkipMissingTool() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL))
                .thenReturn(List.of(toolExpose("missing_tool", null)));
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of());
        McpToolMapper mapper = mapper(List.of(new DemoToolProvider()));

        assertThat(mapper.buildToolSpecifications()).isEmpty();
    }

    @Test
    void buildToolSpecificationsShouldExposeAgentWithTaskStatus() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        expose.setEnabled(1);
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        McpToolMapper mapper = mapper(List.of());

        List<McpServerFeatures.SyncToolSpecification> specs = mapper.buildToolSpecifications();

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).tool().name()).isEqualTo(McpToolMapper.AGENT_TOOL_PREFIX + "report_agent");
        assertThat(specs.get(1).tool().name()).isEqualTo(McpToolMapper.TASK_STATUS_TOOL);
    }

    @Test
    void inlineToolShouldReturnTextResult() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL))
                .thenReturn(List.of(toolExpose("demo_tool", null)));
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of());
        McpServerFeatures.SyncToolSpecification spec = mapper(List.of(new DemoToolProvider()))
                .buildToolSpecifications().get(0);

        McpSchema.CallToolResult result = spec.call().apply(null, Map.of());

        assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
        // 工具返回值经既有链路JSON序列化,字符串结果带引号
        assertThat(((McpSchema.TextContent) result.content().get(0)).text()).isEqualTo("\"demo-result\"");
    }

    @Test
    void inlineToolShouldRejectWhenGuardDenies() {
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL))
                .thenReturn(List.of(toolExpose("demo_tool", null)));
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new AiException(AiErrorCode.FORBIDDEN.getCode(), "资源未开放"))
                .when(guard).check(eq(McpServerExpose.TYPE_TOOL), eq("demo_tool"));
        McpServerFeatures.SyncToolSpecification spec = mapper(List.of(new DemoToolProvider()))
                .buildToolSpecifications().get(0);

        assertThatThrownBy(() -> spec.call().apply(null, Map.of()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("资源未开放");
    }

    @Test
    void agentToolShouldRequireInput() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        McpServerFeatures.SyncToolSpecification spec = mapper(List.of())
                .buildToolSpecifications().get(0);

        McpSchema.CallToolResult result = spec.call().apply(null, Map.of("input", "  "));

        assertThat(Boolean.TRUE.equals(result.isError())).isTrue();
    }

    @Test
    void agentToolShouldSubmitToEngineAndReturnHandle() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        when(agentEngine.submitTask(any())).thenReturn("task-engine-1");
        McpServerFeatures.SyncToolSpecification spec = mapper(List.of())
                .buildToolSpecifications().get(0);

        McpSchema.CallToolResult result = spec.call().apply(null, Map.of("input", "生成日报"));

        verify(agentEngine).submitTask(any());
        verify(taskEnqueuer, never()).enqueue(any());
        assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                .contains("task-engine-1")
                .contains("PENDING")
                .contains(McpToolMapper.TASK_STATUS_TOOL);
    }

    @Test
    void agentToolShouldEnqueueWhenQueueModeEnabled() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        queueProperties.setEnabled(true);
        queueProperties.setMode("queue");
        when(taskEnqueuer.enqueue(any())).thenReturn("task-queue-1");
        McpServerFeatures.SyncToolSpecification spec = mapper(List.of())
                .buildToolSpecifications().get(0);

        McpSchema.CallToolResult result = spec.call().apply(null, Map.of("input", "生成日报"));

        verify(taskEnqueuer).enqueue(any());
        verify(agentEngine, never()).submitTask(any());
        assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                .contains("task-queue-1")
                .contains("QUEUED");
    }

    @Test
    void taskStatusShouldRequireTaskId() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        McpServerFeatures.SyncToolSpecification statusSpec = mapper(List.of())
                .buildToolSpecifications().get(1);

        McpSchema.CallToolResult result = statusSpec.call().apply(null, Map.of("taskId", ""));

        assertThat(Boolean.TRUE.equals(result.isError())).isTrue();
    }

    @Test
    void taskStatusShouldReturnTaskHandle() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        McpServerFeatures.SyncToolSpecification statusSpec = mapper(List.of())
                .buildToolSpecifications().get(1);
        AgentTaskInfo task = new AgentTaskInfo();
        task.setTaskId("t-1");
        task.setAgentCode("report_agent");
        task.setTaskStatus("SUCCEEDED");
        task.setOutputText("日报内容");
        when(agentTaskRepository.queryTask("t-1")).thenReturn(task);

        McpSchema.CallToolResult result = statusSpec.call().apply(null, Map.of("taskId", "t-1"));

        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertThat(text).contains("t-1").contains("SUCCEEDED").contains("日报内容");
    }

    @Test
    void taskStatusShouldErrorWhenTaskMissing() {
        McpServerExpose expose = new McpServerExpose();
        expose.setExposeType(McpServerExpose.TYPE_AGENT);
        expose.setExposeCode("report_agent");
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of());
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of(expose));
        McpServerFeatures.SyncToolSpecification statusSpec = mapper(List.of())
                .buildToolSpecifications().get(1);
        when(agentTaskRepository.queryTask("nope")).thenReturn(null);

        McpSchema.CallToolResult result = statusSpec.call().apply(null, Map.of("taskId", "nope"));

        assertThat(Boolean.TRUE.equals(result.isError())).isTrue();
    }

    @Test
    void appsEnabledShouldAttachUiMetaWhenRenderAllowed() {
        McpAppsProperties appsProperties = new McpAppsProperties();
        appsProperties.setAppsEnabled(true);
        McpServerExpose expose = toolExpose("demo_tool", "自定义描述");
        expose.setRenderAllowed(1);
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of(expose));
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of());
        McpServerFeatures.SyncToolSpecification spec = mapperWithApps(List.of(new DemoToolProvider()),
                appsProperties).buildToolSpecifications().get(0);

        McpSchema.Tool tool = spec.tool();
        assertThat(tool.meta()).isNotNull();
        assertThat(tool.meta().get("ui")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) tool.meta().get("ui")).get("resourceUri"))
                .isEqualTo(McpAppsUiContract.RESOURCE_URI_PREFIX + "demo_tool");

        McpSchema.CallToolResult result = spec.call().apply(null, Map.of());
        assertThat(result.meta()).isNotNull();
        assertThat(result.meta().get("ui")).isInstanceOf(Map.class);
    }

    @Test
    void appsEnabledShouldSkipUiMetaWhenRenderNotAllowed() {
        McpAppsProperties appsProperties = new McpAppsProperties();
        appsProperties.setAppsEnabled(true);
        McpServerExpose expose = toolExpose("demo_tool", "自定义描述");
        expose.setRenderAllowed(0);
        when(exposeService.listEnabled(McpServerExpose.TYPE_TOOL)).thenReturn(List.of(expose));
        when(exposeService.listEnabled(McpServerExpose.TYPE_AGENT)).thenReturn(List.of());
        McpServerFeatures.SyncToolSpecification spec = mapperWithApps(List.of(new DemoToolProvider()),
                appsProperties).buildToolSpecifications().get(0);

        assertThat(spec.tool().meta()).isNull();

        McpSchema.CallToolResult result = spec.call().apply(null, Map.of());
        assertThat(result.meta()).isNull();
    }
}
