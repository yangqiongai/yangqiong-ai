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
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelDsl;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelDslValidator;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelEditDsl;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelEditor;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelGenerator;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelLimits;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelReader;
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
 * 工作区Excel生成工具
 * <p>
 * 请求级注册到Agent工具箱，路径物理限定在用户工作区根内，审批语义与文件写删一致。
 * 内容与编码分离：模型只产出JSON DSL，二进制编码由后端POI确定性完成。
 * </p>
 *
 * @author yangqiong
 */
public class WorkspaceExcelTools {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceExcelTools.class);

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

    private final ExcelLimits limits;

    private WorkspaceExcelTools(Path root, String approvalMode, String sessionId, String userId,
                                ApprovalGate approvalGate, ExcelLimits limits) {
        this.root = root;
        this.approvalMode = approvalMode;
        this.sessionId = sessionId;
        this.userId = userId;
        this.approvalGate = approvalGate;
        this.limits = limits;
    }

    /**
     * 注册Excel生成/编辑/读回工具到请求级工具箱
     * @param toolkit
     * @param ref 路由器分派的工作区引用
     * @param approvalMode
     * @param sessionId
     * @param userId
     * @param approvalGate 审批门控，未装配时MANUAL/CUSTOM层级下写操作直接拒绝
     * @param limits
     */
    public static void registerTo(AgentToolkit toolkit, WorkspaceRef ref, String approvalMode,
                                  String sessionId, String userId, ApprovalGate approvalGate, ExcelLimits limits) {
        WorkspaceExcelTools tools = new WorkspaceExcelTools(
                Paths.get(ref.getRootPath()), approvalMode, sessionId, userId, approvalGate, limits);
        toolkit.addTool(tools.buildWriteTool());
        toolkit.addTool(tools.buildEditTool());
        toolkit.addTool(tools.buildReadTool());
        log.info("工作区Excel工具已注册: root={}, approvalMode={}", ref.getRootPath(), approvalMode);
    }

    private AgentTool buildWriteTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "excel_write";
            }

            @Override
            public String getDescription() {
                return "在工作区内创建真实二进制xlsx工作簿（多sheet）。path为相对工作区根的保存路径（.xlsx结尾），"
                        + "workbook为工作簿定义JSON。适合生成报表、清单等Excel文件；纯文本文件请改用file_write。"
                        + "校验失败时返回含字段路径的错误信息，请修正后重试。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", WorkspaceFileToolsHelper.stringProp("保存路径，相对工作区根，必须以.xlsx结尾"));
                properties.put("workbook", WorkspaceFileToolsHelper.objectProp(WORKBOOK_DSL_DESC));
                schema.put("properties", properties);
                schema.put("required", List.of("path", "workbook"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeWrite(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("excel_write工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("生成Excel失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildEditTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "excel_edit";
            }

            @Override
            public String getDescription() {
                return "对工作区内已有的xlsx增量编辑（不重建文件，未触及单元格原样保留）。path为相对路径，"
                        + "operations为操作列表。编辑前建议先用excel_read读回现状，match条件定位行避免数错行号。"
                        + "任一操作失败则整体失败不落盘，返回第几条失败及原因。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", WorkspaceFileToolsHelper.stringProp("目标文件路径，相对工作区根，必须已存在且以.xlsx结尾"));
                properties.put("operations", WorkspaceFileToolsHelper.objectProp(OPERATIONS_DSL_DESC));
                schema.put("properties", properties);
                schema.put("required", List.of("path", "operations"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeEdit(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("excel_edit工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("编辑Excel失败: " + e.getMessage()));
                        });
            }
        };
    }

    private AgentTool buildReadTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "excel_read";
            }

            @Override
            public String getDescription() {
                return "读取工作区内xlsx/xls文件为结构化JSON（多sheet全量，数据行超100行截断）。"
                        + "path为相对工作区根的路径。读取Excel必须用本工具，file_read无法解析二进制工作簿。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", WorkspaceFileToolsHelper.stringProp("要读取的文件路径，相对工作区根"));
                schema.put("properties", properties);
                schema.put("required", List.of("path"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeRead(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("excel_read工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("读取Excel失败: " + e.getMessage()));
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
        ExcelDsl dsl = ExcelDslValidator.parseCreate(json);
        ExcelDslValidator.validateCreate(dsl, limits);
        Path target = requireWritableTarget(dsl.getPath(), false);

        if (!requestApproval("创建", dsl.getPath(), json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("创建Excel操作未执行：用户拒绝或审批未通过").build()));
        }
        ExcelGenerator.GenerateResult result = ExcelGenerator.generate(dsl, target, limits);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已创建Excel：" + dsl.getPath()
                        + "（" + result.getSheetCount() + "个sheet，" + result.getCellCount() + "单元格）").build()));
    }

    /**
     * 执行编辑（校验→审批→落盘）
     * @param input
     * @return
     */
    private AgentToolResultBlock executeEdit(Map<String, Object> input) throws Exception {
        String json = toJson(input);
        ExcelEditDsl dsl = ExcelDslValidator.parseEdit(json);
        ExcelDslValidator.validateEdit(dsl, limits);
        Path target = requireWritableTarget(dsl.getPath(), true);

        if (!requestApproval("编辑", dsl.getPath(), json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("编辑Excel操作未执行：用户拒绝或审批未通过").build()));
        }
        ExcelEditor.EditResult result = ExcelEditor.apply(dsl, target, limits);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已编辑Excel：" + dsl.getPath()
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
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            return AgentToolResultBlock.error("excel_read仅支持.xlsx/.xls文件，其他文本文件请用file_read");
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            return AgentToolResultBlock.error("文件不存在：" + path);
        }
        try {
            String json = ExcelReader.readAsJson(target);
            return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(json).build()));
        } catch (Exception e) {
            log.error("excel_read解析失败: {}", target, e);
            return AgentToolResultBlock.error("解析Excel失败：" + e.getMessage()
                    + "（文件可能损坏或为受保护的xlsm等格式）");
        }
    }

    /**
     * 校验并解析目标路径（xlsx后缀、创建/编辑存在性要求）
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
                throw new IllegalArgumentException("目标文件不存在：" + path + "（excel_edit仅支持编辑已有文件）");
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
                .reason("Agent请求在工作区" + action + "Excel文件")
                .addParam("action", action)
                .addParam("path", path)
                .addParam("content", dslJson)
                .options(List.of("批准", "拒绝"))
                .timeout(APPROVAL_TIMEOUT)
                .build());
        if (response.getStatus() == ApprovalStatus.APPROVED) {
            return true;
        }
        log.info("Excel{}审批未通过: {}，状态={}", action, path, response.getStatus());
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
    private static final String WORKBOOK_DSL_DESC = """
            工作簿定义JSON对象。结构：{"activeSheet":"激活sheet名(可选)","sheets":[{"name":"sheet名(≤31字符,不重复)", \
            "header":["表头1","表头2"], "rows":[["文本",123,"2026-01-01"]], \
            "columnTypes":["text|number|date|currency|percent"](按列,可选), "columnWidths":[14,12](字符宽,可选), \
            "merges":["A1:D1"](可选), "formulas":[{"cell":"D9","expr":"SUM(D2:D8)"}](expr不以=开头,支持跨sheet如 \
            SUM('调休上班日'!C2:C10),可选), "headerStyle":{"bold":true,"background":"D9E2F3","fontColor":"1F2430", \
            "align":"center"}(可选), "freezeHeader":true(可选), "conditionalFormat":{"range":"D2:D8", \
            "rule":"dataBar"}(可选)}]}。注意：rows中日期写"2026-01-01"配合date类型即为真日期；percent列传\
            小数（50%传0.5）；text列值以=+-@开头会被自动转义；单元格总数有上限。""";

    /**
     * 编辑操作描述（工具schema用）
     */
    private static final String OPERATIONS_DSL_DESC = """
            编辑操作列表JSON。op类型：setCell{"sheet","match":{"表头名或列字母":"期望值"},"column","value"}；\
            addRow{"sheet","values":[...]}；deleteRow{"sheet","match":{...}}(删除所有命中行)；\
            addSheet{"sheet":"新名","header":[...],"rows":[...],"columnTypes":[...]}；deleteSheet{"sheet"}；\
            renameSheet{"from","to"}；setStyle{"sheet","range":"A1:D1","style":{"bold","italic","background",\
            "fontColor","align"}}；sort{"sheet","by":"列","order":"asc|desc"}。操作逐条顺序执行。""";

    /**
     * schema属性构建辅助
     */
    private static class WorkspaceFileToolsHelper {

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
