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

import com.yangqiongai.ai.common.util.AiJsonUtils;

import java.util.Collections;
import java.util.Map;

/**
 * 审批响应
 * <p>
 * 封装审批结果，提供便捷方法获取审批人的选择和表单输入。
 * 由 {@link ApprovalGate#requestApproval(ApprovalRequest)} 返回。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ApprovalResponse resp = approvalGate.requestApproval(request);
 *
 * if (resp.isApproved()) {
 *     // CHOICE模式：获取审批人选择的选项
 *     String selected = resp.getSelectedOption();
 *
 *     // FORM模式：获取审批人填写的字段
 *     String remark = resp.getField("remark");
 *     Map<String, Object> allFields = resp.getFields();
 *
 *     // 获取原始响应数据
 *     Map<String, Object> raw = resp.getRawResponse();
 * } else if (resp.isRejected()) {
 *     String reason = resp.getRejectReason();
 * } else if (resp.isTimeout()) {
 *     // 超时处理
 * }
 * }</pre>
 *
 * @author yangqiong
 */
public class ApprovalResponse {

    private final String requestId;
    private final ApprovalStatus status;
    private final String rejectReason;
    private final Map<String, Object> rawResponse;

    /**
     * @param requestId
     * @param status
     * @param rejectReason
     * @param responsePayload
     */
    public ApprovalResponse(String requestId, ApprovalStatus status,
                            String rejectReason, String responsePayload) {
        this.requestId = requestId;
        this.status = status;
        this.rejectReason = rejectReason;
        this.rawResponse = parsePayload(responsePayload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return AiJsonUtils.fromJsonToMap(payload);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    /**
     * 审批是否通过
     */
    public boolean isApproved() {
        return status == ApprovalStatus.APPROVED;
    }

    /**
     * 审批是否拒绝
     */
    public boolean isRejected() {
        return status == ApprovalStatus.REJECTED;
    }

    /**
     * 审批是否超时
     */
    public boolean isTimeout() {
        return status == ApprovalStatus.TIMEOUT;
    }

    /**
     * 获取审批人选择的选项（CHOICE模式）
     */
    public String getSelectedOption() {
        Object option = rawResponse.get("selectedOption");
        return option != null ? option.toString() : null;
    }

    /**
     * 获取审批人填写的所有字段（FORM模式）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFields() {
        Object fields = rawResponse.get("fields");
        if (fields instanceof Map) {
            return (Map<String, Object>) fields;
        }
        return Collections.emptyMap();
    }

    /**
     * 获取审批人填写的指定字段（FORM模式）
     */
    public String getField(String name) {
        Map<String, Object> fields = getFields();
        Object value = fields.get(name);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取原始响应数据
     */
    public Map<String, Object> getRawResponse() {
        return rawResponse;
    }

    @Override
    public String toString() {
        return "ApprovalResponse{requestId='" + requestId + "', status=" + status
                + (rejectReason != null ? ", rejectReason='" + rejectReason + "'" : "")
                + (isApproved() && !rawResponse.isEmpty() ? ", response=" + rawResponse : "")
                + "}";
    }
}
