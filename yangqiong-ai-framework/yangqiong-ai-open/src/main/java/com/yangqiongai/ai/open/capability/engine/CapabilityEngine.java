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
package com.yangqiongai.ai.open.capability.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.prompt.PromptResolver;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer;
import com.yangqiongai.ai.common.enums.PromptCategory;
import com.yangqiongai.ai.common.prompt.Prompt;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.catalog.CapabilityNotFoundException;
import com.yangqiongai.ai.open.capability.context.DataContextHub;
import com.yangqiongai.ai.open.capability.guard.CapabilityInputValidator;
import com.yangqiongai.ai.open.capability.guard.JsonSchemaValidator;
import com.yangqiongai.ai.open.capability.guard.OutputSchemaGuard;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.open.capability.template.PromptTemplateEngine;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRecord;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRepository;
import com.yangqiongai.ai.open.capability.trace.DedupKey;
import com.yangqiongai.ai.open.capability.trace.RequestDeduplicator;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 能力执行引擎
 * <p>
 * 编排能力执行的完整流程：入参校验 → 去重检查 → 数据上下文注入 → Prompt渲染 →
 * AgentEngine调用 → 输出解析 → 契约校验 → 调用追踪记录。
 * </p>
 * @author yangqiong
 */
public class CapabilityEngine {

    private static final Logger log = LoggerFactory.getLogger(CapabilityEngine.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Prompt(code = "open.capability.default", name = "能力默认模板",
            description = "能力未配置Prompt模板时的默认生成模板", category = PromptCategory.SYSTEM,
            readonly = true, visible = false)
    private static final String CAPABILITY_DEFAULT_PROMPT = "请根据以下信息生成内容：\n\n${description}";

    private final CapabilityCatalog catalog;
    private final CapabilityInvoker invoker;
    private final DataContextHub dataContextHub;
    private final OutputSchemaGuard schemaGuard;
    private final JsonSchemaValidator jsonValidator;
    /**
     * 入参校验器（支持pattern与errorMessage自定义提示，无状态）
     */
    private final CapabilityInputValidator inputValidator = new CapabilityInputValidator();
    private final PromptTemplateEngine templateEngine;
    private final RequestDeduplicator deduplicator;
    private final CapabilityCallRepository callRepository;
    private final long dedupTtlSeconds;

    /**
     * 提示词解析器
     */
    @Autowired(required = false)
    private PromptResolver promptResolver;

    public CapabilityEngine(CapabilityCatalog catalog, CapabilityInvoker invoker,
                             DataContextHub dataContextHub, OutputSchemaGuard schemaGuard,
                             JsonSchemaValidator jsonValidator,
                             PromptTemplateEngine templateEngine,
                             RequestDeduplicator deduplicator,
                             CapabilityCallRepository callRepository) {
        this(catalog, invoker, dataContextHub, schemaGuard, jsonValidator,
                templateEngine, deduplicator, callRepository, 86400);
    }

    public CapabilityEngine(CapabilityCatalog catalog, CapabilityInvoker invoker,
                             DataContextHub dataContextHub, OutputSchemaGuard schemaGuard,
                             JsonSchemaValidator jsonValidator,
                             PromptTemplateEngine templateEngine,
                             RequestDeduplicator deduplicator,
                             CapabilityCallRepository callRepository,
                             long dedupTtlSeconds) {
        this.catalog = catalog;
        this.invoker = invoker;
        this.dataContextHub = dataContextHub;
        this.schemaGuard = schemaGuard;
        this.jsonValidator = jsonValidator;
        this.templateEngine = templateEngine;
        this.deduplicator = deduplicator;
        this.callRepository = callRepository;
        this.dedupTtlSeconds = dedupTtlSeconds;
    }

    /**
     * 同步执行能力
     * @param request
     * @return
     */
    public CapabilityResponse invoke(CapabilityRequest request) {
        long startTime = System.currentTimeMillis();
        String callId = UUID.randomUUID().toString().replace("-", "");
        // 记录是否已上报LLM执行结果，避免异常路径重复上报
        boolean outcomeRecorded = false;

        try {
          
            CapabilitySpec spec = resolveSpec(request.getCapability());

            // 数据上下文校验（required=true时必须提供上下文）
            if (spec.getContext() != null && spec.getContext().isRequired()
                    && (request.getDataContextRefs() == null || request.getDataContextRefs().isEmpty())) {
                CapabilityResponse contextResponse = CapabilityResponse.failure("数据上下文为必填项，请先推送数据上下文");
                contextResponse.setCallId(callId);
                return contextResponse;
            }

            // 入参校验：优先内联Schema内容（数据库创建的能力），回退classpath Schema文件
            if (request.getArguments() != null) {
                com.yangqiongai.ai.open.capability.guard.ValidationResult inputResult = null;
                if (spec.getInputSchemaContent() != null && !spec.getInputSchemaContent().isBlank()) {
                    inputResult = inputValidator.validate(request.getArguments(), spec.getInputSchemaContent());
                } else if (spec.getInputSchema() != null && !spec.getInputSchema().isBlank()) {
                    String schemaPath = "capabilities/" + spec.getCode() + "/" + spec.getInputSchema();
                    inputResult = jsonValidator.validate(request.getArguments(), schemaPath);
                }
                if (inputResult != null && !inputResult.isValid()) {
                    log.warn("入参校验失败: capability={}, errors={}", request.getCapability(), inputResult.getErrors());
                    CapabilityResponse inputResponse = CapabilityResponse.failure(
                            "入参校验失败: " + String.join("; ", inputResult.getErrors()));
                    inputResponse.setCallId(callId);
                    return inputResponse;
                }
            }

            // 执行模式：异步模式直接提交任务
            if (spec.getExecution() != null && "async".equals(spec.getExecution().getMode())) {
                String taskId = invokeAsync(request);
                CapabilityResponse response = CapabilityResponse.success("任务已提交", Map.of("taskId", taskId));
                response.setCallId(callId);
                return response;
            }

            // 去重检查
            if (request.getDedupKey() != null) {
                DedupKey key = new DedupKey(request.getDedupKey(), buildFingerprint(request));
                CapabilityResponse cached = deduplicator.check(key);
                if (cached != null) {
                    log.debug("命中去重缓存: capability={}, dedupKey={}", request.getCapability(), request.getDedupKey());
                    return cached;
                }
            }

            // 渲染Prompt模板
            String prompt = renderPrompt(spec, request);

            // 调用Agent引擎（支持重试）
            AgentResult agentResult = invokeWithRetry(spec, prompt, request);

            // 上报LLM执行结果
            if (promptResolver != null) {
                promptResolver.recordPromptOutcome("open.capability.default", callId,
                        agentResult.isSuccess(), System.currentTimeMillis() - startTime);
            }
            outcomeRecorded = true;

            // 处理结果
            String outputText = agentResult.getOutputAsText();
            CapabilityResponse response;

            if (agentResult.isSuccess()) {
                // 尝试JSON解析结构化输出
                Object structuredOutput = tryParseJson(outputText);
                // 强制结构化输出校验
                boolean forceStructured = spec.getContract() != null && spec.getContract().isForceStructured();
                if (forceStructured && structuredOutput == null) {
                    log.warn("强制结构化输出失败: capability={}, output不是有效JSON", request.getCapability());
                    response = CapabilityResponse.failure("输出不是有效的JSON格式");
                    response.setCallId(callId);
                    response.setDurationMillis(System.currentTimeMillis() - startTime);
                    recordCall(request, spec, response, startTime);
                    return response;
                }
                // 输出契约校验
                if (spec.getContract() != null && spec.getContract().isStrict()
                        && (spec.getOutputSchema() != null || spec.getOutputSchemaContent() != null)) {
                    try {
                        structuredOutput = schemaGuard.validate(structuredOutput != null
                                ? structuredOutput : outputText, spec);
                    } catch (Exception e) {
                        log.warn("输出契约校验失败: capability={}", request.getCapability(), e);
                        if (spec.getContract().isAutoRepair()) {
                            structuredOutput = schemaGuard.validateAndRepair(
                                    structuredOutput != null ? structuredOutput : outputText, spec, null);
                        }
                    }
                }
                response = CapabilityResponse.success(outputText, structuredOutput);
            } else {
                response = CapabilityResponse.failure(
                        agentResult.getErrorMessage() != null ? agentResult.getErrorMessage() : "Agent执行失败");
            }

            response.setCallId(callId);
            response.setDurationMillis(System.currentTimeMillis() - startTime);

            //去重缓存
            if (request.getDedupKey() != null) {
                DedupKey key = new DedupKey(request.getDedupKey(), buildFingerprint(request));
                deduplicator.cache(key, response, dedupTtlSeconds);
            }

            //记录调用追踪
            recordCall(request, spec, response, startTime);

            return response;

        } catch (CapabilityNotFoundException e) {
            CapabilityResponse response = CapabilityResponse.failure(e.getMessage());
            response.setCallId(callId);
            return response;
        } catch (Exception e) {
            log.error("能力执行异常: capability={}", request.getCapability(), e);
            // 异常路径上报LLM执行失败
            if (promptResolver != null && !outcomeRecorded) {
                promptResolver.recordPromptOutcome("open.capability.default", callId,
                        false, System.currentTimeMillis() - startTime);
            }
            CapabilityResponse response = CapabilityResponse.failure("能力执行异常: " + e.getMessage());
            response.setCallId(callId);
            response.setDurationMillis(System.currentTimeMillis() - startTime);
            return response;
        }
    }

    /**
     * 调用Agent引擎
     */
    private AgentResult invokeWithRetry(CapabilitySpec spec, String prompt, CapabilityRequest request) {
        int maxAttempts = 1;
        long backoffMillis = 0;
        if (spec.getExecution() != null && spec.getExecution().getRetry() != null) {
            maxAttempts = Math.max(1, spec.getExecution().getRetry().getMaxAttempts());
            backoffMillis = spec.getExecution().getRetry().getBackoffMillis();
        }
        // 解析超时时间
        long timeoutSeconds = 0;
        if (spec.getExecution() != null && spec.getExecution().getTimeout() != null) {
            timeoutSeconds = parseTimeout(spec.getExecution().getTimeout());
        }

        Exception lastException = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                AgentResult result = invoker.execute(spec, prompt, request, timeoutSeconds);
                if (result.isSuccess()) {
                    return result;
                }
                // 业务失败不重试
                return result;
            } catch (Exception e) {
                // 终态错误（计费/认证类等）重试无意义，直接抛出
                if (ErrorCategorizer.isTerminalModelError(e)) {
                    throw e;
                }
                lastException = e;
                if (attempt < maxAttempts - 1) {
                    long waitMs = backoffMillis * (attempt + 1);
                    log.warn("Agent调用失败，准备重试: attempt={}/{}, waitMs={}, error={}",
                            attempt + 1, maxAttempts, waitMs, e.getMessage());
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Agent调用失败，已重试" + maxAttempts + "次", lastException);
    }

    /**
     * 流式执行能力
     * @param request
     * @return
     */
    public Flux<StreamEvent> stream(CapabilityRequest request) {
        CapabilitySpec spec = resolveSpec(request.getCapability());
        validateRequest(spec, request);
        String prompt = renderPrompt(spec, request);
        return invoker.stream(spec, prompt, request);
    }

    /**
     * 异步执行能力
     * @param request
     * @return 任务ID
     */
    public String invokeAsync(CapabilityRequest request) {
        CapabilitySpec spec = resolveSpec(request.getCapability());
        validateRequest(spec, request);
        String prompt = renderPrompt(spec, request);
        return invoker.submitAsync(spec, prompt, request);
    }

    /**
     * 请求前置校验（数据上下文必填与入参Schema，同步/异步/流式共用）
     * @param spec
     * @param request
     */
    private void validateRequest(CapabilitySpec spec, CapabilityRequest request) {
        // 数据上下文校验（required=true时必须提供上下文）
        if (spec.getContext() != null && spec.getContext().isRequired()
                && (request.getDataContextRefs() == null || request.getDataContextRefs().isEmpty())) {
            throw new CapabilityExecutionException("数据上下文为必填项，请先推送数据上下文");
        }
        // 入参校验：优先内联Schema内容（数据库创建的能力），回退classpath Schema文件
        if (request.getArguments() != null) {
            com.yangqiongai.ai.open.capability.guard.ValidationResult inputResult = null;
            if (spec.getInputSchemaContent() != null && !spec.getInputSchemaContent().isBlank()) {
                inputResult = inputValidator.validate(request.getArguments(), spec.getInputSchemaContent());
            } else if (spec.getInputSchema() != null && !spec.getInputSchema().isBlank()) {
                String schemaPath = "capabilities/" + spec.getCode() + "/" + spec.getInputSchema();
                inputResult = jsonValidator.validate(request.getArguments(), schemaPath);
            }
            if (inputResult != null && !inputResult.isValid()) {
                throw new CapabilityExecutionException("入参校验失败: " + String.join("; ", inputResult.getErrors()));
            }
        }
    }

    /**
     * 查询异步任务状态
     * @param taskId
     * @return
     */
    public CapabilityResponse queryTask(String taskId) {
        Map<String, Object> taskResult = invoker.queryTask(taskId);
        if (taskResult == null) {
            return CapabilityResponse.failure("任务未找到: " + taskId);
        }
        String status = String.valueOf(taskResult.get("status"));
        if ("NOT_FOUND".equals(status)) {
            return CapabilityResponse.failure("任务未找到: " + taskId);
        }
        CapabilityResponse response = new CapabilityResponse();
        response.setCallId(taskId);
        response.setSuccess("SUCCEEDED".equals(status));
        if ("PENDING".equals(status) || "RUNNING".equals(status)) {
            // 未到终态时置失败并说明处理中，调用方以errorMessage区分后继续轮询
            response.setErrorMessage("任务处理中: " + status);
            return response;
        }
        Object outputText = taskResult.get("outputAsText");
        if (outputText != null && !outputText.toString().isBlank()) {
            response.setOutput(outputText.toString());
            response.setStructuredOutput(tryParseJson(outputText.toString()));
        }
        Object error = taskResult.get("errorMessage");
        if (error != null) {
            response.setErrorMessage(error.toString());
        }
        Object durationMs = taskResult.get("durationMs");
        if (durationMs instanceof Number number) {
            response.setDurationMillis(number.longValue());
        }
        Object tokenMetrics = taskResult.get("tokenMetrics");
        if (tokenMetrics != null) {
            response.setTokenMetrics(JSON_MAPPER.convertValue(tokenMetrics, Map.class));
        }
        return response;
    }

    private CapabilitySpec resolveSpec(String capability) {
        CapabilitySpec spec = catalog.get(capability);
        if (spec == null) {
            throw new CapabilityNotFoundException(capability);
        }
        return spec;
    }

    private String renderPrompt(CapabilitySpec spec, CapabilityRequest request) {
        // 加载模板文件
        String templateContent = loadTemplate(spec);
        // 构建变量
        Map<String, Object> variables = new HashMap<>();
        if (request.getArguments() != null) {
            variables.putAll(request.getArguments());
        }
        // 注入数据上下文（受maxContexts限制）
        String contextText = null;
        if (request.getDataContextRefs() != null && !request.getDataContextRefs().isEmpty()) {
            List<String> refs = request.getDataContextRefs();
            int maxContexts = spec.getContext() != null ? spec.getContext().getMaxContexts() : Integer.MAX_VALUE;
            if (refs.size() > maxContexts) {
                log.warn("数据上下文数量({})超过限制({})，已截断: capability={}",
                        refs.size(), maxContexts, spec.getCode());
                refs = refs.subList(0, maxContexts);
            }
            contextText = dataContextHub.renderToPrompt(refs);
            variables.put("dataContexts", contextText);
        }
        // 渲染模板
        String rendered = templateEngine.render(templateContent, variables);
        // 模板未配置${dataContexts}占位符时兜底追加，避免调用方推送的上下文静默丢失
        if (contextText != null && !contextText.isBlank() && !templateContent.contains("${dataContexts}")) {
            rendered = rendered + "\n\n【调用方数据上下文】\n" + contextText;
        }
        // 追加Schema补充描述（字段联动与检查等控制信息，为空时不追加）
        rendered = appendSchemaDescriptions(rendered, spec);
        return rendered;
    }

    /**
     * 追加输入/输出Schema补充描述（字段联动、检查规则等额外控制信息，供大模型遵循）
     * @param rendered
     * @param spec
     * @return
     */
    private String appendSchemaDescriptions(String rendered, CapabilitySpec spec) {
        String input = spec.getInputSchemaDescription();
        String output = spec.getOutputSchemaDescription();
        boolean hasInput = input != null && !input.isBlank();
        boolean hasOutput = output != null && !output.isBlank();
        if (!hasInput && !hasOutput) {
            return rendered;
        }
        StringBuilder notes = new StringBuilder(rendered);
        if (hasInput) {
            notes.append("\n\n【入参Schema补充说明（字段联动与检查）】\n").append(input.trim());
        }
        if (hasOutput) {
            notes.append("\n\n【输出Schema补充说明（字段联动与检查）】\n").append(output.trim());
        }
        return notes.toString();
    }

    private String loadTemplate(CapabilitySpec spec) {
        // 优先使用直接存储的模板内容（DB_CREATED或DB_OVERRIDE时数据库存储）
        if (spec.getPromptTemplateContent() != null) {
            return spec.getPromptTemplateContent();
        }
        // 从classpath加载模板文件
        String path = "capabilities/" + spec.getCode() + "/" + spec.getPromptTemplate();
        try {
            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                            .getResource("classpath:" + path);
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("加载Prompt模板失败: {}", path, e);
        }
        // 使用默认模板
        String defaultContent = CAPABILITY_DEFAULT_PROMPT.replace("${description}",
                spec.getDescription() != null ? spec.getDescription() : "");
        if (promptResolver == null) {
            return defaultContent;
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("description", spec.getDescription());
        return promptResolver.resolve("open.capability.default", defaultContent, variables);
    }

    private Object tryParseJson(String text) {
        if (text == null) {
            return null;
        }
        try {
            return JSON_MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String buildFingerprint(CapabilityRequest request) {
        try {
            return JSON_MAPPER.writeValueAsString(request.getArguments());
        } catch (JsonProcessingException e) {
            return String.valueOf(System.nanoTime());
        }
    }

    private void recordCall(CapabilityRequest request, CapabilitySpec spec,
                             CapabilityResponse response, long startTime) {
        try {
            CapabilityCallRecord record = new CapabilityCallRecord();
            record.setId(response.getCallId());
            record.setCapability(request.getCapability());
            record.setCaller(request.getCaller());
            record.setScopeId(request.getScopeId());
            record.setDedupKey(request.getDedupKey());
            record.setStatus(response.isSuccess() ? "success" : "failure");
            record.setDurationMillis(response.getDurationMillis());
            // 按审计配置控制日志记录内容
            boolean logInput = spec.getAudit() == null || spec.getAudit().isLogInput();
            boolean logOutput = spec.getAudit() == null || spec.getAudit().isLogOutput();
            if (logInput) {
                record.setInput(request.getArguments());
            }
            if (response.isSuccess() && logOutput) {
                record.setOutput(response.getOutput());
            } else {
                record.setErrorMessage(response.getErrorMessage());
            }
            record.setStartedAt(Instant.ofEpochMilli(startTime));
            record.setFinishedAt(Instant.now());
            if (spec.getAudit() != null && spec.getAudit().isEnabled()) {
                callRepository.save(record);
            }
        } catch (Exception e) {
            log.warn("记录调用追踪失败: capability={}", request.getCapability(), e);
        }
    }

    /**
     * 解析超时时间字符串（支持 120s / 5m / 1h 格式）
     * @param timeout
     * @return 秒数
     */
    private static long parseTimeout(String timeout) {
        if (timeout == null || timeout.isBlank()) {
            return 0;
        }
        String trimmed = timeout.trim().toLowerCase();
        try {
            if (trimmed.endsWith("h")) {
                return Long.parseLong(trimmed.replace("h", "")) * 3600;
            } else if (trimmed.endsWith("m")) {
                return Long.parseLong(trimmed.replace("m", "")) * 60;
            } else if (trimmed.endsWith("s")) {
                return Long.parseLong(trimmed.replace("s", ""));
            } else {
                return Long.parseLong(trimmed);
            }
        } catch (NumberFormatException e) {
            log.warn("解析超时时间失败: {}", timeout);
            return 0;
        }
    }
}