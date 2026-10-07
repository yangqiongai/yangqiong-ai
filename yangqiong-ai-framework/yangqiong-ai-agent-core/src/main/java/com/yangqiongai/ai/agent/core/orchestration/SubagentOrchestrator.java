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

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.session.ConversationBridge;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 子代理编排器（基于AgentEngine执行体系）
 * <p>
 * 支持声明式子代理注册、顺序/并行编排、agent_spawn生成子代理实例，
 * 内部通过AgentEngine.execute()统一调度子代理执行
 * </p>
 * @author yangqiong
 */
@Slf4j
@Service
public class SubagentOrchestrator {

    private final AgentEngine agentEngine;
    private final Map<String, SubagentDeclaration> declarations = new ConcurrentHashMap<>();
    private final ExecutorService parallelExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "subagent-parallel");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong spawnCounter = new AtomicLong(0);

    public SubagentOrchestrator(@Lazy AgentEngine agentEngine) {
        this.agentEngine = agentEngine;
    }

    @PreDestroy
    public void shutdown() {
        parallelExecutor.shutdown();
        try {
            if (!parallelExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                parallelExecutor.shutdownNow();
                log.warn("SubagentOrchestrator线程池强制关闭");
            }
        } catch (InterruptedException e) {
            parallelExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 声明子代理
     * @param name 子代理名称
     * @param description 子代理描述
     * @return 子代理声明
     */
    public SubagentDeclaration declareSubagent(String name, String description) {
        SubagentDeclaration declaration = SubagentDeclaration.builder()
                .name(name)
                .description(description)
                .build();
        declarations.put(name, declaration);
        log.info("声明子代理: name={}, description={}", name, description);
        return declaration;
    }

    /**
     * 声明子代理（完整声明）
     * <p>
     * 保留 systemPrompt/modelCode/temperature/maxIterations/tools 等扩展字段，
     * 供 sequential/parallel 模式的 spawnSubagent 查找使用。
     * </p>
     * @param declaration
     */
    public void declareSubagent(SubagentDeclaration declaration) {
        declarations.put(declaration.getName(), declaration);
        log.info("声明子代理: name={}, description={}", declaration.getName(), declaration.getDescription());
    }

    /**
     * 顺序执行子代理流水线
     * @param query 查询输入
     * @param pipeline 子代理声明列表
     * @param parentRequest 父级请求
     * @return 流水线执行结果
     */
    public SubagentSpawnResult executeSequential(String query, List<SubagentDeclaration> pipeline,
                                                 AgentRequest parentRequest) {
        if (pipeline == null || pipeline.isEmpty()) {
            log.warn("流水线为空，无法执行");
            return SubagentSpawnResult.failed("seq-0", "unknown", "流水线为空");
        }

        String pipelineId = "seq-" + spawnCounter.incrementAndGet();
        log.info("顺序执行流水线: pipelineId={}, stepCount={}", pipelineId, pipeline.size());

        Map<String, Object> pipelineData = new HashMap<>();
        pipelineData.put("query", query);
        String lastResult = "";

        for (int i = 0; i < pipeline.size(); i++) {
            SubagentDeclaration decl = pipeline.get(i);
            Map<String, Object> params = new HashMap<>(pipelineData);
            params.put("input", lastResult.isEmpty() ? query : lastResult);

            SubagentSpawnResult stepResult = spawnSubagent(decl, params,
                    Duration.ofSeconds(60), parentRequest, "sequential");
            if (!stepResult.isSuccess()) {
                log.error("流水线步骤执行失败: name={}, error={}", decl.getName(), stepResult.getError());
                return SubagentSpawnResult.failed(pipelineId, decl.getName(), stepResult.getError());
            }

            lastResult = stepResult.getResult();
            pipelineData.put("step_" + i + "_result", lastResult);
            log.info("流水线步骤完成: name={}", decl.getName());
        }

        return SubagentSpawnResult.sync(pipelineId, pipeline.get(pipeline.size() - 1).getName(), lastResult);
    }

    /**
     * 并行执行子代理
     * @param query 查询输入
     * @param agents 子代理声明列表
     * @param parentRequest 父级请求
     * @return 并行执行结果列表
     */
    public List<SubagentSpawnResult> executeParallel(String query, List<SubagentDeclaration> agents,
                                                     AgentRequest parentRequest) {
        if (agents == null || agents.isEmpty()) {
            log.warn("并行代理列表为空");
            return Collections.emptyList();
        }

        log.info("并行执行代理: agentCount={}", agents.size());

        List<CompletableFuture<SubagentSpawnResult>> futures = agents.stream()
                .map(decl -> CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> params = Map.of("input", query);
                    return spawnSubagent(decl, params, Duration.ofSeconds(60),
                            parentRequest, "parallel");
                }, parallelExecutor))
                .collect(Collectors.toList());

        List<SubagentSpawnResult> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            String agentName = agents.get(i).getName();
            try {
                results.add(futures.get(i).get(120, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("并行执行代理异常: agentName={}", agentName, e);
                results.add(SubagentSpawnResult.failed("parallel-err", agentName, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * 生成子代理实例
     * @param name 子代理名称
     * @param params 参数
     * @param timeout 超时时间
     * @param parentRequest 父级请求
     * @param orchestrationMode 编排模式
     * @return 生成结果
     */
    public SubagentSpawnResult spawnSubagent(String name, Map<String, Object> params,
                                             Duration timeout, AgentRequest parentRequest,
                                             String orchestrationMode) {
        SubagentDeclaration declaration = declarations.get(name);
        if (declaration == null) {
            throw new IllegalArgumentException("未声明的子代理: " + name);
        }

        String spawnId = name + "-" + spawnCounter.incrementAndGet();
        log.info("生成子代理: name={}, spawnId={}, timeout={}", name, spawnId, timeout);

        // 后台模式（timeout = 0）
        if (timeout != null && timeout.isZero()) {
            return spawnBackground(spawnId, declaration, params, parentRequest, orchestrationMode);
        }

        // 同步模式（timeout > 0）
        return spawnSync(spawnId, declaration, params, parentRequest, orchestrationMode);
    }

    /**
     * 生成子代理实例（直接使用声明对象，不查共享map）
     * <p>
     * 供 executeSequential/executeParallel 使用，避免并发请求同名声明的互相覆盖。
     * </p>
     * @param declaration 子代理声明
     * @param params 参数
     * @param timeout 超时时间
     * @param parentRequest 父级请求
     * @param orchestrationMode 编排模式
     * @return 生成结果
     */
    public SubagentSpawnResult spawnSubagent(SubagentDeclaration declaration,
                                             Map<String, Object> params,
                                             Duration timeout, AgentRequest parentRequest,
                                             String orchestrationMode) {
        if (declaration == null) {
            throw new IllegalArgumentException("子代理声明不能为空");
        }
        String spawnId = declaration.getName() + "-" + spawnCounter.incrementAndGet();
        log.info("生成子代理: name={}, spawnId={}, timeout={}", declaration.getName(), spawnId, timeout);
        if (timeout != null && timeout.isZero()) {
            return spawnBackground(spawnId, declaration, params, parentRequest, orchestrationMode);
        }
        return spawnSync(spawnId, declaration, params, parentRequest, orchestrationMode);
    }

    /**
     * 列出已声明的子代理
     * @return 子代理声明列表
     */
    public List<SubagentDeclaration> listSubagents() {
        return new ArrayList<>(declarations.values());
    }

    /**
     * 同步生成子代理
     * @param spawnId 生成ID
     * @param declaration 子代理声明
     * @param params 参数
     * @param parentRequest 父级请求
     * @param orchestrationMode 编排模式
     * @return 生成结果
     */
    private SubagentSpawnResult spawnSync(String spawnId, SubagentDeclaration declaration,
                                           Map<String, Object> params, AgentRequest parentRequest,
                                           String orchestrationMode) {
        try {
            AgentRequest request = buildSubagentRequest(declaration, params, parentRequest, orchestrationMode);
            AgentResult result = agentEngine.run(request);

            String output = result != null && result.getOutputAsText() != null
                    ? result.getOutputAsText() : "";
            log.info("子代理同步执行完成: spawnId={}", spawnId);
            return SubagentSpawnResult.sync(spawnId, declaration.getName(), output);
        } catch (Exception e) {
            log.error("子代理同步执行异常: spawnId={}", spawnId, e);
            return SubagentSpawnResult.failed(spawnId, declaration.getName(), e.getMessage());
        }
    }

    /**
     * 后台生成子代理
     * @param spawnId 生成ID
     * @param declaration 子代理声明
     * @param params 参数
     * @param parentRequest 父级请求
     * @param orchestrationMode 编排模式
     * @return 生成结果
     */
    private SubagentSpawnResult spawnBackground(String spawnId, SubagentDeclaration declaration,
                                                 Map<String, Object> params, AgentRequest parentRequest,
                                                 String orchestrationMode) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                AgentRequest request = buildSubagentRequest(declaration, params, parentRequest, orchestrationMode);
                AgentResult result = agentEngine.run(request);

                String output = result != null && result.getOutputAsText() != null
                        ? result.getOutputAsText() : "";
                log.info("子代理后台执行完成: spawnId={}", spawnId);
                return output;
            } catch (Exception e) {
                log.error("子代理后台执行异常: spawnId={}", spawnId, e);
                return "ERROR: " + e.getMessage();
            }
        }, parallelExecutor);

        future.thenAccept(result -> {
            log.info("子代理后台任务完成通知: spawnId={}, resultLength={}", spawnId, result.length());
        });

        log.info("子代理后台任务已提交: spawnId={}", spawnId);
        return SubagentSpawnResult.background(spawnId, declaration.getName(), future);
    }

    /**
     * 构造子代理的AgentRequest
     * <p>
     * 优先使用声明中的 systemPrompt/temperature/maxIterations 等扩展字段，
     * 未配置时使用默认值。
     * </p>
     */
    private AgentRequest buildSubagentRequest(SubagentDeclaration decl,
            Map<String, Object> params, AgentRequest parentRequest, String orchestrationMode) {
        String input = params != null && params.containsKey("input")
                ? String.valueOf(params.get("input")) : "";

        Map<String, Object> body = new HashMap<>();
        // 优先使用声明中的 systemPrompt，否则使用描述拼接
        String sysPrompt = decl.getSystemPrompt() != null && !decl.getSystemPrompt().isBlank()
                ? decl.getSystemPrompt()
                : "你是" + decl.getDescription() + "。请根据输入完成你的专业任务，输出结果。默认使用中文回答。";
        body.put(AgentRequest.BodyKeys.SYSTEM_PROMPT, sysPrompt);
        if (decl.getModelCode() != null) {
            body.put(AgentRequest.BodyKeys.MODEL_CODE, decl.getModelCode());
        }
        if (decl.getTemperature() != null) {
            body.put("temperature", decl.getTemperature());
        }
        if (decl.getMaxIterations() != null) {
            body.put("maxIterations", decl.getMaxIterations());
        }

        // 继承父任务的追踪信息（子代理场景关联父任务）
        String parentTaskId = (String) parentRequest.getBody().get(AgentRequest.BodyKeys.TASK_ID);
        String parentPath = (String) parentRequest.getBody().getOrDefault(AgentRequest.BodyKeys.AGENT_PATH, "main");
        String childPath = parentPath + ">" + decl.getName();
        if (parentTaskId != null) {
            body.put(AgentRequest.BodyKeys.PARENT_TASK_ID, parentTaskId);
        }
        body.put(AgentRequest.BodyKeys.AGENT_PATH, childPath);

        // 会话控制：根据编排模式设置
        Map<String, Object> sessionConfig = resolveSessionConfig(orchestrationMode);
        body.put(AgentRequest.BodyKeys.SESSION_CONFIG, sessionConfig);

        return new AgentRequest()
                .agentCode(decl.getAgentCode())
                .sessionId(parentRequest.getSessionId())
                .input(input)
                .userId(parentRequest.getUserId())
                .body(body);
    }

    /**
     * 根据编排模式解析会话配置
     */
    private Map<String, Object> resolveSessionConfig(String orchestrationMode) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConversationBridge.ATTR_SESSION_ENABLED, true);
        config.put(ConversationBridge.ATTR_SESSION_NAMESPACE, "subagent");

        if ("parallel".equals(orchestrationMode)) {
            config.put(ConversationBridge.ATTR_SESSION_RESTORE_ENABLED, false);
            config.put(ConversationBridge.ATTR_SESSION_PERSIST_USER_INPUT, false);
            config.put(ConversationBridge.ATTR_SESSION_ENSURE_ENABLED, false);
        } else {
            // sequential 模式：恢复历史（看到前序结果），持久化用户输入
            config.put(ConversationBridge.ATTR_SESSION_RESTORE_ENABLED, true);
            config.put(ConversationBridge.ATTR_SESSION_PERSIST_USER_INPUT, true);
            config.put(ConversationBridge.ATTR_SESSION_ENSURE_ENABLED, false);
        }
        return config;
    }
}
