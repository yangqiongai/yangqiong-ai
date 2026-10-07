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

import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.agent.mcp.client.McpClientWrapper;
import com.yangqiongai.ai.agent.mcp.client.McpHealthChecker;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具箱
 * @author yangqiong
 */
public class Toolkit {

    private static final Logger log = LoggerFactory.getLogger(Toolkit.class);

    /**
     * 工具列表
     */
    private final List<Object> tools = new ArrayList<>();

    /**
     * 项目工具描述映射（工具类名 -> 方法描述列表）
     */
    private final Map<String, List<ToolDescriptor>> toolDescriptors = new LinkedHashMap<>();

    /**
     * MCP工具列表
     */
    private final List<McpToolInfo> mcpTools = new ArrayList<>();

    /**
     * MCP工具与客户端的映射（工具 -> 对应的MCP客户端）
     */
    private final Map<McpToolInfo, McpClientWrapper> mcpToolClients = new LinkedHashMap<>();

    /**
     * 工具执行上下文
     */
    private ToolExecutionContext context;

    /**
     * MCP工具调用超时时间（秒）
     */
    private int mcpToolCallTimeoutSeconds = 30;

    /**
     * MCP健康检查器
     */
    private McpHealthChecker mcpHealthChecker;

    /**
     * 添加项目工具
     * @param tool
     */
    public void addTool(Object tool) {
        tools.add(tool);
        extractToolDescriptors(tool);
    }

    /**
     * 添加MCP工具及其客户端引用
     * @param tool
     * @param client
     */
    public void addMcpTool(McpToolInfo tool, McpClientWrapper client) {
        mcpTools.add(tool);
        mcpToolClients.put(tool, client);
    }

    /**
     * 设置MCP工具调用超时时间
     * @param timeoutSeconds
     */
    public void setMcpToolCallTimeoutSeconds(int timeoutSeconds) {
        this.mcpToolCallTimeoutSeconds = timeoutSeconds;
    }

    /**
     * 设置MCP健康检查器
     * @param healthChecker
     */
    public void setMcpHealthChecker(McpHealthChecker healthChecker) {
        this.mcpHealthChecker = healthChecker;
    }

    /**
     * 获取项目工具列表
     * @return
     */
    public List<Object> getTools() {
        return tools;
    }

    /**
     * 获取项目工具描述映射
     * @return
     */
    public Map<String, List<ToolDescriptor>> getToolDescriptors() {
        return toolDescriptors;
    }

    /**
     * 获取MCP工具列表
     * @return
     */
    public List<McpToolInfo> getMcpTools() {
        return mcpTools;
    }

    /**
     * 获取工具执行上下文
     * @return
     */
    public ToolExecutionContext getContext() {
        return context;
    }

    /**
     * 设置工具执行上下文
     * @param context
     */
    public void setContext(ToolExecutionContext context) {
        this.context = context;
    }

    /**
     * 转换为框架层AgentToolkit
     * <p>
     * 项目工具使用自定义@AgentTool注解，通过 AgentToolAdapter 适配为
     * 框架层 AgentTool 接口后注册；无注解方法的对象若直接实现 AgentTool 接口则原样注册。
     * MCP工具通过 McpToolAdapter 适配后注册。
     * </p>
     * @return
     */
    public AgentToolkit toAgentToolkit() {
        AgentToolkit agentToolkit = new DefaultAgentToolkit();
        for (Object tool : tools) {
            // 扫描@AgentTool注解方法，适配为AgentTool注册
            boolean registered = false;
            for (java.lang.reflect.Method method : tool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(AgentTool.class)) {
                    agentToolkit.addTool(new AgentToolAdapter(tool, method));
                    registered = true;
                }
            }
            if (!registered) {
                // 无自定义注解的方法，若对象直接实现AgentTool接口则原样注册
                if (tool instanceof com.yangqiongai.ai.agent.runtime.tool.AgentTool) {
                    agentToolkit.addTool((com.yangqiongai.ai.agent.runtime.tool.AgentTool) tool);
                }
            }
        }
        for (McpToolInfo mcpTool : mcpTools) {
            McpClientWrapper client = mcpToolClients.get(mcpTool);
            if (client != null) {
                McpToolAdapter adapter = new McpToolAdapter(mcpTool, client, mcpToolCallTimeoutSeconds);
                if (mcpHealthChecker != null) {
                    adapter.setHealthChecker(mcpHealthChecker);
                }
                agentToolkit.addTool(adapter);
            } else {
                log.warn("MCP工具缺少客户端引用,跳过注册: tool={}, serverCode={}",
                        mcpTool.getServerCode() + "_" + mcpTool.getName(), mcpTool.getServerCode());
            }
        }
        return agentToolkit;
    }

    /**
     * 从工具对象中提取@AgentTool注解描述
     * @param tool
     */
    private void extractToolDescriptors(Object tool) {
        String className = tool.getClass().getName();
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (java.lang.reflect.Method method : tool.getClass().getDeclaredMethods()) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            if (annotation != null) {
                descriptors.add(new ToolDescriptor(
                        method.getName(),
                        annotation.value(),
                        method.getParameterTypes()
                ));
            }
        }
        if (!descriptors.isEmpty()) {
            toolDescriptors.put(className, descriptors);
        }
    }

    /**
     * 工具描述信息
     */
    public static class ToolDescriptor {

        /**
         * 方法名称
         */
        private final String methodName;

        /**
         * 工具功能描述
         */
        private final String description;

        /**
         * 参数类型列表
         */
        private final Class<?>[] parameterTypes;

        public ToolDescriptor(String methodName, String description, Class<?>[] parameterTypes) {
            this.methodName = methodName;
            this.description = description;
            this.parameterTypes = parameterTypes;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getDescription() {
            return description;
        }

        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }
    }
}
