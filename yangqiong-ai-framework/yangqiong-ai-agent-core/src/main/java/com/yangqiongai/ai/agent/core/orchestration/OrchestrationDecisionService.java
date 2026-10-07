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

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * 编排模式决策
 * <p>
 * 通过 LLM 分析任务特征，推荐最优编排模式。
 * 仅在请求未指定编排模式时调用。
 * </p>
 * @author yangqiong
 */
@Service
public class OrchestrationDecisionService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationDecisionService.class);

    private static final String DEFAULT_MODE = "delegate";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Prompt(code = "core.orchestration.decision", name = "编排模式决策系统提示词",
            description = "分析任务特征推荐编排模式的系统提示词", category = PromptCategory.SYSTEM,
            readonly = true, visible = false)
    private static final String DECISION_SYSTEM_PROMPT = """
            你是一个多代理编排专家。根据用户的任务描述，选择最合适的编排模式。

            可选模式：
            - sequential：顺序流水线，适合有明确步骤依赖的任务（如：提取→分析→总结）
            - parallel：并行分治，适合可独立处理的子任务（如：多维度评估）
            - delegate：主从委派，适合需要主代理动态决策的复杂任务（如：研究+写作）

            请返回 JSON 格式（不要包含其他内容）：
            {"mode": "sequential|parallel|delegate", "reason": "选择原因"}""";

    @Value("${ai.agent.orchestration.dynamic.decision.enabled:true}")
    private boolean enabled;

    @Value("${ai.agent.orchestration.dynamic.decision.model-code:}")
    private String modelCode;

    @Autowired
    private LanguageModelFactory languageModelFactory;

    @Autowired
    private DefaultLlmModelService defaultLlmModelService;

    /**
     * 提示词解析器，支持数据库覆盖与版本管理
     */
    @Autowired(required = false)
    private com.yangqiongai.ai.agent.core.prompt.PromptResolver promptResolver;

    /**
     * 决策编排模式
     * @param task
     * @param requestModelCode
     * @return
     */
    public Mono<String> decideMode(String task, String requestModelCode) {
        if (!enabled) {
            return Mono.just(DEFAULT_MODE);
        }
        String resolvedModelCode = resolveModelCode(requestModelCode);
        if (resolvedModelCode == null || resolvedModelCode.isBlank()) {
            log.warn("未解析到可用模型，编排模式回退为: {}", DEFAULT_MODE);
            return Mono.just(DEFAULT_MODE);
        }
        String truncatedTask = task != null && task.length() > 1000
                ? task.substring(0, 1000) + "..." : (task != null ? task : "");
        log.info("开始决策编排模式: taskLength={}", truncatedTask.length());
        return Mono.fromCallable(() -> {
            String systemPrompt = promptResolver != null
                    ? promptResolver.resolve("core.orchestration.decision", DECISION_SYSTEM_PROMPT)
                    : DECISION_SYSTEM_PROMPT;
            long startTime = System.currentTimeMillis();
            String output = languageModelFactory.generateText(
                    resolvedModelCode, systemPrompt, "任务描述: " + truncatedTask);
            String mode = parseModeFromJson(output);
            // 结果非默认降级视为成功，上报耗时
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("core.orchestration.decision", null,
                        !DEFAULT_MODE.equals(mode), System.currentTimeMillis() - startTime);
            }
            return mode;
        })
                .onErrorReturn(DEFAULT_MODE)
                .doOnNext(mode -> log.info("编排模式决策完成: mode={}", mode));
    }

    /**
     * 是否启用
     * @return
     */
    public boolean isEnabled() {
        return enabled;
    }

    private String parseModeFromJson(String output) {
        if (output == null || output.isBlank()) {
            return DEFAULT_MODE;
        }
        try {
            String json = output.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            JsonNode node = OBJECT_MAPPER.readTree(json);
            String mode = node.path("mode").asText(DEFAULT_MODE);
            return switch (mode.toLowerCase()) {
                case "sequential", "parallel", "delegate" -> mode.toLowerCase();
                default -> DEFAULT_MODE;
            };
        } catch (Exception e) {
            log.warn("解析编排模式JSON失败，回退为默认: output={}", output, e);
            return DEFAULT_MODE;
        }
    }

    /**
     * 解析模型编码
     * <p>
     * 优先级：请求指定 → 配置 modelCode → 默认模型服务。
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
        return code;
    }
}
