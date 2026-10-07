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
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxDsl;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxEditDsl;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxEditor;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxGenerator;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxReader;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxValidator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作区Word生成工具
 * <p>
 * 请求级注册到Agent工具箱，路径物理限定在用户工作区根内，审批语义与文件写删一致。
 * 内容与编码分离：模型只产出JSON DSL，二进制编码由后端POI确定性完成。
 * </p>
 *
 * @author yangqiong
 */
public class WorkspaceDocxTools {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDocxTools.class);

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

    private WorkspaceDocxTools(Path root, String approvalMode, String sessionId, String userId,
                               ApprovalGate approvalGate) {
        this.root = root;
        this.approvalMode = approvalMode;
        this.sessionId = sessionId;
        this.userId = userId;
        this.approvalGate = approvalGate;
    }

    /**
     * 注册Word生成/编辑/读回工具到请求级工具箱
     * @param toolkit
     * @param ref 路由器分派的工作区引用
     * @param approvalMode
     * @param sessionId
     * @param userId
     * @param approvalGate 审批门控，未装配时MANUAL/CUSTOM层级下写操作直接拒绝
     */
    public static void registerTo(AgentToolkit toolkit, WorkspaceRef ref, String approvalMode,
                                  String sessionId, String userId, ApprovalGate approvalGate) {
        WorkspaceDocxTools tools = new WorkspaceDocxTools(
                Paths.get(ref.getRootPath()), approvalMode, sessionId, userId, approvalGate);
        toolkit.addTool(tools.buildWriteTool());
        toolkit.addTool(tools.buildEditTool());
        toolkit.addTool(tools.buildReadTool());
        log.info("工作区Word工具已注册: root={}, approvalMode={}", ref.getRootPath(), approvalMode);
    }

    private AgentTool buildWriteTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "docx_write";
            }

            @Override
            public String getDescription() {
                return "在工作区内创建真实二进制docx文档（标题/段落/表格/图片）。path为相对工作区根的保存路径"
                        + "（.docx结尾），document为文档定义JSON。适合生成报告、纪要等Word文件；"
                        + "纯文本文件请改用file_write。校验失败时返回含字段路径的错误信息，请修正后重试。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", WorkspaceDocxToolsHelper.stringProp("保存路径，相对工作区根，必须以.docx结尾"));
                properties.put("document", WorkspaceDocxToolsHelper.objectProp(DOCUMENT_DSL_DESC));
                schema.put("properties", properties);
                schema.put("required", List.of("path", "document"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeWrite(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("docx_write工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("生成Word失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildEditTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "docx_edit";
            }

            @Override
            public String getDescription() {
                return "对工作区内已有的docx增量编辑（不重建文件，未触及段落原样保留）。path为相对路径，"
                        + "operations为操作列表。编辑前建议先用docx_read读回现状，after按段落文本包含匹配定位。"
                        + "任一操作失败则整体失败不落盘，返回第几条失败及原因。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", WorkspaceDocxToolsHelper.stringProp("目标文件路径，相对工作区根，必须已存在且以.docx结尾"));
                properties.put("operations", WorkspaceDocxToolsHelper.objectProp(OPERATIONS_DSL_DESC));
                schema.put("properties", properties);
                schema.put("required", List.of("path", "operations"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeEdit(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("docx_edit工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("编辑Word失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildReadTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "docx_read";
            }

            @Override
            public String getDescription() {
                return "读取工作区内docx文件为结构化JSON（段落超300条截断，每段文本截2000字符，"
                        + "表格行超100行截断）。path为相对工作区根的路径。"
                        + "读取Word必须用本工具，file_read无法解析二进制文档。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", WorkspaceDocxToolsHelper.stringProp("要读取的文件路径，相对工作区根"));
                schema.put("properties", properties);
                schema.put("required", List.of("path"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeRead(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("docx_read工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("读取Word失败: " + e.getMessage()));
                        });
            }
        };
    }

    /**
     * 执行创建（校验→审批→落盘）
     * @param input
     * @return
     */
    private AgentToolResultBlock executeWrite(Map<String, Object> input) throws Exception {
        String json = toJson(input);
        DocxDsl dsl = DocxValidator.parseCreate(json);
        DocxValidator.validateCreate(dsl);
        Path target = requireWritableTarget(dsl.getPath(), false);

        if (!requestApproval("创建", dsl.getPath(), json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("创建Word操作未执行：用户拒绝或审批未通过").build()));
        }
        DocxGenerator.GenerateResult result = DocxGenerator.generate(dsl, target, root);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已创建Word：" + dsl.getPath()
                        + "（" + result.getBlockCount() + "个内容块）").build()));
    }

    /**
     * 执行编辑（校验→审批→落盘）
     * @param input
     * @return
     */
    private AgentToolResultBlock executeEdit(Map<String, Object> input) throws Exception {
        String json = toJson(input);
        DocxEditDsl dsl = DocxValidator.parseEdit(json);
        DocxValidator.validateEdit(dsl);
        Path target = requireWritableTarget(dsl.getPath(), true);

        if (!requestApproval("编辑", dsl.getPath(), json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("编辑Word操作未执行：用户拒绝或审批未通过").build()));
        }
        DocxEditor.EditResult result = DocxEditor.apply(dsl, target);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已编辑Word：" + dsl.getPath()
                        + "（" + result.getApplied() + "条操作：" + result.getDetail() + "）").build()));
    }

    /**
     * 执行读回（只读无需审批）
     * @param input
     * @return
     */
    private AgentToolResultBlock executeRead(Map<String, Object> input) {
        String path = input.get("path") != null ? String.valueOf(input.get("path")).trim() : "";
        if (path.isEmpty()) {
            return AgentToolResultBlock.error("path不能为空");
        }
        Path target = resolveInsideRoot(path);
        if (target == null) {
            return AgentToolResultBlock.error("路径非法或越界，仅允许读取工作区内的文件");
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".docx")) {
            return AgentToolResultBlock.error("docx_read仅支持.docx文件，其他文本文件请用file_read");
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return AgentToolResultBlock.error("文件不存在：" + path);
        }
        try {
            String json = DocxReader.readAsJson(target);
            return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(json).build()));
        } catch (Exception e) {
            log.error("docx_read解析失败: {}", target, e);
            return AgentToolResultBlock.error("解析Word失败：" + e.getMessage()
                    + "（文件可能损坏或为受保护的docm等格式）");
        }
    }

    /**
     * 校验并解析目标路径（docx后缀、创建/编辑存在性要求）
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
                throw new IllegalArgumentException("目标文件不存在：" + path + "（docx_edit仅支持编辑已有文件）");
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
                .reason("Agent请求在工作区" + action + "Word文件")
                .addParam("action", action)
                .addParam("path", path)
                .addParam("content", dslJson)
                .options(List.of("批准", "拒绝"))
                .timeout(APPROVAL_TIMEOUT)
                .build());
        if (response.getStatus() == ApprovalStatus.APPROVED) {
            return true;
        }
        log.info("Word{}审批未通过: {}，状态={}", action, path, response.getStatus());
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
     * DSL参数描述（工具schema用，引导模型产出合规JSON）
     */
    private static final String DOCUMENT_DSL_DESC = """
            文档定义JSON对象。结构：{"blocks":[{"type":"heading","level":1,"text":"一级标题"}（level为1-4）, \
            {"type":"paragraph","text":"正文段落","align":"left|center|right"(可选),"bold":true(可选)}, \
            {"type":"table","header":["列1","列2"],"rows":[["a","b"],["c","d"]]}（值全部字符串化,行单元格数可\
            与表头列数不同）, {"type":"image","imagePath":"图片相对路径.png","width":450}(支持png/jpg/jpeg/gif, \
            ≤5MB,width单位pt缺省450,高度等比缩放)]}。注意：blocks按顺序渲染,数量1-500；文本不能为空。""";

    /**
     * 编辑操作描述（工具schema用）
     */
    private static final String OPERATIONS_DSL_DESC = """
            编辑操作列表JSON。op类型：replaceText{"from","to"}（全文替换段落与表格内文本,to缺省删）；\
            insertPara{"after","text","type":"paragraph|heading","level"}（after为段落文本包含匹配,命中第一处\
            后插入,为空或未命中追加文末,heading需level 1-4）；addTable{"header":["列1","列2"],\
            "rows":[["a","b"]]}（文末追加表格,值全部字符串化）。操作逐条顺序执行。""";

    /**
     * schema属性构建辅助
     */
    private static class WorkspaceDocxToolsHelper {

        static Map<String, Object> stringProp(String description) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "string");
            prop.put("description", description);
            return prop;
        }

        static Map<String, Object> objectProp(String description) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "object");
            prop.put("description", description);
            return prop;
        }
    }
}
