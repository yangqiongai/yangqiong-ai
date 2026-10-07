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
package com.yangqiongai.ai.agent.core.orchestration;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 多代理编排支持服务
 * <p>
 * 提供三种编排模式，可被任何 AgentProcessor 注入使用：
 * <ul>
 *   <li><b>sequential</b> — 顺序流水线：Agent A → Agent B → Agent C</li>
 *   <li><b>parallel</b> — 并行分治：多个Agent同时执行，结果合并</li>
 *   <li><b>delegate</b> — 主从委派：主Agent通过ReAct循环委派子Agent</li>
 * </ul>
 *
 * <p>使用示例（在任意 AgentProcessor 中）：</p>
 * <pre>
 * {@code @Autowired}
 * private OrchestrationSupport orchestrationSupport;
 *
 * // 注册子代理
 * orchestrationSupport.registerSubagent("extractor", "信息提取专家");
 * orchestrationSupport.registerSubagent("reviewer", "内容审核专家");
 *
 * // 顺序流水线
 * AgentResult result = orchestrationSupport.executeSequential("分析文本", List.of(
 *     SubagentDeclaration.builder().name("extractor").description("信息提取专家").build(),
 *     SubagentDeclaration.builder().name("reviewer").description("内容审核专家").build()
 * ));
 *
 * // 并行分治
 * AgentResult result = orchestrationSupport.executeParallel("评估方案", declarations);
 *
 * // 主从委派（设置到context，由ReActAgentExecutor处理）
 * orchestrationSupport.configureDelegate(context, declarations);
 * </pre>
 *
 * @author yangqiong
 */
@Service
public class OrchestrationSupport {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationSupport.class);

    private static final String MODE_SEQUENTIAL = "sequential";
    private static final String MODE_PARALLEL = "parallel";
    private static final String MODE_DELEGATE = "delegate";

    /**
     * context属性键：编排模式
     */
    public static final String ATTR_ORCHESTRATION_MODE = "_orchestrationMode";

    /**
     * context属性键：子代理声明列表
     */
    public static final String ATTR_SUBAGENT_DECLARATIONS = "_subagentDeclarations";

    /**
     * context属性键：并行执行结果
     */
    public static final String ATTR_PARALLEL_RESULTS = "_parallelResults";

    private final SubagentOrchestrator subagentOrchestrator;

    public OrchestrationSupport(SubagentOrchestrator subagentOrchestrator) {
        this.subagentOrchestrator = subagentOrchestrator;
    }

    // ==================== 子代理注册 ====================

    /**
     * 注册子代理
     * @param name 子代理名称
     * @param description 子代理描述
     */
    public void registerSubagent(String name, String description) {
        subagentOrchestrator.declareSubagent(name, description);
    }

    /**
     * 注册子代理（完整声明）
     * <p>
     * 保留 systemPrompt/modelCode/temperature/maxIterations/tools 等扩展字段。
     * </p>
     * @param declaration 子代理声明
     */
    public void registerSubagent(SubagentDeclaration declaration) {
        subagentOrchestrator.declareSubagent(declaration);
    }

    // ==================== 编排执行 ====================

    /**
     * 顺序流水线执行：Agent A → Agent B → Agent C
     * <p>上一步的输出作为下一步的输入，最终返回最后一个Agent的结果</p>
     *
     * @param query 用户输入
     * @param pipeline 子代理声明列表（按顺序执行）
     * @param parentRequest 父级请求
     * @return 编排结果
     */
    public AgentResult executeSequential(String query, List<SubagentDeclaration> pipeline,
                                         AgentRequest parentRequest) {
        if (pipeline == null || pipeline.isEmpty()) {
            return AgentResult.failure("顺序流水线子代理列表为空");
        }

        log.info("顺序流水线执行: stepCount={}, queryLength={}", pipeline.size(), query.length());

        SubagentSpawnResult result = subagentOrchestrator.executeSequential(query, pipeline, parentRequest);

        if (result.isSuccess()) {
            return AgentResult.success(result.getResult())
                    .body(Map.of("orchestration", Map.of(
                            "mode", MODE_SEQUENTIAL,
                            "steps", pipeline.stream().map(SubagentDeclaration::getName).toList(),
                            "spawnId", result.getSpawnId()
                    )));
        } else {
            return AgentResult.failure("顺序流水线执行失败: " + result.getError())
                    .body(Map.of("orchestration", Map.of(
                            "mode", MODE_SEQUENTIAL,
                            "failedAgent", result.getAgentName(),
                            "error", result.getError() != null ? result.getError() : ""
                    )));
        }
    }

    /**
     * 并行分治执行：多个Agent同时处理同一输入，结果合并
     * <p>各Agent独立执行，结果汇总后合并输出</p>
     *
     * @param query 用户输入
     * @param agents 子代理声明列表（并行执行）
     * @param parentRequest 父级请求
     * @return 编排结果（包含各子代理结果合并）
     */
    public AgentResult executeParallel(String query, List<SubagentDeclaration> agents,
                                       AgentRequest parentRequest) {
        if (agents == null || agents.isEmpty()) {
            return AgentResult.failure("并行分治子代理列表为空");
        }

        log.info("并行分治执行: agentCount={}, queryLength={}", agents.size(), query.length());

        List<SubagentSpawnResult> results = subagentOrchestrator.executeParallel(query, agents, parentRequest);

        // 合并各子代理结果
        StringBuilder combinedResult = new StringBuilder();
        List<Map<String, Object>> subagentDetails = new ArrayList<>();

        for (SubagentSpawnResult r : results) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("agent", r.getAgentName());
            detail.put("success", r.isSuccess());
            if (r.isSuccess()) {
                String resultText = r.getResult() != null ? r.getResult() : "";
                detail.put("resultLength", resultText.length());
                if (combinedResult.length() > 0) {
                    combinedResult.append("\n\n---\n\n");
                }
                combinedResult.append("## ").append(r.getAgentName()).append(" 的分析结果\n\n");
                combinedResult.append(resultText);
            } else {
                detail.put("error", r.getError() != null ? r.getError() : "");
                if (combinedResult.length() > 0) {
                    combinedResult.append("\n\n---\n\n");
                }
                combinedResult.append("## ").append(r.getAgentName()).append(" 执行失败\n\n");
                combinedResult.append("错误: ").append(r.getError());
            }
            subagentDetails.add(detail);
        }

        return AgentResult.success(combinedResult.toString())
                .body(Map.of("orchestration", Map.of(
                        "mode", MODE_PARALLEL,
                        "subagents", subagentDetails
                )));
    }

    /**
     * 配置主从委派模式
     * <p>将子代理声明和规划模式写入context，由ReActAgentExecutor在构建Agent时自动注册子代理中间件。
     * 调用此方法后，直接走标准的 super.process(context) 即可。</p>
     *
     * @param context Agent上下文
     * @param declarations 子代理声明列表
     */
    public void configureDelegate(AgentContext context, List<SubagentDeclaration> declarations) {
        context.setAttribute(AgentContext.CTX_SUBAGENT_DECLARATIONS, declarations);
        context.setAttribute(AgentContext.CTX_PLAN_MODE_ENABLED, true);
        context.setAttribute(ATTR_ORCHESTRATION_MODE, MODE_DELEGATE);
        log.info("主从委派模式已配置: subagentCount={}", declarations.size());
    }

    /**
     * 根据编排模式自动选择执行策略
     *
     * @param mode 编排模式（sequential/parallel/delegate）
     * @param query 用户输入
     * @param declarations 子代理声明列表
     * @param context Agent上下文（delegate模式需要）
     * @param parentRequest 父级请求
     * @return 编排结果
     */
    public AgentResult execute(String mode, String query,
                               List<SubagentDeclaration> declarations,
                               AgentContext context, AgentRequest parentRequest) {
        if (declarations == null || declarations.isEmpty()) {
            return AgentResult.failure("子代理列表为空");
        }

        return switch (mode != null ? mode : MODE_DELEGATE) {
            case MODE_SEQUENTIAL -> executeSequential(query, declarations, parentRequest);
            case MODE_PARALLEL -> executeParallel(query, declarations, parentRequest);
            case MODE_DELEGATE -> {
                configureDelegate(context, declarations);
                yield null; // delegate模式需要调用方走super.process()
            }
            default -> {
                log.warn("未知的编排模式: {}, 使用delegate模式", mode);
                configureDelegate(context, declarations);
                yield null;
            }
        };
    }

    // ==================== 请求解析 ====================

    /**
     * 从请求体解析编排模式
     * @param request Agent请求
     * @return 编排模式（默认delegate）
     */
    public String resolveOrchestrationMode(AgentRequest request) {
        if (request == null) {
            return MODE_DELEGATE;
        }
        String mode = request.getOrchestrationMode();
        if (mode != null) {
            return switch (mode.toLowerCase()) {
                case MODE_SEQUENTIAL, MODE_PARALLEL, MODE_DELEGATE -> mode.toLowerCase();
                default -> {
                    log.warn("未知的编排模式: {}, 使用默认delegate模式", mode);
                    yield MODE_DELEGATE;
                }
            };
        }
        return MODE_DELEGATE;
    }

    /**
     * 从请求体解析子代理声明列表
     * <p>
     * 支持解析 systemPrompt/tools/temperature/maxIterations 等扩展字段。
     * </p>
     * @param request Agent请求
     * @return 子代理声明列表
     */
    public List<SubagentDeclaration> resolveSubagentDeclarations(AgentRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rawList = request.getSubagentDeclarations();
        if (rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<SubagentDeclaration> declarations = new ArrayList<>();
        for (Map<String, Object> map : rawList) {
            String name = map.get("name") instanceof String s ? s : null;
            String description = map.get("description") instanceof String s ? s : "";
            if (name != null && !name.isBlank()) {
                SubagentDeclaration.SubagentDeclarationBuilder b = SubagentDeclaration.builder()
                        .name(name)
                        .description(description);
                if (map.get("systemPrompt") instanceof String s && !s.isBlank()) {
                    b.systemPrompt(s);
                }
                if (map.get("modelCode") instanceof String s && !s.isBlank()) {
                    b.modelCode(s);
                }
                if (map.get("temperature") instanceof Number n) {
                    b.temperature(n.doubleValue());
                }
                if (map.get("maxIterations") instanceof Number n) {
                    b.maxIterations(n.intValue());
                }
                if (map.get("tools") instanceof List<?> t) {
                    b.tools(t.stream().filter(String.class::isInstance).map(String.class::cast).toList());
                }
                declarations.add(b.build());
            }
        }
        return declarations;
    }

    /**
     * 注册请求体中的子代理声明到编排器
     * @param request Agent请求
     * @return 解析出的子代理声明列表
     */
    public List<SubagentDeclaration> registerSubagentsFromRequest(AgentRequest request) {
        List<SubagentDeclaration> declarations = resolveSubagentDeclarations(request);
        for (SubagentDeclaration decl : declarations) {
            registerSubagent(decl);
        }
        return declarations;
    }

    /**
     * 获取可用的编排模式列表
     * @return 模式列表
     */
    public List<String> getAvailableModes() {
        return List.of(MODE_SEQUENTIAL, MODE_PARALLEL, MODE_DELEGATE);
    }

    /**
     * 列出已注册的子代理
     * @return 子代理声明列表
     */
    public List<SubagentDeclaration> listSubagents() {
        return subagentOrchestrator.listSubagents();
    }
}
