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
package com.yangqiongai.ai.open.capability.interaction;

import com.yangqiongai.ai.agent.core.event.ApprovalRequiredEvent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.task.AgentTaskTracker;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.approval.PendingRequestStore;
import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 开放能力交互策略
 * <p>
 * 按能力级interactionEnabled开关实现关闭语义：源头排除审批工具、Prompt约束降低澄清概率、
 * 兜底截止（审批事件自动拒绝、澄清/确认挂起即时失败），保证关闭交互时不向外发送审批与澄清流。
 * 仅登记关闭交互的调用（开启交互的调用不进入本策略，事件照常透传，交互增强由企业版承接）。
 * </p>
 * @author yangqiong
 */
public class OpenInteractionPolicy {

    private static final Logger log = LoggerFactory.getLogger(OpenInteractionPolicy.class);

    /**
     * 审批工具编码（Tool默认取类SimpleName，CUSTOM分类受工具清单管控）
     */
    public static final String APPROVAL_TOOL_CODE = "HumanApprovalTool";

    /**
     * 关闭交互时的统一失败文案
     */
    public static final String CLOSED_INTERACTION_MESSAGE = "该能力未启用交互功能，执行在请求澄清处中止";

    /**
     * 请求级工具禁用清单键（AbstractAgentProcessor.resolveToolkit读取，优先级高于工具白名单）
     */
    private static final String BODY_DENIED_TOOLS = "_agentConfig_deniedTools";

    /**
     * Prompt约束段（降低模型发起澄清的概率，非100%由兜底层保证不外发）
     */
    private static final String CLOSED_PROMPT_CONSTRAINT = "\n\n[系统约束] 本次调用所属能力未启用交互功能："
            + "禁止向用户发起澄清提问，禁止请求人工审批。请基于现有信息直接执行并给出最终结果；"
            + "如信息不足，请在结果中说明缺失项，而不是交互式索取。";

    /**
     * 调用登记有效期（异步调用正常完成不产生挂起，超期登记兜底清理）
     */
    private static final long INVOCATION_TTL_MINUTES = 60;

    private final AgentTaskTracker taskTracker;

    private final ObjectProvider<PendingRequestStore> pendingRequestStoreProvider;

    private final ObjectProvider<ApprovalGate> approvalGateProvider;

    /**
     * 关闭交互的调用登记（sessionId → 调用上下文，仅登记关闭交互的调用）
     */
    private final Map<String, Invocation> invocations = new ConcurrentHashMap<>();

    /**
     * 开启交互的调用登记（sessionId → 调用上下文，供交互增强联动挂起登记与任务状态）
     */
    private final Map<String, Invocation> openInvocations = new ConcurrentHashMap<>();

    public OpenInteractionPolicy(AgentTaskTracker taskTracker,
                                 ObjectProvider<PendingRequestStore> pendingRequestStoreProvider,
                                 ObjectProvider<ApprovalGate> approvalGateProvider) {
        this.taskTracker = taskTracker;
        this.pendingRequestStoreProvider = pendingRequestStoreProvider;
        this.approvalGateProvider = approvalGateProvider;
    }

    /**
     * 判断能力是否开启交互功能
     * @param spec 能力规格
     * @return 开启返回true，未配置或配置为false返回false
     */
    public boolean isInteractionEnabled(CapabilitySpec spec) {
        return spec != null && Boolean.TRUE.equals(spec.getInteractionEnabled());
    }

    /**
     * 应用关闭交互的源头阻止（请求级禁用审批工具，LLM不可见审批工具即不会发起审批）
     * @param agentRequest
     */
    public void applyClosedMode(AgentRequest agentRequest) {
        agentRequest.getBody().put(BODY_DENIED_TOOLS, List.of(APPROVAL_TOOL_CODE));
    }

    /**
     * 构建关闭交互的Prompt约束段
     * @return
     */
    public String buildClosedConstraint() {
        return CLOSED_PROMPT_CONSTRAINT;
    }

    /**
     * 登记关闭交互的调用上下文
     * @param sessionId
     * @param taskId 异步任务ID，同步/流式调用为null
     */
    public void registerInvocation(String sessionId, String taskId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        invocations.put(sessionId, new Invocation(taskId, null));
    }

    /**
     * 移除调用登记（同步/流式调用结束时调用）
     * @param sessionId
     */
    public void unregister(String sessionId) {
        if (sessionId != null) {
            invocations.remove(sessionId);
        }
    }

    /**
     * 登记开启交互的调用上下文（开放接口交互增强联动挂起登记时按会话匹配）
     * @param sessionId
     * @param taskId 异步任务ID，同步/流式调用为null
     * @param scopeId 调用作用域ID（审批事件载体无scopeId，登记点捕获供归属校验）
     */
    public void registerOpenInvocation(String sessionId, String taskId, String scopeId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        openInvocations.put(sessionId, new Invocation(taskId, scopeId));
    }

    /**
     * 查询开启交互调用的作用域ID
     * @param sessionId
     * @return 非开放调用返回null
     */
    public String findOpenScopeId(String sessionId) {
        Invocation invocation = sessionId == null ? null : openInvocations.get(sessionId);
        return invocation != null ? invocation.scopeId : null;
    }

    /**
     * 判断会话是否为开启交互的开放接口调用
     * @param sessionId
     * @return
     */
    public boolean isOpenInvocation(String sessionId) {
        return sessionId != null && openInvocations.containsKey(sessionId);
    }

    /**
     * 查询开启交互调用的异步任务ID
     * @param sessionId
     * @return 异步调用返回任务ID，同步/流式或非开放调用返回null
     */
    public String findOpenTaskId(String sessionId) {
        Invocation invocation = sessionId == null ? null : openInvocations.get(sessionId);
        return invocation != null ? invocation.taskId : null;
    }

    /**
     * 移除开启交互的调用登记（续跑终态收尾时调用）
     * @param sessionId
     */
    public void removeOpenInvocation(String sessionId) {
        if (sessionId != null) {
            openInvocations.remove(sessionId);
        }
    }

    /**
     * 挂起登记回调（暂停恢复登记存储装饰器在登记挂起现场时回调）
     * <p>
     * 异步调用：任务即时标记失败（先于执行线程收尾，guard保证不被完成态覆盖）；
     * 同步调用：标记中止标记，由execute在返回前检查并转换为失败结果。
     * </p>
     * @param sessionId
     */
    public void onSuspendRegistered(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Invocation invocation = invocations.get(sessionId);
        if (invocation == null) {
            return;
        }
        if (invocation.taskId != null) {
            // 异步调用：即时标记失败并移除登记（一次性处理）
            invocations.remove(sessionId);
            taskTracker.markFailed(invocation.taskId, "interaction", CLOSED_INTERACTION_MESSAGE);
            log.info("关闭交互的异步调用发生挂起，已标记任务失败: sessionId={}, taskId={}", sessionId, invocation.taskId);
        } else {
            invocation.suspended = true;
        }
    }

    /**
     * 同步调用是否发生了交互中止（execute返回前检查）
     * @param sessionId
     * @return
     */
    public boolean isSuspended(String sessionId) {
        Invocation invocation = sessionId == null ? null : invocations.get(sessionId);
        return invocation != null && invocation.suspended;
    }

    /**
     * 过滤关闭交互调用的挂起类事件（澄清/引擎确认不透传，流截断后自然结束）
     * @param flux
     * @return
     */
    public Flux<StreamEvent> filterSuspendEvents(Flux<StreamEvent> flux) {
        return flux.filter(event -> event.getKind() != StreamEvent.Kind.CLARIFICATION_REQUIRED
                && event.getKind() != StreamEvent.Kind.CONFIRM_REQUIRED);
    }

    /**
     * 审批漏网兜底（如@Suspendable静态路径）：关闭交互的调用触发审批事件时自动拒绝，latch立即释放不外发
     * @param event
     */
    @EventListener
    public void onApprovalRequired(ApprovalRequiredEvent event) {
        try {
            String sessionId = event.getSessionId();
            if (sessionId == null || !invocations.containsKey(sessionId)) {
                return;
            }
            PendingRequestStore store = pendingRequestStoreProvider.getIfAvailable();
            ApprovalGate approvalGate = approvalGateProvider.getIfAvailable();
            if (store == null || approvalGate == null) {
                log.warn("关闭交互的审批兜底未生效，审批组件不可用: sessionId={}, requestId={}",
                        sessionId, event.getRequestId());
                return;
            }
            PendingRequestInfo pending = store.findByRequestId(event.getRequestId()).orElse(null);
            if (pending == null) {
                return;
            }
            approvalGate.reject(pending.getApprovalToken(), "open-interaction-policy", CLOSED_INTERACTION_MESSAGE);
            log.info("关闭交互的调用触发审批，已自动拒绝: sessionId={}, requestId={}", sessionId, event.getRequestId());
        } catch (Exception e) {
            log.warn("关闭交互的审批自动拒绝失败: requestId={}", event.getRequestId(), e);
        }
    }

    /**
     * 定时清理过期调用登记（异步调用正常完成或异常结束均不产生挂起，超期登记兜底回收）
     */
    @Scheduled(fixedRate = 1800000)
    public void cleanupExpiredInvocations() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(INVOCATION_TTL_MINUTES);
        invocations.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(threshold));
        openInvocations.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(threshold));
    }

    /**
     * 关闭交互的调用上下文
     */
    private static class Invocation {

        /**
         * 异步任务ID（同步/流式调用为null）
         */
        private final String taskId;

        /**
         * 调用作用域ID（开启交互登记携带，供交互增强归属校验）
         */
        private final String scopeId;

        /**
         * 是否发生交互中止（同步调用检查标记）
         */
        private volatile boolean suspended;

        /**
         * 登记时刻
         */
        private final LocalDateTime createdAt = LocalDateTime.now();

        private Invocation(String taskId, String scopeId) {
            this.taskId = taskId;
            this.scopeId = scopeId;
        }
    }
}
