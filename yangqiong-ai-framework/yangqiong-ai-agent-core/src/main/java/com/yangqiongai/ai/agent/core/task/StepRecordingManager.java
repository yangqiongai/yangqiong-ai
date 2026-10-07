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

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.runtime.budget.UsageListener;
import com.yangqiongai.ai.agent.runtime.budget.UsageRecord;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import com.yangqiongai.ai.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 步骤记录管理器
 * <p>
 * 统一管理同步/流式/异步三种执行路径的步骤采集。
 * 为每种路径自动生成taskId、创建TaskStepRecorder，
 * 并在执行完成后负责步骤的DB持久化。
 * </p>
 * @author yangqiong
 */
@Component
public class StepRecordingManager {

    private static final Logger log = LoggerFactory.getLogger(StepRecordingManager.class);

    /**
     * 内部key：执行模式标记
     */
    private static final String INTERNAL_EXECUTION_MODE = "_internalExecutionMode";

    /**
     * 内部key：录制taskId回写
     */
    private static final String RECORDING_TASK_ID = "_recordingTaskId";

    /**
     * 活跃录制过期时间（小时）
     */
    private static final int EXPIRY_HOURS = 1;

    @Value("${ai.agent.task.step-recording.enabled:true}")
    private boolean stepRecordingEnabled;

    @Value("${ai.agent.task.step-recording.sync-enabled:true}")
    private boolean syncEnabled;

    @Value("${ai.agent.task.step-recording.stream-enabled:true}")
    private boolean streamEnabled;

    @Autowired
    private TaskStore persistenceService;

    @Autowired
    private AgentTaskTracker agentTaskTracker;

    /**
     * 用量监听SPI（metering未启用时无bean，静默跳过）
     */
    @Autowired(required = false)
    private ObjectProvider<UsageListener> usageListenerProvider;

    /**
     * 活跃的Recorder注册表（用于执行结束后取回并持久化）
     */
    private final ConcurrentHashMap<String, ActiveRecording> activeRecordings = new ConcurrentHashMap<>();

    /**
     * 创建步骤采集器
     * <p>
     * 优先使用已有的TASK_ID（异步场景），否则为同步/流式路径生成新的taskId
     * </p>
     * @param request
     * @return TaskStepRecorder，不需要记录或禁用时返回null
     */
    public TaskStepRecorder createRecorder(AgentRequest request) {
        if (!stepRecordingEnabled || request == null) {
            return null;
        }

        Map<String, Object> body = request.getBody();

        // 异步路径已有TASK_ID（由 AgentTaskTracker.submit 写入）
        Object existingTaskId = body.get(AgentRequest.BodyKeys.TASK_ID);
        if (existingTaskId instanceof String tid && !tid.isBlank()) {
            // 异步路径由 AgentTaskTracker 在任务完成时统一drain持久化，
            // 必须复用tracker持有的recorder实例，另建新实例会收不到回调导致步骤丢失
            AgentTaskRecord record = agentTaskTracker.query(tid);
            if (record != null) {
                if (record.getStepRecorder() != null) {
                    return record.getStepRecorder();
                }
                TaskStepRecorder recorder = new TaskStepRecorder(tid);
                recorder.setScopeId(record.getScopeId());
                return recorder;
            }
            // 非异步任务持有的TASK_ID（同步/流式路径回写的录制taskId，重试场景），继续按同步/流式逻辑处理
        }

        // 同步/流式路径：根据执行模式和开关判断
        TaskSource source = resolveSource(request);
        if (source == TaskSource.SYNC && !syncEnabled) {
            return null;
        }
        if (source == TaskSource.STREAM && !streamEnabled) {
            return null;
        }

        // 重试场景：复用已有的taskId和recorder，避免重复创建DB任务记录
        Object existingRecordingId = body.get(RECORDING_TASK_ID);
        if (existingRecordingId instanceof String rid && !rid.isBlank()) {
            ActiveRecording existing = activeRecordings.get(rid);
            if (existing != null) {
                log.debug("重试场景复用已有录制器: taskId={}", rid);
                return existing.recorder;
            }
        }

        String taskId = StringUtils.generateCompactId();
        TaskStepRecorder recorder = new TaskStepRecorder(taskId);
        recorder.setScopeId(request.getScopeId() != null ? request.getScopeId() : SessionContext.getScopeId());

        // 注册活跃录制，等待 finish 时持久化
        ActiveRecording recording = new ActiveRecording(taskId, source, request, recorder);
        activeRecordings.put(taskId, recording);

        // 回写taskId供 Engine 的 finish 调用使用
        body.put(RECORDING_TASK_ID, taskId);

        // 回写TASK_ID供执行器上报调用级用量与子任务父链路使用
        body.put(AgentRequest.BodyKeys.TASK_ID, taskId);

        // 预创建DB任务记录
        preCreateTaskRecord(recording);

        log.debug("创建步骤采集器: taskId={}, source={}", taskId, source);
        return recorder;
    }

    /**
     * 完成录制并持久化
     * <p>
     * 由 DefaultAgentEngine 在同步/流式路径的 finally 块中调用，
     * 或者由 AgentTaskTracker 在异步路径中自行处理（此处跳过 ASYNC）。
     * </p>
     * @param taskId
     * @param status
     * @param outputText
     * @param durationMs
     * @param tokenMetrics
     */
    public void finishRecording(String taskId, String status, String outputText, long durationMs, TokenMetrics tokenMetrics) {
        if (taskId == null) {
            return;
        }
        ActiveRecording recording = activeRecordings.remove(taskId);
        if (recording == null) {
            return;
        }

        try {
            List<AgentTaskStepInfo> steps = recording.recorder.drainSteps();
            if (!steps.isEmpty()) {
                persistenceService.saveSteps(steps);
            }
            // 任务级token统计缺失时从LLM调用步骤聚合（流式路径无结果级TokenMetrics）
            TokenMetrics metrics = tokenMetrics != null ? tokenMetrics : aggregateTokenMetrics(steps);
            persistenceService.updateTaskStatus(taskId, status, outputText, durationMs, metrics);
            // 同步/流式路径不走AgentTaskTracker.markSucceeded，在此补齐run级用量上报
            publishRunUsage(recording, taskId, status, durationMs, metrics);
            log.info("步骤持久化完成: taskId={}, source={}, stepCount={}, status={}",
                    taskId, recording.source, steps.size(), status);
        } catch (Exception e) {
            log.error("步骤持久化失败: taskId={}", taskId, e);
        }
    }

    /**
     * 发布run级用量（计量监听存在时上报，供配额累加与用量统计落库）
     * <p>
     * 计量异常仅记录告警，不影响任务收尾。
     * </p>
     * @param recording 活跃录制
     * @param taskId 任务ID
     * @param status 任务结果状态
     * @param durationMs 执行时长
     * @param metrics Token用量
     */
    private void publishRunUsage(ActiveRecording recording, String taskId, String status,
                                 long durationMs, TokenMetrics metrics) {
        UsageListener listener = usageListenerProvider != null ? usageListenerProvider.getIfAvailable() : null;
        if (listener == null) {
            return;
        }
        try {
            AgentRequest request = recording.request;
            listener.onUsage(new UsageRecord(
                    StringUtils.generateCompactId(), null, taskId,
                    request.getAgentCode(), request.getModelCode(),
                    metrics != null ? metrics.getInputTokens() : 0,
                    metrics != null ? metrics.getOutputTokens() : 0,
                    metrics != null ? metrics.getTotalTokens() : 0,
                    durationMs, status,
                    request.getScopeId() != null ? request.getScopeId() : SessionContext.getScopeId(),
                    request.getUserId()));
        } catch (Exception e) {
            log.warn("run级用量上报失败: taskId={}", taskId, e);
        }
    }

    /**
     * 从LLM调用步骤聚合任务级Token统计
     * @param steps
     * @return
     */
    private TokenMetrics aggregateTokenMetrics(List<AgentTaskStepInfo> steps) {
        long input = 0;
        long output = 0;
        long total = 0;
        for (AgentTaskStepInfo step : steps) {
            if ("LLM_CALL".equals(step.getStepType())) {
                input += step.getInputTokens() != null ? step.getInputTokens() : 0;
                output += step.getOutputTokens() != null ? step.getOutputTokens() : 0;
                total += step.getTotalTokens() != null ? step.getTotalTokens() : 0;
            }
        }
        return new TokenMetrics(input, output, total, 0);
    }

    /**
     * 从请求中获取录制的taskId
     * @param request
     * @return taskId，无录制时返回null
     */
    public static String getRecordingTaskId(AgentRequest request) {
        if (request == null || request.getBody() == null) {
            return null;
        }
        Object taskId = request.getBody().get(RECORDING_TASK_ID);
        return taskId instanceof String tid ? tid : null;
    }

    /**
     * 解析任务来源
     * @param request
     * @return
     */
    private TaskSource resolveSource(AgentRequest request) {
        Object sourceMark = request.getBody().get(INTERNAL_EXECUTION_MODE);
        if ("stream".equals(sourceMark)) {
            return TaskSource.STREAM;
        }
        return TaskSource.SYNC;
    }

    /**
     * 预创建DB任务记录
     * @param recording
     */
    private void preCreateTaskRecord(ActiveRecording recording) {
        try {
            AgentTaskInfo info = new AgentTaskInfo();
            info.setTaskId(recording.taskId);
            info.setAgentCode(recording.request.getAgentCode());
            info.setSessionId(recording.request.getSessionId());
            info.setUserId(recording.request.getUserId());
            info.setScopeId(recording.request.getScopeId());
            info.setTaskSource(recording.source.name());
            info.setTaskStatus("RUNNING");
            info.setUserInput(recording.request.getInputAsText());
            info.setCreateTime(LocalDateTime.now());
            persistenceService.createTask(info);
        } catch (Exception e) {
            log.warn("预创建任务记录失败（不影响执行）: taskId={}", recording.taskId, e);
        }
    }

    /**
     * 定时清理过期的活跃录制（每10分钟执行）
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupExpiredRecordings() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(EXPIRY_HOURS);
        for (Map.Entry<String, ActiveRecording> entry : activeRecordings.entrySet()) {
            if (entry.getValue().startedAt.isBefore(expiryThreshold)) {
                ActiveRecording recording = activeRecordings.remove(entry.getKey());
                if (recording != null) {
                    // 持久化已有的步骤
                    try {
                        List<AgentTaskStepInfo> steps = recording.recorder.drainSteps();
                        if (!steps.isEmpty()) {
                            persistenceService.saveSteps(steps);
                        }
                        persistenceService.updateTaskStatus(recording.taskId, "FAILED",
                                "录制超时自动清理", 0L, null);
                    } catch (Exception e) {
                        log.warn("清理过期录制失败: taskId={}", recording.taskId, e);
                    }
                    log.info("清理过期录制: taskId={}, source={}", recording.taskId, recording.source);
                }
            }
        }
    }

    /**
     * 活跃录制信息
     */
    private static class ActiveRecording {

        final String taskId;
        final TaskSource source;
        final AgentRequest request;
        final TaskStepRecorder recorder;
        final LocalDateTime startedAt;

        ActiveRecording(String taskId, TaskSource source, AgentRequest request, TaskStepRecorder recorder) {
            this.taskId = taskId;
            this.source = source;
            this.request = request;
            this.recorder = recorder;
            this.startedAt = LocalDateTime.now();
        }
    }
}
