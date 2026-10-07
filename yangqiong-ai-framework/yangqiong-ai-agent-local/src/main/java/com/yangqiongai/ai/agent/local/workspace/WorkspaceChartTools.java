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
import com.yangqiongai.ai.agent.local.workspace.chart.ChartDsl;
import com.yangqiongai.ai.agent.local.workspace.chart.ChartRenderer;
import com.yangqiongai.ai.agent.local.workspace.chart.ChartValidator;
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
import java.util.Map;

/**
 * 工作区图表生成工具
 * <p>
 * 请求级注册到Agent工具箱，路径物理限定在用户工作区根内，审批语义与文件写删一致。
 * 内容与渲染分离：模型只产出JSON DSL，PNG绘制由后端JFreeChart确定性完成。
 * </p>
 *
 * @author yangqiong
 */
public class WorkspaceChartTools {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceChartTools.class);

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

    private WorkspaceChartTools(Path root, String approvalMode, String sessionId, String userId,
                                ApprovalGate approvalGate) {
        this.root = root;
        this.approvalMode = approvalMode;
        this.sessionId = sessionId;
        this.userId = userId;
        this.approvalGate = approvalGate;
    }

    /**
     * 注册图表渲染工具到请求级工具箱
     * @param toolkit
     * @param ref 路由器分派的工作区引用
     * @param approvalMode
     * @param sessionId
     * @param userId
     * @param approvalGate 审批门控，未装配时MANUAL/CUSTOM层级下写操作直接拒绝
     */
    public static void registerTo(AgentToolkit toolkit, WorkspaceRef ref, String approvalMode,
                                  String sessionId, String userId, ApprovalGate approvalGate) {
        WorkspaceChartTools tools = new WorkspaceChartTools(
                Paths.get(ref.getRootPath()), approvalMode, sessionId, userId, approvalGate);
        toolkit.addTool(tools.buildRenderTool());
        log.info("工作区图表工具已注册: root={}, approvalMode={}", ref.getRootPath(), approvalMode);
    }

    private AgentTool buildRenderTool() {
        return new AgentTool() {

            @Override
            public String getName() {
                return "chart_render";
            }

            @Override
            public String getDescription() {
                return "在工作区内渲染统计图表为PNG图片。path为相对工作区根的保存路径（.png结尾），type为图表类型，"
                        + "categories为类别标签数组，series为数据系列JSON数组。适合生成柱状图、折线图、饼图等"
                        + "数据可视化图片，中文标签自动适配字体。校验失败时返回含字段路径的错误信息，请修正后重试。";
            }

            @Override
            public Map<String, Object> getParameters() {
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", "object");
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("path", SchemaHelper.stringProp("图片保存路径，相对工作区根，必须以.png结尾"));
                properties.put("type", SchemaHelper.stringProp("图表类型：bar柱状图/line折线图/pie饼图"));
                properties.put("title", SchemaHelper.stringProp("图表标题（可选）"));
                properties.put("categories", SchemaHelper.arrayProp(CATEGORIES_PROP_DESC));
                properties.put("series", SchemaHelper.arrayOfObjectProp(SERIES_DSL_DESC));
                schema.put("properties", properties);
                schema.put("required", List.of("path", "type", "categories", "series"));
                return schema;
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.fromCallable(() -> executeRender(param.getInput()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("chart_render工具执行失败", e);
                            return Mono.just(AgentToolResultBlock.error("生成图表失败: " + e.getMessage()));
                        });
            }
        };
    }

    /**
     * 执行图表渲染（校验→审批→落盘）
     * @param input
     * @return
     */
    private AgentToolResultBlock executeRender(Map<String, Object> input) throws Exception {
        String json = toJson(input);
        ChartDsl dsl = ChartValidator.parse(json);
        ChartValidator.validate(dsl);
        Path target = requireWritableTarget(dsl.getPath(), false);

        if (!requestApproval("创建", dsl.getPath(), json)) {
            return AgentToolResultBlock.of(List.of(
                    AgentTextBlock.builder().text("创建图表操作未执行：用户拒绝或审批未通过").build()));
        }
        ChartRenderer.RenderResult result = ChartRenderer.render(dsl, target);
        return AgentToolResultBlock.of(List.of(
                AgentTextBlock.builder().text("已生成图表：" + dsl.getPath()
                        + "（" + result.getPointCount() + "个数据点）").build()));
    }

    /**
     * 校验并解析目标路径（png后缀、创建存在性要求）
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
                .reason("Agent请求在工作区" + action + "图表文件")
                .addParam("action", action)
                .addParam("path", path)
                .addParam("content", dslJson)
                .options(List.of("批准", "拒绝"))
                .timeout(APPROVAL_TIMEOUT)
                .build());
        if (response.getStatus() == ApprovalStatus.APPROVED) {
            return true;
        }
        log.info("图表{}审批未通过: {}，状态={}", action, path, response.getStatus());
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
     * 类别参数描述（工具schema用）
     */
    private static final String CATEGORIES_PROP_DESC = "横轴类别标签字符串数组，如 [\"一月\",\"二月\",\"三月\"]，最多10000个";

    /**
     * 数据系列参数描述（工具schema用，引导模型产出合规JSON）
     */
    private static final String SERIES_DSL_DESC = """
            数据系列JSON数组。结构：[{"name":"系列名","values":[120,89,150]}]，values为与categories等长的数值数组；\
            pie饼图仅允许1个系列。整图可在最外层附加可选width/height（像素，200-2000，缺省800x500）。\
            示例：[{"name":"销售额","values":[120,89,150]},{"name":"成本","values":[80,60,95]}]""";

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

        static Map<String, Object> arrayOfObjectProp(String description) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "array");
            prop.put("description", description);
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", "object");
            prop.put("items", items);
            return prop;
        }
    }
}
