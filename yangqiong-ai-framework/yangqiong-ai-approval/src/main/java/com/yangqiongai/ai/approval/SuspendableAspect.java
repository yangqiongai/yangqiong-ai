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
package com.yangqiongai.ai.approval;

import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.core.event.ApprovalRequiredEvent;
import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.common.util.AiJsonUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Suspendable 注解AOP拦截器
 * <p>
 * 拦截标注了 {@link Suspendable} 的方法，在执行前自动触发审批流程。
 * 根据 {@link Suspendable#options()} 和 {@link Suspendable#inputFields()} 构建
 * {@code inputSchema}，前端据此渲染选择框或表单。审批通过后，将审批人的响应数据
 * 注入 {@link SessionContext}，方法体内可通过 {@link SessionContext#getApprovalResponse()} 获取。
 * </p>
 *
 * <h3>前端交互流程</h3>
 * <pre>
 * 1. 调用 @Suspendable 方法 → AOP创建审批请求（含inputSchema）
 * 2. AOP发布 ApprovalRequiredEvent → SSE推送 approval_required 事件（含inputSchema）
 * 3. 前端根据 inputSchema 渲染：
 *    - options非空 → 渲染选择按钮
 *    - inputFields非空 → 渲染表单
 *    - 均空 → 渲染确认/拒绝按钮
 * 4. 前端调用 POST /api/approval/{requestId}/approve，body包含 responsePayload
 * 5. AOP的 waitForApproval 被唤醒，将 responsePayload 注入 SessionContext
 * 6. 方法体可通过 SessionContext.getApprovalResponse() 读取审批人的选择和输入
 * </pre>
 *
 * @author yangqiong
 */
@Aspect
@Component
public class SuspendableAspect {

    private static final Logger log = LoggerFactory.getLogger(SuspendableAspect.class);

    private static final Set<String> SENSITIVE_PARAM_NAMES = Set.of(
            "password", "pwd", "passwd", "token", "secret", "apiKey", "api_key",
            "credential", "authorization", "privateKey", "private_key", "ssn"
    );

    private static final Pattern SENSITIVE_NAME_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|token|secret|api_?key|credential|authorization|private_?key|ssn)"
    );

    @Autowired
    private ApprovalGate approvalGate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 拦截所有标注了 @Suspendable 的方法
     * @param joinPoint
     * @param suspendable
     * @return
     * @throws Throwable
     */
    @Around("@annotation(suspendable)")
    public Object aroundSuspendable(ProceedingJoinPoint joinPoint, Suspendable suspendable) throws Throwable {
        if (!suspendable.enabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
        String reason = suspendable.reason().isEmpty() ? "方法需要审批: " + methodName : suspendable.reason();
        int timeoutSeconds = suspendable.timeoutSeconds();

        String sessionId = SessionContext.getSessionId();
        String userId = SessionContext.getUserId();

        if (sessionId == null || sessionId.isEmpty()) {
            log.error("@Suspendable 方法 {} 未设置会话上下文，拒绝执行（fail-closed）", methodName);
            throw new IllegalStateException("@Suspendable 方法必须在有效的会话上下文中调用: " + methodName);
        }

        Map<String, Object> params = buildParamsSummary(joinPoint);
        String inputSchema = buildInputSchema(suspendable);

        log.info("方法 {} 触发审批门控: sessionId={}, userId={}, reason={}", methodName, sessionId, userId, reason);

        PendingRequestInfo request = approvalGate.requestApproval(sessionId, userId, "TOOL",
                methodName, reason, params, inputSchema);

        ApprovalRequiredEvent approvalEvent = new ApprovalRequiredEvent(
                this, request.getRequestId(), sessionId, userId, "TOOL",
                methodName, reason, timeoutSeconds, inputSchema);
        eventPublisher.publishEvent(approvalEvent);
        log.info("已发布审批请求事件: requestId={}, targetName={}", request.getRequestId(), methodName);

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        PendingRequestInfo result = approvalGate.waitForApproval(request.getRequestId(), timeout);

        if (result == null) {
            throw ApprovalRejectedException.timeout(request.getRequestId());
        }

        ApprovalStatus status = ApprovalStatus.valueOf(result.getStatus());
        switch (status) {
            case APPROVED:
                log.info("方法 {} 审批通过，开始执行: requestId={}", methodName, request.getRequestId());
                injectApprovalResponse(result);
                return joinPoint.proceed();
            case REJECTED:
                log.info("方法 {} 审批拒绝: requestId={}, reason={}", methodName, request.getRequestId(), result.getRejectReason());
                throw ApprovalRejectedException.rejected(request.getRequestId(), result.getRejectReason());
            case TIMEOUT:
                log.info("方法 {} 审批超时: requestId={}", methodName, request.getRequestId());
                throw ApprovalRejectedException.timeout(request.getRequestId());
            default:
                log.warn("方法 {} 审批状态异常: requestId={}, status={}", methodName, request.getRequestId(), status);
                throw ApprovalRejectedException.timeout(request.getRequestId());
        }
    }

    /**
     * 将审批人的响应数据注入SessionContext，供方法体读取
     * @param result
     */
    private void injectApprovalResponse(PendingRequestInfo result) {
        String payload = result.getResponsePayload();
        if (payload == null || payload.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> responseMap = AiJsonUtils.fromJsonToMap(payload);
            if (responseMap != null && !responseMap.isEmpty()) {
                SessionContext.setApprovalResponse(responseMap);
                log.debug("注入审批响应到SessionContext: requestId={}, keys={}", result.getRequestId(), responseMap.keySet());
            }
        } catch (Exception e) {
            log.warn("解析审批响应数据失败，跳过注入: requestId={}", result.getRequestId(), e);
        }
    }

    /**
     * 根据注解的options和inputFields构建inputSchema JSON
     * @param suspendable
     * @return
     */
    private String buildInputSchema(Suspendable suspendable) {
        String[] options = suspendable.options();
        String[] inputFields = suspendable.inputFields();
        if (options.length == 0 && inputFields.length == 0) {
            return null;
        }

        Map<String, Object> schema = new HashMap<>();
        if (options.length > 0) {
            schema.put("options", options);
        }
        if (inputFields.length > 0) {
            schema.put("fields", inputFields);
        }
        return AiJsonUtils.toJson(schema);
    }

    /**
     * 构建参数摘要（避免参数过大）
     * @param joinPoint
     * @return
     */
    private Map<String, Object> buildParamsSummary(ProceedingJoinPoint joinPoint) {
        Map<String, Object> params = new HashMap<>();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                String name = paramNames[i];
                Object value = args[i];
                if (isSensitiveParam(name)) {
                    params.put(name, "******");
                } else if (value instanceof String s && s.length() > 200) {
                    params.put(name, s.substring(0, 200) + "...");
                } else {
                    params.put(name, value);
                }
            }
        }
        return params;
    }

    /**
     * 判断参数名是否为敏感字段
     * @param paramName
     * @return
     */
    private boolean isSensitiveParam(String paramName) {
        if (paramName == null) {
            return false;
        }
        return SENSITIVE_PARAM_NAMES.contains(paramName.toLowerCase())
                || SENSITIVE_NAME_PATTERN.matcher(paramName).find();
    }
}
