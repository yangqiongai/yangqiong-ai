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
package com.yangqiongai.ai.platform.api.approval;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.event.ApprovalEventBridge;
import com.yangqiongai.ai.agent.core.executor.ConfirmPendingRegistry;
import com.yangqiongai.ai.approval.*;
import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.common.util.AiJsonUtils;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批管理接口
 * @author yangqiong
 */
@Tag(name = "审批管理接口")
@RestController
@RequestMapping("/api/approval")
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    @Autowired
    private ApprovalGate approvalGate;

    @Autowired
    private PendingRequestStore pendingRequestStore;

    @Autowired
    private ApprovalEventBridge approvalEventBridge;

    @Autowired
    private AgentEngine agentEngine;

    @Autowired
    private ConfirmPendingRegistry confirmPendingRegistry;

    /**
     * 订阅审批事件SSE流（独立于LLM流）
     * @param sessionId
     * @return
     */
    @Operation(summary = "订阅审批事件SSE流", description = "独立于LLM流的审批事件订阅，前端通过此端点接收审批请求通知")
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamApprovalEvents(
            @Parameter(name = "sessionId", description = "会话ID") @PathVariable String sessionId) {
        SseEmitter emitter = new SseEmitter(600000L);

        Sinks.Many<StreamEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        approvalEventBridge.registerApprovalSink(sessionId, sink);

        Flux<StreamEvent> eventFlux = sink.asFlux();
        SseEmitter finalEmitter = emitter;

        reactor.core.Disposable disposable = eventFlux.subscribe(
                event -> {
                    try {
                        String eventName = event.getKind() == StreamEvent.Kind.APPROVAL_REQUIRED
                                ? "approval_required" : "message";
                        finalEmitter.send(SseEmitter.event().name(eventName).data(event.getPayload()));
                    } catch (Exception e) {
                        log.debug("SSE发送失败，连接可能已关闭: {}", e.getMessage());
                    }
                },
                error -> {
                    try { finalEmitter.completeWithError(error); } catch (Exception ignored) {}
                },
                () -> {
                    try { finalEmitter.complete(); } catch (Exception ignored) {}
                }
        );

        emitter.onCompletion(() -> {
            disposable.dispose();
            approvalEventBridge.removeApprovalSink(sessionId);
        });
        emitter.onTimeout(() -> {
            disposable.dispose();
            approvalEventBridge.removeApprovalSink(sessionId);
        });
        emitter.onError(ex -> {
            disposable.dispose();
            approvalEventBridge.removeApprovalSink(sessionId);
        });

        return emitter;
    }

    /**
     * 查询待审批列表，支持按发起用户过滤
     * @param userId
     * @return
     */
    @Operation(summary = "查询待审批列表", description = "userId为空时返回全部待审批，指定userId时返回该用户发起的待审批")
    @GetMapping("/pending")
    public ApiResult<List<PendingRequestInfo>> listPending(
            @Parameter(name = "userId", description = "发起用户ID，为空时查询全部") @RequestParam(required = false) String userId) {
        return ApiResult.ok(pendingRequestStore.findPendingRequests(userId));
    }

    /**
     * 查询审批详情
     * @param requestId
     * @return
     */
    @Operation(summary = "查询审批详情")
    @GetMapping("/{requestId}")
    public ApiResult<PendingRequestInfo> getDetail(
            @Parameter(name = "requestId", description = "请求ID") @PathVariable String requestId) {
        return pendingRequestStore.findByRequestId(requestId)
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode(), "审批请求不存在: " + requestId));
    }

    /**
     * 审批通过，可携带审批人的选择和表单输入
     * @param requestId
     * @param body
     * @return
     */
    @Operation(summary = "审批通过", description = "可携带responsePayload（选择结果和表单输入），通过后SuspendableAspect会将其注入SessionContext；引擎确认暂停现场（requestId即会话ID）优先路由到引擎恢复")
    @PostMapping("/{requestId}/approve")
    public ApiResult<Map<String, Object>> approve(
            @Parameter(name = "requestId", description = "请求ID") @PathVariable String requestId,
            @RequestBody Map<String, Object> body) {
        String approvedBy = body.get("approvedBy") == null ? null : body.get("approvedBy").toString();
        if (approvedBy == null || approvedBy.isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "approvedBy不能为空");
        }
        // 引擎确认暂停现场优先路由，未命中走框架审批门
        if (confirmPendingRegistry.exists(requestId)) {
            resumeConfirmInBackground(requestId, true, approvedBy, null);
            Map<String, Object> result = new HashMap<>();
            result.put("requestId", requestId);
            result.put("status", ApprovalStatus.APPROVED.name());
            return ApiResult.ok(result);
        }
        return pendingRequestStore.findByRequestId(requestId)
                .map(request -> {
                    if (!ApprovalStatus.PENDING.name().equals(request.getStatus())) {
                        return ApiResult.<Map<String, Object>>fail(AiErrorCode.PARAM_ERROR.getCode(), "审批请求非待审批状态: " + request.getStatus());
                    }
                    Object responsePayloadObj = body.get("responsePayload");
                    String responsePayload = responsePayloadObj != null
                            ? AiJsonUtils.toJson(responsePayloadObj) : null;
                    approvalGate.approve(request.getApprovalToken(), approvedBy, responsePayload);
                    Map<String, Object> result = new HashMap<>();
                    result.put("requestId", requestId);
                    result.put("status", ApprovalStatus.APPROVED.name());
                    return ApiResult.ok(result);
                })
                .orElse(ApiResult.fail(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode(), "审批请求不存在: " + requestId));
    }

    /**
     * 审批拒绝
     * @param requestId
     * @param body
     * @return
     */
    @Operation(summary = "审批拒绝", description = "引擎确认暂停现场（requestId即会话ID）优先路由到引擎恢复")
    @PostMapping("/{requestId}/reject")
    public ApiResult<Map<String, Object>> reject(
            @Parameter(name = "requestId", description = "请求ID") @PathVariable String requestId,
            @RequestBody Map<String, String> body) {
        String rejectedBy = body.get("rejectedBy");
        if (rejectedBy == null || rejectedBy.isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "rejectedBy不能为空");
        }
        // 引擎确认暂停现场优先路由，未命中走框架审批门
        if (confirmPendingRegistry.exists(requestId)) {
            resumeConfirmInBackground(requestId, false, rejectedBy, body.getOrDefault("reason", ""));
            Map<String, Object> result = new HashMap<>();
            result.put("requestId", requestId);
            result.put("status", ApprovalStatus.REJECTED.name());
            return ApiResult.ok(result);
        }
        return pendingRequestStore.findByRequestId(requestId)
                .map(request -> {
                    if (!ApprovalStatus.PENDING.name().equals(request.getStatus())) {
                        return ApiResult.<Map<String, Object>>fail(AiErrorCode.PARAM_ERROR.getCode(), "审批请求非待审批状态: " + request.getStatus());
                    }
                    String reason = body.getOrDefault("reason", "");
                    approvalGate.reject(request.getApprovalToken(), rejectedBy, reason);
                    Map<String, Object> result = new HashMap<>();
                    result.put("requestId", requestId);
                    result.put("status", ApprovalStatus.REJECTED.name());
                    return ApiResult.ok(result);
                })
                .orElse(ApiResult.fail(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode(), "审批请求不存在: " + requestId));
    }

    /**
     * 后台执行引擎确认恢复（非流式入口续跑事件仅落日志，对话页续跑渲染走 /api/agent/chat/confirm 流式端点）
     * @param requestId
     * @param approved
     * @param operator
     * @param reason
     */
    private void resumeConfirmInBackground(String requestId, boolean approved, String operator, String reason) {
        agentEngine.resumeConfirm(requestId, approved, operator, reason)
                .subscribe(event -> { },
                        error -> log.warn("引擎确认恢复失败: requestId={}, error={}", requestId, error.getMessage()),
                        () -> log.info("引擎确认恢复完成: requestId={}", requestId));
    }
}
