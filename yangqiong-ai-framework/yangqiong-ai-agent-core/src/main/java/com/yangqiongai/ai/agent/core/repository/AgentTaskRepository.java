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
package com.yangqiongai.ai.agent.core.repository;

import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent任务仓库
 * @author yangqiong
 */
public interface AgentTaskRepository {

    /**
     * 创建任务记录
     * @param task
     */
    void createTask(AgentTaskInfo task);

    /**
     * 更新状态为RUNNING
     * @param taskId
     */
    void markRunning(String taskId);

    /**
     * 更新状态为SUCCEEDED
     * @param taskId
     * @param outputText
     * @param outputJson
     * @param tokenMetrics
     * @param durationMs
     */
    void markSucceeded(String taskId, String outputText, String outputJson,
                       TokenMetrics tokenMetrics, long durationMs);

    /**
     * 更新状态为FAILED
     * @param taskId
     * @param errorMessage
     * @param durationMs
     */
    void markFailed(String taskId, String errorMessage, long durationMs);

    /**
     * 查询任务
     * @param taskId
     * @return
     */
    AgentTaskInfo queryTask(String taskId);

    /**
     * 查询子任务
     * @param parentTaskId
     * @return
     */
    List<AgentTaskInfo> queryChildTasks(String parentTaskId);

    /**
     * 递归查询任务树
     * @param taskId
     * @return
     */
    List<AgentTaskInfo> queryTaskTree(String taskId);

    /**
     * 按用户ID分页查询任务历史
     * @param userId
     * @param offset
     * @param limit
     * @return
     */
    List<AgentTaskInfo> queryTaskHistoryByUser(String userId, int offset, int limit);

    /**
     * 统计用户的任务总数
     * @param userId
     * @return
     */
    long countTaskHistoryByUser(String userId);

    /**
     * 恢复未完成任务
     * @return
     */
    int recoverPendingTasks();

    /**
     * 恢复超时任务
     * @return
     */
    int recoverStaleTasks();

    /**
     * 更新任务状态
     * @param taskId
     * @param status
     * @param outputText
     * @param durationMs
     * @param tokenMetrics
     */
    void updateTaskStatus(String taskId, String status, String outputText, long durationMs, TokenMetrics tokenMetrics);

    /**
     * 抢占队列任务（按优先级+入队时间排序，CAS抢占防止多实例重复执行）
     * @param runnerId
     * @param limit
     * @return
     */
    List<AgentTaskInfo> claimQueued(String runnerId, int limit);

    /**
     * 回收心跳超时的RUNNING任务（重派次数未耗尽则置回QUEUED，否则置FAILED）
     * @param heartbeatExpireBefore
     * @param maxRedeliver
     * @param limit
     * @return
     */
    int reclaimExpired(LocalDateTime heartbeatExpireBefore, int maxRedeliver, int limit);

    /**
     * 刷新本实例全部在跑任务的心跳
     * @param runnerId
     */
    void heartbeat(String runnerId);

    /**
     * 按状态统计任务数（scopeId/agentCode为null时不限定该维度）
     * @param taskStatus
     * @param scopeId
     * @param agentCode
     * @return
     */
    long countByStatus(String taskStatus, String scopeId, String agentCode);
}
