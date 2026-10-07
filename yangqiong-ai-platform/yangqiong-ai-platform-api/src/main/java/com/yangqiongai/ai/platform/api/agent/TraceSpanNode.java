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
package com.yangqiongai.ai.platform.api.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行Span树节点
 * @author yangqiong
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceSpanNode {

    /**
     * Span ID
     */
    private String spanId;

    /**
     * 父Span ID
     */
    private String parentSpanId;

    /**
     * 追踪ID
     */
    private String traceId;

    /**
     * 操作名(agent_run/reasoning/acting/model_call/tool_call/middleware)
     */
    private String operation;

    /**
     * 状态(OK/ERROR)
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 耗时(毫秒)
     */
    private Long durationMs;

    /**
     * 开始时间(ISO格式)
     */
    private String startTime;

    /**
     * 属性标签
     */
    private Object attributes;

    /**
     * 子Span列表
     */
    private List<TraceSpanNode> children = new ArrayList<>();

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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
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

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Object getAttributes() {
        return attributes;
    }

    public void setAttributes(Object attributes) {
        this.attributes = attributes;
    }

    public List<TraceSpanNode> getChildren() {
        return children;
    }

    public void setChildren(List<TraceSpanNode> children) {
        this.children = children;
    }
}
