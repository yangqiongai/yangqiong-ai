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
package com.yangqiongai.ai.open.capability.engine;

import java.util.Map;

/**
 * 能力调用响应
 * @author yangqiong
 */
public class CapabilityResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 输出文本
     */
    private String output;

    /**
     * 结构化输出（JSON解析后的对象）
     */
    private Object structuredOutput;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 调用ID
     */
    private String callId;

    /**
     * Token消耗
     */
    private Map<String, Object> tokenMetrics;

    /**
     * 耗时（毫秒）
     */
    private long durationMillis;

    public static CapabilityResponse success(String output, Object structuredOutput) {
        CapabilityResponse r = new CapabilityResponse();
        r.success = true;
        r.output = output;
        r.structuredOutput = structuredOutput;
        return r;
    }

    public static CapabilityResponse failure(String errorMessage) {
        CapabilityResponse r = new CapabilityResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Object getStructuredOutput() {
        return structuredOutput;
    }

    public void setStructuredOutput(Object structuredOutput) {
        this.structuredOutput = structuredOutput;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public Map<String, Object> getTokenMetrics() {
        return tokenMetrics;
    }

    public void setTokenMetrics(Map<String, Object> tokenMetrics) {
        this.tokenMetrics = tokenMetrics;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }
}