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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.local.workspace.gateway.PreviewOptions;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区读列工具
 * @author yangqiong
 */
public class WorkspaceReadTools {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceReadTools.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 路由器分派的工作区引用
     */
    private final WorkspaceRef ref;

    /**
     * 路由器按类型分派的文件操作网关
     */
    private final WorkspaceGateway gateway;

    private WorkspaceReadTools(WorkspaceRef ref, WorkspaceGateway gateway) {
        this.ref = ref;
        this.gateway = gateway;
    }

    /**
     * 注册工作区读列工具到请求级工具箱（虚拟根工作区无引擎本地文件沙箱，读列经网关到执行端）
     * @param toolkit
     * @param ref 路由器分派的工作区引用
     * @param gateway 路由器按工作区类型分派的文件操作网关
     */
    public static void registerTo(AgentToolkit toolkit, WorkspaceRef ref, WorkspaceGateway gateway) {
        WorkspaceReadTools tools = new WorkspaceReadTools(ref, gateway);
        toolkit.addTool(tools.buildReadTool());
        toolkit.addTool(tools.buildListTool());
        log.info("工作区读列工具已注册: workspaceId={}, type={}", ref.getWorkspaceId(), ref.getType());
    }

    private AgentTool buildReadTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "file_read";
            }

            @Override
            public String getDescription() {
                return "读取当前工作区内的文件内容。path为相对工作区根的路径，文本文件返回内容，表格/文档返回结构化数据。";
            }

            @Override
            public Map<String, Object> getParameters() {
                return buildSchema("要读取的文件路径，相对工作区根，例如 docs/notes.md");
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> readFile(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("file_read工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("读取文件失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildListTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "file_list";
            }

            @Override
            public String getDescription() {
                return "列出当前工作区内某个目录的一级内容。dir为相对工作区根的目录路径，空表示根目录。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", "string");
                prop.put("description", "目录路径，相对工作区根，空表示根目录");
                properties.put("dir", prop);
                schema.put("properties", properties);
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> listDir(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("file_list工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("列目录失败: " + e.getMessage()));
                        });
            }
        };
    }

    /**
     * 经网关读取文件并格式化为模型可读文本
     * @param input
     * @return
     */
    private AgentToolResultBlock readFile(Map<String, Object> input) throws Exception {
        String path = input.get("path") != null ? String.valueOf(input.get("path")).trim() : "";
        if (path.isEmpty()) {
            return AgentToolResultBlock.error("path不能为空");
        }
        Map<String, Object> result = gateway.read(ref, path, PreviewOptions.empty());
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text(formatReadResult(path, result)).build()));
    }

    /**
     * 经网关列目录并格式化为条目清单
     * @param input
     * @return
     */
    private AgentToolResultBlock listDir(Map<String, Object> input) {
        String dir = input.get("dir") != null ? String.valueOf(input.get("dir")).trim() : "";
        List<Map<String, Object>> entries = gateway.tree(ref, dir.isEmpty() ? null : dir);
        if (entries.isEmpty()) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("（空目录：" + (dir.isEmpty() ? "工作区根" : dir) + "）").build()));
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> entry : entries) {
            String name = String.valueOf(entry.get("name"));
            boolean isDir = Boolean.TRUE.equals(entry.get("dir"));
            sb.append(isDir ? "[目录] " : "[文件] ").append(name).append('\n');
        }
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text(sb.toString()).build()));
    }

    /**
     * 按内容类型格式化读取结果（文本直出，结构化转JSON，二进制给说明）
     * @param path
     * @param result
     * @return
     */
    private String formatReadResult(String path, Map<String, Object> result) {
        String type = String.valueOf(result.get("type"));
        if ("text".equals(type)) {
            String content = String.valueOf(result.get("content"));
            if (Boolean.TRUE.equals(result.get("truncated"))) {
                content = content + "\n（内容过长已截断）";
            }
            return content;
        }
        if ("excel".equals(type) || "docx".equals(type) || "zip".equals(type)) {
            try {
                return OBJECT_MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                return "文件 " + path + " 结构化内容序列化失败";
            }
        }
        return "文件 " + path + " 为" + type + "类型二进制文件，无法以文本形式读取";
    }

    private Map<String, Object> buildSchema(String pathDesc) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", buildStringProp(pathDesc));
        schema.put("properties", properties);
        schema.put("required", List.of("path"));
        return schema;
    }

    private Map<String, Object> buildStringProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }
}
