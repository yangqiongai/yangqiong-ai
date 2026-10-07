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
package com.yangqiongai.ai.platform.api.agent;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.provider.PreferenceRecorderProvider;
import com.yangqiongai.ai.agent.core.processor.AgentProcessor;
import com.yangqiongai.ai.agent.core.session.AgentSessionStore;
import com.yangqiongai.ai.agent.core.stream.InterruptControlRegistry;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.platform.bss.sse.SseStreamHelper;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.common.scope.PlanLimitGuard;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.bss.security.annotation.IgnoreSecurityCheckEntity;
import com.yangqiongai.ai.platform.api.runtime.AgentRunSubmitter;
import com.yangqiongai.ai.platform.api.scheduling.AgentTaskEnqueuer;
import com.yangqiongai.ai.platform.api.scheduling.QueueProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent通用对话接口
 *
 * @author yangqiong
 */
@Tag(name = "Agent通用对话接口")
@RestController
@RequestMapping("/api/agent/chat")
@IgnoreSecurityCheckEntity
public class AgentChatController {

    @Autowired
    private AgentEngine agentEngine;

    @Autowired
    private AgentManager agentService;

    @Autowired
    private ObjectProvider<AgentSessionStore> sessionStoreProvider;

    /**
     * 套餐数量限制守卫
     */
    @Autowired
    private PlanLimitGuard planLimitGuard;

    @Autowired
    private InterruptControlRegistry interruptControlRegistry;

    /**
     * 异步运行提交器（分布式运行时未启用时无bean）
     */
    @Autowired
    private ObjectProvider<AgentRunSubmitter> runSubmitterProvider;

    /**
     * 任务入队器（队列模式 ai.agent.queue.mode=queue 时启用）
     */
    @Autowired
    private AgentTaskEnqueuer agentTaskEnqueuer;

    @Autowired
    private QueueProperties queueProperties;

    /**
     * 用户偏好记录器（可选依赖，ai-memory 模块未启用时为 null）
     */
    @Autowired(required = false)
    private PreferenceRecorderProvider preferenceRecorder;

    /**
     * 同步对话
     * @param request
     * @return
     */
    @Operation(summary = "同步对话", description = "发送消息并等待完整响应，agentCode默认为default")
    @PostMapping("/execute")
    public ApiResult<AgentResult> execute(
            @Parameter(name = "request", description = "Agent对话请求") @RequestBody AgentRequest request) {
        ensureDefaults(request);
        checkSessionLimit(request);
        setupApprovalContext(request);
        recordPreferencesIfPresent(request);
        try {
            return ApiResult.ok(agentEngine.run(request));
        } finally {
            SessionContext.clear();
        }
    }

    /**
     * 流式对话
     * @param request
     * @return
     */
    @Operation(summary = "流式对话", description = "发送消息并以SSE流式返回响应，agentCode默认为default")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(name = "request", description = "Agent对话请求") @RequestBody AgentRequest request) {
        ensureDefaults(request);
        checkSessionLimit(request);
        setupApprovalContext(request);
        recordPreferencesIfPresent(request);
        SseEmitter emitter = new SseEmitter(300000L);
        Flux<StreamEvent> eventFlux = agentEngine.stream(request);
        SseStreamHelper.streamEventsToResponse(emitter, eventFlux);
        emitter.onCompletion(SessionContext::clear);
        emitter.onTimeout(SessionContext::clear);
        return emitter;
    }

    /**
     * 恢复澄清续跑
     * @param request
     * @return
     */
    @Operation(summary = "恢复澄清续跑", description = "提交用户对AI提问的答案，SSE流式返回续跑响应")
    @PostMapping(value = "/clarification", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter clarification(
            @Parameter(name = "request", description = "澄清恢复请求") @RequestBody ClarificationRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        Flux<StreamEvent> eventFlux = agentEngine.resumeClarification(
                request.getSessionId(), request.getToolCallId(), request.getAnswer());
        SseStreamHelper.streamEventsToResponse(emitter, eventFlux);
        emitter.onCompletion(SessionContext::clear);
        emitter.onTimeout(SessionContext::clear);
        return emitter;
    }

    /**
     * 恢复引擎确认续跑
     * @param request
     * @return
     */
    @Operation(summary = "恢复引擎确认续跑", description = "提交用户对工具调用的批准/拒绝决策，SSE流式返回续跑响应")
    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter confirm(
            @Parameter(name = "request", description = "引擎确认恢复请求") @RequestBody ConfirmRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);
        Flux<StreamEvent> eventFlux = agentEngine.resumeConfirm(
                request.getSessionId(), request.isApproved(), request.getOperator(), request.getReason());
        SseStreamHelper.streamEventsToResponse(emitter, eventFlux);
        emitter.onCompletion(SessionContext::clear);
        emitter.onTimeout(SessionContext::clear);
        return emitter;
    }

    /**
     * 提交异步对话任务
     * @param request
     * @return
     */
    @Operation(summary = "提交异步对话任务", description = "提交后返回taskId，通过查询接口轮询结果")
    @PostMapping("/submit")
    public ApiResult<Map<String, String>> submit(
            @Parameter(name = "request", description = "Agent对话请求") @RequestBody AgentRequest request) {
        ensureDefaults(request);
        checkSessionLimit(request);
        // 队列模式：写QUEUED由调度器抢占执行（背压超限抛出明确错误）
        if (queueProperties.isEnabled() && queueProperties.isQueueMode()) {
            String taskId = agentTaskEnqueuer.enqueue(request);
            return ApiResult.ok(Map.of("taskId", taskId, "queued", "true"));
        }
        AgentRunSubmitter runSubmitter = runSubmitterProvider.getIfAvailable();
        // 分布式运行时启用时走SPI提交链路（预落库+抢锁+本地执行），否则保持原行为
        if (runSubmitter != null) {
            return ApiResult.ok(runSubmitter.submitAsync(request));
        }
        String taskId = agentEngine.submitTask(request);
        return ApiResult.ok(Map.of("taskId", taskId));
    }

    /**
     * 查询异步任务状态
     * @param taskId
     * @return
     */
    @Operation(summary = "查询异步任务状态")
    @GetMapping("/task/{taskId}")
    public ApiResult<Map<String, Object>> queryTask(
            @Parameter(name = "taskId", description = "任务ID") @PathVariable String taskId) {
        return ApiResult.ok(agentEngine.queryTask(taskId));
    }

    /**
     * 取消异步任务
     * @param taskId
     * @return
     */
    @Operation(summary = "取消异步任务", description = "仅PENDING/RUNNING状态的任务可取消")
    @DeleteMapping("/task/{taskId}")
    public ApiResult<Map<String, String>> cancelTask(
            @Parameter(name = "taskId", description = "任务ID") @PathVariable String taskId) {
        boolean cancelled = agentEngine.cancelTask(taskId);
        if (cancelled) {
            return ApiResult.ok(Map.of("taskId", taskId, "status", "cancelled"));
        }
        return ApiResult.fail("任务不存在或已处于终态，无法取消: " + taskId);
    }

    /**
     * 查询已注册的AgentProcessor列表
     * @return
     */
    @Operation(summary = "查询已注册的AgentProcessor", description = "返回当前可用的agentCode列表")
    @GetMapping("/processors")
    public ApiResult<Object> listProcessors() {
        Object data = agentService.listEnabled().stream()
                .map(a -> {
                    Map<String, String> item = new HashMap<>();
                    item.put("agentCode", a.getAgentCode());
                    item.put("agentName", a.getAgentName());
                    item.put("category", a.getCategory() != null ? a.getCategory() : "");
                    item.put("sessionType", a.getSessionType() != null ? a.getSessionType() : "");
                    return item;
                })
                .toList();
        return ApiResult.ok(data);
    }

    /**
     * 健康检查
     * @return
     */
    @Operation(summary = "Agent健康检查", description = "验证Agent引擎是否可用")
    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("engine", agentEngine.getClass().getSimpleName());
        return ApiResult.ok(result);
    }

    /**
     * 关闭会话
     * @param sessionId
     */
    @Operation(summary = "关闭会话", description = "将会话状态设为已结束(0)")
    @PutMapping("/session/{sessionId}/close")
    public ApiResult<Map<String, String>> closeSession(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        AgentSessionStore store = sessionStoreProvider.getIfAvailable();
        if (store != null) {
            store.closeSession(sessionId);
        }
        return ApiResult.ok(Map.of("sessionId", sessionId, "status", "closed"));
    }

    /**
     * 中断流式会话
     * @param sessionId
     * @return
     */
    @Operation(summary = "中断流式会话", description = "向正在流式执行的Agent发送中断信号")
    @PostMapping("/session/{sessionId}/interrupt")
    public ApiResult<Map<String, String>> interruptSession(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        boolean triggered = interruptControlRegistry.interrupt(sessionId, "用户主动中断");
        if (triggered) {
            return ApiResult.ok(Map.of("sessionId", sessionId, "status", "interrupted"));
        }
        return ApiResult.fail("未找到活跃的流式会话或会话已结束: " + sessionId);
    }

    /**
     * 直接大模型同步对话
     * @param request
     * @return
     */
    @Operation(summary = "直接大模型同步对话", description = "直接调用大模型，不走Agent循环，支持body中传kbIds实现RAG检索增强")
    @PostMapping("/direct-execute")
    public ApiResult<AgentResult> directExecute(
            @Parameter(name = "request", description = "Agent对话请求") @RequestBody AgentRequest request) {
        request.setAgentCode(AgentProcessor.DIRECT_LLM);
        ensureDirectDefaults(request);
        checkSessionLimit(request);
        setupApprovalContext(request);
        recordPreferencesIfPresent(request);
        try {
            return ApiResult.ok(agentEngine.run(request));
        } finally {
            SessionContext.clear();
        }
    }

    /**
     * 直接大模型流式对话
     * @param request
     * @return
     */
    @Operation(summary = "直接大模型流式对话", description = "直接调用大模型并以SSE流式返回，支持body中传kbIds实现RAG检索增强")
    @PostMapping(value = "/direct-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter directStream(
            @Parameter(name = "request", description = "Agent对话请求") @RequestBody AgentRequest request) {
        request.setAgentCode(AgentProcessor.DIRECT_LLM);
        ensureDirectDefaults(request);
        checkSessionLimit(request);
        setupApprovalContext(request);
        recordPreferencesIfPresent(request);
        SseEmitter emitter = new SseEmitter(300000L);
        Flux<StreamEvent> eventFlux = agentEngine.stream(request);
        SseStreamHelper.streamEventsToResponse(emitter, eventFlux);
        emitter.onCompletion(SessionContext::clear);
        emitter.onTimeout(SessionContext::clear);
        return emitter;
    }

    /**
     * 校验套餐并发会话上限并登记活跃会话
     * @param request
     * @return
     */
    private void checkSessionLimit(AgentRequest request) {
        planLimitGuard.enterSession(request.getSessionId());
    }

    /**
     * 确保请求必要字段有默认值
     * @param request
     */
    private void ensureDefaults(AgentRequest request) {
        if (request.getAgentCode() == null || request.getAgentCode().isBlank()) {
            request.setAgentCode(AgentProcessor.DEFAULT_AGENT);
        }
        if (request.getInput() == null || request.getInput().isEmpty()) {
            request.setInput("你好");
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            request.setSessionId("test-session-" + System.currentTimeMillis());
        }
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            request.setUserId("system");
        }
    }

    /**
     * 确保直接大模型请求必要字段有默认值
     * @param request
     */
    private void ensureDirectDefaults(AgentRequest request) {
        if (request.getInput() == null || request.getInput().isEmpty()) {
            request.setInput("你好");
        }
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            request.setSessionId("direct-session-" + System.currentTimeMillis());
        }
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            request.setUserId("system");
        }
    }

    /**
     * 设置工具审批上下文，供 @Suspendable AOP拦截器使用
     * @param request
     */
    private void setupApprovalContext(AgentRequest request) {
        SessionContext.setSessionId(request.getSessionId());
        SessionContext.setUserId(request.getUserId());
        SessionContext.setScopeId(ScopeContext.getScopeId());
        // 将scope随请求携带，异步/流式线程持久化与计量上报依赖此值
        request.setScopeId(ScopeContext.getScopeId());
    }

    /**
     * 识别并记录用户显式声明的偏好（口令式 + 请求参数式），可选依赖未启用时跳过
     * @param request
     */
    private void recordPreferencesIfPresent(AgentRequest request) {
        if (preferenceRecorder == null) {
            return;
        }
        try {
            preferenceRecorder.recordFromRequest(request);
        } catch (Exception e) {
            // 偏好记录失败不影响主对话流程
        }
    }

    /**
     * 澄清恢复请求
     */
    public static class ClarificationRequest {

        /**
         * 会话ID
         */
        private String sessionId;

        /**
         * 关联工具调用ID
         */
        private String toolCallId;

        /**
         * 用户澄清答案
         */
        private String answer;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public void setToolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }

    /**
     * 引擎确认恢复请求
     */
    public static class ConfirmRequest {

        /**
         * 会话ID
         */
        private String sessionId;

        /**
         * 是否批准
         */
        private boolean approved;

        /**
         * 审批操作人
         */
        private String operator;

        /**
         * 拒绝理由
         */
        private String reason;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
