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

import com.yangqiongai.ai.approval.model.PendingRequestInfo;
import com.yangqiongai.ai.approval.repository.PendingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 待审批请求持久化
 * @author yangqiong
 */
@Repository
public class PendingRequestStore {

    private static final Logger log = LoggerFactory.getLogger(PendingRequestStore.class);

    @Autowired(required = false)
    private PendingRequestRepository pendingRequestRepository;

    /**
     * 保存待审批请求
     * @param request
     * @return
     */
    public void save(PendingRequestInfo request) {
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());
        pendingRequestRepository.save(request);
        log.info("保存审批请求: requestId={}, targetName={}", request.getRequestId(), request.getTargetName());
    }

    /**
     * 根据请求ID查找
     * @param requestId
     * @return
     */
    public Optional<PendingRequestInfo> findByRequestId(String requestId) {
        return pendingRequestRepository.findByRequestId(requestId);
    }

    /**
     * 根据审批令牌查找
     * @param token
     * @return
     */
    public Optional<PendingRequestInfo> findByApprovalToken(String token) {
        return pendingRequestRepository.findByApprovalToken(token);
    }

    /**
     * 更新审批状态
     * @param requestId
     * @param status
     * @param resolvedBy
     * @param rejectReason
     * @param responsePayload
     */
    public void updateStatus(String requestId, String status, String resolvedBy,
                             String rejectReason, String responsePayload) {
        pendingRequestRepository.updateStatus(requestId, status, resolvedBy, rejectReason, responsePayload);
        log.info("更新审批状态: requestId={}, status={}", requestId, status);
    }

    /**
     * 按期望状态条件更新审批状态（CAS语义，用于多实例并发防护）
     * @param requestId
     * @param expectedStatus
     * @param status
     * @param resolvedBy
     * @param rejectReason
     * @param responsePayload
     * @return 更新行数(0表示状态已被并发变更)
     */
    public int casUpdateStatus(String requestId, String expectedStatus, String status, String resolvedBy,
                               String rejectReason, String responsePayload) {
        int updated = pendingRequestRepository.casUpdateStatus(requestId, expectedStatus, status,
                resolvedBy, rejectReason, responsePayload);
        if (updated > 0) {
            log.info("CAS更新审批状态: requestId={}, {}->{}", requestId, expectedStatus, status);
        }
        return updated;
    }

    /**
     * 回滚审批状态至待审批
     * @param requestId
     * @return 更新行数
     */
    public int revertToPending(String requestId) {
        return pendingRequestRepository.revertToPending(requestId);
    }

    /**
     * 更新过期时间
     * @param requestId
     * @param expireTime
     */
    public void updateExpireTime(String requestId, LocalDateTime expireTime) {
        pendingRequestRepository.updateExpireTime(requestId, expireTime);
    }

    /**
     * 查询已过期的待审批请求
     * @return
     */
    public List<PendingRequestInfo> findExpiredRequests() {
        return pendingRequestRepository.findExpiredRequests();
    }

    /**
     * 查询待审批请求列表，userId为null时返回全部
     * @param userId
     * @return
     */
    public List<PendingRequestInfo> findPendingRequests(String userId) {
        return pendingRequestRepository.findPendingRequests(userId);
    }

    /**
     * 按审批人查询待审批请求列表
     * @param scopeId 作用域ID，为空时不过滤
     * @param approver
     * @return
     */
    public List<PendingRequestInfo> findPendingRequestsByApprover(String scopeId, String approver) {
        return pendingRequestRepository.findPendingRequestsByApprover(scopeId, approver);
    }
}
