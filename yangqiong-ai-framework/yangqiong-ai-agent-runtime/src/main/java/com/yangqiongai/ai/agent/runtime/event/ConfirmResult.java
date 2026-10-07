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
package com.yangqiongai.ai.agent.runtime.event;

/**
 * 人工审批确认结果
 * <p>
 * toolCallId 为精确匹配键（并行同名工具审批互不干扰），
 * 兼容保留 toolName 匹配：toolCallId 为空时回退按工具名匹配。
 * </p>
 * @author yangqiong
 */
public class ConfirmResult {

    /**
     * 工具调用ID（精确匹配键，可为空）
     */
    private final String toolCallId;

    /**
     * 工具名称
     */
    private final String toolName;

    /**
     * 是否批准
     */
    private final boolean approved;

    /**
     * 审批理由
     */
    private final String reason;

    public ConfirmResult(String toolName, boolean approved, String reason) {
        this(null, toolName, approved, reason);
    }

    /**
     * 全参构造
     * @param toolCallId
     * @param toolName
     * @param approved
     * @param reason
     */
    public ConfirmResult(String toolCallId, String toolName, boolean approved, String reason) {
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.approved = approved;
        this.reason = reason;
    }

    /**
     * 创建按工具调用ID的批准结果
     * @param toolCallId
     * @param toolName
     * @return
     */
    public static ConfirmResult approveCall(String toolCallId, String toolName) {
        return new ConfirmResult(toolCallId, toolName, true, null);
    }

    /**
     * 创建按工具调用ID的拒绝结果
     * @param toolCallId
     * @param toolName
     * @param reason
     * @return
     */
    public static ConfirmResult denyCall(String toolCallId, String toolName, String reason) {
        return new ConfirmResult(toolCallId, toolName, false, reason);
    }

    /**
     * 创建批准结果
     * @param toolName
     * @return
     */
    public static ConfirmResult approve(String toolName) {
        return new ConfirmResult(null, toolName, true, null);
    }

    /**
     * 创建拒绝结果
     * @param toolName
     * @param reason
     * @return
     */
    public static ConfirmResult deny(String toolName, String reason) {
        return new ConfirmResult(null, toolName, false, reason);
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 是否批准
     * @return
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * 获取审批理由
     * @return
     */
    public String getReason() {
        return reason;
    }
}
