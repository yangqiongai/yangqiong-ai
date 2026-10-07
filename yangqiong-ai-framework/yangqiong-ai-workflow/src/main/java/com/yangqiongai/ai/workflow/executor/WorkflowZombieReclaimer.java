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
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流僵尸实例回收
 * <p>
 * 周期扫描运行中的实例，心跳超过阈值仍处于RUNNING的判定为失联
 * （进程崩溃、节点线程丢失等），自动置为FAILED并发布回收事件，
 * 避免实例永久卡死无法恢复。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(prefix = "workflow.engine", name = "zombie-reclaim-enabled",
        havingValue = "true", matchIfMissing = true)
public class WorkflowZombieReclaimer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowZombieReclaimer.class);

    /**
     * 回收错误信息
     */
    public static final String RECLAIM_ERROR_MESSAGE = "实例失联自动回收(心跳超时)";

    private final WorkflowStateStore stateStore;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 心跳失联阈值（毫秒），须大于心跳刷新间隔的数倍
     */
    private final long zombieThresholdMs;

    public WorkflowZombieReclaimer(WorkflowStateStore stateStore,
                                   ApplicationEventPublisher eventPublisher,
                                   @Value("${workflow.engine.zombie-threshold-ms:600000}") long zombieThresholdMs) {
        this.stateStore = stateStore;
        this.eventPublisher = eventPublisher;
        this.zombieThresholdMs = zombieThresholdMs;
    }

    /**
     * 周期扫描失联实例并回收
     */
    @Scheduled(fixedDelayString = "${workflow.engine.zombie-scan-interval-ms:60000}")
    public void scan() {
        try {
            reclaimZombies();
        } catch (Exception e) {
            log.error("僵尸实例扫描异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 回收心跳超时的运行中实例
     * @return 本次回收的实例数
     */
    public int reclaimZombies() {
        List<WorkflowState> runningList = stateStore.listRunning();
        if (runningList.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int reclaimed = 0;
        for (WorkflowState state : runningList) {
            Long heartbeat = state.getLastHeartbeatTime();
            long lastAlive = heartbeat != null ? heartbeat
                    : (state.getUpdateTime() != null ? state.getUpdateTime() : 0L);
            if (now - lastAlive < zombieThresholdMs) {
                continue;
            }
            doReclaim(state, now);
            reclaimed++;
        }
        if (reclaimed > 0) {
            log.warn("僵尸实例回收完成: count={}, thresholdMs={}", reclaimed, zombieThresholdMs);
        }
        return reclaimed;
    }

    /**
     * 将失联实例置为失败并发布回收事件
     * @param state
     * @param now
     */
    private void doReclaim(WorkflowState state, long now) {
        log.warn("回收失联工作流实例: instanceId={}, definitionName={}, lastHeartbeatTime={}",
                state.getInstanceId(), state.getDefinitionName(), state.getLastHeartbeatTime());
        // 仍在运行中的节点一并标记失败，保持状态一致
        if (state.getNodeStates() != null) {
            for (NodeExecutionStatus nodeStatus : state.getNodeStates().values()) {
                if (nodeStatus.isRunning()) {
                    nodeStatus.setStatus(ExecutionStatus.FAILED);
                    nodeStatus.setEndTime(now);
                    nodeStatus.setErrorMessage(RECLAIM_ERROR_MESSAGE);
                }
            }
        }
        state.setStatus(ExecutionStatus.FAILED);
        state.setVariable("errorMessage", RECLAIM_ERROR_MESSAGE);
        state.setUpdateTime(now);
        stateStore.save(state);
        eventPublisher.publishEvent(new WorkflowInstanceReclaimedEvent(this, state.getInstanceId(),
                state.getDefinitionName(), RECLAIM_ERROR_MESSAGE));
    }
}
