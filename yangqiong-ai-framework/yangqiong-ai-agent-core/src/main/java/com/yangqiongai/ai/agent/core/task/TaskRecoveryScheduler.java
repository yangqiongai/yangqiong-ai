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
package com.yangqiongai.ai.agent.core.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务恢复调度器
 * <p>
 * 启动时：无条件恢复所有 PENDING/RUNNING 的孤儿任务（服务重启前的残留）
 * 定时中：仅恢复超时的任务（PENDING 超10分钟/RUNNING 超30分钟），避免误杀正常执行中的任务
 * </p>
 * @author yangqiong
 */
@Component
public class TaskRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryScheduler.class);

    @Autowired
    private AgentTaskTracker tracker;

    /**
     * 应用启动完成后，恢复服务重启前残留的未完成任务
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        int count = tracker.recoverAllPending();
        if (count > 0) {
            log.info("启动恢复未完成任务数: {}", count);
        }
    }

    /**
     * 定时恢复超时的孤儿任务（每60秒检查一次）
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverStale() {
        int count = tracker.recoverPending();
        if (count > 0) {
            log.info("定时恢复超时任务数: {}", count);
        }
    }
}
