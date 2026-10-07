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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批请求（编程式API）
 * <p>
 * 用于在业务代码中动态发起审批，支持运行时根据上下文决定是否需要审批、
 * 提供什么选项、收集什么信息。与 {@link Suspendable} 注解的静态声明方式互补。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 简单确认
 * ApprovalResponse resp = approvalGate.requestApproval(
 *     ApprovalRequest.builder()
 *         .sessionId(sessionId)
 *         .userId(userId)
 *         .resourceType("TOOL")
 *         .targetName("deleteData")
 *         .reason("批量删除操作，涉及" + count + "条数据")
 *         .build()
 * );
 * if (resp.isApproved()) {
 *     // 执行删除
 * }
 *
 * // 多选项 + 表单输入
 * ApprovalResponse resp = approvalGate.requestApproval(
 *     ApprovalRequest.builder()
 *         .sessionId(sessionId)
 *         .userId(userId)
 *         .resourceType("WORKFLOW_NODE")
 *         .targetName("mergeData")
 *         .reason("发现数据冲突，请选择合并策略")
 *         .options(List.of("覆盖目标", "保留两者", "取消合并"))
 *         .inputFields(List.of("remark"))
 *         .timeout(Duration.ofMinutes(10))
 *         .build()
 * );
 * if (resp.isApproved()) {
 *     String strategy = resp.getSelectedOption();
 *     String remark = resp.getField("remark");
 *     // 根据 strategy 和 remark 执行合并
 * }
 * }</pre>
 *
 * @author yangqiong
 */
public class ApprovalRequest {

    private final String sessionId;
    private final String userId;
    private final String approver;
    private final String resourceType;
    private final String targetName;
    private final String reason;
    private final Map<String, Object> params;
    private final List<String> options;
    private final List<String> inputFields;
    private final Duration timeout;

    private ApprovalRequest(Builder builder) {
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.approver = builder.approver;
        this.resourceType = builder.resourceType;
        this.targetName = builder.targetName;
        this.reason = builder.reason;
        this.params = builder.params;
        this.options = builder.options;
        this.inputFields = builder.inputFields;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getApprover() {
        return approver;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public List<String> getOptions() {
        return options;
    }

    public List<String> getInputFields() {
        return inputFields;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * 是否有交互式输入（options或inputFields非空）
     */
    public boolean hasInteractiveInput() {
        return (options != null && !options.isEmpty()) || (inputFields != null && !inputFields.isEmpty());
    }

    /**
     * ApprovalRequest构建器
     */
    public static class Builder {

        private String sessionId;
        private String userId;
        private String approver;
        private String resourceType = "CUSTOM";
        private String targetName;
        private String reason = "";
        private Map<String, Object> params = new HashMap<>();
        private List<String> options;
        private List<String> inputFields;
        private Duration timeout = Duration.ofMinutes(5);

        /**
         * 设置会话ID（必填）
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * 设置用户ID（必填）
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置审批人（user:xxx/user:xxx,yyy/role:xxx，缺省时按发起人处理）
         */
        public Builder approver(String approver) {
            this.approver = approver;
            return this;
        }

        /**
         * 设置资源类型（默认CUSTOM）
         */
        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        /**
         * 设置目标名称（如方法名、节点名）
         */
        public Builder targetName(String targetName) {
            this.targetName = targetName;
            return this;
        }

        /**
         * 设置审批原因
         */
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * 设置请求参数
         */
        public Builder params(Map<String, Object> params) {
            this.params = params != null ? params : new HashMap<>();
            return this;
        }

        /**
         * 添加单个参数
         */
        public Builder addParam(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        /**
         * 设置供审批人选择的选项列表
         */
        public Builder options(List<String> options) {
            this.options = options;
            return this;
        }

        /**
         * 设置需要审批人填写的字段名列表
         */
        public Builder inputFields(List<String> inputFields) {
            this.inputFields = inputFields;
            return this;
        }

        /**
         * 设置超时时间（默认5分钟）
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 构建ApprovalRequest
         */
        public ApprovalRequest build() {
            if (sessionId == null || sessionId.isEmpty()) {
                throw new IllegalArgumentException("sessionId不能为空");
            }
            if (targetName == null || targetName.isEmpty()) {
                throw new IllegalArgumentException("targetName不能为空");
            }
            return new ApprovalRequest(this);
        }
    }
}
