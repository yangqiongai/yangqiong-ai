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

import org.springframework.context.ApplicationEvent;

/**
 * 审批完成事件
 * <p>
 * 审批被通过、拒绝或超时时发布，工作流引擎监听此事件恢复暂停的工作流。
 * </p>
 * @author yangqiong
 */
public class ApprovalResolvedEvent extends ApplicationEvent {

    private final String requestId;

    private final String sessionId;

    private final ApprovalStatus status;

    private final String responsePayload;

    private final String rejectReason;

    /**
     * 审批完成事件
     * @param source
     * @param requestId
     * @param sessionId
     * @param status
     * @param responsePayload
     * @param rejectReason
     */
    public ApprovalResolvedEvent(Object source, String requestId, String sessionId,
                                  ApprovalStatus status, String responsePayload, String rejectReason) {
        super(source);
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.status = status;
        this.responsePayload = responsePayload;
        this.rejectReason = rejectReason;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public String getRejectReason() {
        return rejectReason;
    }
}
