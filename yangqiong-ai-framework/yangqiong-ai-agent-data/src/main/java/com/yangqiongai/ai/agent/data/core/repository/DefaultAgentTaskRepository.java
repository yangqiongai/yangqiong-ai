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
package com.yangqiongai.ai.agent.data.core.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.data.core.entity.AgentTaskEntity;
import com.yangqiongai.ai.agent.data.core.mapper.AgentTaskMapper;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent任务仓库默认实现
 * @author yangqiong
 */
public class DefaultAgentTaskRepository implements AgentTaskRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentTaskRepository.class);

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Value("${ai.agent.task.persistence.enabled:true}")
    private boolean persistenceEnabled;

    @Override
    public void createTask(AgentTaskInfo task) {
        if (!persistenceEnabled) return;
        try {
            AgentTaskEntity entity = toEntity(task);
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            agentTaskMapper.insert(entity);
        } catch (Exception e) {
            log.error("创建任务记录失败: taskId={}", task.getTaskId(), e);
        }
    }

    @Override
    public void markRunning(String taskId) {
        if (!persistenceEnabled) return;
        try {
            runWithTaskScope(taskId, () -> {
                LambdaUpdateWrapper<AgentTaskEntity> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AgentTaskEntity::getTaskId, taskId)
                        .set(AgentTaskEntity::getTaskStatus, "RUNNING")
                        .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                agentTaskMapper.update(null, wrapper);
            });
        } catch (Exception e) {
            log.error("标记任务RUNNING失败: taskId={}", taskId, e);
        }
    }

    @Override
    public void markSucceeded(String taskId, String outputText, String outputJson,
                              TokenMetrics tokenMetrics, long durationMs) {
        if (!persistenceEnabled) return;
        try {
            runWithTaskScope(taskId, () -> {
                LambdaUpdateWrapper<AgentTaskEntity> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AgentTaskEntity::getTaskId, taskId)
                        .set(AgentTaskEntity::getTaskStatus, "SUCCEEDED")
                        .set(AgentTaskEntity::getOutputText, outputText)
                        .set(AgentTaskEntity::getOutputJson, outputJson)
                        .set(AgentTaskEntity::getDurationMs, durationMs)
                        .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                if (tokenMetrics != null) {
                    wrapper.set(AgentTaskEntity::getInputTokens, (int) tokenMetrics.getInputTokens())
                            .set(AgentTaskEntity::getOutputTokens, (int) tokenMetrics.getOutputTokens())
                            .set(AgentTaskEntity::getTotalTokens, (int) tokenMetrics.getTotalTokens())
                            .set(AgentTaskEntity::getExecutionTime, tokenMetrics.getTime());
                }
                agentTaskMapper.update(null, wrapper);
            });
        } catch (Exception e) {
            log.error("标记任务SUCCEEDED失败: taskId={}", taskId, e);
        }
    }

    @Override
    public void markFailed(String taskId, String errorMessage, long durationMs) {
        if (!persistenceEnabled) return;
        try {
            runWithTaskScope(taskId, () -> {
                LambdaUpdateWrapper<AgentTaskEntity> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AgentTaskEntity::getTaskId, taskId)
                        .set(AgentTaskEntity::getTaskStatus, "FAILED")
                        .set(AgentTaskEntity::getErrorMessage, errorMessage)
                        .set(AgentTaskEntity::getDurationMs, durationMs)
                        .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                agentTaskMapper.update(null, wrapper);
            });
        } catch (Exception e) {
            log.error("标记任务FAILED失败: taskId={}", taskId, e);
        }
    }

    @Override
    public AgentTaskInfo queryTask(String taskId) {
        if (!persistenceEnabled) return null;
        try {
            AgentTaskEntity entity = agentTaskMapper.selectByTaskId(taskId);
            return entity != null ? toInfo(entity) : null;
        } catch (Exception e) {
            log.error("查询任务失败: taskId={}", taskId, e);
            return null;
        }
    }

    @Override
    public List<AgentTaskInfo> queryChildTasks(String parentTaskId) {
        if (!persistenceEnabled) return Collections.emptyList();
        try {
            return agentTaskMapper.selectChildTasks(parentTaskId).stream().map(this::toInfo).toList();
        } catch (Exception e) {
            log.error("查询子任务失败: parentTaskId={}", parentTaskId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AgentTaskInfo> queryTaskTree(String taskId) {
        if (!persistenceEnabled) return Collections.emptyList();
        try {
            return agentTaskMapper.selectTaskTree(taskId).stream().map(this::toInfo).toList();
        } catch (Exception e) {
            log.error("查询任务树失败: taskId={}", taskId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AgentTaskInfo> queryTaskHistoryByUser(String userId, int offset, int limit) {
        if (!persistenceEnabled) return Collections.emptyList();
        try {
            return agentTaskMapper.selectTaskHistoryByUser(userId, offset, limit).stream().map(this::toInfo).toList();
        } catch (Exception e) {
            log.error("查询用户任务历史失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public long countTaskHistoryByUser(String userId) {
        if (!persistenceEnabled) return 0;
        try {
            return agentTaskMapper.countTaskHistoryByUser(userId);
        } catch (Exception e) {
            log.error("统计用户任务总数失败: userId={}", userId, e);
            return 0;
        }
    }

    @Override
    public int recoverPendingTasks() {
        if (!persistenceEnabled) return 0;
        try {
            LambdaQueryWrapper<AgentTaskEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(AgentTaskEntity::getTaskStatus, "PENDING", "RUNNING");
            List<AgentTaskEntity> pendingTasks = agentTaskMapper.selectList(wrapper);
            int count = 0;
            for (AgentTaskEntity task : pendingTasks) {
                // 队列模式任务(runner_heartbeat非空)交由心跳回收闭环处理，避免误杀其他实例在跑任务
                if ("RUNNING".equals(task.getTaskStatus()) && task.getRunnerHeartbeat() != null) {
                    continue;
                }
                LambdaUpdateWrapper<AgentTaskEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(AgentTaskEntity::getTaskId, task.getTaskId())
                        .eq(AgentTaskEntity::getTaskStatus, task.getTaskStatus())
                        .set(AgentTaskEntity::getTaskStatus, "FAILED")
                        .set(AgentTaskEntity::getErrorMessage, "服务重启，任务恢复时标记为失败")
                        .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                count += agentTaskMapper.update(null, updateWrapper);
            }
            return count;
        } catch (Exception e) {
            log.error("恢复未完成任务失败", e);
            return 0;
        }
    }

    @Override
    public int recoverStaleTasks() {
        if (!persistenceEnabled) return 0;
        try {
            LocalDateTime now = LocalDateTime.now();
            int count = 0;
            // PENDING超过10分钟
            LambdaQueryWrapper<AgentTaskEntity> pendingWrapper = new LambdaQueryWrapper<>();
            pendingWrapper.eq(AgentTaskEntity::getTaskStatus, "PENDING")
                    .lt(AgentTaskEntity::getCreateTime, now.minusMinutes(10));
            List<AgentTaskEntity> stalePendingTasks = agentTaskMapper.selectList(pendingWrapper);
            for (AgentTaskEntity task : stalePendingTasks) {
                LambdaUpdateWrapper<AgentTaskEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(AgentTaskEntity::getTaskId, task.getTaskId())
                        .set(AgentTaskEntity::getTaskStatus, "FAILED")
                        .set(AgentTaskEntity::getErrorMessage, "PENDING超时未执行，被恢复调度标记为失败")
                        .set(AgentTaskEntity::getUpdateTime, now);
                agentTaskMapper.update(null, updateWrapper);
                count++;
            }
            // RUNNING超过30分钟（仅处理无心跳的存量任务，队列模式任务由回收逻辑统一判定）
            LambdaQueryWrapper<AgentTaskEntity> runningWrapper = new LambdaQueryWrapper<>();
            runningWrapper.eq(AgentTaskEntity::getTaskStatus, "RUNNING")
                    .isNull(AgentTaskEntity::getRunnerHeartbeat)
                    .lt(AgentTaskEntity::getCreateTime, now.minusMinutes(30));
            List<AgentTaskEntity> staleRunningTasks = agentTaskMapper.selectList(runningWrapper);
            for (AgentTaskEntity task : staleRunningTasks) {
                LambdaUpdateWrapper<AgentTaskEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(AgentTaskEntity::getTaskId, task.getTaskId())
                        .set(AgentTaskEntity::getTaskStatus, "FAILED")
                        .set(AgentTaskEntity::getErrorMessage, "RUNNING超时未完成，被恢复调度标记为失败")
                        .set(AgentTaskEntity::getUpdateTime, now);
                agentTaskMapper.update(null, updateWrapper);
                count++;
            }
            return count;
        } catch (Exception e) {
            log.error("恢复超时任务失败", e);
            return 0;
        }
    }

    @Override
    public void updateTaskStatus(String taskId, String status, String outputText, long durationMs, TokenMetrics tokenMetrics) {
        if (!persistenceEnabled) return;
        try {
            runWithTaskScope(taskId, () -> {
                LambdaUpdateWrapper<AgentTaskEntity> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(AgentTaskEntity::getTaskId, taskId)
                        .set(AgentTaskEntity::getTaskStatus, status)
                        .set(AgentTaskEntity::getOutputText, outputText)
                        .set(AgentTaskEntity::getDurationMs, durationMs)
                        .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                // 失败收尾携带错误描述时同步写入error_message，保证失败任务原因可追踪
                if ("FAILED".equals(status) && outputText != null && !outputText.isBlank()) {
                    wrapper.set(AgentTaskEntity::getErrorMessage, outputText);
                }
                if (tokenMetrics != null) {
                    wrapper.set(AgentTaskEntity::getInputTokens, (int) tokenMetrics.getInputTokens())
                            .set(AgentTaskEntity::getOutputTokens, (int) tokenMetrics.getOutputTokens())
                            .set(AgentTaskEntity::getTotalTokens, (int) tokenMetrics.getTotalTokens());
                }
                agentTaskMapper.update(null, wrapper);
            });
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}, status={}", taskId, status, e);
        }
    }

    @Override
    public List<AgentTaskInfo> claimQueued(String runnerId, int limit) {
        if (!persistenceEnabled) return Collections.emptyList();
        try {
            // 按优先级降序+入队时间升序取候选，再以CAS抢占（仅QUEUED状态可被置RUNNING），多实例下同一任务仅一方成功
            LambdaQueryWrapper<AgentTaskEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentTaskEntity::getTaskStatus, "QUEUED")
                    .orderByDesc(AgentTaskEntity::getPriority)
                    .orderByAsc(AgentTaskEntity::getQueuedTime)
                    .last("LIMIT " + Math.max(1, limit));
            List<AgentTaskEntity> candidates = agentTaskMapper.selectList(wrapper);
            LocalDateTime now = LocalDateTime.now();
            List<AgentTaskInfo> claimed = new ArrayList<>();
            for (AgentTaskEntity candidate : candidates) {
                LambdaUpdateWrapper<AgentTaskEntity> claimWrapper = new LambdaUpdateWrapper<>();
                claimWrapper.eq(AgentTaskEntity::getTaskId, candidate.getTaskId())
                        .eq(AgentTaskEntity::getTaskStatus, "QUEUED")
                        .set(AgentTaskEntity::getTaskStatus, "RUNNING")
                        .set(AgentTaskEntity::getRunnerId, runnerId)
                        .set(AgentTaskEntity::getRunnerHeartbeat, now)
                        .set(AgentTaskEntity::getUpdateTime, now);
                if (agentTaskMapper.update(null, claimWrapper) > 0) {
                    candidate.setTaskStatus("RUNNING");
                    candidate.setRunnerId(runnerId);
                    candidate.setRunnerHeartbeat(now);
                    claimed.add(toInfo(candidate));
                }
            }
            return claimed;
        } catch (Exception e) {
            log.error("抢占队列任务失败: runnerId={}", runnerId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public int reclaimExpired(LocalDateTime heartbeatExpireBefore, int maxRedeliver, int limit) {
        if (!persistenceEnabled) return 0;
        try {
            LambdaQueryWrapper<AgentTaskEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentTaskEntity::getTaskStatus, "RUNNING")
                    .lt(AgentTaskEntity::getRunnerHeartbeat, heartbeatExpireBefore)
                    .last("LIMIT " + Math.max(1, limit));
            List<AgentTaskEntity> expiredTasks = agentTaskMapper.selectList(wrapper);
            int count = 0;
            for (AgentTaskEntity task : expiredTasks) {
                int redeliverCount = task.getRedeliverCount() != null ? task.getRedeliverCount() : 0;
                if (redeliverCount < maxRedeliver) {
                    // 未耗尽重派次数：CAS置回QUEUED等待重新抢占
                    LambdaUpdateWrapper<AgentTaskEntity> requeueWrapper = new LambdaUpdateWrapper<>();
                    requeueWrapper.eq(AgentTaskEntity::getTaskId, task.getTaskId())
                            .eq(AgentTaskEntity::getTaskStatus, "RUNNING")
                            .lt(AgentTaskEntity::getRunnerHeartbeat, heartbeatExpireBefore)
                            .set(AgentTaskEntity::getTaskStatus, "QUEUED")
                            .set(AgentTaskEntity::getRunnerId, null)
                            .set(AgentTaskEntity::getRunnerHeartbeat, null)
                            .setSql("redeliver_count = redeliver_count + 1")
                            .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                    if (agentTaskMapper.update(null, requeueWrapper) > 0) {
                        log.info("回收心跳超时任务并重新入队: taskId={}, redeliverCount={}",
                                task.getTaskId(), redeliverCount + 1);
                        count++;
                    }
                } else {
                    // 重派耗尽：置FAILED
                    LambdaUpdateWrapper<AgentTaskEntity> failWrapper = new LambdaUpdateWrapper<>();
                    failWrapper.eq(AgentTaskEntity::getTaskId, task.getTaskId())
                            .eq(AgentTaskEntity::getTaskStatus, "RUNNING")
                            .lt(AgentTaskEntity::getRunnerHeartbeat, heartbeatExpireBefore)
                            .set(AgentTaskEntity::getTaskStatus, "FAILED")
                            .set(AgentTaskEntity::getErrorMessage, "队列任务重派次数耗尽")
                            .set(AgentTaskEntity::getUpdateTime, LocalDateTime.now());
                    if (agentTaskMapper.update(null, failWrapper) > 0) {
                        log.warn("队列任务重派次数耗尽置FAILED: taskId={}", task.getTaskId());
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            log.error("回收心跳超时任务失败", e);
            return 0;
        }
    }

    @Override
    public void heartbeat(String runnerId) {
        if (!persistenceEnabled) return;
        try {
            LambdaUpdateWrapper<AgentTaskEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(AgentTaskEntity::getRunnerId, runnerId)
                    .eq(AgentTaskEntity::getTaskStatus, "RUNNING")
                    .set(AgentTaskEntity::getRunnerHeartbeat, LocalDateTime.now());
            agentTaskMapper.update(null, wrapper);
        } catch (Exception e) {
            log.error("刷新任务心跳失败: runnerId={}", runnerId, e);
        }
    }

    @Override
    public long countByStatus(String taskStatus, String scopeId, String agentCode) {
        if (!persistenceEnabled) return 0;
        try {
            LambdaQueryWrapper<AgentTaskEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentTaskEntity::getTaskStatus, taskStatus);
            if (scopeId != null && !scopeId.isBlank()) {
                wrapper.eq(AgentTaskEntity::getScopeId, scopeId);
            }
            if (agentCode != null && !agentCode.isBlank()) {
                wrapper.eq(AgentTaskEntity::getAgentCode, agentCode);
            }
            Long count = agentTaskMapper.selectCount(wrapper);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("统计任务数失败: status={}, scopeId={}, agentCode={}", taskStatus, scopeId, agentCode, e);
            return 0;
        }
    }

    /**
     * 按taskId解析任务scope并绑定后执行更新
     * <p>引擎线程无ScopeContext上下文，租户拦截器会追加scope_id='default'过滤导致任务更新丢失，
     * 此处先查任务自身scope再绑定执行；任务不存在或scope与当前一致时直接执行</p>
     * @param taskId 任务ID
     * @param action 更新动作
     */
    private void runWithTaskScope(String taskId, Runnable action) {
        String taskScope = agentTaskMapper.selectScopeByTaskId(taskId);
        if (taskScope == null || taskScope.isBlank() || taskScope.equals(ScopeContext.getScopeId())) {
            action.run();
            return;
        }
        ScopeContext.setScopeId(taskScope);
        try {
            action.run();
        } finally {
            ScopeContext.clear();
        }
    }

    private AgentTaskInfo toInfo(AgentTaskEntity entity) {
        AgentTaskInfo info = new AgentTaskInfo();
        info.setId(entity.getId());
        info.setTaskId(entity.getTaskId());
        info.setParentTaskId(entity.getParentTaskId());
        info.setForkCallSeq(entity.getForkCallSeq());
        info.setAgentPath(entity.getAgentPath());
        info.setAgentCode(entity.getAgentCode());
        info.setAgentName(entity.getAgentName());
        info.setSessionId(entity.getSessionId());
        info.setUserId(entity.getUserId());
        info.setScopeId(entity.getScopeId());
        info.setUserInput(entity.getUserInput());
        info.setUserInputJson(entity.getUserInputJson());
        info.setOutputText(entity.getOutputText());
        info.setOutputJson(entity.getOutputJson());
        info.setTaskStatus(entity.getTaskStatus());
        info.setPriority(entity.getPriority());
        info.setQueuedTime(entity.getQueuedTime());
        info.setRunnerId(entity.getRunnerId());
        info.setRedeliverCount(entity.getRedeliverCount());
        info.setTaskSource(entity.getTaskSource());
        info.setErrorMessage(entity.getErrorMessage());
        info.setInputTokens(entity.getInputTokens());
        info.setOutputTokens(entity.getOutputTokens());
        info.setTotalTokens(entity.getTotalTokens());
        info.setExecutionTime(entity.getExecutionTime());
        info.setDurationMs(entity.getDurationMs());
        info.setBody(entity.getBody());
        return info;
    }

    private AgentTaskEntity toEntity(AgentTaskInfo info) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(info.getId());
        entity.setTaskId(info.getTaskId());
        entity.setParentTaskId(info.getParentTaskId());
        entity.setForkCallSeq(info.getForkCallSeq());
        entity.setAgentPath(info.getAgentPath());
        entity.setAgentCode(info.getAgentCode());
        entity.setAgentName(info.getAgentName());
        entity.setSessionId(info.getSessionId());
        entity.setUserId(info.getUserId());
        entity.setScopeId(info.getScopeId());
        entity.setUserInput(info.getUserInput());
        entity.setUserInputJson(info.getUserInputJson());
        entity.setOutputText(info.getOutputText());
        entity.setOutputJson(info.getOutputJson());
        entity.setTaskStatus(info.getTaskStatus());
        entity.setPriority(info.getPriority());
        entity.setQueuedTime(info.getQueuedTime());
        entity.setRunnerId(info.getRunnerId());
        entity.setRedeliverCount(info.getRedeliverCount());
        entity.setTaskSource(info.getTaskSource());
        entity.setErrorMessage(info.getErrorMessage());
        entity.setInputTokens(info.getInputTokens());
        entity.setOutputTokens(info.getOutputTokens());
        entity.setTotalTokens(info.getTotalTokens());
        entity.setExecutionTime(info.getExecutionTime());
        entity.setDurationMs(info.getDurationMs());
        entity.setBody(info.getBody());
        return entity;
    }
}
