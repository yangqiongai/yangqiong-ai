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

import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.spi.WorkflowInstanceReclaimedEvent;
import com.yangqiongai.ai.workflow.store.InMemoryWorkflowStateStore;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 工作流僵尸实例回收单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WorkflowZombieReclaimerTest {

    private WorkflowStateStore stateStore;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WorkflowZombieReclaimer reclaimer;

    @BeforeEach
    void setUp() {
        stateStore = new InMemoryWorkflowStateStore();
        reclaimer = new WorkflowZombieReclaimer(stateStore, eventPublisher, 600000L);
    }

    private WorkflowState saveRunningState(String instanceId, Long lastHeartbeatTime) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(instanceId);
        state.setDefinitionName("test-workflow");
        state.setStatus(ExecutionStatus.RUNNING);
        state.setCreateTime(System.currentTimeMillis());
        state.setUpdateTime(System.currentTimeMillis());
        state.setLastHeartbeatTime(lastHeartbeatTime);
        stateStore.save(state);
        return state;
    }

    @Test
    @DisplayName("心跳超时的运行中实例被回收为FAILED并发布事件")
    void reclaim_staleRunningInstance() {
        WorkflowState stale = saveRunningState("inst-stale", System.currentTimeMillis() - 700000L);
        NodeExecutionStatus runningNode = new NodeExecutionStatus();
        runningNode.setNodeId("node-1");
        runningNode.setStatus(ExecutionStatus.RUNNING);
        runningNode.setStartTime(System.currentTimeMillis() - 700000L);
        stale.setNodeState("node-1", runningNode);
        saveRunningState("inst-fresh", System.currentTimeMillis());

        int reclaimed = reclaimer.reclaimZombies();

        assertThat(reclaimed).isEqualTo(1);
        WorkflowState after = stateStore.load("inst-stale");
        assertThat(after.isFailed()).isTrue();
        assertThat(after.getVariable("errorMessage")).isEqualTo(WorkflowZombieReclaimer.RECLAIM_ERROR_MESSAGE);
        assertThat(after.getNodeState("node-1").isFailed()).isTrue();
        assertThat(after.getNodeState("node-1").getErrorMessage())
                .isEqualTo(WorkflowZombieReclaimer.RECLAIM_ERROR_MESSAGE);
        verify(eventPublisher).publishEvent(any(WorkflowInstanceReclaimedEvent.class));
    }

    @Test
    @DisplayName("心跳新鲜的运行中实例不被回收")
    void reclaim_freshRunningInstance() {
        saveRunningState("inst-fresh", System.currentTimeMillis());

        int reclaimed = reclaimer.reclaimZombies();

        assertThat(reclaimed).isEqualTo(0);
        assertThat(stateStore.load("inst-fresh").isRunning()).isTrue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("心跳为空时回退updateTime判定")
    void reclaim_fallbackToUpdateTime() {
        WorkflowState state = saveRunningState("inst-stale", null);
        state.setUpdateTime(System.currentTimeMillis() - 700000L);
        stateStore.save(state);

        int reclaimed = reclaimer.reclaimZombies();

        assertThat(reclaimed).isEqualTo(1);
        assertThat(stateStore.load("inst-stale").isFailed()).isTrue();
    }

    @Test
    @DisplayName("非运行中状态不参与回收")
    void reclaim_ignoresNonRunningStates() {
        WorkflowState paused = saveRunningState("inst-paused", System.currentTimeMillis() - 700000L);
        paused.setStatus(ExecutionStatus.PAUSED);
        stateStore.save(paused);
        WorkflowState completed = saveRunningState("inst-completed", System.currentTimeMillis() - 700000L);
        completed.setStatus(ExecutionStatus.COMPLETED);
        stateStore.save(completed);

        int reclaimed = reclaimer.reclaimZombies();

        assertThat(reclaimed).isEqualTo(0);
        assertThat(stateStore.load("inst-paused").isPaused()).isTrue();
        assertThat(stateStore.load("inst-completed").isCompleted()).isTrue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("无运行中实例时返回0")
    void reclaim_emptyStore() {
        assertThat(reclaimer.reclaimZombies()).isEqualTo(0);
        assertThat(stateStore.listRunning()).isEmpty();
    }

    @Test
    @DisplayName("listRunning仅返回运行中实例")
    void listRunning_filterByStatus() {
        saveRunningState("inst-running", System.currentTimeMillis());
        WorkflowState paused = saveRunningState("inst-paused", System.currentTimeMillis());
        paused.setStatus(ExecutionStatus.PAUSED);
        stateStore.save(paused);

        List<WorkflowState> running = stateStore.listRunning();

        assertThat(running).hasSize(1);
        assertThat(running.get(0).getInstanceId()).isEqualTo("inst-running");
    }

    @Test
    @DisplayName("scan捕获异常不影响下次调度")
    void scan_swallowsException() {
        WorkflowZombieReclaimer broken = new WorkflowZombieReclaimer(null, eventPublisher, 600000L);
        // store为null时scan内部捕获异常不外抛
        broken.scan();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
