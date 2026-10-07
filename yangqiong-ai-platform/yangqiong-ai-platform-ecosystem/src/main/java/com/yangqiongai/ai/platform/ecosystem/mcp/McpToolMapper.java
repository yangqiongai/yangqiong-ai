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
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.tool.AgentToolAdapter;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.common.util.AiJsonUtils;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import com.yangqiongai.ai.platform.ecosystem.mcp.entity.McpServerExpose;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台工具映射
 * <p>
 * 将平台工具与代理映射为SDK tool provider：TOOL类型内联执行平台工具，
 * AGENT类型按队列模式提交任务返回任务句柄（队列关闭时引擎异步直执），
 * 存在代理出口时附带task_status轮询工具。
 * </p>
 * @author yangqiong
 */
public class McpToolMapper {

    private static final Logger log = LoggerFactory.getLogger(McpToolMapper.class);

    /**
     * 代理工具名前缀
     */
    public static final String AGENT_TOOL_PREFIX = "agent_";

    /**
     * 任务状态轮询工具名
     */
    public static final String TASK_STATUS_TOOL = "task_status";

    private final McpExposeService exposeService;

    private final McpToolsCallGuard guard;

    private final AgentTaskRepository agentTaskRepository;

    /**
     * 平台工具提供者(Spring容器中的Tool实现bean)
     */
    private final List<Tool> toolProviders;

    /**
     * 代理工具同步执行等待上限(毫秒)
     */
    private final long toolCallTimeoutMillis;

    /**
     * MCP Apps UI契约(未启用时不下发ui声明)
     */
    private final McpAppsUiContract appsUiContract;

    /**
     * Agent任务队列配置
     */
    private final QueueProperties queueProperties;

    /**
     * Agent任务入队器
     */
    private final AgentTaskEnqueuer taskEnqueuer;

    /**
     * Agent引擎
     */
    private final AgentEngine agentEngine;

    public McpToolMapper(McpExposeService exposeService, McpToolsCallGuard guard,
                         AgentTaskRepository agentTaskRepository, List<Tool> toolProviders,
                         long toolCallTimeoutMillis, McpAppsUiContract appsUiContract,
                         QueueProperties queueProperties, AgentTaskEnqueuer taskEnqueuer,
                         AgentEngine agentEngine) {
        this.exposeService = exposeService;
        this.guard = guard;
        this.agentTaskRepository = agentTaskRepository;
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
        this.toolCallTimeoutMillis = toolCallTimeoutMillis;
        this.appsUiContract = appsUiContract;
        this.queueProperties = queueProperties;
        this.taskEnqueuer = taskEnqueuer;
        this.agentEngine = agentEngine;
    }

    /**
     * 构建SDK工具规范列表(仅含启用白名单项)
     * @return
     */
    public List<McpServerFeatures.SyncToolSpecification> buildToolSpecifications() {
        List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<>();
        Map<String, AgentToolAdapter> platformTools = indexPlatformTools(toolProviders);

        boolean agentExposed = false;
        for (McpServerExpose expose : exposeService.listEnabled(McpServerExpose.TYPE_TOOL)) {
            AgentToolAdapter adapter = platformTools.get(expose.getExposeCode());
            if (adapter == null) {
                log.warn("MCP出口引用的平台工具不存在: {}", expose.getExposeCode());
                continue;
            }
            specifications.add(buildInlineToolSpec(expose, adapter));
        }
        for (McpServerExpose expose : exposeService.listEnabled(McpServerExpose.TYPE_AGENT)) {
            agentExposed = true;
            specifications.add(buildAgentToolSpec(expose));
        }
        if (agentExposed) {
            specifications.add(buildTaskStatusSpec());
        }
        return specifications;
    }

    /**
     * 平台工具内联执行规范
     * @param expose
     * @param adapter
     * @return
     */
    private McpServerFeatures.SyncToolSpecification buildInlineToolSpec(McpServerExpose expose,
                                                                        AgentToolAdapter adapter) {
        McpSchema.Tool.Builder toolBuilder = McpSchema.Tool.builder()
                .name(adapter.getName())
                .description(resolveDescription(expose, adapter.getDescription()))
                .inputSchema(buildJsonSchema(adapter.getParameters()));
        McpAppsUiDeclaration uiDeclaration = appsUiContract.buildUiDeclaration(expose);
        if (uiDeclaration != null) {
            toolBuilder.meta(appsUiContract.uiToolMeta(uiDeclaration));
        }
        McpSchema.Tool tool = toolBuilder.build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> {
            guard.check(McpServerExpose.TYPE_TOOL, expose.getExposeCode());
            return invokePlatformTool(adapter, args, uiDeclaration);
        });
    }

    /**
     * 代理长任务规范:入队并返回任务句柄
     * @param expose
     * @return
     */
    private McpServerFeatures.SyncToolSpecification buildAgentToolSpec(McpServerExpose expose) {
        String toolName = AGENT_TOOL_PREFIX + expose.getExposeCode();
        Map<String, Object> properties = new HashMap<>();
        properties.put("input", Map.of("type", "string", "description", "任务输入文本"));
        McpSchema.Tool.Builder toolBuilder = McpSchema.Tool.builder()
                .name(toolName)
                .description(resolveDescription(expose, "提交代理长任务,返回任务句柄,用task_status轮询结果"))
                .inputSchema(new McpSchema.JsonSchema("object", properties, List.of("input"), null, null, null));
        McpAppsUiDeclaration uiDeclaration = appsUiContract.buildUiDeclaration(expose);
        if (uiDeclaration != null) {
            toolBuilder.meta(appsUiContract.uiToolMeta(uiDeclaration));
        }
        McpSchema.Tool tool = toolBuilder.build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> {
            guard.check(McpServerExpose.TYPE_AGENT, expose.getExposeCode());
            return submitAgentTask(expose.getExposeCode(), args, uiDeclaration);
        });
    }

    /**
     * 任务状态轮询工具规范
     * @return
     */
    private McpServerFeatures.SyncToolSpecification buildTaskStatusSpec() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("taskId", Map.of("type", "string", "description", "任务句柄ID"));
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name(TASK_STATUS_TOOL)
                .description("查询异步任务状态与结果")
                .inputSchema(new McpSchema.JsonSchema("object", properties, List.of("taskId"), null, null, null))
                .build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> {
            Object taskId = args.get("taskId");
            if (taskId == null || taskId.toString().isBlank()) {
                return McpSchema.CallToolResult.builder()
                        .addTextContent("taskId不能为空")
                        .isError(true)
                        .build();
            }
            AgentTaskInfo task = agentTaskRepository.queryTask(taskId.toString());
            if (task == null) {
                return McpSchema.CallToolResult.builder()
                        .addTextContent("任务不存在: " + taskId)
                        .isError(true)
                        .build();
            }
            Map<String, Object> handle = new HashMap<>();
            handle.put("taskId", task.getTaskId());
            handle.put("agentCode", task.getAgentCode());
            handle.put("status", task.getTaskStatus());
            handle.put("output", task.getOutputText());
            handle.put("errorMessage", task.getErrorMessage());
            return McpSchema.CallToolResult.builder()
                    .addTextContent(AiJsonUtils.toJson(handle))
                    .build();
        });
    }

    /**
     * 内联执行平台工具(结果meta携带ui契约)
     * @param adapter
     * @param args
     * @param uiDeclaration
     * @return
     */
    private McpSchema.CallToolResult invokePlatformTool(AgentToolAdapter adapter, Map<String, Object> args,
                                                        McpAppsUiDeclaration uiDeclaration) {
        try {
            AgentToolResultBlock result = adapter.callAsync(new AgentToolCallParam(args))
                    .block(java.time.Duration.ofMillis(toolCallTimeoutMillis));
            if (result == null) {
                return McpSchema.CallToolResult.builder().addTextContent("工具无返回").isError(true).build();
            }
            McpSchema.CallToolResult.Builder resultBuilder = McpSchema.CallToolResult.builder()
                    .addTextContent(result.getTextContent())
                    .isError(result.isError());
            Map<String, Object> uiMeta = appsUiContract.uiToolMeta(uiDeclaration);
            if (uiMeta != null) {
                resultBuilder.meta(uiMeta);
            }
            return resultBuilder.build();
        } catch (Exception e) {
            log.error("MCP工具执行失败: tool={}", adapter.getName(), e);
            return McpSchema.CallToolResult.builder()
                    .addTextContent("工具执行失败: " + e.getMessage())
                    .isError(true)
                    .build();
        }
    }

    /**
     * 代理任务提交,返回任务句柄
     * <p>
     * 队列模式：写QUEUED由调度器抢占执行；否则走引擎异步直执（任务表同源，task_status可轮询）。
     * </p>
     * @param agentCode
     * @param args
     * @param uiDeclaration
     * @return
     */
    private McpSchema.CallToolResult submitAgentTask(String agentCode, Map<String, Object> args,
                                                     McpAppsUiDeclaration uiDeclaration) {
        Object input = args == null ? null : args.get("input");
        if (input == null || input.toString().isBlank()) {
            return McpSchema.CallToolResult.builder()
                    .addTextContent("input不能为空")
                    .isError(true)
                    .build();
        }
        AgentRequest request = new AgentRequest();
        request.setAgentCode(agentCode);
        request.setInput(input.toString());
        String taskId;
        String status;
        if (queueProperties.isEnabled() && queueProperties.isQueueMode()) {
            taskId = taskEnqueuer.enqueue(request);
            status = "QUEUED";
        } else {
            taskId = agentEngine.submitTask(request);
            status = "PENDING";
        }

        Map<String, Object> handle = new HashMap<>();
        handle.put("taskId", taskId);
        handle.put("status", status);
        handle.put("pollTool", TASK_STATUS_TOOL);
        McpSchema.CallToolResult.Builder resultBuilder = McpSchema.CallToolResult.builder()
                .addTextContent(AiJsonUtils.toJson(handle));
        Map<String, Object> uiMeta = appsUiContract.uiToolMeta(uiDeclaration);
        if (uiMeta != null) {
            resultBuilder.meta(uiMeta);
        }
        return resultBuilder.build();
    }

    /**
     * 构建JSON Schema(平台工具参数定义转换)
     * @param parameters
     * @return
     */
    private McpSchema.JsonSchema buildJsonSchema(Map<String, Object> parameters) {
        Map<String, Object> schema = parameters == null ? Map.of() : parameters;
        Object properties = schema.get("properties");
        Object required = schema.get("required");
        @SuppressWarnings("unchecked")
        Map<String, Object> propertyMap = properties instanceof Map ? (Map<String, Object>) properties : Map.of();
        List<String> requiredList = new ArrayList<>();
        if (required instanceof List<?> list) {
            list.forEach(item -> requiredList.add(String.valueOf(item)));
        }
        return new McpSchema.JsonSchema("object", propertyMap, requiredList, null, null, null);
    }

    /**
     * 索引运行时平台工具(带@AgentTool注解的方法)
     * <p>
     * 索引key与MCP出口TOOL类型的exposeCode、管理端工具选项接口共用同一标识。
     * </p>
     * @param toolProviders
     * @return
     */
    public static Map<String, AgentToolAdapter> indexPlatformTools(List<Tool> toolProviders) {
        Map<String, AgentToolAdapter> index = new HashMap<>();
        for (Tool provider : toolProviders) {
            for (Method method : provider.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(com.yangqiongai.ai.agent.tool.AgentTool.class)) {
                    continue;
                }
                AgentToolAdapter adapter = new AgentToolAdapter(provider, method);
                index.putIfAbsent(adapter.getName(), adapter);
            }
        }
        return index;
    }

    private String resolveDescription(McpServerExpose expose, String fallback) {
        return expose.getDescription() != null && !expose.getDescription().isBlank()
                ? expose.getDescription() : fallback;
    }
}
