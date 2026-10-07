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
package com.yangqiongai.ai.approval.repository;

import com.yangqiongai.ai.approval.model.PendingRequestInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 待审批请求
 * @author yangqiong
 */
public interface PendingRequestRepository {

    /**
     * 保存待审批请求
     * @param request
     * @return
     */
    void save(PendingRequestInfo request);

    /**
     * 根据请求ID查找
     * @param requestId
     * @return
     */
    Optional<PendingRequestInfo> findByRequestId(String requestId);

    /**
     * 根据审批令牌查找
     * @param token
     * @return
     */
    Optional<PendingRequestInfo> findByApprovalToken(String token);

    /**
     * 更新审批状态
     * @param requestId
     * @param status
     * @param resolvedBy
     * @param rejectReason
     * @param responsePayload
     */
    void updateStatus(String requestId, String status, String resolvedBy,
                      String rejectReason, String responsePayload);

    /**
     * 按期望状态条件更新审批状态（CAS语义，仅期望状态匹配时生效）
     * @param requestId
     * @param expectedStatus
     * @param status
     * @param resolvedBy
     * @param rejectReason
     * @param responsePayload
     * @return 更新行数(0表示状态已被并发变更)
     */
    int casUpdateStatus(String requestId, String expectedStatus, String status, String resolvedBy,
                        String rejectReason, String responsePayload);

    /**
     * 回滚审批状态至待审批（用于SLA升级占坑后创建下一级失败的兜底恢复）
     * @param requestId
     * @return 更新行数
     */
    int revertToPending(String requestId);

    /**
     * 更新过期时间
     * @param requestId
     * @param expireTime
     */
    void updateExpireTime(String requestId, LocalDateTime expireTime);

    /**
     * 查询已过期的待审批请求
     * @return
     */
    List<PendingRequestInfo> findExpiredRequests();

    /**
     * 查询待审批请求列表
     * @param userId
     * @return
     */
    List<PendingRequestInfo> findPendingRequests(String userId);

    /**
     * 按审批人查询待审批请求列表（approver与存储值精确匹配，多审批人由调用方拆分）
     * @param scopeId 作用域ID，为空时不过滤
     * @param approver
     * @return
     */
    List<PendingRequestInfo> findPendingRequestsByApprover(String scopeId, String approver);
}
