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

import org.springframework.context.ApplicationEvent;

/**
 * 审批请求事件
 * <p>
 * 当 @Suspendable 标注的方法触发审批门控时发布此事件。
 * {@link ApprovalEventBridge} 监听此事件，将审批请求推送到SSE流，通知前端进行审批操作。
 * </p>
 * <p>
 * {@code inputSchema} 描述了需要审批人提供的信息结构（选项列表、表单字段），
 * 前端据此渲染对应的交互界面。为空时为简单确认模式。
 * </p>
 * <p>
 * 定位说明：本包为 core 的 Spring 应用事件包，仅服务于 @Suspendable AOP 审批 SSE 桥接，
 * 不进入运行时 AgentEvent 事件流（运行时事件见 com.yangqiongai.ai.agent.runtime.event）。
 * </p>
 *
 * @author yangqiong
 */
public class ApprovalRequiredEvent extends ApplicationEvent {

    private final String requestId;
    private final String sessionId;
    private final String userId;
    private final String resourceType;
    private final String targetName;
    private final String reason;
    private final int timeoutSeconds;

    /**
     * 输入模式定义（JSON），描述需要审批人提供的信息结构
     */
    private final String inputSchema;

    /**
     * @param source
     * @param requestId
     * @param sessionId
     * @param userId
     * @param resourceType
     * @param targetName
     * @param reason
     * @param timeoutSeconds
     * @param inputSchema
     */
    public ApprovalRequiredEvent(Object source, String requestId, String sessionId,
                                  String userId, String resourceType, String targetName,
                                  String reason, int timeoutSeconds, String inputSchema) {
        super(source);
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.resourceType = resourceType;
        this.targetName = targetName;
        this.reason = reason;
        this.timeoutSeconds = timeoutSeconds;
        this.inputSchema = inputSchema;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    /**
     * 转换为SSE推送的JSON格式payload
     */
    public String toPayload() {
        return "{\"requestId\":\"" + escapeJson(requestId) + "\","
                + "\"sessionId\":\"" + escapeJson(sessionId) + "\","
                + "\"userId\":\"" + escapeJson(userId != null ? userId : "") + "\","
                + "\"resourceType\":\"" + escapeJson(resourceType != null ? resourceType : "") + "\","
                + "\"targetName\":\"" + escapeJson(targetName) + "\","
                + "\"reason\":\"" + escapeJson(reason != null ? reason : "") + "\","
                + "\"timeoutSeconds\":" + timeoutSeconds + ","
                + "\"inputSchema\":" + (inputSchema != null && !inputSchema.isEmpty()
                        ? "\"" + escapeJson(inputSchema) + "\"" : "null") + "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
