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
package com.yangqiongai.ai.workflow.executor;

import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowTimeResumeRecovery 单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WorkflowTimeResumeRecoveryTest {

    @Mock
    private WorkflowStateStore stateStore;

    @Mock
    private TimeControlNodeHandler timeControlNodeHandler;

    private WorkflowTimeResumeRecovery recovery;

    @BeforeEach
    void setUp() {
        recovery = new WorkflowTimeResumeRecovery(stateStore, timeControlNodeHandler);
    }

    // ==================== 辅助方法 ====================

    private WorkflowState buildPausedState(String instanceId, String nodeId, Long resumeAt) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(instanceId);
        state.setPausedNodeId(nodeId);
        if (resumeAt != null) {
            state.setVariable("timeResumeAt:" + nodeId, resumeAt);
        }
        return state;
    }

    // ==================== 启动恢复扫描 ====================

    @Test
    @DisplayName("扫描到带恢复时间变量的暂停实例并重新调度")
    void recoversPausedInstance() {
        long resumeAt = System.currentTimeMillis() + 60_000;
        when(stateStore.listPaused()).thenReturn(List.of(
                buildPausedState("inst-1", "time-node", resumeAt)
        ));

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler).scheduleResumeEvent("inst-1", "time-node", resumeAt);
    }

    @Test
    @DisplayName("恢复时间已过期的实例同样重新调度（0延迟立即恢复）")
    void recoversExpiredInstance() {
        long resumeAt = System.currentTimeMillis() - 60_000;
        when(stateStore.listPaused()).thenReturn(List.of(
                buildPausedState("inst-2", "time-node", resumeAt)
        ));

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler).scheduleResumeEvent("inst-2", "time-node", resumeAt);
    }

    @Test
    @DisplayName("无恢复时间变量的暂停实例跳过")
    void skipsStateWithoutResumeVariable() {
        when(stateStore.listPaused()).thenReturn(List.of(
                buildPausedState("inst-3", "approval-node", null)
        ));

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler, never()).scheduleResumeEvent(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("恢复变量类型非法时跳过")
    void skipsStateWithIllegalVariableType() {
        WorkflowState state = buildPausedState("inst-4", "time-node", null);
        state.setVariable("timeResumeAt:time-node", "not-a-number");
        when(stateStore.listPaused()).thenReturn(List.of(state));

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler, never()).scheduleResumeEvent(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("暂停节点ID为空时跳过")
    void skipsStateWithBlankPausedNodeId() {
        when(stateStore.listPaused()).thenReturn(List.of(
                buildPausedState("inst-5", "", 123L)
        ));

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler, never()).scheduleResumeEvent(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("无暂停实例时不调度")
    void handlesEmptyPausedList() {
        when(stateStore.listPaused()).thenReturn(Collections.emptyList());

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler, never()).scheduleResumeEvent(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("存储查询异常不向外传播")
    void swallowsStoreException() {
        when(stateStore.listPaused()).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> recovery.recoverOnStartup()).doesNotThrowAnyException();

        verify(timeControlNodeHandler, never()).scheduleResumeEvent(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("多个暂停实例全部调度")
    void recoversMultipleInstances() {
        long now = System.currentTimeMillis();
        when(stateStore.listPaused()).thenReturn(List.of(
                buildPausedState("inst-a", "node-a", now + 1000),
                buildPausedState("inst-b", "node-b", now + 2000),
                buildPausedState("inst-c", "node-c", null)
        ));

        recovery.recoverOnStartup();

        verify(timeControlNodeHandler).scheduleResumeEvent(eq("inst-a"), eq("node-a"), anyLong());
        verify(timeControlNodeHandler).scheduleResumeEvent(eq("inst-b"), eq("node-b"), anyLong());
        verify(timeControlNodeHandler, never()).scheduleResumeEvent(eq("inst-c"), anyString(), anyLong());
    }
}
