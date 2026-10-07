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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 时间控制恢复扫描，服务启动时重新调度暂停实例的定时恢复
 * @author yangqiong
 */
@Component
public class WorkflowTimeResumeRecovery {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTimeResumeRecovery.class);

    private static final String RESUME_AT_VAR_PREFIX = "timeResumeAt:";

    private final WorkflowStateStore workflowStateStore;

    private final TimeControlNodeHandler timeControlNodeHandler;

    @Autowired
    public WorkflowTimeResumeRecovery(WorkflowStateStore workflowStateStore, TimeControlNodeHandler timeControlNodeHandler) {
        this.workflowStateStore = workflowStateStore;
        this.timeControlNodeHandler = timeControlNodeHandler;
    }

    /**
     * 服务就绪后扫描暂停实例，重新调度时间控制节点的恢复定时器
     * @param event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            List<WorkflowState> pausedStates = workflowStateStore.listPaused();
            int scheduled = 0;
            for (WorkflowState state : pausedStates) {
                String pausedNodeId = state.getPausedNodeId();
                if (pausedNodeId == null || pausedNodeId.isBlank()) {
                    continue;
                }
                // 仅时间控制暂停的实例写入了恢复时间变量，据此识别而无需反序列化定义
                Object resumeAtValue = state.getVariable(RESUME_AT_VAR_PREFIX + pausedNodeId);
                if (!(resumeAtValue instanceof Number)) {
                    continue;
                }
                long resumeAtMillis = ((Number) resumeAtValue).longValue();
                log.info("启动恢复扫描，重新调度时间控制恢复: instanceId={}, nodeId={}, 恢复时间={}",
                        state.getInstanceId(), pausedNodeId, resumeAtMillis);
                // 已过期的设定时间以0延迟立即发布，由引擎恢复补跑；引擎侧会校验取消/节点变更
                timeControlNodeHandler.scheduleResumeEvent(state.getInstanceId(), pausedNodeId, resumeAtMillis);
                scheduled++;
            }
            if (scheduled > 0) {
                log.info("时间控制恢复扫描完成，共重新调度{}个暂停实例", scheduled);
            }
        } catch (Exception e) {
            log.error("时间控制恢复扫描失败", e);
        }
    }
}
