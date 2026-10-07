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

/**
 * 审批被拒绝异常
 * <p>
 * 当 @Suspendable 标注的方法审批被拒绝或超时时抛出。
 * </p>
 * @author yangqiong
 */
public class ApprovalRejectedException extends RuntimeException {

    private final String requestId;
    private final ApprovalStatus status;

    public ApprovalRejectedException(String requestId, ApprovalStatus status, String message) {
        super(message);
        this.requestId = requestId;
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    /**
     * 创建拒绝异常
     */
    public static ApprovalRejectedException rejected(String requestId, String reason) {
        return new ApprovalRejectedException(requestId, ApprovalStatus.REJECTED,
                "审批被拒绝: requestId=" + requestId + ", reason=" + reason);
    }

    /**
     * 创建超时异常
     */
    public static ApprovalRejectedException timeout(String requestId) {
        return new ApprovalRejectedException(requestId, ApprovalStatus.TIMEOUT,
                "审批超时: requestId=" + requestId);
    }
}
