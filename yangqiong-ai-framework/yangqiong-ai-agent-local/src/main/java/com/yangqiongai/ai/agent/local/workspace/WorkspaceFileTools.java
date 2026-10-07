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

import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.approval.ApprovalRequest;
import com.yangqiongai.ai.approval.ApprovalResponse;
import com.yangqiongai.ai.approval.ApprovalStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作区文件写删工具
 * <p>
 * 请求级注册到Agent工具箱，路径物理限定在用户工作区根内。
 * 按工作区审批层级控制：MANUAL/CUSTOM走编程式审批（ApprovalGate），
 * AUTO/FULL_ACCESS直接执行。
 * </p>
 *
 * @author yangqiong
 */
public class WorkspaceFileTools {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceFileTools.class);

    /**
     * 审批等待超时时长（需小于工具调用超时10分钟）
     */
    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(8);

    private final Path root;

    private final String approvalMode;

    private final String sessionId;

    private final String userId;

    private final ApprovalGate approvalGate;

    /**
     * 路由器分派的工作区引用
     */
    private final WorkspaceRef ref;

    /**
     * 路由器按类型分派的文件操作网关
     */
    private final WorkspaceGateway gateway;

    private WorkspaceFileTools(Path root, String approvalMode, String sessionId, String userId,
                               ApprovalGate approvalGate, WorkspaceRef ref, WorkspaceGateway gateway) {
        this.root = root;
        this.approvalMode = approvalMode;
        this.sessionId = sessionId;
        this.userId = userId;
        this.approvalGate = approvalGate;
        this.ref = ref;
        this.gateway = gateway;
    }

    /**
     * 注册工作区写删工具到请求级工具箱
     * @param toolkit
     * @param ref 路由器分派的工作区引用
     * @param gateway 路由器按工作区类型分派的文件操作网关
     * @param approvalMode
     * @param sessionId
     * @param userId
     * @param approvalGate 审批门控，未装配时MANUAL/CUSTOM层级下写删操作直接拒绝
     */
    public static void registerTo(AgentToolkit toolkit, WorkspaceRef ref, WorkspaceGateway gateway,
                                  String approvalMode, String sessionId, String userId, ApprovalGate approvalGate) {
        // 虚拟根（连接器/浏览器桥）rootPath为空，不做本地路径解析，路径安全由网关执行端约束
        WorkspaceFileTools tools = new WorkspaceFileTools(
                ref.getRootPath() == null ? null : Paths.get(ref.getRootPath()),
                approvalMode, sessionId, userId, approvalGate, ref, gateway);
        toolkit.addTool(tools.buildWriteTool());
        toolkit.addTool(tools.buildDeleteTool());
        log.info("工作区写删工具已注册: root={}, approvalMode={}", ref.getRootPath(), approvalMode);
    }

    private AgentTool buildWriteTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "file_write";
            }

            @Override
            public String getDescription() {
                return "在当前工作区内创建或覆盖文件。path为相对工作区根的路径，content为文件文本内容（UTF-8）。";
            }

            @Override
            public Map<String, Object> getParameters() {
                return buildSchema("要写入的文件路径，相对工作区根，例如 docs/notes.md", "要写入的文件文本内容");
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeWithApproval(param.getInput(), false))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("file_write工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("写入文件失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildDeleteTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "file_delete";
            }

            @Override
            public String getDescription() {
                return "删除当前工作区内的文件（仅支持文件，不支持目录）。path为相对工作区根的路径。";
            }

            @Override
            public Map<String, Object> getParameters() {
                return buildSchema("要删除的文件路径，相对工作区根", null);
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeWithApproval(param.getInput(), true))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("file_delete工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("删除文件失败: " + e.getMessage()));
                        });
            }
        };
    }

    /**
     * 校验并执行写/删操作，按审批层级决定是否走人工审批
     * @param input
     * @param delete
     * @return
     */
    private AgentToolResultBlock executeWithApproval(Map<String, Object> input, boolean delete) throws Exception {
        String path = input.get("path") != null ? String.valueOf(input.get("path")).trim() : "";
        if (path.isEmpty()) {
            return AgentToolResultBlock.error("path不能为空");
        }
        String content = input.get("content") != null ? String.valueOf(input.get("content")) : "";
        Path target = resolveInsideRoot(path);
        if (target == null) {
            return AgentToolResultBlock.error("路径非法或越界，仅允许操作工作区内的文件");
        }
        if (delete && root != null && (Files.isDirectory(target) || !Files.exists(target))) {
            return AgentToolResultBlock.error("目标不存在或为目录，file_delete仅支持删除工作区内的文件");
        }

        if (requiresApproval() && approvalGate != null) {
            String action = delete ? "删除" : "写入";
            ApprovalResponse response = approvalGate.requestApproval(ApprovalRequest.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .resourceType("file")
                    .targetName(path)
                    .reason("Agent请求在工作区" + action + "文件")
                    .addParam("action", action)
                    .addParam("path", path)
                    .addParam("content", delete ? "" : content)
                    .options(List.of("批准", "拒绝"))
                    .timeout(APPROVAL_TIMEOUT)
                    .build());
            if (response.getStatus() != ApprovalStatus.APPROVED) {
                String detail = response.getStatus() == ApprovalStatus.REJECTED
                        ? "用户拒绝了本次" + action + "操作" + (response.getRejectReason() != null ? "：" + response.getRejectReason() : "")
                        : "审批" + (response.getStatus() == ApprovalStatus.TIMEOUT ? "超时" : "未通过");
                return AgentToolResultBlock.of(List.of(
                        AgentTextBlock.builder().text(action + "操作未执行：" + detail).build()));
            }
        } else if (requiresApproval()) {
            return AgentToolResultBlock.error("当前工作区为人工审批层级，但审批组件未装配，已拒绝执行" + (delete ? "删除" : "写入"));
        }

        // 经路由分派的网关执行文件操作，审批裁决已在上方平台侧完成
        if (delete) {
            gateway.delete(ref, path);
            log.info("工作区文件已删除: {}", target);
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("已删除文件：" + path).build()));
        }
        gateway.write(ref, path, content.getBytes(StandardCharsets.UTF_8));
        log.info("工作区文件已写入: {}", target);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已写入文件：" + path + "（" + content.length() + "字符）").build()));
    }

    /**
     * 是否需要人工审批（MANUAL/CUSTOM层级）
     * @return
     */
    private boolean requiresApproval() {
        return "MANUAL".equalsIgnoreCase(approvalMode) || "CUSTOM".equalsIgnoreCase(approvalMode);
    }

    /**
     * 解析相对路径到工作区内绝对路径，越界返回null；虚拟根仅校验相对路径与穿越，安全由网关执行端约束
     * @param path
     * @return
     */
    private Path resolveInsideRoot(String path) {
        if (path.contains("..")) {
            return null;
        }
        if (root == null) {
            Path relative = Paths.get(path).normalize();
            return relative.isAbsolute() ? null : relative;
        }
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            return null;
        }
        return target;
    }

    private Map<String, Object> buildSchema(String pathDesc, String contentDesc) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("path", buildStringProp(pathDesc));
        if (contentDesc != null) {
            properties.put("content", buildStringProp(contentDesc));
        }
        schema.put("properties", properties);
        schema.put("required", contentDesc != null ? List.of("path", "content") : List.of("path"));
        return schema;
    }

    private Map<String, Object> buildStringProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }
}
