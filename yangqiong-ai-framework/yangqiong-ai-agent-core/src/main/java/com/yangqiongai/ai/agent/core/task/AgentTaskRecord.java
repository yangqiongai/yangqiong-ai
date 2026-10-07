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
import com.yangqiongai.ai.agent.core.model.content.OutputBlock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent任务记录
 * @author yangqiong
 */
public class AgentTaskRecord {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 父任务ID（子代理/轨迹分叉场景）
     */
    private String parentTaskId;

    /**
     * 分叉点：来源任务的模型调用序号（轨迹分叉场景）
     */
    private Integer forkCallSeq;

    /**
     * 代理调用路径
     */
    private String agentPath;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 代理显示名称
     */
    private String agentName;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 主模型编码
     */
    private String modelCode;

    /**
     * 状态
     */
    private TaskStatus status;

    /**
     * 任务来源
     */
    private TaskSource taskSource;

    /**
     * 当前步骤
     */
    private String currentStep;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 输出（多模态内容块列表）
     */
    private List<OutputBlock> outputBlocks;

    /**
     * Token消耗指标
     */
    private TokenMetrics tokenMetrics;

    /**
     * 任务总执行时长（毫秒）
     */
    private Long durationMs;

    /**
     * 事件日志列表
     */
    private List<TaskEventLog> events = new CopyOnWriteArrayList<>();

    /**
     * 步骤采集器
     */
    private TaskStepRecorder stepRecorder;

    public AgentTaskRecord() {
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public Integer getForkCallSeq() {
        return forkCallSeq;
    }

    public void setForkCallSeq(Integer forkCallSeq) {
        this.forkCallSeq = forkCallSeq;
    }

    public String getAgentPath() {
        return agentPath;
    }

    public void setAgentPath(String agentPath) {
        this.agentPath = agentPath;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskSource getTaskSource() {
        return taskSource;
    }

    public void setTaskSource(TaskSource taskSource) {
        this.taskSource = taskSource;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<OutputBlock> getOutputBlocks() {
        return outputBlocks;
    }

    public void setOutputBlocks(List<OutputBlock> outputBlocks) {
        this.outputBlocks = outputBlocks;
    }

    public TokenMetrics getTokenMetrics() {
        return tokenMetrics;
    }

    public void setTokenMetrics(TokenMetrics tokenMetrics) {
        this.tokenMetrics = tokenMetrics;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public List<TaskEventLog> getEvents() {
        return events;
    }

    public void setEvents(List<TaskEventLog> events) {
        this.events = events;
    }

    public TaskStepRecorder getStepRecorder() {
        return stepRecorder;
    }

    public void setStepRecorder(TaskStepRecorder stepRecorder) {
        this.stepRecorder = stepRecorder;
    }

    /**
     * 任务状态枚举（对齐数据库 PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED）
     */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }
}
