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
package com.yangqiongai.ai.agent.local.workspace;

import com.yangqiongai.ai.agent.runtime.config.AgentPermissionDecision;
import com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate;

import java.util.Map;
import java.util.Set;

/**
 * 工作区引擎审批策略门
 * <p>
 * 引擎审批实现方式下的统一裁决入口：MANUAL/CUSTOM层级写删类工具ASK暂停等待人工确认；
 * AUTO层级删除类破坏性工具ASK等待人工确认（写入类放行交AI自动审批）；FULL_ACCESS全部放行。
 * 裁决由引擎triage与工具执行共用，已批准调用的放行由引擎审批台账豁免（ToolExecutor）。
 * </p>
 * @author yangqiong
 */
public class WorkspaceApprovalPolicyGate implements ToolPolicyGate {

    /**
     * 工作区写删类工具清单（读类工具file_read/excel_read/docx_read/chart_render不在其列）
     */
    private static final Set<String> WRITE_TOOLS = Set.of(
            "file_write", "file_delete", "excel_write", "excel_edit",
            "docx_write", "docx_edit", "zip_pack", "zip_unpack");

    /**
     * 工作区审批层级（MANUAL/AUTO/FULL_ACCESS/CUSTOM）
     */
    private final String approvalMode;

    public WorkspaceApprovalPolicyGate(String approvalMode) {
        this.approvalMode = approvalMode;
    }

    /**
     * 按工具名裁决权限
     * @param toolName
     * @param toolCallId
     * @param scopeId
     * @param runId
     * @param toolInput
     * @return
     */
    @Override
    public AgentPermissionDecision evaluate(String toolName, String toolCallId, String scopeId,
                                            String runId, Map<String, Object> toolInput) {
        if (requiresApproval(toolName)) {
            return AgentPermissionDecision.ASK;
        }
        return AgentPermissionDecision.ALLOW;
    }

    /**
     * 判断工具是否需要人工确认
     * @param toolName
     * @return
     */
    private boolean requiresApproval(String toolName) {
        if ("AUTO".equalsIgnoreCase(approvalMode)) {
            // AUTO层级仅删除类破坏性操作强制人工审批卡片确认，其余写入类交AI自动审批
            return isDestructiveTool(toolName);
        }
        if (!"MANUAL".equalsIgnoreCase(approvalMode) && !"CUSTOM".equalsIgnoreCase(approvalMode)) {
            return false;
        }
        if (WRITE_TOOLS.contains(toolName)) {
            return true;
        }
        // 按前缀兜底覆盖其他写删类工具（delete_/remove_/drop_/write_/edit_/exec_）
        return isDestructiveTool(toolName) || toolName != null && (toolName.startsWith("write_")
                || toolName.startsWith("edit_") || toolName.startsWith("exec_"));
    }

    /**
     * 判断删除类破坏性工具
     * @param toolName
     * @return
     */
    private boolean isDestructiveTool(String toolName) {
        if (toolName == null) {
            return false;
        }
        return "file_delete".equals(toolName) || toolName.startsWith("delete_")
                || toolName.startsWith("remove_") || toolName.startsWith("drop_");
    }
}
