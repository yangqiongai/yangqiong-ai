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

import com.yangqiongai.ai.agent.core.event.ApprovalRequiredEvent;
import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.common.util.AiJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 审批门控
 * @author yangqiong
 */
@Service
public class ApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(ApprovalGate.class);

    private static final Duration DEFAULT_EXPIRE_DURATION = Duration.ofMinutes(30);

    private final Map<String, CountDownLatch> latchMap = new ConcurrentHashMap<>();

    @Autowired
    private PendingRequestStore pendingRequestStore;

    @Autowired
    private ApprovalTokenGenerator tokenGenerator;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 请求审批
     * @param sessionId
     * @param userId
     * @param resourceType
     * @param targetName
     * @param reason
     * @param params
     * @param inputSchema
     * @return
     */
    public PendingRequestInfo requestApproval(String sessionId, String userId, String resourceType,
                                          String targetName, String reason,
                                          Map<String, Object> params, String inputSchema) {
        return doCreateRequest(sessionId, userId, null, resourceType, targetName, reason,
                params, inputSchema, null);
    }

    /**
     * 编程式请求审批（支持动态options和inputFields）
     * <p>
     * 在业务代码中动态发起审批，自动发布审批事件并等待结果。
     * 审批通过后可通过返回的 ApprovalResponse 获取审批人的选择和表单输入。
     * </p>
     * @param request
     * @return
     */
    public ApprovalResponse requestApproval(ApprovalRequest request) {
        String inputSchema = buildInputSchema(request.getOptions(), request.getInputFields());

        PendingRequestInfo pending = doCreateRequest(
                request.getSessionId(), request.getUserId(), request.getApprover(), request.getResourceType(),
                request.getTargetName(), request.getReason(), request.getParams(), inputSchema,
                request.getTimeout());

        ApprovalRequiredEvent event = new ApprovalRequiredEvent(
                this, pending.getRequestId(), request.getSessionId(), request.getUserId(),
                request.getResourceType(), request.getTargetName(), request.getReason(),
                (int) request.getTimeout().getSeconds(), inputSchema);
        eventPublisher.publishEvent(event);
        log.info("已发布编程式审批请求事件: requestId={}, targetName={}", pending.getRequestId(), request.getTargetName());

        PendingRequestInfo result = waitForApproval(pending.getRequestId(), request.getTimeout());

        if (result == null) {
            return new ApprovalResponse(pending.getRequestId(), ApprovalStatus.TIMEOUT, null, null);
        }

        ApprovalStatus status = ApprovalStatus.valueOf(result.getStatus());
        return new ApprovalResponse(result.getRequestId(), status, result.getRejectReason(), result.getResponsePayload());
    }

    /**
     * 异步创建审批请求（不阻塞线程）
     * <p>
     * 仅创建审批请求并发布审批事件，不等待结果。适用于工作流节点审批的异步暂停模式：
     * 调用方创建请求后立即返回 requestId，工作流线程释放；审批完成后通过
     * {@link ApprovalResolvedEvent} 事件恢复执行。
     * </p>
     * @param request
     * @return 审批请求ID
     */
    public String createPendingRequest(ApprovalRequest request) {
        String inputSchema = buildInputSchema(request.getOptions(), request.getInputFields());

        PendingRequestInfo pending = doCreateRequest(
                request.getSessionId(), request.getUserId(), request.getApprover(), request.getResourceType(),
                request.getTargetName(), request.getReason(), request.getParams(), inputSchema,
                request.getTimeout());

        ApprovalRequiredEvent event = new ApprovalRequiredEvent(
                this, pending.getRequestId(), request.getSessionId(), request.getUserId(),
                request.getResourceType(), request.getTargetName(), request.getReason(),
                (int) request.getTimeout().getSeconds(), inputSchema);
        eventPublisher.publishEvent(event);
        log.info("已创建异步审批请求: requestId={}, targetName={}", pending.getRequestId(), request.getTargetName());

        return pending.getRequestId();
    }

    /**
     * 创建审批请求并落库
     * @param sessionId
     * @param userId
     * @param approver
     * @param resourceType
     * @param targetName
     * @param reason
     * @param params
     * @param inputSchema
     * @param timeout 为null时按默认过期时长
     * @return
     */
    private PendingRequestInfo doCreateRequest(String sessionId, String userId, String approver,
                                               String resourceType, String targetName, String reason,
                                               Map<String, Object> params, String inputSchema, Duration timeout) {
        String requestId = tokenGenerator.generateRequestId();
        String approvalToken = tokenGenerator.generateToken();

        PendingRequestInfo request = new PendingRequestInfo();
        request.setRequestId(requestId);
        request.setApprovalToken(approvalToken);
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.setApprover(approver);
        request.setResourceType(resourceType);
        request.setTargetName(targetName);
        request.setReason(reason);
        request.setTargetParams(AiJsonUtils.toJson(params));
        request.setInputSchema(inputSchema);
        request.setStatus(ApprovalStatus.PENDING.name());
        request.setExpireTime(LocalDateTime.now().plus(timeout != null ? timeout : DEFAULT_EXPIRE_DURATION));

        pendingRequestStore.save(request);
        log.info("创建审批请求: requestId={}, targetName={}, sessionId={}", requestId, targetName, sessionId);
        return request;
    }

    /**
     * 根据选项列表和字段列表构建inputSchema JSON
     * @param options
     * @param inputFields
     * @return
     */
    private String buildInputSchema(List<String> options, List<String> inputFields) {
        boolean hasOptions = options != null && !options.isEmpty();
        boolean hasFields = inputFields != null && !inputFields.isEmpty();
        if (!hasOptions && !hasFields) {
            return null;
        }
        Map<String, Object> schema = new HashMap<>();
        if (hasOptions) {
            schema.put("options", options);
        }
        if (hasFields) {
            schema.put("fields", inputFields);
        }
        return AiJsonUtils.toJson(schema);
    }

    /**
     * 检查审批状态
     * @param requestId
     * @return
     */
    public ApprovalStatus checkApprovalStatus(String requestId) {
        Optional<PendingRequestInfo> optional = pendingRequestStore.findByRequestId(requestId);
        if (optional.isEmpty()) {
            return ApprovalStatus.TIMEOUT;
        }
        PendingRequestInfo request = optional.get();
        if (request.getExpireTime() != null && LocalDateTime.now().isAfter(request.getExpireTime())) {
            return ApprovalStatus.TIMEOUT;
        }
        return ApprovalStatus.valueOf(request.getStatus());
    }

    /**
     * 审批通过，携带审批人的选择和表单输入
     * @param approvalToken
     * @param approvedBy
     * @param responsePayload
     */
    public void approve(String approvalToken, String approvedBy, String responsePayload) {
        if (!tokenGenerator.isTokenValid(approvalToken)) {
            log.warn("审批令牌格式无效: token={}", approvalToken);
            return;
        }
        Optional<PendingRequestInfo> optional = pendingRequestStore.findByApprovalToken(approvalToken);
        if (optional.isEmpty()) {
            log.warn("审批令牌未找到对应请求: token={}", approvalToken);
            return;
        }
        PendingRequestInfo request = optional.get();
        if (!ApprovalStatus.PENDING.name().equals(request.getStatus())) {
            log.warn("审批请求非待审批状态: requestId={}, status={}", request.getRequestId(), request.getStatus());
            return;
        }
        pendingRequestStore.updateStatus(request.getRequestId(), ApprovalStatus.APPROVED.name(),
                approvedBy, null, responsePayload);
        CountDownLatch latch = latchMap.get(request.getRequestId());
        if (latch != null) {
            latch.countDown();
        }
        eventPublisher.publishEvent(new ApprovalResolvedEvent(
                this, request.getRequestId(), request.getSessionId(),
                ApprovalStatus.APPROVED, responsePayload, null));
        log.info("审批通过: requestId={}, approvedBy={}", request.getRequestId(), approvedBy);
    }

    /**
     * 审批拒绝
     * @param approvalToken
     * @param rejectedBy
     * @param reason
     */
    public void reject(String approvalToken, String rejectedBy, String reason) {
        if (!tokenGenerator.isTokenValid(approvalToken)) {
            log.warn("审批令牌格式无效: token={}", approvalToken);
            return;
        }
        Optional<PendingRequestInfo> optional = pendingRequestStore.findByApprovalToken(approvalToken);
        if (optional.isEmpty()) {
            log.warn("审批令牌未找到对应请求: token={}", approvalToken);
            return;
        }
        PendingRequestInfo request = optional.get();
        if (!ApprovalStatus.PENDING.name().equals(request.getStatus())) {
            log.warn("审批请求非待审批状态: requestId={}, status={}", request.getRequestId(), request.getStatus());
            return;
        }
        pendingRequestStore.updateStatus(request.getRequestId(), ApprovalStatus.REJECTED.name(),
                rejectedBy, reason, null);
        CountDownLatch latch = latchMap.get(request.getRequestId());
        if (latch != null) {
            latch.countDown();
        }
        eventPublisher.publishEvent(new ApprovalResolvedEvent(
                this, request.getRequestId(), request.getSessionId(),
                ApprovalStatus.REJECTED, null, reason));
        log.info("审批拒绝: requestId={}, rejectedBy={}, reason={}", request.getRequestId(), rejectedBy, reason);
    }

    /**
     * 等待审批结果
     * @param requestId
     * @param timeout
     * @return
     */
    public PendingRequestInfo waitForApproval(String requestId, Duration timeout) {
        CountDownLatch latch = new CountDownLatch(1);
        latchMap.put(requestId, latch);

        try {
            boolean awaited = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Optional<PendingRequestInfo> optional = pendingRequestStore.findByRequestId(requestId);
            if (optional.isEmpty()) {
                return null;
            }
            PendingRequestInfo request = optional.get();
            if (!awaited && ApprovalStatus.PENDING.name().equals(request.getStatus())) {
                pendingRequestStore.updateStatus(requestId, ApprovalStatus.TIMEOUT.name(), null, null, null);
                request.setStatus(ApprovalStatus.TIMEOUT.name());
            }
            return request;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Optional<PendingRequestInfo> optional = pendingRequestStore.findByRequestId(requestId);
            if (optional.isPresent()) {
                PendingRequestInfo request = optional.get();
                pendingRequestStore.updateStatus(requestId, ApprovalStatus.TIMEOUT.name(), null, null, null);
                request.setStatus(ApprovalStatus.TIMEOUT.name());
                return request;
            }
            return null;
        } finally {
            latchMap.remove(requestId);
        }
    }

    /**
     * 清理过期请求
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredRequests() {
        List<PendingRequestInfo> expired = pendingRequestStore.findExpiredRequests();
        for (PendingRequestInfo request : expired) {
            pendingRequestStore.updateStatus(request.getRequestId(), ApprovalStatus.TIMEOUT.name(), null, null, null);
            CountDownLatch latch = latchMap.get(request.getRequestId());
            if (latch != null) {
                latch.countDown();
            }
            eventPublisher.publishEvent(new ApprovalResolvedEvent(
                    this, request.getRequestId(), request.getSessionId(),
                    ApprovalStatus.TIMEOUT, null, "审批超时"));
            log.info("清理过期审批请求: requestId={}", request.getRequestId());
        }
    }
}
