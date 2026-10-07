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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.llm.DefaultLlmModelService;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 子代理规格自动生成
 * <p>
 * 通过 LLM 根据任务描述自动生成子代理声明。
 * 原 agentscope SubagentSpecGenerator/Model 依赖已移除，generateMarkdown 方法已删除，
 * 保留 generateDeclarations 方法基于框架层 LanguageModelFactory 生成结构化 JSON 声明。
 * </p>
 * @author yangqiong
 */
@Service
public class SubagentSpecGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SubagentSpecGeneratorService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Prompt(code = "core.subagent.generate", name = "子代理生成系统提示词",
            description = "根据任务自动生成子代理声明的系统提示词", category = PromptCategory.SYSTEM,
            readonly = true, visible = false)
    private static final String GENERATION_SYSTEM_PROMPT = """
            你是多代理系统架构师。根据用户任务，设计协作子代理团队。

            请返回 JSON 数组（不要包含其他内容），每个元素是一个子代理声明：
            [
              {
                "name": "子代理英文名称（小写下划线）",
                "description": "子代理职责描述",
                "systemPrompt": "子代理系统提示词，定义其专业能力和行为规范",
                "modelCode": "",
                "tools": [],
                "temperature": 0.7,
                "maxIterations": 10,
                "inheritTools": true,
                "allowedTools": [],
                "deniedTools": [],
                "inheritSkills": true,
                "inheritMcp": true,
                "inheritMiddlewares": true
              }
            ]

            能力控制字段说明（可选，未提供时使用默认值true/空）：
            - inheritTools: 是否继承主代理工具箱，默认true
            - allowedTools: 工具白名单（工具名称数组），非空时子代理仅可用这些工具
            - deniedTools: 工具黑名单（工具名称数组），这些工具对子代理不可用
            - inheritSkills: 是否继承主代理技能箱，默认true
            - inheritMcp: 是否继承主代理MCP工具，默认true
            - inheritMiddlewares: 是否继承主代理中间件链，默认true

            约束：
            - name 使用英文，小写下划线格式
            - 每个子代理应有明确的专业分工
            - 子代理数量不超过指定上限
            - 只返回 JSON 数组，不要包含 markdown 代码块标记""";

    @Value("${ai.agent.subagent.generation.enabled:false}")
    private boolean enabled;

    @Value("${ai.agent.subagent.generation.model-code:}")
    private String modelCode;

    @Value("${ai.agent.orchestration.dynamic.generation.enabled:true}")
    private boolean declarationGenerationEnabled;

    @Value("${ai.agent.orchestration.dynamic.generation.max-subagents:5}")
    private int defaultMaxSubagents;

    @Value("${ai.agent.orchestration.dynamic.generation.model-code:}")
    private String declarationModelCode;

    @Autowired
    private LanguageModelFactory languageModelFactory;

    @Autowired
    private DefaultLlmModelService defaultLlmModelService;

    /**
     * 提示词解析器
     */
    @Autowired(required = false)
    private PromptResolver promptResolver;

    /**
     * 根据任务描述自动生成子代理声明列表
     * <p>
     * 调用 LLM 分析任务，生成结构化 JSON 格式的子代理声明，
     * 自动解析为 SubagentDeclaration 列表。生成的声明可直接用于编排执行。
     * </p>
     * @param task
     * @param maxSubagents
     * @param existingAgentNames
     * @param requestModelCode
     * @return
     */
    public Mono<List<SubagentDeclaration>> generateDeclarations(
            String task, int maxSubagents, Collection<String> existingAgentNames,
            String requestModelCode) {
        if (!declarationGenerationEnabled) {
            log.warn("子代理声明动态生成未启用，返回空列表");
            return Mono.just(Collections.emptyList());
        }
        String resolvedModelCode = resolveModelCode(requestModelCode);
        if (resolvedModelCode == null || resolvedModelCode.isBlank()) {
            log.warn("未解析到可用模型，返回空列表");
            return Mono.just(Collections.emptyList());
        }
        int limit;
        if (maxSubagents > 0) {
            limit = maxSubagents;
            if (limit > defaultMaxSubagents) {
                log.warn("请求的子代理数量({})超过默认配置值({})，将尊重请求但请注意资源消耗",
                        limit, defaultMaxSubagents);
            }
        } else {
            limit = defaultMaxSubagents;
        }
        String truncatedTask = task != null && task.length() > 2000
                ? task.substring(0, 2000) + "..." : (task != null ? task : "");
        log.info("开始动态生成子代理声明: taskLength={}, maxSubagents={}", truncatedTask.length(), limit);
        String userPrompt = "任务: " + truncatedTask + "\n最大子代理数: " + limit;
        return Mono.fromCallable(() -> {
            String systemPrompt = promptResolver != null
                    ? promptResolver.resolve("core.subagent.generate", GENERATION_SYSTEM_PROMPT)
                    : GENERATION_SYSTEM_PROMPT;
            long startTime = System.currentTimeMillis();
            String output = languageModelFactory.generateText(
                    resolvedModelCode, systemPrompt, userPrompt);
            List<SubagentDeclaration> decls = parseDeclarationsFromJson(output, limit);
            // 解析结果非空视为成功，上报耗时
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("core.subagent.generate", null,
                        !decls.isEmpty(), System.currentTimeMillis() - startTime);
            }
            return decls;
        })
                .onErrorReturn(Collections.emptyList())
                .doOnNext(decls -> log.info("子代理声明生成完成: count={}", decls.size()));
    }

    /**
     * 是否启用 Markdown 规格生成
     * @return
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 是否启用声明动态生成
     * @return
     */
    public boolean isDeclarationGenerationEnabled() {
        return declarationGenerationEnabled;
    }

    @SuppressWarnings("unchecked")
    private List<SubagentDeclaration> parseDeclarationsFromJson(String output, int maxSubagents) {
        if (output == null || output.isBlank()) {
            return Collections.emptyList();
        }
        try {
            String json = output.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            List<Map<String, Object>> rawList = OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<SubagentDeclaration> declarations = new java.util.ArrayList<>();
            for (Map<String, Object> map : rawList) {
                String name = map.get("name") instanceof String s ? s : null;
                String description = map.get("description") instanceof String s ? s : "";
                if (name == null || name.isBlank()) {
                    continue;
                }
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
                if (map.get("inheritTools") instanceof Boolean bVal) {
                    b.inheritTools(bVal);
                }
                if (map.get("allowedTools") instanceof List<?> at) {
                    Set<String> allowed = new HashSet<>();
                    at.stream().filter(String.class::isInstance).map(String.class::cast).forEach(allowed::add);
                    if (!allowed.isEmpty()) {
                        b.allowedTools(allowed);
                    }
                }
                if (map.get("deniedTools") instanceof List<?> dt) {
                    Set<String> denied = new HashSet<>();
                    dt.stream().filter(String.class::isInstance).map(String.class::cast).forEach(denied::add);
                    if (!denied.isEmpty()) {
                        b.deniedTools(denied);
                    }
                }
                if (map.get("inheritSkills") instanceof Boolean bVal) {
                    b.inheritSkills(bVal);
                }
                if (map.get("inheritMcp") instanceof Boolean bVal) {
                    b.inheritMcp(bVal);
                }
                if (map.get("inheritMiddlewares") instanceof Boolean bVal) {
                    b.inheritMiddlewares(bVal);
                }
                declarations.add(b.build());
                if (declarations.size() >= maxSubagents) {
                    break;
                }
            }
            return declarations;
        } catch (Exception e) {
            log.warn("解析子代理声明JSON失败: output={}", output, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析模型编码
     * <p>
     * 优先级：请求指定 → 配置 modelCode → 默认模型服务 → 配置 declarationModelCode。
     * </p>
     * @param requestModelCode
     * @return
     */
    private String resolveModelCode(String requestModelCode) {
        String code = requestModelCode != null && !requestModelCode.isBlank()
                ? requestModelCode : modelCode;
        if (code == null || code.isBlank()) {
            code = defaultLlmModelService.resolveModel(null, null);
        }
        if (code == null || code.isBlank()) {
            code = declarationModelCode;
        }
        return code;
    }
}
