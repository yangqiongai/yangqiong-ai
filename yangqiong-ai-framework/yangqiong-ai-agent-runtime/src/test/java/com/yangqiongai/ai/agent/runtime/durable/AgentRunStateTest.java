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
package com.yangqiongai.ai.agent.runtime.durable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 运行状态机单元测试
 * @author yangqiong
 */
class AgentRunStateTest {

    @Nested
    @DisplayName("终态判断")
    class TerminalTest {

        @Test
        @DisplayName("三个终态返回true")
        void isTerminal_terminalStates_shouldReturnTrue() {
            assertThat(AgentRunState.SUCCEEDED.isTerminal()).isTrue();
            assertThat(AgentRunState.FAILED.isTerminal()).isTrue();
            assertThat(AgentRunState.CANCELLED.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("非终态返回false")
        void isTerminal_nonTerminalStates_shouldReturnFalse() {
            assertThat(AgentRunState.CREATED.isTerminal()).isFalse();
            assertThat(AgentRunState.RUNNING.isTerminal()).isFalse();
            assertThat(AgentRunState.WAITING_APPROVAL.isTerminal()).isFalse();
        }
    }

    @Nested
    @DisplayName("迁移规则")
    class TransitionTest {

        @Test
        @DisplayName("CREATED只允许迁移到RUNNING或CANCELLED")
        void canTransitionTo_fromCreated_shouldOnlyAllowRunningOrCancelled() {
            assertThat(AgentRunState.CREATED.canTransitionTo(AgentRunState.RUNNING)).isTrue();
            assertThat(AgentRunState.CREATED.canTransitionTo(AgentRunState.CANCELLED)).isTrue();
            assertThat(AgentRunState.CREATED.canTransitionTo(AgentRunState.SUCCEEDED)).isFalse();
            assertThat(AgentRunState.CREATED.canTransitionTo(AgentRunState.FAILED)).isFalse();
            assertThat(AgentRunState.CREATED.canTransitionTo(AgentRunState.WAITING_APPROVAL)).isFalse();
        }

        @Test
        @DisplayName("RUNNING允许迁移到等待审批或三个终态")
        void canTransitionTo_fromRunning_shouldAllowWaitingAndTerminals() {
            assertThat(AgentRunState.RUNNING.canTransitionTo(AgentRunState.WAITING_APPROVAL)).isTrue();
            assertThat(AgentRunState.RUNNING.canTransitionTo(AgentRunState.SUCCEEDED)).isTrue();
            assertThat(AgentRunState.RUNNING.canTransitionTo(AgentRunState.FAILED)).isTrue();
            assertThat(AgentRunState.RUNNING.canTransitionTo(AgentRunState.CANCELLED)).isTrue();
            assertThat(AgentRunState.RUNNING.canTransitionTo(AgentRunState.CREATED)).isFalse();
        }

        @Test
        @DisplayName("WAITING_APPROVAL只允许迁移到RUNNING或CANCELLED")
        void canTransitionTo_fromWaitingApproval_shouldOnlyAllowRunningOrCancelled() {
            assertThat(AgentRunState.WAITING_APPROVAL.canTransitionTo(AgentRunState.RUNNING)).isTrue();
            assertThat(AgentRunState.WAITING_APPROVAL.canTransitionTo(AgentRunState.CANCELLED)).isTrue();
            assertThat(AgentRunState.WAITING_APPROVAL.canTransitionTo(AgentRunState.SUCCEEDED)).isFalse();
            assertThat(AgentRunState.WAITING_APPROVAL.canTransitionTo(AgentRunState.FAILED)).isFalse();
        }

        @Test
        @DisplayName("终态不可迁移到任何状态")
        void canTransitionTo_fromTerminal_shouldRejectAll() {
            for (AgentRunState target : AgentRunState.values()) {
                assertThat(AgentRunState.SUCCEEDED.canTransitionTo(target)).isFalse();
                assertThat(AgentRunState.FAILED.canTransitionTo(target)).isFalse();
                assertThat(AgentRunState.CANCELLED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        @DisplayName("null与同状态迁移被拒绝")
        void canTransitionTo_nullOrSame_shouldReject() {
            assertThat(AgentRunState.RUNNING.canTransitionTo(null)).isFalse();
            assertThat(AgentRunState.RUNNING.canTransitionTo(AgentRunState.RUNNING)).isFalse();
        }
    }

    @Nested
    @DisplayName("运行记录迁移")
    class RecordTransitionTest {

        @Test
        @DisplayName("合法迁移成功并递增版本")
        void transitionTo_valid_shouldUpdateStateAndVersion() {
            AgentRunRecord record = new AgentRunRecord("r1", "s1", "c1", "u1", "agent", AgentRunState.CREATED, 1000L);
            record.transitionTo(AgentRunState.RUNNING, "启动");
            assertThat(record.getState()).isEqualTo(AgentRunState.RUNNING);
            assertThat(record.getVersion()).isEqualTo(1);
            assertThat(record.getUpdatedAt()).isGreaterThanOrEqualTo(1000L);
            assertThat(record.getTransitions()).hasSize(1);
        }

        @Test
        @DisplayName("FAILED迁移写入错误原因")
        void transitionTo_failed_shouldWriteError() {
            AgentRunRecord record = new AgentRunRecord("r1", "s1", "c1", "u1", "agent", AgentRunState.RUNNING, 1000L);
            record.transitionTo(AgentRunState.FAILED, "模型超时");
            assertThat(record.getState()).isEqualTo(AgentRunState.FAILED);
            assertThat(record.getError()).isEqualTo("模型超时");
        }

        @Test
        @DisplayName("非法迁移抛出异常且状态不变")
        void transitionTo_invalid_shouldThrowAndKeepState() {
            AgentRunRecord record = new AgentRunRecord("r1", "s1", "c1", "u1", "agent", AgentRunState.CREATED, 1000L);
            assertThatThrownBy(() -> record.transitionTo(AgentRunState.SUCCEEDED, "跳级"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("非法状态迁移");
            assertThat(record.getState()).isEqualTo(AgentRunState.CREATED);
            assertThat(record.getVersion()).isEqualTo(0);
        }

        @Test
        @DisplayName("终态后任何迁移都被拒绝")
        void transitionFrom_terminal_shouldThrow() {
            AgentRunRecord record = new AgentRunRecord("r1", "s1", "c1", "u1", "agent", AgentRunState.RUNNING, 1000L);
            record.transitionTo(AgentRunState.SUCCEEDED, "完成");
            assertThatThrownBy(() -> record.transitionTo(AgentRunState.RUNNING, "复活"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("乐观锁")
    class CasUpdateTest {

        @Test
        @DisplayName("版本匹配时执行更新并递增版本")
        void casUpdate_versionMatched_shouldApplyAndIncrement() {
            AgentRunRecord record = new AgentRunRecord("r1", "s1", "c1", "u1", "agent", AgentRunState.RUNNING, 1000L);
            boolean applied = record.casUpdate(0, () -> record.setError("更新"));
            assertThat(applied).isTrue();
            assertThat(record.getError()).isEqualTo("更新");
            assertThat(record.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("版本不匹配时返回false且不执行更新")
        void casUpdate_versionMismatched_shouldReject() {
            AgentRunRecord record = new AgentRunRecord("r1", "s1", "c1", "u1", "agent", AgentRunState.RUNNING, 1000L);
            boolean applied = record.casUpdate(99, () -> record.setError("不该生效"));
            assertThat(applied).isFalse();
            assertThat(record.getError()).isNull();
            assertThat(record.getVersion()).isEqualTo(0);
        }
    }
}
