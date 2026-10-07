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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.runtime.budget.UsageListener;
import com.yangqiongai.ai.agent.runtime.budget.UsageRecord;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.model.content.OutputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextOutputBlock;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Agent任务追踪器
 * <p>
 * DB 持久化 + 内存缓存双层架构。
 * submit 时写 DB（PENDING）+ 放入内存 Map；状态变更同步更新 DB + 内存。
 * </p>
 * @author yangqiong
 */
@Service
public class AgentTaskTracker {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskTracker.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 最大任务容量（内存缓存）
     */
    private static final int MAX_TASKS = 1000;

    /**
     * 过期时间（小时）
     */
    private static final int EXPIRY_HOURS = 24;

    private final Map<String, AgentTaskRecord> taskStore = new ConcurrentHashMap<>();

    /**
     * 任务执行Future（取消时中断线程）
     */
    private final Map<String, Future<?>> taskFutures = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-task-worker");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private TaskStore persistenceService;

    /**
     * 用量监听SPI（metering未启用时无bean为null，静默跳过）
     */
    @Autowired(required = false)
    private UsageListener usageListener;

    @Value("${ai.agent.task.step-recording.enabled:true}")
    private boolean stepRecordingEnabled;

    /**
     * 提交异步任务
     * @param taskId
     * @param request
     * @param runner 返回 AgentResult 的可调用对象
     * @return
     */
    public AgentTaskRecord submit(String taskId, AgentRequest request, Callable<AgentResult> runner) {
        return submit(taskId, request, runner, true);
    }

    /**
     * 提交异步任务
     * @param taskId
     * @param request
     * @param runner 返回 AgentResult 的可调用对象
     * @param createDbRecord 是否创建DB任务记录（队列调度模式复用已入队记录时传false）
     * @return
     */
    public AgentTaskRecord submit(String taskId, AgentRequest request, Callable<AgentResult> runner, boolean createDbRecord) {
        evictIfOverCapacity();

        // 将 taskId 写入 request.body 供后续 AgentContext 使用
        request.addBody(AgentRequest.BodyKeys.TASK_ID, taskId);

        // 从 body 读取父任务关联信息（子代理/轨迹分叉场景）
        String parentTaskId = (String) request.getBody().get(AgentRequest.BodyKeys.PARENT_TASK_ID);
        Integer forkCallSeq = readForkCallSeq(request);
        String agentPath = (String) request.getBody().getOrDefault(AgentRequest.BodyKeys.AGENT_PATH, "main");

        // 创建内存记录
        AgentTaskRecord record = new AgentTaskRecord();
        record.setTaskId(taskId);
        record.setParentTaskId(parentTaskId);
        record.setForkCallSeq(forkCallSeq);
        record.setAgentPath(agentPath);
        record.setAgentCode(request.getAgentCode());
        record.setSessionId(request.getSessionId());
        record.setUserId(request.getUserId());
        record.setScopeId(request.getScopeId());
        record.setModelCode(request.getModelCode());
        record.setStatus(AgentTaskRecord.TaskStatus.PENDING);
        record.setStartedAt(LocalDateTime.now());
        record.setEvents(new CopyOnWriteArrayList<>());

        // 创建步骤采集器
        if (stepRecordingEnabled) {
            TaskStepRecorder recorder = new TaskStepRecorder(taskId);
            recorder.setScopeId(request.getScopeId());
            record.setStepRecorder(recorder);
        }

        taskStore.put(taskId, record);

        // 创建 DB 记录（队列调度模式任务已在入队时落库，跳过避免重复）
        if (createDbRecord) {
            createDbTask(record, request);
        }

        Future<?> future = executor.submit(() -> {
            try {
                markRunning(taskId, "submitted");
                AgentResult result = runner.call();
                // 任务执行期间可能已被取消，避免覆盖 CANCELLED 状态
                if (!isCancelled(taskId)) {
                    markCompleted(taskId, result);
                }
            } catch (Exception e) {
                // 任务已被取消时，中断异常无需再标记为失败
                if (isCancelled(taskId)) {
                    log.info("任务已被取消，忽略执行异常: taskId={}", taskId);
                } else {
                    log.error("异步任务执行异常: taskId={}", taskId, e);
                    markFailed(taskId, "execution", resolveErrorMessage(e));
                }
            }
        });
        taskFutures.put(taskId, future);
        return record;
    }

    /**
     * 判断任务是否已被取消
     * @param taskId
     * @return
     */
    private boolean isCancelled(String taskId) {
        AgentTaskRecord record = taskStore.get(taskId);
        return record != null && record.getStatus() == AgentTaskRecord.TaskStatus.CANCELLED;
    }

    /**
     * 解析失败根因消息，异常自身无消息时沿因果链向下取，保证失败原因可追踪
     * @param e
     * @return
     */
    public static String resolveErrorMessage(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return e.getClass().getName();
    }

    /**
     * 查询任务状态（优先内存，miss 时查 DB）
     * @param taskId
     * @return
     */
    public AgentTaskRecord query(String taskId) {
        AgentTaskRecord record = taskStore.get(taskId);
        if (record != null) {
            return record;
        }
        // 内存 miss，查 DB
        AgentTaskInfo info = persistenceService.queryTask(taskId);
        if (info == null) {
            return null;
        }
        return toMemoryRecord(info);
    }

    /**
     * 标记运行中
     * @param taskId
     * @param step
     */
    public void markRunning(String taskId, String step) {
        AgentTaskRecord record = taskStore.get(taskId);
        if (record != null) {
            record.setStatus(AgentTaskRecord.TaskStatus.RUNNING);
            record.setCurrentStep(step);
            appendLog(taskId, step, "RUNNING", "任务开始运行");
        }
        persistenceService.markRunning(taskId);
    }

    /**
     * 标记完成（捕获 AgentResult）
     * @param taskId
     * @param result
     */
    public void markCompleted(String taskId, AgentResult result) {
        AgentTaskRecord record = taskStore.get(taskId);
        long durationMs = record != null && record.getStartedAt() != null
                ? Duration.between(record.getStartedAt(), LocalDateTime.now()).toMillis()
                : 0L;

        String outputText = "";
        String outputJson = null;
        TokenMetrics tokenMetrics = null;
        List<OutputBlock> outputBlocks = null;

        if (result != null) {
            outputText = result.getOutputAsText();
            if (result.getOutput() != null && !result.getOutput().isEmpty()) {
                outputBlocks = result.getOutput();
                outputJson = serializeOutputBlocks(outputBlocks);
            }
            tokenMetrics = result.getTokenMetrics();
        }

        if (record != null) {
            record.setStatus(AgentTaskRecord.TaskStatus.SUCCEEDED);
            record.setCurrentStep("done");
            record.setFinishedAt(LocalDateTime.now());
            record.setOutputBlocks(outputBlocks);
            record.setTokenMetrics(tokenMetrics);
            record.setDurationMs(durationMs);
            appendLog(taskId, "done", "SUCCEEDED", "任务执行完成");

            // 持久化步骤
            if (record.getStepRecorder() != null) {
                List<AgentTaskStepInfo> steps = record.getStepRecorder().drainSteps();
                if (!steps.isEmpty()) {
                    persistenceService.saveSteps(steps);
                }
            }
        }

        persistenceService.markSucceeded(taskId, outputText, outputJson, tokenMetrics, durationMs);
        publishRunUsage(record, "SUCCEEDED", tokenMetrics, durationMs);
    }

    /**
     * 标记失败
     * @param taskId
     * @param step
     * @param error
     */
    public void markFailed(String taskId, String step, String error) {
        AgentTaskRecord record = taskStore.get(taskId);
        long durationMs = record != null && record.getStartedAt() != null
                ? Duration.between(record.getStartedAt(), LocalDateTime.now()).toMillis()
                : 0L;

        if (record != null) {
            record.setStatus(AgentTaskRecord.TaskStatus.FAILED);
            record.setCurrentStep(step);
            record.setFinishedAt(LocalDateTime.now());
            record.setErrorMessage(error);
            record.setDurationMs(durationMs);
            appendLog(taskId, step, "FAILED", error);

            // 失败时也持久化已采集的步骤
            if (record.getStepRecorder() != null) {
                List<AgentTaskStepInfo> steps = record.getStepRecorder().drainSteps();
                if (!steps.isEmpty()) {
                    persistenceService.saveSteps(steps);
                }
            }
        }

        persistenceService.markFailed(taskId, error, durationMs);
        publishRunUsage(record, "FAILED", record != null ? record.getTokenMetrics() : null, durationMs);
    }

    /**
     * 取消异步任务（仅PENDING/RUNNING状态可取消，终态任务返回false）
     * @param taskId
     * @param reason
     * @return 是否成功发起取消
     */
    public boolean cancel(String taskId, String reason) {
        AgentTaskRecord record = taskStore.get(taskId);
        if (record == null) {
            // 内存 miss 时查 DB（如服务重启后的残留任务）
            AgentTaskInfo info = persistenceService.queryTask(taskId);
            if (info == null) {
                return false;
            }
            record = toMemoryRecord(info);
        }
        AgentTaskRecord.TaskStatus status = record.getStatus();
        if (status == AgentTaskRecord.TaskStatus.SUCCEEDED
                || status == AgentTaskRecord.TaskStatus.FAILED
                || status == AgentTaskRecord.TaskStatus.CANCELLED) {
            log.info("任务已处于终态，无法取消: taskId={}, status={}", taskId, status);
            return false;
        }
        // 标记取消（含DB落库）
        markCancelled(taskId, reason);
        // 中断执行线程（若任务在运行或排队中）
        Future<?> future = taskFutures.get(taskId);
        if (future != null) {
            boolean interrupted = future.cancel(true);
            if (interrupted) {
                log.info("已向任务发出取消信号: taskId={}, interrupted={}", taskId, interrupted);
            }
        }
        return true;
    }

    /**
     * 标记任务取消
     * @param taskId
     * @param reason
     */
    public void markCancelled(String taskId, String reason) {
        AgentTaskRecord record = taskStore.get(taskId);
        long durationMs = record != null && record.getStartedAt() != null
                ? Duration.between(record.getStartedAt(), LocalDateTime.now()).toMillis()
                : 0L;

        if (record != null) {
            record.setStatus(AgentTaskRecord.TaskStatus.CANCELLED);
            record.setCurrentStep("cancelled");
            record.setFinishedAt(LocalDateTime.now());
            record.setErrorMessage(reason);
            record.setDurationMs(durationMs);
            appendLog(taskId, "cancelled", "CANCELLED", reason);

            // 取消时也持久化已采集的步骤
            if (record.getStepRecorder() != null) {
                List<AgentTaskStepInfo> steps = record.getStepRecorder().drainSteps();
                if (!steps.isEmpty()) {
                    persistenceService.saveSteps(steps);
                }
            }
        }

        persistenceService.updateTaskStatus(taskId, "CANCELLED", "", durationMs, null);
        publishRunUsage(record, "CANCELLED", record != null ? record.getTokenMetrics() : null, durationMs);
    }

    /**
     * 发布run级用量（单run单条上报，recordId=UUID幂等）
     * <p>
     * 计量异常仅记录告警，不影响任务收尾。
     * </p>
     * @param record 任务记录
     * @param status 任务结果状态
     * @param tokenMetrics Token用量
     * @param durationMs 执行时长
     */
    private void publishRunUsage(AgentTaskRecord record, String status, TokenMetrics tokenMetrics, Long durationMs) {
        if (usageListener == null || record == null) {
            return;
        }
        try {
            usageListener.onUsage(new UsageRecord(
                    java.util.UUID.randomUUID().toString(), null, record.getTaskId(),
                    record.getAgentCode(), record.getModelCode(),
                    tokenMetrics != null ? tokenMetrics.getInputTokens() : 0,
                    tokenMetrics != null ? tokenMetrics.getOutputTokens() : 0,
                    tokenMetrics != null ? tokenMetrics.getTotalTokens() : 0,
                    durationMs != null ? durationMs : 0, status,
                    record.getScopeId(), record.getUserId()));
        } catch (Exception e) {
            log.warn("run级用量上报失败: taskId={}", record.getTaskId(), e);
        }
    }

    /**
     * 追加事件日志
     * @param taskId
     * @param step
     * @param type
     * @param message
     */
    public void appendLog(String taskId, String step, String type, String message) {
        AgentTaskRecord record = taskStore.get(taskId);
        if (record != null) {
            TaskEventLog eventLog = new TaskEventLog(type, step, message, null, LocalDateTime.now());
            record.getEvents().add(eventLog);
        }
    }

    /**
     * 启动时无条件恢复所有未完成任务（服务重启前的残留，安全标记为失败）
     * @return
     */
    public int recoverAllPending() {
        int memoryCount = 0;
        List<AgentTaskRecord> pendingTasks = new ArrayList<>();
        for (AgentTaskRecord record : taskStore.values()) {
            if (record.getStatus() == AgentTaskRecord.TaskStatus.PENDING
                    || record.getStatus() == AgentTaskRecord.TaskStatus.RUNNING) {
                pendingTasks.add(record);
            }
        }
        for (AgentTaskRecord record : pendingTasks) {
            AgentTaskRecord.TaskStatus prevStatus = record.getStatus();
            record.setStatus(AgentTaskRecord.TaskStatus.FAILED);
            record.setFinishedAt(LocalDateTime.now());
            record.setErrorMessage("服务重启，任务恢复时标记为失败");
            log.info("启动恢复未完成任务(内存): taskId={}, prevStatus={}", record.getTaskId(), prevStatus);
            memoryCount++;
        }
        int dbCount = persistenceService.recoverPendingTasks();
        return memoryCount + dbCount;
    }

    /**
     * 恢复未完成任务（仅处理超时的孤儿任务，避免误杀正在运行的任务）
     * @return
     */
    public int recoverPending() {
        LocalDateTime now = LocalDateTime.now();
        int memoryCount = 0;

        // 内存中仅恢复超时的 PENDING/RUNNING 任务
        List<AgentTaskRecord> staleTasks = new ArrayList<>();
        for (AgentTaskRecord record : taskStore.values()) {
            if (record.getStatus() == AgentTaskRecord.TaskStatus.PENDING) {
                // PENDING 超过10分钟仍未运行，视为孤儿任务
                if (record.getStartedAt() != null && record.getStartedAt().isBefore(now.minusMinutes(10))) {
                    staleTasks.add(record);
                }
            } else if (record.getStatus() == AgentTaskRecord.TaskStatus.RUNNING) {
                // RUNNING 超过30分钟仍未完成，视为卡死任务
                if (record.getStartedAt() != null && record.getStartedAt().isBefore(now.minusMinutes(30))) {
                    staleTasks.add(record);
                }
            }
        }
        for (AgentTaskRecord record : staleTasks) {
            AgentTaskRecord.TaskStatus prevStatus = record.getStatus();
            record.setStatus(AgentTaskRecord.TaskStatus.FAILED);
            record.setFinishedAt(now);
            record.setErrorMessage("任务超时未完成，被恢复调度标记为失败");
            log.info("恢复超时任务(内存): taskId={}, prevStatus={}, status=FAILED", record.getTaskId(), prevStatus);
            memoryCount++;
        }

        // DB 恢复（带超时判断）
        int dbCount = persistenceService.recoverStaleTasks();
        return memoryCount + dbCount;
    }

    /**
     * 定时清理过期任务（每小时执行，仅清理内存缓存）
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredTasks() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(EXPIRY_HOURS);
        List<String> expiredTaskIds = new ArrayList<>();
        for (Map.Entry<String, AgentTaskRecord> entry : taskStore.entrySet()) {
            AgentTaskRecord record = entry.getValue();
            if ((record.getStatus() == AgentTaskRecord.TaskStatus.SUCCEEDED
                    || record.getStatus() == AgentTaskRecord.TaskStatus.FAILED)
                    && record.getStartedAt() != null
                    && record.getStartedAt().isBefore(expiryThreshold)) {
                expiredTaskIds.add(entry.getKey());
            }
        }
        for (String taskId : expiredTaskIds) {
            taskStore.remove(taskId);
            taskFutures.remove(taskId);
            log.info("清理过期任务(内存): taskId={}", taskId);
        }
        if (!expiredTaskIds.isEmpty()) {
            log.info("过期任务清理完成: cleanedCount={}, remainingCount={}",
                    expiredTaskIds.size(), taskStore.size());
        }
    }

    /**
     * 容量超限时移除最旧的任务（仅内存）
     */
    private void evictIfOverCapacity() {
        while (taskStore.size() >= MAX_TASKS) {
            String oldestTaskId = taskStore.entrySet().stream()
                    .filter(e -> e.getValue().getStartedAt() != null)
                    .min(Comparator.comparing(e -> e.getValue().getStartedAt()))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldestTaskId != null) {
                taskStore.remove(oldestTaskId);
                taskFutures.remove(oldestTaskId);
                log.info("容量超限移除最旧任务(内存): taskId={}, currentSize={}", oldestTaskId, taskStore.size());
            } else {
                break;
            }
        }
    }

    /**
     * 读取分叉点模型调用序号（轨迹分叉场景，缺失或非法时返回null）
     * @param request
     * @return
     */
    private Integer readForkCallSeq(AgentRequest request) {
        Object value = request.getBody().get(AgentRequest.BodyKeys.FORK_CALL_SEQ);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 创建 DB 任务记录
     * @param record
     * @param request
     */
    private void createDbTask(AgentTaskRecord record, AgentRequest request) {
        try {
            AgentTaskInfo info = new AgentTaskInfo();
            info.setTaskId(record.getTaskId());
            info.setParentTaskId(record.getParentTaskId());
            info.setForkCallSeq(record.getForkCallSeq());
            info.setAgentPath(record.getAgentPath());
            info.setAgentCode(record.getAgentCode());
            info.setAgentName(record.getAgentName());
            info.setSessionId(record.getSessionId());
            info.setUserId(record.getUserId());
            info.setScopeId(request.getScopeId());
            info.setTaskStatus("PENDING");
            info.setCreateTime(LocalDateTime.now());

            // 用户输入
            String inputText = request.getInputAsText();
            info.setUserInput(inputText);
            List<InputBlock> inputBlocks = request.getInput();
            if (inputBlocks != null && !inputBlocks.isEmpty()) {
                info.setUserInputJson(serializeInputBlocks(inputBlocks));
            }

            // body
            info.setBody(serializeBody(request.getBody()));

            persistenceService.createTask(info);
        } catch (Exception e) {
            log.error("创建DB任务记录失败: taskId={}", record.getTaskId(), e);
        }
    }

    /**
     * DB 实体转内存记录
     * @param entity
     * @return
     */
    private AgentTaskRecord toMemoryRecord(AgentTaskInfo info) {
        AgentTaskRecord record = new AgentTaskRecord();
        record.setTaskId(info.getTaskId());
        record.setParentTaskId(info.getParentTaskId());
        record.setForkCallSeq(info.getForkCallSeq());
        record.setAgentPath(info.getAgentPath());
        record.setAgentCode(info.getAgentCode());
        record.setAgentName(info.getAgentName());
        record.setSessionId(info.getSessionId());
        record.setUserId(info.getUserId());
        record.setScopeId(info.getScopeId());
        record.setStatus(AgentTaskRecord.TaskStatus.valueOf(info.getTaskStatus()));
        record.setStartedAt(info.getCreateTime());
        record.setFinishedAt(info.getUpdateTime());
        record.setErrorMessage(info.getErrorMessage());
        record.setDurationMs(info.getDurationMs());

        // 还原DB持久化的输出块，保证重启后任务结果可查
        List<OutputBlock> outputBlocks = deserializeOutputBlocks(info.getOutputJson());
        if (outputBlocks == null && info.getOutputText() != null && !info.getOutputText().isBlank()) {
            // 输出块JSON缺失或不可解析时回退纯文本输出
            outputBlocks = List.of(TextOutputBlock.of(info.getOutputText()));
        }
        if (outputBlocks != null) {
            record.setOutputBlocks(outputBlocks);
        }

        if (info.getInputTokens() != null || info.getOutputTokens() != null
                || info.getTotalTokens() != null) {
            TokenMetrics metrics = new TokenMetrics(
                    info.getInputTokens() != null ? info.getInputTokens() : 0,
                    info.getOutputTokens() != null ? info.getOutputTokens() : 0,
                    info.getTotalTokens() != null ? info.getTotalTokens() : 0,
                    info.getExecutionTime() != null ? info.getExecutionTime() : 0
            );
            record.setTokenMetrics(metrics);
        }
        return record;
    }

    /**
     * 序列化 OutputBlock 列表为 JSON
     * @param blocks
     * @return
     */
    private String serializeOutputBlocks(List<OutputBlock> blocks) {
        try {
            return OBJECT_MAPPER.writeValueAsString(blocks);
        } catch (Exception e) {
            log.warn("序列化OutputBlock失败", e);
            return null;
        }
    }

    /**
     * 反序列化 JSON 为 OutputBlock 列表
     * @param json
     * @return
     */
    private List<OutputBlock> deserializeOutputBlocks(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<OutputBlock>>() {
            });
        } catch (Exception e) {
            // 兼容缺少type多态标识的JSON，按文本块解析
            try {
                List<Map<String, Object>> raw = OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
                });
                List<OutputBlock> blocks = new ArrayList<>();
                for (Map<String, Object> item : raw) {
                    Object text = item.get("text");
                    if (text != null) {
                        blocks.add(TextOutputBlock.of(text.toString()));
                    }
                }
                return blocks.isEmpty() ? null : blocks;
            } catch (Exception ex) {
                log.warn("反序列化OutputBlock失败", ex);
                return null;
            }
        }
    }

    /**
     * 序列化 InputBlock 列表为 JSON
     * @param blocks
     * @return
     */
    private String serializeInputBlocks(List<InputBlock> blocks) {
        try {
            return OBJECT_MAPPER.writeValueAsString(blocks);
        } catch (Exception e) {
            log.warn("序列化InputBlock失败", e);
            return null;
        }
    }

    /**
     * 序列化 body 为 JSON
     * @param body
     * @return
     */
    private String serializeBody(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("序列化body失败", e);
            return null;
        }
    }

    /**
     * 容器销毁时优雅关闭
     */
    @PreDestroy
    public void onDestroy() {
        shutdown();
    }

    /**
     * 优雅关闭执行器，记录未完成任务
     */
    public void shutdown() {
        for (AgentTaskRecord record : taskStore.values()) {
            if (record.getStatus() != AgentTaskRecord.TaskStatus.SUCCEEDED) {
                log.warn("未完成任务: taskId={}, status={}, currentStep={}",
                        record.getTaskId(), record.getStatus(), record.getCurrentStep());
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("执行器未在5秒内完成关闭，已强制终止");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
