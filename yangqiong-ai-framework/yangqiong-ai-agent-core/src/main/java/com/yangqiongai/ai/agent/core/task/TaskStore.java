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

import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.core.repository.AgentTaskStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 任务持久化
 * @author yangqiong
 */
@Service
public class TaskStore {

    private static final Logger log = LoggerFactory.getLogger(TaskStore.class);

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private AgentTaskStepRepository agentTaskStepRepository;

    @Value("${ai.agent.task.persistence.enabled:true}")
    private boolean persistenceEnabled;

    public void createTask(AgentTaskInfo task) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            agentTaskRepository.createTask(task);
        } catch (Exception e) {
            log.error("创建任务记录失败: taskId={}", task.getTaskId(), e);
        }
    }

    public void markRunning(String taskId) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            agentTaskRepository.markRunning(taskId);
        } catch (Exception e) {
            log.error("标记任务RUNNING失败: taskId={}", taskId, e);
        }
    }

    public void markSucceeded(String taskId, String outputText, String outputJson,
                              TokenMetrics tokenMetrics, long durationMs) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            agentTaskRepository.markSucceeded(taskId, outputText, outputJson, tokenMetrics, durationMs);
        } catch (Exception e) {
            log.error("标记任务SUCCEEDED失败: taskId={}", taskId, e);
        }
    }

    public void markFailed(String taskId, String errorMessage, long durationMs) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            agentTaskRepository.markFailed(taskId, errorMessage, durationMs);
        } catch (Exception e) {
            log.error("标记任务FAILED失败: taskId={}", taskId, e);
        }
    }

    public void saveSteps(List<AgentTaskStepInfo> steps) {
        if (!persistenceEnabled || steps == null || steps.isEmpty()) {
            return;
        }
        try {
            agentTaskStepRepository.saveSteps(steps);
        } catch (Exception e) {
            log.error("批量写入步骤失败: stepCount={}", steps.size(), e);
        }
    }

    public AgentTaskInfo queryTask(String taskId) {
        try {
            return agentTaskRepository.queryTask(taskId);
        } catch (Exception e) {
            log.error("查询任务失败: taskId={}", taskId, e);
            return null;
        }
    }

    public List<AgentTaskStepInfo> querySteps(String taskId) {
        try {
            return agentTaskStepRepository.querySteps(taskId);
        } catch (Exception e) {
            log.error("查询任务步骤失败: taskId={}", taskId, e);
            return Collections.emptyList();
        }
    }

    public List<AgentTaskInfo> queryChildTasks(String parentTaskId) {
        try {
            return agentTaskRepository.queryChildTasks(parentTaskId);
        } catch (Exception e) {
            log.error("查询子任务失败: parentTaskId={}", parentTaskId, e);
            return Collections.emptyList();
        }
    }

    public List<AgentTaskInfo> queryTaskTree(String taskId) {
        try {
            return agentTaskRepository.queryTaskTree(taskId);
        } catch (Exception e) {
            log.error("查询任务树失败: taskId={}", taskId, e);
            return Collections.emptyList();
        }
    }

    public List<AgentTaskStepInfo> queryStepsByTaskTree(String taskId) {
        try {
            return agentTaskStepRepository.queryStepsByTaskTree(taskId);
        } catch (Exception e) {
            log.error("查询任务树步骤失败: taskId={}", taskId, e);
            return Collections.emptyList();
        }
    }

    public List<AgentTaskInfo> queryTaskHistoryByUser(String userId, int offset, int limit) {
        try {
            return agentTaskRepository.queryTaskHistoryByUser(userId, offset, limit);
        } catch (Exception e) {
            log.error("查询用户任务历史失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    public long countTaskHistoryByUser(String userId) {
        try {
            return agentTaskRepository.countTaskHistoryByUser(userId);
        } catch (Exception e) {
            log.error("统计用户任务总数失败: userId={}", userId, e);
            return 0;
        }
    }

    public int recoverPendingTasks() {
        if (!persistenceEnabled) {
            return 0;
        }
        try {
            return agentTaskRepository.recoverPendingTasks();
        } catch (Exception e) {
            log.error("恢复未完成任务失败", e);
            return 0;
        }
    }

    public int recoverStaleTasks() {
        if (!persistenceEnabled) {
            return 0;
        }
        try {
            return agentTaskRepository.recoverStaleTasks();
        } catch (Exception e) {
            log.error("恢复超时任务失败", e);
            return 0;
        }
    }

    public void updateTaskStatus(String taskId, String status, String outputText, long durationMs, TokenMetrics tokenMetrics) {
        if (!persistenceEnabled) {
            return;
        }
        try {
            agentTaskRepository.updateTaskStatus(taskId, status, outputText, durationMs, tokenMetrics);
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}", taskId, status, e);
        }
    }
}
