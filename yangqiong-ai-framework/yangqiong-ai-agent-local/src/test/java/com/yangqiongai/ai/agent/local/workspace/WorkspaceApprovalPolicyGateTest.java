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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作区引擎审批策略门测试
 * @author yangqiong
 */
class WorkspaceApprovalPolicyGateTest {

    /**
     * AUTO层级：删除类破坏性工具转人工审批
     */
    @Test
    void autoModeAsksForDeleteTools() {
        WorkspaceApprovalPolicyGate gate = new WorkspaceApprovalPolicyGate("AUTO");

        assertThat(gate.evaluate("file_delete", "call-1", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("delete_user_data", "call-2", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("remove_temp", "call-3", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("drop_table_cache", "call-4", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * AUTO层级：写入与读类工具放行交AI自动审批
     */
    @Test
    void autoModeAllowsWriteAndReadTools() {
        WorkspaceApprovalPolicyGate gate = new WorkspaceApprovalPolicyGate("AUTO");

        assertThat(gate.evaluate("file_write", "call-1", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(gate.evaluate("excel_write", "call-2", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(gate.evaluate("file_read", "call-3", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(gate.evaluate("write_report", "call-4", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    /**
     * MANUAL层级：写删类工具全部转人工审批
     */
    @Test
    void manualModeAsksForWriteAndDeleteTools() {
        WorkspaceApprovalPolicyGate gate = new WorkspaceApprovalPolicyGate("MANUAL");

        assertThat(gate.evaluate("file_write", "call-1", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("file_delete", "call-2", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("edit_doc", "call-3", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
    }

    /**
     * MANUAL层级：读类工具放行
     */
    @Test
    void manualModeAllowsReadTools() {
        WorkspaceApprovalPolicyGate gate = new WorkspaceApprovalPolicyGate("MANUAL");

        assertThat(gate.evaluate("file_read", "call-1", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    /**
     * CUSTOM层级：与MANUAL一致的写删类裁决
     */
    @Test
    void customModeAsksForWriteAndDeleteTools() {
        WorkspaceApprovalPolicyGate gate = new WorkspaceApprovalPolicyGate("CUSTOM");

        assertThat(gate.evaluate("file_write", "call-1", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("file_delete", "call-2", null, null, null))
                .isEqualTo(AgentPermissionDecision.ASK);
        assertThat(gate.evaluate("file_read", "call-3", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }

    /**
     * FULL_ACCESS层级：全部放行
     */
    @Test
    void fullAccessModeAllowsAllTools() {
        WorkspaceApprovalPolicyGate gate = new WorkspaceApprovalPolicyGate("FULL_ACCESS");

        assertThat(gate.evaluate("file_delete", "call-1", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(gate.evaluate("file_write", "call-2", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
        assertThat(gate.evaluate("file_read", "call-3", null, null, null))
                .isEqualTo(AgentPermissionDecision.ALLOW);
    }
}
