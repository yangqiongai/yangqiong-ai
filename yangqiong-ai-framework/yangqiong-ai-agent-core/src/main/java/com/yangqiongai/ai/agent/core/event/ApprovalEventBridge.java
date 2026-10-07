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
package com.yangqiongai.ai.agent.core.event;

import com.yangqiongai.ai.common.sse.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批事件桥接器
 * <p>
 * 监听 {@link ApprovalRequiredEvent}（由 @Suspendable AOP拦截器发布），
 * 将审批请求推送到对应 sessionId 的 SSE 流，使前端能收到审批通知。
 * </p>
 *
 * <h3>两种SSE订阅方式</h3>
 * <ol>
 *   <li><b>LLM流内嵌</b>：流式执行时，审批事件混在 LLM 文本流中推送（APPROVAL_REQUIRED 事件类型）</li>
 *   <li><b>独立审批SSE</b>：前端通过 GET /api/approval/stream/{sessionId} 单独订阅审批事件</li>
 * </ol>
 *
 * <h3>工作原理</h3>
 * <pre>
 * 1. 流式/同步执行开始时，调用 registerLlmSink(sessionId, sink) 注册LLM流
 * 2. 前端连接独立审批SSE时，调用 registerApprovalSink(sessionId, sink)
 * 3. @Suspendable 触发审批 → SuspendableAspect 发布 ApprovalRequiredEvent
 * 4. 本类 @EventListener 接收事件 → 同时推送到 LLM 流和独立审批流
 * 5. 执行结束时，调用 removeLlmSink(sessionId) 清理
 * </pre>
 *
 * <p>
 * 定位说明：本类为 @Suspendable AOP 审批的 SSE 桥接专用组件，不参与运行时 AgentEvent 事件流
 * （运行时事件见 com.yangqiongai.ai.agent.runtime.event）。
 * </p>
 *
 * @author yangqiong
 */
@Component
public class ApprovalEventBridge {

    private static final Logger log = LoggerFactory.getLogger(ApprovalEventBridge.class);

    /**
     * LLM流式sink，按sessionId管理（流式/同步执行时注册）
     */
    private final Map<String, Sinks.Many<StreamEvent>> llmSinks = new ConcurrentHashMap<>();

    /**
     * 独立审批SSE sink，按sessionId管理（前端单独订阅）
     */
    private final Map<String, Sinks.Many<StreamEvent>> approvalSinks = new ConcurrentHashMap<>();

    /**
     * 注册LLM流式sink（流式/同步执行时调用）
     * @param sessionId 会话ID
     * @param sink 流式事件sink
     */
    public void registerLlmSink(String sessionId, Sinks.Many<StreamEvent> sink) {
        llmSinks.put(sessionId, sink);
        log.debug("注册LLM审批sink: sessionId={}", sessionId);
    }

    /**
     * 移除LLM流式sink（执行结束时调用）
     * @param sessionId 会话ID
     */
    public void removeLlmSink(String sessionId) {
        llmSinks.remove(sessionId);
        log.debug("移除LLM审批sink: sessionId={}", sessionId);
    }

    /**
     * 注册独立审批SSE sink（前端订阅审批事件时调用）
     * @param sessionId 会话ID
     * @param sink 审批事件sink
     */
    public void registerApprovalSink(String sessionId, Sinks.Many<StreamEvent> sink) {
        approvalSinks.put(sessionId, sink);
        log.info("注册独立审批SSE: sessionId={}", sessionId);
    }

    /**
     * 移除独立审批SSE sink
     * @param sessionId 会话ID
     */
    public void removeApprovalSink(String sessionId) {
        approvalSinks.remove(sessionId);
        log.info("移除独立审批SSE: sessionId={}", sessionId);
    }

    /**
     * 监听审批请求事件，推送到LLM流和独立审批SSE
     * @param event 审批请求事件
     */
    @EventListener
    public void onApprovalRequired(ApprovalRequiredEvent event) {
        StreamEvent approvalEvent = StreamEvent.approvalRequired(event.toPayload());
        String sessionId = event.getSessionId();

        // 推送到LLM流（内嵌方式）
        Sinks.Many<StreamEvent> llmSink = llmSinks.get(sessionId);
        if (llmSink != null) {
            llmSink.tryEmitNext(approvalEvent);
            log.info("已推送审批请求到LLM流: requestId={}, sessionId={}", event.getRequestId(), sessionId);
        }

        // 推送到独立审批SSE
        Sinks.Many<StreamEvent> approvalSink = approvalSinks.get(sessionId);
        if (approvalSink != null) {
            approvalSink.tryEmitNext(approvalEvent);
            log.info("已推送审批请求到独立审批SSE: requestId={}, sessionId={}", event.getRequestId(), sessionId);
        }

        if (llmSink == null && approvalSink == null) {
            log.warn("无活跃SSE流接收审批事件: requestId={}, sessionId={}", event.getRequestId(), sessionId);
        }
    }
}
