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
package com.yangqiongai.ai.agent.core.model;

import java.time.LocalDateTime;

/**
 * Agent任务步骤
 * @author yangqiong
 */
public class AgentTaskStepInfo {

    /**
     * 主键
     */
    private Long id;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 步骤序号（从1递增）
     */
    private Integer stepOrder;

    /**
     * 步骤类型(LLM_CALL/TOOL_CALL/TOOL_RESULT/SUBAGENT_CALL)
     */
    private String stepType;

    /**
     * 执行代理名称
     */
    private String agentName;

    /**
     * 步骤内容（LLM推理文本/工具输入输出摘要）
     */
    private String stepContent;

    /**
     * 工具名称（TOOL_CALL类型）
     */
    private String toolName;

    /**
     * 工具输入（JSON）
     */
    private String toolInput;

    /**
     * 工具输出（文本摘要）
     */
    private String toolOutput;

    /**
     * 步骤输入Token数（LLM_CALL）
     */
    private Integer inputTokens;

    /**
     * 步骤输出Token数（LLM_CALL）
     */
    private Integer outputTokens;

    /**
     * 步骤总Token数（LLM_CALL）
     */
    private Integer totalTokens;

    /**
     * 步骤执行时长（毫秒）
     */
    private Long durationMs;

    /**
     * 权限决策(ALLOW/ASK/DENY)
     */
    private String permissionDecision;

    /**
     * 工具退出码
     */
    private Integer exitCode;

    /**
     * 模型调用序号(LLM_CALL步骤，从1递增，与上下文快照callSeq一致；查询时动态计算非持久化)
     */
    private Integer callSeq;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    public Integer getCallSeq() {
        return callSeq;
    }

    public void setCallSeq(Integer callSeq) {
        this.callSeq = callSeq;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getStepContent() {
        return stepContent;
    }

    public void setStepContent(String stepContent) {
        this.stepContent = stepContent;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolInput() {
        return toolInput;
    }

    public void setToolInput(String toolInput) {
        this.toolInput = toolInput;
    }

    public String getToolOutput() {
        return toolOutput;
    }

    public void setToolOutput(String toolOutput) {
        this.toolOutput = toolOutput;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getPermissionDecision() {
        return permissionDecision;
    }

    public void setPermissionDecision(String permissionDecision) {
        this.permissionDecision = permissionDecision;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
