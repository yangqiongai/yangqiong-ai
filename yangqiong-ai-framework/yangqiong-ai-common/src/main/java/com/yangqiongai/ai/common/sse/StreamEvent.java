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
package com.yangqiongai.ai.common.sse;

/**
 * 流式事件载体，区分文本增量、推理增量和工具调用增量
 * @author yangqiong
 */
public class StreamEvent {

    /**
     * 事件类型枚举
     */
    public enum Kind {

        /**
         * 可见文本增量
         */
        TEXT_DELTA,

        /**
         * 推理思考增量
         */
        THINKING_DELTA,

        /**
         * 工具调用增量
         */
        TOOL_CALL_DELTA,

        /**
         * 工具审批请求（需要人工确认）
         */
        APPROVAL_REQUIRED,

        /**
         * 用户澄清请求（需要用户补充信息）
         */
        CLARIFICATION_REQUIRED,

        /**
         * 引擎工具确认请求（需要人工批准后继续执行）
         */
        CONFIRM_REQUIRED,

        /**
         * 预算告警（Token/成本超阈值或超限）
         */
        BUDGET_WARNING
    }

    private final Kind kind;

    private final String payload;

    private StreamEvent(Kind kind, String payload) {
        this.kind = kind;
        this.payload = payload;
    }

    /**
     * 创建可见文本增量事件
     * @param text
     * @return
     */
    public static StreamEvent textDelta(String text) {
        return new StreamEvent(Kind.TEXT_DELTA, text);
    }

    /**
     * 创建推理思考增量事件
     * @param text
     * @return
     */
    public static StreamEvent thinkingDelta(String text) {
        return new StreamEvent(Kind.THINKING_DELTA, text);
    }

    /**
     * 创建工具调用增量事件
     * @param text
     * @return
     */
    public static StreamEvent toolCallDelta(String text) {
        return new StreamEvent(Kind.TOOL_CALL_DELTA, text);
    }

    /**
     * 创建审批请求事件
     * @param approvalPayload 审批信息JSON（包含requestId、toolName、reason、timeoutSeconds）
     * @return
     */
    public static StreamEvent approvalRequired(String approvalPayload) {
        return new StreamEvent(Kind.APPROVAL_REQUIRED, approvalPayload);
    }

    /**
     * 创建用户澄清请求事件
     * @param clarificationPayload 澄清信息JSON（包含question、toolCallId）
     * @return
     */
    public static StreamEvent clarificationRequired(String clarificationPayload) {
        return new StreamEvent(Kind.CLARIFICATION_REQUIRED, clarificationPayload);
    }

    /**
     * 创建引擎工具确认请求事件
     * @param confirmPayload 确认信息JSON（包含requestId、toolCalls待确认工具清单）
     * @return
     */
    public static StreamEvent confirmRequired(String confirmPayload) {
        return new StreamEvent(Kind.CONFIRM_REQUIRED, confirmPayload);
    }

    /**
     * 创建预算告警事件
     * @param budgetPayload 预算告警JSON（包含budgetType、level、message）
     * @return
     */
    public static StreamEvent budgetWarning(String budgetPayload) {
        return new StreamEvent(Kind.BUDGET_WARNING, budgetPayload);
    }

    /**
     * 获取事件类型
     * @return
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * 获取事件载荷
     * @return
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 是否为文本增量事件
     * @return
     */
    public boolean isTextDelta() {
        return kind == Kind.TEXT_DELTA;
    }

    /**
     * 是否为推理增量事件
     * @return
     */
    public boolean isThinkingDelta() {
        return kind == Kind.THINKING_DELTA;
    }

    /**
     * 是否为工具调用增量事件
     * @return
     */
    public boolean isToolCallDelta() {
        return kind == Kind.TOOL_CALL_DELTA;
    }

    /**
     * 是否为审批请求事件
     * @return
     */
    public boolean isApprovalRequired() {
        return kind == Kind.APPROVAL_REQUIRED;
    }

    /**
     * 是否为用户澄清请求事件
     * @return
     */
    public boolean isClarificationRequired() {
        return kind == Kind.CLARIFICATION_REQUIRED;
    }

    /**
     * 是否为引擎工具确认请求事件
     * @return
     */
    public boolean isConfirmRequired() {
        return kind == Kind.CONFIRM_REQUIRED;
    }

    /**
     * 是否为预算告警事件
     * @return
     */
    public boolean isBudgetWarning() {
        return kind == Kind.BUDGET_WARNING;
    }
}
