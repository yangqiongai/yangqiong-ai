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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 人工审批确认结果单元测试
 * @author yangqiong
 */
class ConfirmResultTest {

    @Test
    @DisplayName("按工具调用ID的批准结果应携带ID且状态为批准")
    void approveCall_shouldCarryToolCallId() {
        ConfirmResult result = ConfirmResult.approveCall("tc-1", "search");
        assertThat(result.getToolCallId()).isEqualTo("tc-1");
        assertThat(result.getToolName()).isEqualTo("search");
        assertThat(result.isApproved()).isTrue();
        assertThat(result.getReason()).isNull();
    }

    @Test
    @DisplayName("按工具调用ID的拒绝结果应携带ID与理由")
    void denyCall_shouldCarryToolCallIdAndReason() {
        ConfirmResult result = ConfirmResult.denyCall("tc-2", "delete", "高危操作不允许");
        assertThat(result.getToolCallId()).isEqualTo("tc-2");
        assertThat(result.getToolName()).isEqualTo("delete");
        assertThat(result.isApproved()).isFalse();
        assertThat(result.getReason()).isEqualTo("高危操作不允许");
    }

    @Test
    @DisplayName("按名称的批准结果应回退为空ID")
    void approve_byName_shouldFallbackNullToolCallId() {
        ConfirmResult result = ConfirmResult.approve("search");
        assertThat(result.getToolCallId()).isNull();
        assertThat(result.getToolName()).isEqualTo("search");
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    @DisplayName("三参构造不携带工具调用ID")
    void threeArgConstructor_shouldHaveNullToolCallId() {
        ConfirmResult result = new ConfirmResult("search", false, "不批准");
        assertThat(result.getToolCallId()).isNull();
        assertThat(result.getToolName()).isEqualTo("search");
        assertThat(result.isApproved()).isFalse();
        assertThat(result.getReason()).isEqualTo("不批准");
    }

    @Test
    @DisplayName("全参构造应保留全部字段")
    void fullConstructor_shouldKeepAllFields() {
        ConfirmResult result = new ConfirmResult("tc-3", "write", true, null);
        assertThat(result.getToolCallId()).isEqualTo("tc-3");
        assertThat(result.getToolName()).isEqualTo("write");
        assertThat(result.isApproved()).isTrue();
        assertThat(result.getReason()).isNull();
    }
}
