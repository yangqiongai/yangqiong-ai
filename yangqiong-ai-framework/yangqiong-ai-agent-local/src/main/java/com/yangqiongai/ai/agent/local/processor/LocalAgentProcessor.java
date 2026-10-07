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
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.processor.AbstractAgentProcessor;
import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.workspace.UserWorkspaceService;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceApprovalPolicyGate;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceChartTools;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceDocxTools;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceExcelTools;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceFileTools;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceReadTools;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceProperties;
import com.yangqiongai.ai.agent.local.workspace.WorkspaceZipTools;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelLimits;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGatewayRouter;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode;
import com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig;
import com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.approval.ApprovalRequest;
import com.yangqiongai.ai.approval.ApprovalResponse;
import com.yangqiongai.ai.approval.ApprovalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 本地模式处理器
 * <p>
 * 启用全部本地能力，包括 Shell 工具、文件系统工具、
 * 工作区上下文感知、At-Path 扩展、动态技能加载、默认工作区技能、记忆工具/Hook。
 * 适用于需要在本地环境执行文件操作、命令执行、代码编写等任务的场景。
 * </p>
 *
 * @author yangqiong
 */
public class LocalAgentProcessor extends AbstractAgentProcessor {

    @Value("${ai.agent.local.max-iters:25}")
    protected int localMaxIters;

    @Value("${ai.agent.local.workspace-path:}")
    protected String defaultWorkspacePath;

    @Autowired
    private UserWorkspaceService userWorkspaceService;

    @Autowired
    private WorkspaceProperties workspaceProperties;

    /**
     * 工作区网关组合路由器（按工作区类型分派文件操作网关）
     */
    @Autowired
    private WorkspaceGatewayRouter workspaceGatewayRouter;

    @Autowired(required = false)
    private ApprovalGate approvalGate;

    /**
     * 请求级工作区参数键（复用_前缀保留字段机制，仅对localAgent生效）
     */
    private static final String BODY_KEY_WORKSPACE_ID = "_workspaceId";

    /**
     * 请求级运行ID参数键（引擎运行链路注入body，经工作区引用透传供执行端审计关联）
     */
    private static final String BODY_KEY_RUN_ID = "harness.runId";

    /**
     * Excel工具能力提示词（工作区模式下注入）
     */
    private static final String EXCEL_TOOL_PROMPT = """
            ### Excel文件能力
            需要生成或修改Excel（.xlsx）时，禁止用file_write写CSV文本伪装，必须使用专用工具：
            - excel_write：创建真实二进制工作簿，支持多sheet、真日期/数值/货币/百分比类型、表头样式、合并、\
            公式（支持跨sheet引用如SUM('调休上班日'!C2:C10)）、冻结首行、数据条条件格式。参数：path（.xlsx结尾）+ \
            workbook（含activeSheet与sheets数组，sheet含name/header/rows/columnTypes/columnWidths/merges/formulas/\
            headerStyle/freezeHeader/conditionalFormat）。
            - excel_read：读取xlsx/xls为结构化JSON（多sheet全量，超100行截断）。读取Excel必须用它，不要用file_read。
            - excel_edit：增量编辑已有xlsx（operations列表：setCell/addRow/deleteRow/addSheet/deleteSheet/\
            renameSheet/setStyle/sort），未触及单元格原样保留；编辑前先excel_read读回现状，用match条件定位行。
            规范：columnTypes按列声明类型（date列写"2026-01-01"即为真日期；percent列50%写0.5）；\
            工具校验失败会返回含字段路径的错误，请按提示修正后重试；写操作在工作区审批层级为MANUAL/CUSTOM时需用户批准。\n""";

    /**
     * 二期文件生成工具能力提示词（工作区模式下注入）
     */
    private static final String GENERATE_TOOL_PROMPT = """
            ### Word/图表/打包文件能力
            - docx_write：创建真实二进制Word（.docx）。参数：path（.docx结尾）+ document.blocks（内容块数组：\
            {"type":"heading","text":"标题","level":1-4}；{"type":"paragraph","text":"正文","align":"left|center|right",\
            "bold":true}；{"type":"table","header":["列1","列2"],"rows":[["a","b"]]}；{"type":"image","path":"工作区内图片.png",\
            "width":450}，image仅支持png/jpg/jpeg/gif且必须已存在于工作区内，可先用chart_render生成图表再插入）。\
            生成报告/公文类文档必须用它，禁止用file_write伪装。
            - docx_read：读取docx为结构化JSON（段落+表格，编辑前先读回现状）。
            - docx_edit：增量编辑已有docx（operations列表：replaceText{"from","to"}全文替换；insertPara{"after":\
            "匹配段落文本(contains)","text":"新段落","type":"paragraph|heading","level"}，after为空则追加文末；\
            addTable{"header":[...],"rows":[[...]]}追加文末），任一操作失败整体不落盘。
            - chart_render：把数据渲染为PNG图片（.png结尾）。参数：path + type("bar"|"line"|"pie") + title + \
            categories（X轴/扇区名数组）+ series（[{"name":"系列名","values":[数值]}]；pie仅允许1个系列）+ \
            width/height(默认800x500)。中文标签正常显示。
            - zip_pack：把工作区内文件/目录清单打包为zip。参数：zipPath（.zip结尾）+ paths（相对路径数组，目录递归打包）。
            - zip_unpack：解压工作区内zip到子目录。参数：zipPath + destDir（相对路径，自动创建，拒绝恶意条目路径）。
            规范：以上写操作在MANUAL/CUSTOM审批层级需用户批准；校验失败返回含字段路径的错误，请修正后重试。\n""";

    @Override
    public String getAgentCode() {
        return LOCAL_AGENT;
    }

    @Override
    protected String getAgentName() {
        return "LocalAgent";
    }

    @Override
    protected int getMaxIterations() {
        return localMaxIters;
    }

    @Override
    protected boolean disableLongTermMemoryTools() {
        return false;
    }

    /**
     * 覆盖父类，不调用任何 disable* 方法，保留全部本地能力
     * @param request
     * @param systemPrompt
     * @return
     */
    @Override
    protected HarnessAgentRuntimeBuilder buildAgentBuilder(AgentRequest request, String systemPrompt) {
        HarnessAgentRuntimeBuilder builder = agentRuntimeFactory.createBuilder();
        builder.systemPrompt(systemPrompt);
        builder.memoryConfig(AgentMemoryConfig.defaults());
        // 模型调用瞬时故障（连接重置/429/5xx）指数退避重试，流式仅首片到达前重试避免重复输出
        builder.modelRetryConfig(AgentModelRetryConfig.defaultEnabled());
        builder.maxIters(getMaxIterations());
        // 开启ask_user工具：模型缺信息时主动向用户提问，前端弹出澄清卡片提交答案后续跑
        builder.askUserEnabled(true);
        WorkspaceInfo workspace = resolveWorkspace(request);
        if (workspace != null) {
            // 虚拟根（连接器/浏览器桥）文件操作经网关ws.fs.*工具执行，不绑定引擎本地文件沙箱
            if (!UserWorkspaceService.isVirtualRoot(workspace.getRootPath())) {
                // 请求级沙箱根注入：先设置沙箱根再启用文件工具（fileToolsEnabled读取当前已设置的根），审批层级按工作区配置
                builder.fileToolkit(workspace.getRootPath(), null);
                builder.fileToolsEnabled(true);
            }
            AgentApprovalMode approvalMode = AgentApprovalMode.valueOf(workspace.getApprovalMode());
            builder.approvalMode(approvalMode);
            // AUTO模式复用主模型作为审批裁判（与本地Harness装配一致），未解析到模型时由引擎build校验兜底
            if (approvalMode == AgentApprovalMode.AUTO) {
                String modelCode = agentBootstrapService.resolveModelCode(request);
                if (modelCode != null && !modelCode.isBlank() && agentModelFactory != null) {
                    builder.approvalJudgeModel(agentModelFactory.getModel(modelCode, null));
                }
                // 删除类高危操作不经AI裁判（裁判提示词对删除类固定拒绝），强制转人工审批卡片放行
                builder.requireApproval(Set.of("file_delete", "ws.fs.delete"));
            }
            // 引擎审批实现方式：策略门交引擎triage裁决（写删类ASK暂停等人工确认），工具内框架审批门放行避免双重审批
            if ("engine".equalsIgnoreCase(workspaceProperties.getApprovalEngine())) {
                builder.toolPolicyGate(new WorkspaceApprovalPolicyGate(workspace.getApprovalMode()));
            }
            // 工具调用超时放长到10分钟：MANUAL审批层级下写删工具需等待人工批准（审批等待上限5分钟）
            builder.toolCallTimeout(java.time.Duration.ofMinutes(10));
        }
        return builder;
    }

    /**
     * 覆盖父类，系统提示词声明工作区边界（不暴露服务器物理路径，模型以相对路径访问）
     * @param request
     * @return
     */
    @Override
    protected String resolveSystemPrompt(AgentRequest request) {
        String prompt = super.resolveSystemPrompt(request);
        WorkspaceInfo workspace = resolveWorkspace(request);
        if (workspace != null) {
            if (UserWorkspaceService.isVirtualRoot(workspace.getRootPath())) {
                // 虚拟根：文件在远程执行端，模型须经网关工作区文件工具访问，本地文件工具不可用
                prompt += "\n当前工作区：" + workspace.getName() + "（远程桥接工作区，标识：" + workspace.getRootPath() + "）\n"
                        + "文件的读取、列举、写入、删除等操作必须使用工作区文件工具（file_read、file_list、file_write、file_delete系列），不要使用本地文件工具。\n";
            } else {
                prompt += "\n当前工作区：" + workspace.getName() + "，所有文件读写操作都限定在该工作区内，"
                        + "文件路径请使用相对工作区根的相对路径。\n";
                if (workspaceProperties.getGenerate().isEnabled()) {
                    prompt += EXCEL_TOOL_PROMPT + GENERATE_TOOL_PROMPT;
                }
            }
        } else if (defaultWorkspacePath != null && !defaultWorkspacePath.isBlank()
                && !prompt.contains("当前默认工作区")) {
            prompt += "当前默认工作区已启用，文件读写操作限定在默认工作区内，"
                    + "文件路径请使用相对工作区根的相对路径。\n";
        }
        return prompt;
    }

    /**
     * 解析请求级工作区：_workspaceId校验归属与启用状态，无效时返回null降级为静态默认路径
     * @param request
     * @return
     */
    private WorkspaceInfo resolveWorkspace(AgentRequest request) {
        Object workspaceId = request == null || request.getBody() == null
                ? null : request.getBody().get(BODY_KEY_WORKSPACE_ID);
        if (workspaceId == null) {
            return null;
        }
        try {
            String userId = request.getUserId();
            return userWorkspaceService.resolveEnabled(
                    Long.valueOf(workspaceId.toString()),
                    userId == null || userId.isBlank() ? "anonymous" : userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析请求级运行ID：引擎运行链路在body注入harness.runId，直连运行无该键返回null
     * @param request
     * @return
     */
    static String resolveRunId(AgentRequest request) {
        Object runId = request == null || request.getBody() == null
                ? null : request.getBody().get(BODY_KEY_RUN_ID);
        return runId == null || runId.toString().isBlank() ? null : runId.toString();
    }

    /**
     * 注册请求级工作区写删工具（路径限定沙箱根，审批层级按工作区配置）
     * @param builder
     * @param request
     * @param toolkit
     */
    @Override
    protected void configureTool(AgentRuntimeBuilder builder, AgentRequest request, AgentToolkit toolkit) {
        // 不禁用任何工具，保留全部工具
        WorkspaceInfo workspace = resolveWorkspace(request);
        if (workspace != null) {
            // 经组合路由器按工作区类型分派网关（当前仅SERVER类型，连接器/浏览器桥实现SPI注册bean即接入）
            WorkspaceRef ref = WorkspaceRef.of(workspace);
            // 运行ID透传：连接器/浏览器桥执行端据以审计关联Agent运行（非引擎运行链路为空）
            ref.setRunId(resolveRunId(request));
            WorkspaceGateway gateway = workspaceGatewayRouter.route(ref.getType());
            // 引擎审批模式下工具内框架审批门放行（人工确认已由引擎triage暂停完成）
            boolean engineApproval = "engine".equalsIgnoreCase(workspaceProperties.getApprovalEngine());
            ApprovalGate toolApprovalGate = engineApproval ? new EngineModeApprovalGate() : approvalGate;
            WorkspaceFileTools.registerTo(toolkit, ref, gateway, workspace.getApprovalMode(),
                    request.getSessionId(), request.getUserId(), toolApprovalGate);
            if (UserWorkspaceService.isVirtualRoot(workspace.getRootPath())) {
                // 虚拟根工作区无引擎本地文件沙箱，读列能力经网关工具补齐
                WorkspaceReadTools.registerTo(toolkit, ref, gateway);
            }
            if (workspaceProperties.getGenerate().isEnabled()
                    && !UserWorkspaceService.isVirtualRoot(workspace.getRootPath())) {
                // 生成类工具走本地磁盘路径，虚拟根工作区文件在远程执行端，仅注册经网关的文件工具
                WorkspaceProperties.Generate generate = workspaceProperties.getGenerate();
                ExcelLimits limits = ExcelLimits.of(generate.getMaxCells(), generate.getMaxFileSize(),
                        generate.getMaxSheets());
                WorkspaceExcelTools.registerTo(toolkit, ref, workspace.getApprovalMode(), request.getSessionId(),
                        request.getUserId(), toolApprovalGate, limits);
                WorkspaceDocxTools.registerTo(toolkit, ref, workspace.getApprovalMode(), request.getSessionId(),
                        request.getUserId(), toolApprovalGate);
                WorkspaceChartTools.registerTo(toolkit, ref, workspace.getApprovalMode(), request.getSessionId(),
                        request.getUserId(), toolApprovalGate);
                WorkspaceZipTools.registerTo(toolkit, ref, workspace.getApprovalMode(), request.getSessionId(),
                        request.getUserId(), toolApprovalGate);
            }
        }
    }

    /**
     * 本地工作区感知的系统提示词
     * @return
     */
    @Override
    protected String getDefaultSystemPrompt() {
        String prompt = "你是一个具备本地环境访问能力的AI助手。\n"
                + "你可以执行Shell命令、读写文件系统、管理工作区、加载动态技能。\n"
                + "请根据用户任务合理使用这些能力，优先使用工作区内的文件和工具。\n"
                + "默认使用中文回答，除非用户明确要求其他语言。\n";
        if (defaultWorkspacePath != null && !defaultWorkspacePath.isBlank()) {
            prompt += "当前默认工作区已启用，文件读写操作限定在默认工作区内，文件路径请使用相对工作区根的相对路径。\n";
        }
        return prompt;
    }

    /**
     * 构建用户输入消息
     * @param request
     * @return
     */
    @Override
    protected List<AgentMessage> buildInputMessages(AgentRequest request) {
        List<InputBlock> inputBlocks = request == null ? List.of() : request.getInput();
        List<AgentContentBlock> userInput;
        if (inputBlocks == null || inputBlocks.isEmpty()) {
            userInput = List.of(AgentTextBlock.builder().text("").build());
        } else {
            userInput = ContentBlockConverter.fromInputBlocks(inputBlocks);
        }
        List<AgentMessage> inputs = new ArrayList<>();
        AgentMessage userMessage = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(userInput)
                .build();
        inputs.add(userMessage);
        return inputs;
    }

    /**
     * 引擎审批模式下工具内审批门放行
     * <p>
     * 人工确认已由引擎triage暂停完成，工具内框架审批门直接批准以避免双重审批。
     * </p>
     */
    private static class EngineModeApprovalGate extends ApprovalGate {

        /**
         * 直接批准
         * @param request
         * @return
         */
        @Override
        public ApprovalResponse requestApproval(ApprovalRequest request) {
            return new ApprovalResponse(null, ApprovalStatus.APPROVED, null, null);
        }
    }
}
