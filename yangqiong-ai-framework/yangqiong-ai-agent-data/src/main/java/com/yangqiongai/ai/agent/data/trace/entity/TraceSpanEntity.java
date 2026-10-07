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
package com.yangqiongai.ai.agent.data.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * Agent运行Span
 * @author yangqiong
 */
@TableName("ai_agent_trace_span")
public class TraceSpanEntity extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 追踪ID(一次运行根span与子span共享)
     */
    private String traceId;

    /**
     * Span ID
     */
    private String spanId;

    /**
     * 父Span ID(根span为空)
     */
    private String parentSpanId;

    /**
     * 关联任务ID(ai_agent_task.task_id)
     */
    private String taskId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 操作名(agent_run/reasoning/acting/model_call/tool_call/middleware)
     */
    private String operation;

    /**
     * 状态(OK/ERROR)
     */
    private String status;

    /**
     * 错误信息(截断)
     */
    private String errorMessage;

    /**
     * 耗时(毫秒)
     */
    private Long durationMs;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 属性标签(截断后JSON)
     */
    private String attributes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }
}
