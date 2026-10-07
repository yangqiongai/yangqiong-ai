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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.local.workspace.zip.ZipPacker;
import com.yangqiongai.ai.agent.local.workspace.zip.ZipUnpacker;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作区zip打包解包工具
 * <p>
 * 请求级注册到Agent工具箱，路径物理限定在用户工作区根内，审批语义与文件写删一致。
 * </p>
 *
 * @author yangqiong
 */
public class WorkspaceZipTools {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceZipTools.class);

    /**
     * 审批等待超时时长（需小于工具调用超时10分钟）
     */
    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(8);

    /**
     * DSL入参序列化器
     */
    private static final ObjectMapper DSL_MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path root;

    private final String approvalMode;

    private final String sessionId;

    private final String userId;

    private final ApprovalGate approvalGate;

    private WorkspaceZipTools(Path root, String approvalMode, String sessionId, String userId,
                              ApprovalGate approvalGate) {
        this.root = root;
        this.approvalMode = approvalMode;
        this.sessionId = sessionId;
        this.userId = userId;
        this.approvalGate = approvalGate;
    }

    /**
     * 注册zip打包/解包工具到请求级工具箱
     * @param toolkit
     * @param ref 路由器分派的工作区引用
     * @param approvalMode
     * @param sessionId
     * @param userId
     * @param approvalGate 审批门控，未装配时MANUAL/CUSTOM层级下写操作直接拒绝
     */
    public static void registerTo(AgentToolkit toolkit, WorkspaceRef ref, String approvalMode,
                                  String sessionId, String userId, ApprovalGate approvalGate) {
        WorkspaceZipTools tools = new WorkspaceZipTools(
                Paths.get(ref.getRootPath()), approvalMode, sessionId, userId, approvalGate);
        toolkit.addTool(tools.buildPackTool());
        toolkit.addTool(tools.buildUnpackTool());
        log.info("工作区zip工具已注册: root={}, approvalMode={}", ref.getRootPath(), approvalMode);
    }

    private AgentTool buildPackTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "zip_pack";
            }

            @Override
            public String getDescription() {
                return "将工作区内指定的文件/目录清单打包为一个zip压缩包。zipPath为压缩包保存路径（.zip结尾），"
                        + "paths为相对工作区根的文件或目录JSON数组，目录会递归打包并保留相对路径结构。"
                        + "条目总数上限2000、源文件总大小上限100MB。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("zipPath", SchemaHelper.stringProp("压缩包保存路径，相对工作区根，必须以.zip结尾"));
                properties.put("paths", SchemaHelper.arrayProp(PATHS_PROP_DESC));
                schema.put("properties", properties);
                schema.put("required", List.of("zipPath", "paths"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executePack(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("zip_pack工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("打包zip失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildUnpackTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "zip_unpack";
            }

            @Override
            public String getDescription() {
                return "将工作区内已有的zip压缩包解包到指定目录。zipPath为压缩包路径（必须已存在且以.zip结尾），"
                        + "destDir为解包目标目录（相对工作区根，不存在会自动创建）。带zip-slip路径防护，"
                        + "拒绝越界条目；条目总数上限2000、解压总大小上限100MB。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("zipPath", SchemaHelper.stringProp("要解包的压缩包路径，相对工作区根，必须已存在且以.zip结尾"));
                properties.put("destDir", SchemaHelper.stringProp("解包目标目录，相对工作区根（不存在会自动创建），不能为空且不含.."));
                schema.put("properties", properties);
                schema.put("required", List.of("zipPath", "destDir"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeUnpack(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("zip_unpack工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("解包zip失败: " + e.getMessage()));
                        });
            }
        };
    }

    /**
     * 执行打包（校验→审批→落盘）
     * @param input
     * @return
     */
    private AgentToolResultBlock executePack(Map<String, Object> input) throws Exception {
        String json = toJson(input);
        String zipPath = stringParam(input, "zipPath");
        if (zipPath.isEmpty()) {
            throw new IllegalArgumentException("zipPath不能为空");
        }
        if (!zipPath.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("zipPath必须以.zip结尾：" + zipPath);
        }
        List<String> paths = readPaths(input.get("paths"));
        Path target = requireWritableTarget(zipPath, false);

        if (!requestApproval("打包", zipPath, json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("打包zip操作未执行：用户拒绝或审批未通过").build()));
        }
        ZipPacker.PackResult result = ZipPacker.pack(root, paths, target);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已打包：" + zipPath + "（" + result.getEntryCount() + "个条目，共"
                        + result.getTotalBytes() / 1024 + " KB）").build()));
    }

    /**
     * 执行解包（校验→审批→落盘）
     * @param input
     * @return
     */
    private AgentToolResultBlock executeUnpack(Map<String, Object> input) throws Exception {
        String json = toJson(input);
        String zipPath = stringParam(input, "zipPath");
        if (zipPath.isEmpty()) {
            throw new IllegalArgumentException("zipPath不能为空");
        }
        if (!zipPath.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("zipPath必须以.zip结尾：" + zipPath);
        }
        String destDir = stringParam(input, "destDir");
        if (destDir.isEmpty()) {
            throw new IllegalArgumentException("destDir不能为空");
        }
        Path zipFile = requireWritableTarget(zipPath, true);

        if (!requestApproval("解包", zipPath, json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("解包zip操作未执行：用户拒绝或审批未通过").build()));
        }
        ZipUnpacker.UnpackResult result = ZipUnpacker.unpack(root, zipFile, destDir);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已解包：" + result.getFileCount() + "个文件，"
                        + result.getDirCount() + "个目录").build()));
    }

    /**
     * 解析paths参数（兼容已解析列表与字符串数组JSON两种入参形态）
     * @param raw
     * @return
     */
    private List<String> readPaths(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("paths不能为空");
        }
        if (raw instanceof List<?> list) {
            List<String> paths = new ArrayList<>();
            for (Object item : list) {
                paths.add(item != null ? String.valueOf(item) : null);
            }
            return paths;
        }
        try {
            return DSL_MAPPER.readValue(String.valueOf(raw), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("paths须为字符串数组JSON：" + e.getMessage());
        }
    }

    /**
     * 读取字符串入参
     * @param input
     * @param key
     * @return
     */
    private static String stringParam(Map<String, Object> input, String key) {
        return input.get(key) != null ? String.valueOf(input.get(key)).trim() : "";
    }

    /**
     * 校验并解析目标路径（zip后缀存在性要求）
     * @param path
     * @param mustExist
     * @return
     */
    private Path requireWritableTarget(String path, boolean mustExist) {
        Path target = resolveInsideRoot(path);
        if (target == null) {
            throw new IllegalArgumentException("路径非法或越界，仅允许操作工作区内的文件");
        }
        if (mustExist) {
            if (!Files.exists(target) || Files.isDirectory(target)) {
                throw new IllegalArgumentException("目标文件不存在：" + path);
            }
        } else if (Files.isDirectory(target)) {
            throw new IllegalArgumentException("目标路径为目录：" + path);
        }
        return target;
    }

    /**
     * 编程式审批（MANUAL/CUSTOM层级出卡，AUTO/FULL_ACCESS直通）
     * @param action
     * @param path
     * @param dslJson
     * @return 是否通过
     */
    private boolean requestApproval(String action, String path, String dslJson) {
        if (!requiresApproval()) {
            return true;
        }
        if (approvalGate == null) {
            throw new IllegalArgumentException("当前工作区为人工审批层级，但审批组件未装配，已拒绝执行" + action);
        }
        ApprovalResponse response = approvalGate.requestApproval(ApprovalRequest.builder()
                .sessionId(sessionId)
                .userId(userId)
                .resourceType("file")
                .targetName(path)
                .reason("Agent请求在工作区" + action + "zip文件")
                .addParam("action", action)
                .addParam("path", path)
                .addParam("content", dslJson)
                .options(List.of("批准", "拒绝"))
                .timeout(APPROVAL_TIMEOUT)
                .build());
        if (response.getStatus() == ApprovalStatus.APPROVED) {
            return true;
        }
        log.info("zip{}审批未通过: {}，状态={}", action, path, response.getStatus());
        return false;
    }

    /**
     * 是否需要人工审批（MANUAL/CUSTOM层级）
     * @return
     */
    private boolean requiresApproval() {
        return "MANUAL".equalsIgnoreCase(approvalMode) || "CUSTOM".equalsIgnoreCase(approvalMode);
    }

    /**
     * 解析相对路径到工作区内绝对路径，越界返回null
     * @param path
     * @return
     */
    private Path resolveInsideRoot(String path) {
        if (path.contains("..")) {
            return null;
        }
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            return null;
        }
        return target;
    }

    /**
     * 工具入参序列化为DSL的JSON文本
     * @param input
     * @return
     */
    private static String toJson(Map<String, Object> input) {
        try {
            return DSL_MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具入参序列化失败：" + e.getMessage());
        }
    }

    /**
     * 打包清单参数描述（工具schema用，引导模型产出合规JSON）
     */
    private static final String PATHS_PROP_DESC = """
            待打包的文件/目录相对路径字符串数组JSON，如 ["报表.xlsx","资料"]。目录会递归打包并保留相对路径结构，\
            条目总数上限2000、源文件总大小上限100MB。""";

    /**
     * schema属性构建辅助
     */
    private static class SchemaHelper {

        static Map<String, Object> stringProp(String description) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "string");
            prop.put("description", description);
            return prop;
        }

        static Map<String, Object> arrayProp(String description) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "array");
            prop.put("description", description);
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", "string");
            prop.put("items", items);
            return prop;
        }
    }
}
