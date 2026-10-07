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
package com.yangqiongai.ai.agent.data.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * Agent任务
 * @author yangqiong
 */
@TableName("ai_agent_task")
public class AgentTaskEntity extends ScopeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

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
     * 代理调用路径（如 main>research>sql）
     */
    private String agentPath;

    /**
     * 代理编码
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
     * 用户输入（纯文本，多模态的文本部分）
     */
    private String userInput;

    /**
     * 用户输入（多模态JSON，List<InputBlock>）
     */
    private String userInputJson;

    /**
     * 输出文本（多模态的文本部分）
     */
    private String outputText;

    /**
     * 输出（多模态JSON，List<OutputBlock>）
     */
    private String outputJson;

    /**
     * 状态(PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED)
     */
    private String taskStatus;

    /**
     * 优先级0-9(大者先执行)
     */
    private Integer priority;

    /**
     * 入队时间
     */
    private LocalDateTime queuedTime;

    /**
     * 执行实例标识(hostname:port:uuid)
     */
    private String runnerId;

    /**
     * 执行实例心跳
     */
    private LocalDateTime runnerHeartbeat;

    /**
     * 重派次数
     */
    private Integer redeliverCount;

    /**
     * 任务来源(SYNC/STREAM/ASYNC)
     */
    private String taskSource;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 真实输入Token数
     */
    private Integer inputTokens;

    /**
     * 真实输出Token数
     */
    private Integer outputTokens;

    /**
     * 真实总Token数
     */
    private Integer totalTokens;

    /**
     * 模型执行耗时（秒）
     */
    private Double executionTime;

    /**
     * 任务总执行时长（毫秒）
     */
    private Long durationMs;

    /**
     * 请求数据体
     */
    private String body;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public Integer getForkCallSeq() { return forkCallSeq; }
    public void setForkCallSeq(Integer forkCallSeq) { this.forkCallSeq = forkCallSeq; }
    public String getAgentPath() { return agentPath; }
    public void setAgentPath(String agentPath) { this.agentPath = agentPath; }
    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public String getUserInputJson() { return userInputJson; }
    public void setUserInputJson(String userInputJson) { this.userInputJson = userInputJson; }
    public String getOutputText() { return outputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }
    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String outputJson) { this.outputJson = outputJson; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public LocalDateTime getQueuedTime() { return queuedTime; }
    public void setQueuedTime(LocalDateTime queuedTime) { this.queuedTime = queuedTime; }
    public String getRunnerId() { return runnerId; }
    public void setRunnerId(String runnerId) { this.runnerId = runnerId; }
    public LocalDateTime getRunnerHeartbeat() { return runnerHeartbeat; }
    public void setRunnerHeartbeat(LocalDateTime runnerHeartbeat) { this.runnerHeartbeat = runnerHeartbeat; }
    public Integer getRedeliverCount() { return redeliverCount; }
    public void setRedeliverCount(Integer redeliverCount) { this.redeliverCount = redeliverCount; }
    public String getTaskSource() { return taskSource; }
    public void setTaskSource(String taskSource) { this.taskSource = taskSource; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Double getExecutionTime() { return executionTime; }
    public void setExecutionTime(Double executionTime) { this.executionTime = executionTime; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
