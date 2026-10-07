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
package com.yangqiongai.ai.open.capability.controller;

import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.context.DataContext;
import com.yangqiongai.ai.open.capability.context.DataContextHub;
import com.yangqiongai.ai.open.capability.engine.CapabilityEngine;
import com.yangqiongai.ai.open.capability.engine.CapabilityRequest;
import com.yangqiongai.ai.open.capability.engine.CapabilityResponse;
import com.yangqiongai.ai.open.capability.ingest.CapabilityDocumentIngestPort;
import com.yangqiongai.ai.open.capability.spec.AgentOverrides;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.common.bean.ApiResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 开放能力API入口
 * <p>
 * 对外暴露 /open/v1 统一入口，提供能力查询、数据上下文推送、能力调用等接口。
 * </p>
 * @author yangqiong
 */
@RestController
@RequestMapping("/open/v1")
public class OpenCapabilityController {

    private final CapabilityCatalog catalog;
    private final DataContextHub dataContextHub;
    private final CapabilityEngine engine;
    private final ObjectProvider<CapabilityDocumentIngestPort> ingestPortProvider;

    /**
     * 对外视图转换器（NON_NULL：剥离内部字段后不输出null字段名）
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper SPEC_VIEW_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

    /**
     * 提示词模板引擎内置变量（非调用方入参，提取argumentHints时排除）
     */
    private static final java.util.Set<String> TEMPLATE_BUILTIN_VARIABLES =
            java.util.Set.of("dataContexts", "description");

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "open-capability-sse");
        thread.setDaemon(true);
        return thread;
    });

    public OpenCapabilityController(CapabilityCatalog catalog,
                                     DataContextHub dataContextHub,
                                     CapabilityEngine engine,
                                     ObjectProvider<CapabilityDocumentIngestPort> ingestPortProvider) {
        this.catalog = catalog;
        this.dataContextHub = dataContextHub;
        this.engine = engine;
        this.ingestPortProvider = ingestPortProvider;
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }

    /**
     * 查询所有能力
     * @return
     */
    @GetMapping("/capabilities")
    public ApiResult<Map<String, Object>> listCapabilities() {
        List<Map<String, Object>> items = catalog.list().stream().map(this::toPublicView).toList();
        return ApiResult.ok(Map.of("items", items, "total", items.size()));
    }

    /**
     * 查询能力详情
     * @param code
     * @return
     */
    @GetMapping("/capabilities/{code}")
    public ApiResult<Map<String, Object>> getCapability(@PathVariable String code) {
        CapabilitySpec spec = catalog.get(code);
        if (spec == null) {
            return ApiResult.fail("能力不存在或已禁用: " + code);
        }
        return ApiResult.ok(toPublicView(spec));
    }

    /**
     * 转换为对外视图（深拷贝剥离内部编排信息，避免污染注册表对象）
     * <p>
     * 剥离Agent编码/运行时覆盖/执行配置/输出契约/上下文配置/审计配置/提示词模板/Schema文件名等内部字段；
     * 保留能力目录标识与argumentHints入参提示（调用方组装arguments的唯一依据）。
     * </p>
     * @param spec
     * @return
     */
    private Map<String, Object> toPublicView(CapabilitySpec spec) {
        CapabilitySpec view = SPEC_VIEW_MAPPER.convertValue(spec, CapabilitySpec.class);
        view.setAgentCode(null);
        view.setAgentOverrides(null);
        view.setExecution(null);
        view.setContract(null);
        view.setContext(null);
        view.setAudit(null);
        view.setPromptTemplate(null);
        view.setPromptTemplateContent(null);
        view.setInputSchema(null);
        view.setOutputSchema(null);
        view.setInputSchemaDescription(null);
        view.setOutputSchemaDescription(null);
        Map<String, Object> result = SPEC_VIEW_MAPPER.convertValue(view, Map.class);
        result.put("argumentHints", extractArgumentHints(spec));
        result.put("endpoints", buildEndpoints(spec));
        return result;
    }

    /**
     * 构建该能力当前支持的调用端点列表（调用方拼接服务地址即可调用，无需硬编码路径）
     * <p>
     * 企业版可覆盖本方法为工作流执行体追加异步/流式等增强端点。
     * </p>
     * @param spec
     * @return 每项含mode/method/path/description
     */
    protected List<Map<String, Object>> buildEndpoints(CapabilitySpec spec) {
        List<Map<String, Object>> endpoints = new java.util.ArrayList<>();
        if ("WORKFLOW".equals(spec.getExecType())) {
            // 工作流执行体为企业版能力，社区版不可调用
            endpoints.add(endpoint("sync", "POST", "/open/v1/run", "同步调用（工作流执行体为企业版能力，社区版不可调用）"));
            return endpoints;
        }
        endpoints.add(endpoint("sync", "POST", "/open/v1/run", "同步调用，返回完整结果"));
        endpoints.add(endpoint("async", "POST", "/open/v1/run/async", "异步提交，返回taskId"));
        endpoints.add(endpoint("query", "GET", "/open/v1/tasks/{taskId}", "查询异步任务结果，{taskId}替换为提交返回的任务ID"));
        endpoints.add(endpoint("stream", "POST", "/open/v1/run/stream", "SSE流式调用，推送文本增量"));
        return endpoints;
    }

    /**
     * 构建单个端点描述
     * @param mode
     * @param method
     * @param path
     * @param description
     * @return
     */
    protected Map<String, Object> endpoint(String mode, String method, String path, String description) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("mode", mode);
        item.put("method", method);
        item.put("path", path);
        item.put("description", description);
        return item;
    }

    /**
     * 提取能力入参提示：入参Schema参数与提示词模板${占位符}的合集
     * <p>
     * Schema参数在前（含必填/说明），模板中Schema未覆盖的占位符补充在后（required=false）；
     * 排除引擎内置变量，不暴露提示词内容。
     * </p>
     * @param spec
     * @return 每项含name/required/description（description可能为null）
     */
    private List<Map<String, Object>> extractArgumentHints(CapabilitySpec spec) {
        List<Map<String, Object>> hints = new java.util.ArrayList<>();
        java.util.Set<String> known = new java.util.HashSet<>();
        if (spec.getInputSchemaContent() != null && !spec.getInputSchemaContent().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode schema =
                        SPEC_VIEW_MAPPER.readTree(spec.getInputSchemaContent());
                com.fasterxml.jackson.databind.JsonNode properties = schema.get("properties");
                java.util.Set<String> required = new java.util.HashSet<>();
                if (schema.get("required") != null && schema.get("required").isArray()) {
                    schema.get("required").forEach(n -> required.add(n.asText()));
                }
                if (properties != null) {
                    properties.fieldNames().forEachRemaining(name -> {
                        known.add(name);
                        Map<String, Object> hint = new java.util.LinkedHashMap<>();
                        hint.put("name", name);
                        hint.put("required", required.contains(name));
                        com.fasterxml.jackson.databind.JsonNode desc = properties.get(name).get("description");
                        hint.put("description", desc != null ? desc.asText() : null);
                        hints.add(hint);
                    });
                }
            } catch (Exception e) {
                // 入参提示为辅助信息，Schema解析失败时回退模板提取
            }
        }
        // 模板占位符补充Schema未覆盖的参数名（无Schema时即为全部参数来源）
        String template = spec.getPromptTemplateContent();
        if (template != null && !template.isBlank()) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}").matcher(template);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!TEMPLATE_BUILTIN_VARIABLES.contains(name) && known.add(name)) {
                    Map<String, Object> hint = new java.util.LinkedHashMap<>();
                    hint.put("name", name);
                    hint.put("required", false);
                    hint.put("description", null);
                    hints.add(hint);
                }
            }
        }
        return hints;
    }

    /**
     * 上传文件入能力配置的文件上传库（与invoke统一的body传参风格；uploadKbCode未配置时不支持上传）
     * @param capability
     * @param file
     * @return
     */
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Map<String, Object>> uploadCapabilityFile(@RequestParam("capability") String capability,
                                                               @RequestParam("file") MultipartFile file) {
        CapabilitySpec spec = catalog.get(capability);
        if (spec == null) {
            return ApiResult.fail("能力不存在或已禁用: " + capability);
        }
        String uploadKbCode = resolveUploadKbCode(spec);
        if (uploadKbCode == null) {
            return ApiResult.fail("该能力未启用文件上传，请先在能力配置中指定文件上传库");
        }
        CapabilityDocumentIngestPort ingestPort = ingestPortProvider.getIfAvailable();
        if (ingestPort == null) {
            return ApiResult.fail("文件入库组件未装配");
        }
        if (file == null || file.isEmpty()) {
            return ApiResult.fail("文件不能为空");
        }
        try {
            CapabilityDocumentIngestPort.IngestResult result = ingestPort.ingest(
                    uploadKbCode, file.getOriginalFilename(), file.getBytes(), file.getContentType());
            return ApiResult.ok(Map.of(
                    "docId", result.getDocId() != null ? result.getDocId() : "",
                    "kbId", result.getKbId() != null ? result.getKbId() : uploadKbCode,
                    "docStatus", result.getDocStatus() != null ? result.getDocStatus() : "",
                    "capability", capability));
        } catch (Exception e) {
            return ApiResult.fail("文件入库失败: " + e.getMessage());
        }
    }

    /**
     * 解析能力配置的文件上传库
     * @param spec
     * @return 未配置时返回null
     */
    private String resolveUploadKbCode(CapabilitySpec spec) {
        AgentOverrides overrides = spec.getAgentOverrides();
        if (overrides == null || overrides.getKnowledgeBase() == null) {
            return null;
        }
        String uploadKbCode = overrides.getKnowledgeBase().getUploadKbCode();
        return uploadKbCode != null && !uploadKbCode.isBlank() ? uploadKbCode : null;
    }

    /**
     * 推送数据上下文
     * @param context
     * @return
     */
    @PostMapping("/context")
    public ApiResult<Map<String, String>> pushDataContext(@RequestBody DataContext context) {
        String ref = dataContextHub.push(context);
        return ApiResult.ok(Map.of("ref", ref));
    }

    /**
     * 批量推送数据上下文
     * @param contexts
     * @return
     */
    @PostMapping("/context/batch")
    public ApiResult<Map<String, Object>> pushDataContextBatch(@RequestBody List<DataContext> contexts) {
        List<String> refs = dataContextHub.pushBatch(contexts);
        return ApiResult.ok(Map.of("refs", refs));
    }

    /**
     * 查询数据上下文
     * @param ref
     * @return
     */
    @GetMapping("/context/{ref}")
    public ApiResult<DataContext> getDataContext(@PathVariable String ref) {
        DataContext context = dataContextHub.get(ref);
        if (context == null) {
            return ApiResult.fail("数据上下文不存在或已过期: " + ref);
        }
        return ApiResult.ok(context);
    }

    /**
     * 同步调用能力
     * @param request
     * @return
     */
    @PostMapping("/run")
    public ApiResult<CapabilityResponse> invoke(@RequestBody CapabilityRequest request) {
        CapabilityResponse response = engine.invoke(request);
        if (!response.isSuccess()) {
            // 失败时保留callId供调用方对账留痕记录
            return ApiResult.fail(response.getCallId() != null
                    ? "[callId=" + response.getCallId() + "] " + response.getErrorMessage()
                    : response.getErrorMessage());
        }
        return ApiResult.ok(response);
    }

    /**
     * 流式调用能力
     * @param request
     * @return
     */
    @PostMapping(value = "/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter invokeStream(@RequestBody CapabilityRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Flux<StreamEvent> flux = engine.stream(request);
            executor.execute(() -> {
                try {
                    flux.subscribe(
                            event -> {
                                try {
                                    emitter.send(event);
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                }
                            },
                            emitter::completeWithError,
                            emitter::complete
                    );
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
        } catch (Exception e) {
            // 前置校验失败（能力不存在/入参非法等）以错误事件通知调用方
            emitter.completeWithError(new IllegalStateException(e.getMessage(), e));
        }
        return emitter;
    }

    /**
     * 异步调用能力
     * @param request
     * @return
     */
    @PostMapping("/run/async")
    public ApiResult<Map<String, String>> invokeAsync(@RequestBody CapabilityRequest request) {
        try {
            String taskId = engine.invokeAsync(request);
            return ApiResult.ok(Map.of("taskId", taskId));
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 查询异步任务状态
     * @param taskId
     * @return
     */
    @GetMapping("/tasks/{taskId}")
    public ApiResult<CapabilityResponse> queryTask(@PathVariable String taskId) {
        CapabilityResponse response = engine.queryTask(taskId);
        if (!response.isSuccess()) {
            return ApiResult.fail(response.getErrorMessage());
        }
        return ApiResult.ok(response);
    }
}